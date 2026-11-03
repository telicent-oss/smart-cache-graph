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
package io.telicent.backup.services;

import io.telicent.backup.utils.BackupUtils;
import io.telicent.core.LabelsMaintenanceTestSupport;
import io.telicent.jena.abac.ABAC;
import io.telicent.jena.abac.SysABAC;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.Label;
import io.telicent.jena.abac.labels.StoreFmtByHash;
import io.telicent.jena.abac.labels.hashing.HasherUtil;
import io.telicent.jena.abac.labels.store.rocksdb.modern.DictionaryLabelStoreRocksDB;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPlugin;
import io.telicent.smart.cache.security.data.plugins.rdf.abac.RdfAbacPlugin;
import io.telicent.smart.cache.storage.RestoreConfig;
import io.telicent.smart.cache.storage.RestoreStatus;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.system.Txn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests restoring a dataset whose security labels are held in a real RocksDB backed labels store, rather than the
 * {@code LabelsStoreMem} used by the other backup/restore tests.
 * <p>
 * {@code DatasetBackupService.restoreDataset()} runs every registered restore step inside
 * {@code Txn.executeWrite(dsg, ...)}, and {@code DatasetGraphABAC} deliberately propagates that transaction into the
 * labels store (see its own "Propagate transactions to the labels store" comment), which for a RocksDB store opens a
 * native {@code rocksdb::Transaction}. Restoring that same store then calls
 * {@code AbstractRocksDBStorage.restore()}, which begins by closing the database -- destroying the native
 * {@code TransactionDB}, every column family handle and the shared read/write options that the in-flight transaction
 * still points at -- before reopening a fresh one. Nothing invalidates the stale transaction, so the next use of it
 * dereferences freed memory and kills the process.
 * </p>
 * <p>
 * A memory backed labels store has no native state and no transaction to invalidate, which is why the existing tests
 * pass.
 * </p>
 */
class TestRestoreWithRocksDbLabelStore {

    private static final String DATASET_NAME = "rocks-labels-ds";
    private static final String DAP_NAME = "/" + DATASET_NAME;

    @TempDir
    private Path workDir;

    private String originalBackupDir;
    private DictionaryLabelStoreRocksDB labelsStore;
    private DatasetGraph data;
    private DatasetGraphABAC dsg;

    private static Quad quad(int i) {
        return Quad.create(Quad.defaultGraphIRI,
                           NodeFactory.createURI("http://example.org/s" + i),
                           NodeFactory.createURI("http://example.org/p" + i),
                           NodeFactory.createURI("http://example.org/o" + i));
    }

    @BeforeEach
    void setup() throws Exception {
        originalBackupDir = BackupUtils.dirBackups;

        final File dbDir = workDir.resolve("labels.db").toFile();
        labelsStore = new DictionaryLabelStoreRocksDB(dbDir, new StoreFmtByHash(HasherUtil.createXX128Hasher()));
        data = DatasetGraphFactory.createTxnMem();
        dsg = ABAC.authzDataset(data, null, labelsStore, SysABAC.allowLabel, null);

        Txn.executeWrite(dsg, () -> {
            dsg.add(quad(0));
            labelsStore.add(quad(0), Label.fromText("seed"));
        });
    }

    @AfterEach
    void cleanup() {
        BackupUtils.dirBackups = originalBackupDir;
        if (labelsStore != null) {
            labelsStore.close();
        }
    }

    // -----------------------------------------------------------------------------------
    // The defect, stated directly: a store cannot be restored while the calling thread holds a
    // transaction on it, because the restore closes and recreates the native handles that the
    // transaction is built on.
    // -----------------------------------------------------------------------------------

