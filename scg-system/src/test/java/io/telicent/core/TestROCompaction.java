package io.telicent.core;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.system.Txn;
import org.apache.jena.tdb2.DatabaseMgr;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.apache.jena.tdb2.store.DatasetGraphTDB;
import org.apache.jena.tdb2.sys.CopyDSG;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class TestROCompaction {

    private static final long TRIPLES = 2_000;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<DatasetGraphSwitchable> datasets = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        executor.shutdownNow();
        datasets.forEach(DatasetGraphSwitchable::close);
    }

    private DatasetGraphSwitchable createDataset() throws IOException {
        Path dir = Files.createTempDirectory("ro-compaction");
        DatasetGraphSwitchable dsg = (DatasetGraphSwitchable) DatabaseMgr.connectDatasetGraph(dir.toString());
        datasets.add(dsg);
        // Several commits so there is something to reclaim
        for (int batch = 0; batch < 10; batch++) {
            final int b = batch;
            Txn.executeWrite(dsg, () -> {
                for (int i = 0; i < TRIPLES / 10; i++) {
                    dsg.getDefaultGraph().add(NodeFactory.createURI("urn:s" + b + "_" + i),
                                              NodeFactory.createURI("urn:p"), NodeFactory.createLiteralString("o" + i));
                }
            });
        }
        return dsg;
    }

    private static long count(DatasetGraphSwitchable dsg) {
        return Txn.calculateRead(dsg, () -> (long) dsg.getDefaultGraph().size());
    }

    private static List<String> storageDirs(DatasetGraphSwitchable dsg) throws IOException {
        try (Stream<Path> files = Files.list(dsg.getContainerPath())) {
            return files.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private static Path location(DatasetGraphSwitchable dsg) {
        return Path.of(((DatasetGraphTDB) dsg.get()).getLocation().getDirectoryPath());
    }

    @Test
    void givenCopyFailed_whenRetrying_thenNoDataIsLost() throws Exception {
        // Given: a compaction whose copy fails part way, after the temporary store has been created and written to
        DatasetGraphSwitchable dsg = createDataset();
        try (MockedStatic<CopyDSG> copy = Mockito.mockStatic(CopyDSG.class)) {
            copy.when(() -> CopyDSG.copy(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
                // As in the real copy, writing inside a write transaction that then fails
                DatasetGraph target = invocation.getArgument(1);
                Txn.executeWrite(target, () -> {
                    target.add(NodeFactory.createURI("urn:g"), NodeFactory.createURI("urn:partial"),
                               NodeFactory.createURI("urn:p"), NodeFactory.createURI("urn:o"));
                    throw new IllegalStateException("Simulated failure part way through the copy");
                });
                return null;
            });
            assertThrows(IllegalStateException.class, () -> ROCompaction.compact(dsg, true, Duration.ofSeconds(10)));
        }
        assertEquals(List.of("Data-0001"), storageDirs(dsg), "The failed copy's temporary directory should be removed");
        assertEquals(TRIPLES, count(dsg));

        // When: compacting again, which uses the same temporary directory name
        ROCompaction.compact(dsg, true, Duration.ofSeconds(10));

        // Then: the compacted copy has all the data, rather than being an empty store left cached by the failure
        assertEquals(List.of("Data-0002"), storageDirs(dsg));
        assertEquals(TRIPLES, count(dsg));
        Txn.executeWrite(dsg, () -> dsg.getDefaultGraph()
                                       .add(NodeFactory.createURI("urn:new"), NodeFactory.createURI("urn:p"),
                                            NodeFactory.createURI("urn:o")));
        assertEquals(TRIPLES + 1, count(dsg));
    }

    @Test
    void givenIdleDataset_whenCompacting_thenSwitchesToCompactedCopyAndDeletesOld() throws Exception {
        // given
        DatasetGraphSwitchable dsg = createDataset();
        assertEquals(List.of("Data-0001"), storageDirs(dsg));

        // when
        ROCompaction.compact(dsg, true, Duration.ofSeconds(10));

        // then
        assertEquals(List.of("Data-0002"), storageDirs(dsg));
        assertTrue(location(dsg).endsWith("Data-0002"));
        assertEquals(TRIPLES, count(dsg));
        // And the compacted database takes writes
        Txn.executeWrite(dsg, () -> dsg.getDefaultGraph()
                                       .add(NodeFactory.createURI("urn:new"), NodeFactory.createURI("urn:p"),
                                            NodeFactory.createURI("urn:o")));
        assertEquals(TRIPLES + 1, count(dsg));
    }

    @Test
    void givenReadSpanningTheCopy_whenCompacting_thenReadFinishesOnOldCopyAndCompactionCompletes() throws Exception {
        // given: a read that starts before the compaction and ends while it is waiting to switch over. With Jena's
        // DatabaseMgr.compact this read ends on the new database and the compaction then waits forever.
        DatasetGraphSwitchable dsg = createDataset();
        CountDownLatch readStarted = new CountDownLatch(1);
        Future<Long> read = executor.submit(() -> Txn.calculateRead(dsg, () -> {
            readStarted.countDown();
            sleep(1_000);
            return (long) dsg.getDefaultGraph().size();
        }));
        assertTrue(readStarted.await(10, TimeUnit.SECONDS));

        // when
        Future<?> compaction = executor.submit(() -> ROCompaction.compact(dsg, true, Duration.ofSeconds(30)));

        // then
        compaction.get(30, TimeUnit.SECONDS);
        assertEquals(TRIPLES, read.get(10, TimeUnit.SECONDS));
        assertEquals(List.of("Data-0002"), storageDirs(dsg));
        assertEquals(TRIPLES, count(dsg));
    }

    @Test
    void givenContinuousConcurrentReads_whenCompacting_thenCompletesAndEveryReadSucceeds() throws Exception {
        // given
        DatasetGraphSwitchable dsg = createDataset();
        AtomicBoolean stop = new AtomicBoolean();
        AtomicLong reads = new AtomicLong();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Future<?>> readers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            readers.add(executor.submit(() -> {
                while (!stop.get()) {
                    try {
                        long n = Txn.calculateRead(dsg, () -> (long) dsg.getDefaultGraph().size());
                        if (n != TRIPLES) {
                            failure.compareAndSet(null, new AssertionError("Read saw " + n + " triples"));
                        }
                        reads.incrementAndGet();
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    }
                }
            }));
        }
        Thread.sleep(200);

        // when
        Future<?> compaction = executor.submit(() -> ROCompaction.compact(dsg, true, Duration.ofSeconds(30)));

        // then
        try {
            compaction.get(60, TimeUnit.SECONDS);
            long afterCompaction = reads.get();
            Thread.sleep(200);
            assertTrue(reads.get() > afterCompaction, "Reads should continue after the switch");
        } finally {
            stop.set(true);
            for (Future<?> reader : readers) {
                reader.get(10, TimeUnit.SECONDS);
            }
        }
        assertNull(failure.get(), () -> "A read failed: " + failure.get());
        assertEquals(List.of("Data-0002"), storageDirs(dsg));
        assertEquals(TRIPLES, count(dsg));
    }

    @Test
    void givenReadOutlastingSwitchTimeout_whenCompacting_thenAbandonedAndDatabaseUnchanged() throws Exception {
        // given
        DatasetGraphSwitchable dsg = createDataset();
        Path before = location(dsg);
        CountDownLatch readStarted = new CountDownLatch(1);
        CountDownLatch finishRead = new CountDownLatch(1);
        Future<Long> read = executor.submit(() -> Txn.calculateRead(dsg, () -> {
            readStarted.countDown();
            await(finishRead);
            return (long) dsg.getDefaultGraph().size();
        }));
        assertTrue(readStarted.await(10, TimeUnit.SECONDS));

        // when
        try {
            assertThrows(ROCompaction.SwitchAbandonedException.class,
                         () -> ROCompaction.compact(dsg, true, Duration.ofMillis(300)));
        } finally {
            finishRead.countDown();
        }

        // then
        assertEquals(TRIPLES, read.get(10, TimeUnit.SECONDS));
        assertEquals(before, location(dsg));
        assertEquals(List.of("Data-0001"), storageDirs(dsg));
        assertEquals(TRIPLES, count(dsg));
        // Reads and writes carry on once the helper holding the switch window lets go
        Txn.executeWrite(dsg, () -> dsg.getDefaultGraph()
                                       .add(NodeFactory.createURI("urn:new"), NodeFactory.createURI("urn:p"),
                                            NodeFactory.createURI("urn:o")));
        assertEquals(TRIPLES + 1, count(dsg));
        // And a later compaction succeeds
        ROCompaction.compact(dsg, true, Duration.ofSeconds(10));
        assertEquals(List.of("Data-0002"), storageDirs(dsg));
        assertEquals(TRIPLES + 1, count(dsg));
    }

    @Test
    void givenWriterQueuedBehindTheCopy_whenSwitching_thenTimesOutRatherThanDeadlocking() throws Exception {
        // given: while the copy holds writers back, a writer arrives. It holds the switchable's non-exclusive lock while
        // it waits for the copy to finish, so the switch can never get exclusive access: without a timeout this is a
        // deadlock
        DatasetGraphSwitchable dsg = createDataset();
        AtomicReference<Future<?>> writer = new AtomicReference<>();
        AtomicBoolean switched = new AtomicBoolean(true);

        // when
        dsg.execReadOnlyDatabase(() -> {
            writer.set(executor.submit(() -> Txn.executeWrite(dsg, () -> dsg.getDefaultGraph().add(
                    NodeFactory.createURI("urn:queued"), NodeFactory.createURI("urn:p"), NodeFactory.createURI("urn:o")))));
            sleep(200);
            switched.set(ROCompaction.runExclusively(dsg, Duration.ofMillis(300), () -> { }));
        });

        // then
        assertFalse(switched.get());
        writer.get().get(10, TimeUnit.SECONDS);
        assertEquals(TRIPLES + 1, count(dsg));
    }

    @Test
    void givenTransactionActive_whenRunningExclusivelyTimesOut_thenNewTransactionsAreNotBlocked() throws Exception {
        // given: a long-running read that outlasts the switch timeout
        DatasetGraphSwitchable dsg = createDataset();
        CountDownLatch readStarted = new CountDownLatch(1);
        CountDownLatch finishRead = new CountDownLatch(1);
        Future<?> read = executor.submit(() -> Txn.executeRead(dsg, () -> {
            readStarted.countDown();
            await(finishRead);
        }));
        assertTrue(readStarted.await(10, TimeUnit.SECONDS));
        AtomicBoolean ran = new AtomicBoolean();

        try {
            // when
            boolean result = ROCompaction.runExclusively(dsg, Duration.ofMillis(100), () -> ran.set(true));

            // then
            assertFalse(result);
            assertFalse(ran.get());
            // While the long read is still running, new reads and writes must not be held back
            assertEquals(TRIPLES, executor.submit(() -> count(dsg)).get(2, TimeUnit.SECONDS));
            executor.submit(() -> Txn.executeWrite(dsg, () -> dsg.getDefaultGraph().add(
                    NodeFactory.createURI("urn:after"), NodeFactory.createURI("urn:p"), NodeFactory.createURI("urn:o"))))
                    .get(2, TimeUnit.SECONDS);
        } finally {
            finishRead.countDown();
        }
        read.get(10, TimeUnit.SECONDS);
        assertTrue(ROCompaction.runExclusively(dsg, Duration.ofSeconds(5), () -> ran.set(true)));
        assertTrue(ran.get());
    }

    @Test
    void givenReadsInFlight_whenRunningExclusively_thenWaitsForThemAndHoldsBackNewOnes() throws Exception {
        // given: a read that finishes shortly, i.e. within the timeout
        DatasetGraphSwitchable dsg = createDataset();
        CountDownLatch readStarted = new CountDownLatch(1);
        AtomicBoolean readFinished = new AtomicBoolean();
        Future<?> read = executor.submit(() -> Txn.executeRead(dsg, () -> {
            readStarted.countDown();
            sleep(300);
            readFinished.set(true);
        }));
        assertTrue(readStarted.await(10, TimeUnit.SECONDS));
        AtomicBoolean newReadDuringAction = new AtomicBoolean();
        AtomicReference<Future<Long>> newRead = new AtomicReference<>();

        // when
        boolean result = ROCompaction.runExclusively(dsg, Duration.ofSeconds(10), () -> {
            assertTrue(readFinished.get(), "In-flight read should have finished first");
            newRead.set(executor.submit(() -> count(dsg)));
            sleep(200);
            newReadDuringAction.set(newRead.get().isDone());
        });

        // then
        assertTrue(result);
        assertFalse(newReadDuringAction.get(), "New reads should wait while the action runs");
        assertEquals(TRIPLES, newRead.get().get(10, TimeUnit.SECONDS));
        read.get(10, TimeUnit.SECONDS);
    }

    @Test
    void givenNoAccessToTheExclusivityLock_whenRunningExclusively_thenFallsBackToPolling() throws Exception {
        // given
        DatasetGraphSwitchable dsg = createDataset();
        CountDownLatch readStarted = new CountDownLatch(1);
        CountDownLatch finishRead = new CountDownLatch(1);
        Future<?> read = executor.submit(() -> Txn.executeRead(dsg, () -> {
            readStarted.countDown();
            await(finishRead);
        }));
        assertTrue(readStarted.await(10, TimeUnit.SECONDS));
        AtomicBoolean ran = new AtomicBoolean();

        // when
        try {
            assertFalse(ROCompaction.runExclusively(dsg, null, Duration.ofMillis(100), () -> ran.set(true)));
            // then
            assertFalse(ran.get());
            assertEquals(TRIPLES, executor.submit(() -> count(dsg)).get(2, TimeUnit.SECONDS));
        } finally {
            finishRead.countDown();
        }
        read.get(10, TimeUnit.SECONDS);
        assertTrue(ROCompaction.runExclusively(dsg, null, Duration.ofSeconds(5), () -> ran.set(true)));
        assertTrue(ran.get());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
