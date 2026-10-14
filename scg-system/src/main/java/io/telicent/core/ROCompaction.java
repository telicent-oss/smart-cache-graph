/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.telicent.core;

import org.apache.jena.atlas.io.IO;
import org.apache.jena.dboe.base.file.Location;
import org.apache.jena.dboe.storage.system.DatasetGraphTxnCtl;
import org.apache.jena.dboe.sys.Names;
import org.apache.jena.dboe.transaction.txn.TransactionCoordinator;
import org.apache.jena.tdb2.TDBException;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.apache.jena.tdb2.store.DatasetGraphTDB;
import org.apache.jena.tdb2.sys.CopyDSG;
import org.apache.jena.tdb2.sys.DatabaseOps;
import org.apache.jena.tdb2.sys.StoreConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Compacts a TDB2 database while it continues to serve reads.
 * <p>
 * Adapted from Apache Jena's {@code DatabaseOps.compact} (Apache License 2.0). Jena's version blocks writers while it
 * copies the database, then switches the {@link DatasetGraphSwitchable} to the copy while readers are still active.
 * A read transaction that began before the switch then ends on the new database rather than the one it began on, so
 * the old database never sees it finish and Jena waits forever to retire it (see apache/jena#3488). This version takes
 * the switchable's exclusive mode just for the switch itself. HTTP request admission closes first,
 * accepted requests drain, and new dataset requests receive HTTP 503, so none queue behind the switch. If that window can't be obtained in time, e.g. because a writer
 * is queued behind the copy, the copy is discarded and the database is left unchanged rather than waiting forever.
 * </p>
 */
final class ROCompaction {

    private static final Logger LOG = LoggerFactory.getLogger(ROCompaction.class);
    private static final String TMP_SUFFIX = "-tmp";
    private static final Pattern INDEX = Pattern.compile("\\d+");
    private static final Object COMPACTION_LOCK = new Object();
    private static final Field EXCLUSIVITY_LOCK = findExclusivityLock();

    /**
     * Thrown when a compaction is abandoned before switching over; the dataset is unchanged
     */
    static final class SwitchAbandonedException extends RuntimeException {
        SwitchAbandonedException(String message) {
            super(message);
        }
    }

    private ROCompaction() {
    }

    /**
     * Compacts the database, blocking writers (but not readers) while it copies
     *
     * @param container     Switchable dataset
     * @param deleteOld     Whether to delete the old database once it is no longer in use
     * @param switchTimeout How long to wait for in-flight transactions to finish so the switch can happen, and for the
     *                      old database to become idle afterward
     * @throws SwitchAbandonedException if the switch couldn't happen in time; the compacted copy is deleted
     */
    static void compact(DatasetGraphSwitchable container, boolean deleteOld, Duration switchTimeout) {
        synchronized (COMPACTION_LOCK) {
            Path containerPath = container.getContainerPath();
            Path db1 = DatabaseOps.findStorageLocation(containerPath);
            if (db1 == null) {
                throw new TDBException("No database storage in " + containerPath);
            }
            Location loc1 = Location.create(db1);
            if (!(container.get() instanceof DatasetGraphTDB current)) {
                throw new TDBException("Not a TDB2 database: " + containerPath);
            }
            if (!current.getLocation().equals(loc1)) {
                throw new TDBException("Inconsistent (not latest?) : " + current.getLocation() + " : " + loc1);
            }
            Path db2 = db1.resolveSibling(String.format("%s-%04d", DatabaseOps.dbNameBase, extractIndex(db1) + 1));
            Path tmpDir = Path.of(db2 + TMP_SUFFIX);
            if (Files.exists(db2) || Files.exists(tmpDir)) {
                throw new TDBException("Inconsistent : " + db2 + " (or its temporary copy) already exists");
            }
            try {
                Files.createDirectory(tmpDir);
            } catch (IOException e) {
                throw new TDBException("Unable to create " + tmpDir, e);
            }

            boolean switched = false;
            try {
                copyAndSwitch(container, current, Location.create(tmpDir), db2, switchTimeout);
                switched = true;
            } finally {
                if (!switched) {
                    discard(tmpDir);
                    discard(db2);
                }
            }
            retire(current, db1, deleteOld, switchTimeout);
        }
    }

    private static void copyAndSwitch(DatasetGraphSwitchable container, DatasetGraphTDB current, Location tmp,
                                      Path db2, Duration switchTimeout) {
        // Holds back new writers on the switchable until the end of the lambda; readers carry on
        container.execReadOnlyDatabase(() -> {
            copyConfigFiles(current.getLocation(), tmp);
            // StoreConnection caches stores by location. A store left cached here by an earlier failed attempt would
            // point at files that have since been deleted, so never reuse one.
            StoreConnection.internalExpel(tmp, true);
            try {
                DatasetGraphTDB copy = StoreConnection.connectCreate(tmp).getDatasetGraphTDB();
                CopyDSG.copy(current, copy);
            } finally {
                // Always close and uncache the temporary store, whether or not the copy succeeded, before its directory
                // is moved into place or (on failure) deleted. Otherwise a retry, which uses the same directory name,
                // would get the cached store back and write into deleted files, then switch to an empty database and
                // delete the original.
                StoreConnection.internalExpel(tmp, true);
            }
            try {
                Files.move(Path.of(tmp.getDirectoryPath()), db2);
            } catch (IOException e) {
                throw new TDBException("Unable to move compacted copy into place at " + db2, e);
            }
            Location loc2 = Location.create(db2);
            DatasetGraphTDB compacted = StoreConnection.connectCreate(loc2).getDatasetGraphTDB();

            boolean switched = false;
            try {
                switched = runExclusively(container, switchTimeout, () -> switchTo(container, current, compacted));
            } finally {
                if (!switched) {
                    StoreConnection.internalExpel(loc2, true);
                }
            }
            if (!switched) {
                throw new SwitchAbandonedException(
                        "In-flight transactions did not finish within " + switchTimeout + " so the compacted copy " +
                        "was discarded and the database is unchanged");
            }
        });
    }

    private static void switchTo(DatasetGraphSwitchable container, DatasetGraphTDB current,
                                 DatasetGraphTDB compacted) {
        TransactionCoordinator txnMgr1 = current.getTxnSystem().getTxnMgr();
        TransactionCoordinator txnMgr2 = compacted.getTxnSystem().getTxnMgr();
        txnMgr2.execExclusive(() -> {
            txnMgr2.takeOverFrom(txnMgr1);
            txnMgr2.modifyConfigDirect(() -> txnMgr1.listExternals().forEach(txnMgr2::addExternal));
            if (!container.change(current, compacted)) {
                LOG.warn("Inconsistent: switchable dataset was not using the expected database, switching anyway");
                container.set(compacted);
            }
        });
    }

    /**
     * Closes HTTP request admission, drains accepted requests, then runs an action in exclusive mode. No transactions
     * are active on the switchable and none can
     * start.
     * <p>
     * Jena only offers to wait for exclusive mode indefinitely, or to try once without waiting, and with steady query
     * traffic a single attempt rarely finds a moment with no transactions. Waiting indefinitely isn't safe either:
     * while a request for exclusive mode is queued, new transactions queue behind it, so one long query would hold up
     * every other one. So this waits on the switchable's exclusivity lock directly with a timeout: while it waits, new
     * transactions queue so the in-flight ones drain; when it times out it leaves the queue and they carry on.
     * </p>
     *
     * @return Whether the action ran
     */
    static boolean runExclusively(DatasetGraphSwitchable container, Duration timeout, Runnable action) {
        return runExclusively(container, exclusivityLock(container), timeout, action);
    }

    /**
     * @param exclusivityLock The switchable's exclusivity lock, or {@code null} if it isn't accessible, in which case
     *                        exclusive mode is polled for instead
     */
    static boolean runExclusively(DatasetGraphSwitchable container, Lock exclusivityLock, Duration timeout,
                                  Runnable action) {
        long deadline = System.nanoTime() + timeout.toNanos();
        try (DatasetMaintenanceRegistry.SwitchWindow window = DatasetMaintenanceRegistry.beginSwitch(container)) {
            if (!window.awaitRequests(deadline)) {
                return false;
            }
            Duration remaining = Duration.ofNanos(Math.max(0, deadline - System.nanoTime()));
            if (exclusivityLock == null) {
                return pollExclusively(container, remaining, action);
            }
            if (!exclusivityLock.tryLock(remaining.toNanos(), TimeUnit.NANOSECONDS)) {
                return false;
            }
            try {
                action.run();
                return true;
            } finally {
                exclusivityLock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Repeatedly tries for exclusive mode without queueing for it, so transactions are never held back, at the cost
     * of possibly never finding a moment when none are active
     */
    private static boolean pollExclusively(DatasetGraphSwitchable container, Duration timeout, Runnable action) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!container.tryExclusiveMode(false)) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        try {
            action.run();
            return true;
        } finally {
            container.finishExclusiveMode();
        }
    }

    /**
     * @return The write side of the switchable's exclusivity lock (which {@code startExclusiveMode} takes), or
     * {@code null} if this version of Jena doesn't have it
     */
    static Lock exclusivityLock(DatasetGraphSwitchable container) {
        if (EXCLUSIVITY_LOCK == null) {
            return null;
        }
        try {
            return ((ReadWriteLock) EXCLUSIVITY_LOCK.get(container)).writeLock();
        } catch (IllegalAccessException | ClassCastException e) {
            return null;
        }
    }

    private static Field findExclusivityLock() {
        try {
            Field field = DatasetGraphTxnCtl.class.getDeclaredField("exclusivitylock");
            if (!ReadWriteLock.class.isAssignableFrom(field.getType())) {
                throw new NoSuchFieldException("exclusivitylock is a " + field.getType());
            }
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException e) {
            LOG.warn("Unable to access Jena's dataset exclusivity lock ({}); RO Compaction will only switch over " +
                     "when it finds a moment with no active transactions", e.toString());
            return null;
        }
    }

    /**
     * Shuts down the old database once nothing is using it, then optionally deletes it
     */
    private static void retire(DatasetGraphTDB old, Path db1, boolean deleteOld, Duration timeout) {
        // No transactions went through the switchable to the old database once it switched, so this is normally
        // immediate; it is bounded in case something holds a transaction on the old database directly.
        TransactionCoordinator txnMgr1 = old.getTxnSystem().getTxnMgr();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!txnMgr1.tryExclusiveMode(false)) {
            if (System.nanoTime() >= deadline) {
                LOG.warn("Old database {} still has active transactions after {}; leaving it in place", db1, timeout);
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Interrupted waiting to retire old database {}; leaving it in place", db1);
                return;
            }
        }
        StoreConnection.release(old.getLocation());
        if (deleteOld) {
            LOG.debug("Deleting old database {} after successful compaction", db1);
            IO.deleteAll(db1);
        }
    }

    private static void copyConfigFiles(Location from, Location to) {
        try (Stream<Path> files = Files.list(Path.of(from.getDirectoryPath()))) {
            for (Path f : files.filter(ROCompaction::isConfigFile).toList()) {
                Files.copy(f, Path.of(to.getPath(f.getFileName().toString())));
            }
        } catch (IOException e) {
            throw new TDBException("Unable to copy configuration files from " + from, e);
        }
    }

    private static boolean isConfigFile(Path path) {
        String name = path.getFileName().toString();
        return name.equals(Names.TDB_CONFIG_FILE) || name.endsWith(".opt");
    }

    private static int extractIndex(Path db) {
        Matcher matcher = INDEX.matcher(db.getFileName().toString());
        if (!matcher.find()) {
            throw new TDBException("Expected a numbered database directory: " + db);
        }
        return Integer.parseInt(matcher.group());
    }

    private static void discard(Path dir) {
        if (Files.exists(dir)) {
            try {
                IO.deleteAll(dir);
            } catch (RuntimeException e) {
                LOG.warn("Unable to delete abandoned compaction copy {}", dir, e);
            }
        }
    }
}