    @Test
    void givenRocksDbLabelStore_whenRestoreDataset_thenLabelsRestoreRunsOutsideTheDatasetTransaction()
            throws Exception {
        // Given -- a dataset whose labels live in RocksDB, and a labels store whose restore() records whether the
        // calling thread is inside a transaction on that store.  It deliberately does not perform the real
        // close-and-reopen, because doing so here would abort this JVM rather than fail the test; see
        // givenRocksDbLabelStore_whenRestoreDataset_thenServerDoesNotCrash() for that.
        final var inTransactionDuringRestore = new AtomicBoolean(false);

        final var plugin = mock(DataSecurityPlugin.class);
        when(plugin.prepareLabelToNode()).thenReturn(new RdfAbacPlugin().prepareLabelToNode());
        final var recordingStore = mock(LabelsMaintenanceTestSupport.MaintainableLabelsStore.class);
        when(recordingStore.restore(any(RestoreConfig.class))).thenAnswer(invocation -> {
            inTransactionDuringRestore.set(labelsStore.getTransactional().isInTransaction());
            return RestoreStatus.builder().success(true).build();
        });
        // NB - Stubbed for this dataset specifically, not for any dataset: the service decides where to hold the
        //      restore's write transaction by asking the plugin which dataset in the chain carries the labels
        LabelsMaintenanceTestSupport.withLabelsStore(plugin, dsg, recordingStore);

        // Sanity check -- if the ABAC dataset ever stops propagating its transaction into the labels store then the
        // assertion below would pass for the wrong reason
        Txn.executeWrite(dsg, () -> assertTrue(labelsStore.getTransactional().isInTransaction(),
                                               "The ABAC dataset should propagate its write transaction into the "
                                                       + "labels store"));

        final Path backupDir = stageBackup();
        final var service = new DatasetBackupService(registryFor(dsg), plugin);

        // When
        service.restoreDataset(backupDir.toString(), DATASET_NAME, OBJECT_MAPPER.createObjectNode());

        // Then
        assertFalse(inTransactionDuringRestore.get(),
                    "The labels store was restored while the restoring thread still held an open transaction on it. "
                            + "AbstractRocksDBStorage.restore() closes the RocksDB database and reopens a new one, so "
                            + "that transaction is left pointing at freed native memory and the next use of it kills "
                            + "the JVM. The store level restore must happen outside Txn.executeWrite(dsg, ...).");
    }

    // -----------------------------------------------------------------------------------
    // The consequence.  Run in a child JVM because the failure mode is a native abort, which would
    // otherwise take the surefire fork (and every other test in it) down with it.
    // -----------------------------------------------------------------------------------

    @Test
    void givenRocksDbLabelStore_whenRestoreDataset_thenServerDoesNotCrash() throws Exception {
        // Given
        final Path childDir = Files.createDirectories(workDir.resolve("child"));
        final var builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"),
                // Keep any JVM crash log with the rest of the child's scratch state rather than in the module directory
                "-XX:ErrorFile=" + childDir.resolve("hs_err_pid%p.log"),
                RestoreWithRocksDbLabelStoreHarness.class.getName(),
                childDir.toString());
        builder.directory(childDir.toFile());
        builder.redirectErrorStream(true);

        // When
        final Process child = builder.start();
        final var output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(child.waitFor(5, TimeUnit.MINUTES), "Restore harness did not terminate");

        // Then
        assertEquals(0, child.exitValue(),
                     "Restoring a dataset with a RocksDB labels store killed the JVM. Exit code 134 (SIGABRT) or 139 "
                             + "(SIGSEGV) is the use-after-free described on this class: the labels store is closed "
                             + "and reopened underneath the restoring thread's own open RocksDB transaction.\n"
                             + "Harness output:\n" + output);
    }

    // -----------------------------------------------------------------------------------
    // The other half of the contract: a restore must still be applied as a whole, with no update
    // interleaved with it, or the data and its labels end up out of step.
    // -----------------------------------------------------------------------------------

    @Test
    void givenRocksDbLabelStore_whenRestoreDataset_thenWriteLockIsHeldOnTheDataThroughout() throws Exception {
        // Given
        final var dataInWriteTransaction = new AtomicBoolean(false);
        final var labelsInTransaction = new AtomicBoolean(true);

        final var plugin = mock(DataSecurityPlugin.class);
        when(plugin.prepareLabelToNode()).thenReturn(new RdfAbacPlugin().prepareLabelToNode());
        final var recordingStore = mock(LabelsMaintenanceTestSupport.MaintainableLabelsStore.class);
        when(recordingStore.restore(any(RestoreConfig.class))).thenAnswer(invocation -> {
            dataInWriteTransaction.set(data.isInTransaction() && data.transactionMode() == ReadWrite.WRITE);
            labelsInTransaction.set(labelsStore.getTransactional().isInTransaction());
            return RestoreStatus.builder().success(true).build();
        });
        LabelsMaintenanceTestSupport.withLabelsStore(plugin, dsg, recordingStore);

        final Path backupDir = stageBackup();
        final var service = new DatasetBackupService(registryFor(dsg), plugin);

        // When
        service.restoreDataset(backupDir.toString(), DATASET_NAME, OBJECT_MAPPER.createObjectNode());

        // Then
        assertTrue(dataInWriteTransaction.get(),
                   "The restore must hold a write transaction on the dataset's data for its whole duration, otherwise "
                           + "an update can be applied between the labels restore and the data restore and leave the "
                           + "two out of step.");
        assertFalse(labelsInTransaction.get(),
                    "The write transaction must not reach the labels store, which is the store being restored.");
    }

    @Test
    void givenLabelledDataset_whenChoosingTheRestoreLockTarget_thenTheUnderlyingDataIsUsed() {
        // Given
        final var plugin = mock(DataSecurityPlugin.class);
        LabelsMaintenanceTestSupport.withLabelsStore(plugin, dsg, labelsStore);
        final var service = new DatasetBackupService(registryFor(dsg), plugin);

        // When and Then
        assertSame(data, service.restoreLockTarget(dsg),
                   "A labelled dataset should be unwrapped so that the restore's transaction never reaches the "
                           + "labels store");
    }

    @Test
    void givenDatasetWithNoLabelsStore_whenChoosingTheRestoreLockTarget_thenTheDatasetItselfIsUsed() {
        // Given
        // NB - No labels store is stubbed, so the plugin reports none for any dataset and there is nothing to unwrap
        final var plugin = mock(DataSecurityPlugin.class);
        final var service = new DatasetBackupService(registryFor(dsg), plugin);

        // When and Then
        assertSame(dsg, service.restoreLockTarget(dsg),
                   "A dataset with no labels store should be locked as-is rather than unwrapped");
    }

    // -----------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------

    private DataAccessPointRegistry registryFor(DatasetGraphABAC dataset) {
        final var registry = mock(DataAccessPointRegistry.class);
        final var dap = new DataAccessPoint(DAP_NAME, DataService.newBuilder().dataset(dataset).build());
        when(registry.get(DAP_NAME)).thenReturn(dap);
        when(registry.accessPoints()).thenReturn(List.of(dap));
        return registry;
    }

    /**
     * Lays out a backup on disk in the shape {@code restoreDataset} expects. The labels directory only has to exist,
     * since the recording store does not read it.
     */
    private Path stageBackup() throws Exception {
        final Path backupsDir = Files.createDirectories(workDir.resolve("backups"));
        BackupUtils.dirBackups = backupsDir.toString();

        final Path datasetDir = backupsDir.resolve("1").resolve(DATASET_NAME);
        Files.createDirectories(datasetDir.resolve("labels"));
        final Path tdbDir = Files.createDirectories(datasetDir.resolve("tdb"));
        try (var os = Files.newOutputStream(tdbDir.resolve(DATASET_NAME + "_backup.nq.gz"));
             var gz = new java.util.zip.GZIPOutputStream(os)) {
            gz.write("<http://example.org/s0> <http://example.org/p0> <http://example.org/o0> .\n"
                             .getBytes(StandardCharsets.UTF_8));
        }
        return backupsDir.resolve("1");
    }

}
