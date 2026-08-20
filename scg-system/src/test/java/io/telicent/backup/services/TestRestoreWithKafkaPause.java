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

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.backup.utils.BackupUtils;
import io.telicent.jena.abac.ABAC;
import io.telicent.jena.abac.SysABAC;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.LabelsStoreMem;
import io.telicent.smart.cache.payloads.RdfPayload;
import io.telicent.smart.cache.projectors.Projector;
import io.telicent.smart.cache.projectors.driver.ProjectorDriver;
import io.telicent.smart.cache.projectors.sinks.NullSink;
import io.telicent.smart.cache.sources.Event;
import io.telicent.smart.cache.sources.EventSource;
import org.apache.jena.fuseki.kafka.FKS;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.kafka.KConnectorDesc;
import org.apache.jena.kafka.common.FusekiProjector;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.kafka.common.utils.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestRestoreWithKafkaPause {

    private static final String DATASET_NAME = "restore-test-ds";
    private static final String DAP_NAME = "/" + DATASET_NAME;

    private DataAccessPointRegistry mockRegistry;
    private DatasetGraphABAC dsg;
    private DatasetBackupService_Test cut;
    private ObjectNode resultNode;
    private Path baseDir;

    @BeforeEach
    public void setup() throws Exception {
        DatasetBackupService_Test.clear();
        fksDrivers().clear();

        mockRegistry = mock(DataAccessPointRegistry.class);

        dsg = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                                 null,
                                 LabelsStoreMem.create(),
                                 SysABAC.allowLabel,
                                 null);
        DataAccessPoint dap = new DataAccessPoint(DAP_NAME,
                                                  DataService.newBuilder().dataset(dsg).build());
        when(mockRegistry.get(DAP_NAME)).thenReturn(dap);
        // listDatasets() / accessPoints() not exercised here but harmless to wire up
        when(mockRegistry.accessPoints()).thenReturn(List.of(dap));

        cut = new DatasetBackupService_Test(mockRegistry);
        resultNode = OBJECT_MAPPER.createObjectNode();

        baseDir = Files.createTempDirectory("scg-restore-pause-test");
        BackupUtils.dirBackups = baseDir.toString();
        Path datasetDir = baseDir.resolve("1").resolve(DATASET_NAME);
        Path tdbDir = datasetDir.resolve("tdb");
        Path labelsDir = datasetDir.resolve("labels");
        Files.createDirectories(tdbDir);
        Files.createDirectories(labelsDir);
        Files.createFile(tdbDir.resolve(DATASET_NAME + "_backup.nq.gz"));
    }

    @AfterEach
    public void cleanup() throws Exception {
        fksDrivers().clear();
        if (baseDir != null && Files.exists(baseDir)) {
            Files.walk(baseDir)
                 .sorted((a, b) -> b.compareTo(a))
                 .map(Path::toFile)
                 .forEach(java.io.File::delete);
        }
    }

    // -----------------------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------------------

    @Test
    public void givenRegisteredProjectorAtPausePoint_whenRestoreDataset_thenPauseRestoreResumeOrder()
            throws Exception {
        // Given
        FusekiProjector projector = mock(FusekiProjector.class);
        when(projector.isAtPausePoint()).thenReturn(true);
        registerDrivers(DAP_NAME, mockDriverFor(projector));

        // When
        boolean ok = cut.restoreDataset(baseDir.resolve("1").toString(), DATASET_NAME, resultNode);

        // Then
        assertTrue(ok, "restore should succeed");
        InOrder order = inOrder(projector);
        order.verify(projector).requestPause();
        order.verify(projector, times(1)).requestResume();
        assertTrue(DatasetBackupService_Test.getCallCount(DatasetBackupService_Test.RESTORE_TDB) >= 1,
                   "restoreTDB should have been invoked");
    }

    // -----------------------------------------------------------------------------------
    // Timeout path
    // -----------------------------------------------------------------------------------

    @Test
    public void givenProjectorNeverPauses_whenRestoreDataset_thenAbortedAndProjectorResumed()
            throws Exception {
        // Given
        FusekiProjector projector = mock(FusekiProjector.class);
        when(projector.isAtPausePoint()).thenReturn(false);
        registerDrivers(DAP_NAME, mockDriverFor(projector));

        Duration originalTimeout = setPauseTimeoutForTest(Duration.ofMillis(200));
        try {
            // When
            boolean ok = cut.restoreDataset(baseDir.resolve("1").toString(), DATASET_NAME, resultNode);

            // Then
            assertFalse(ok, "restore should refuse when projector won't pause");
            assertFalse(DatasetBackupService_Test.getCallCount(DatasetBackupService_Test.RESTORE_TDB) > 0,
                        "restoreTDB must not run if the projector did not pause");
            verify(projector, times(1)).requestPause();
            verify(projector, times(1)).requestResume();
            assertTrue(resultNode.get(DATASET_NAME).get("reason").asText().toLowerCase().contains("pause"),
                       "Aborted-restore reason should mention the pause timeout, got: "
                               + resultNode.get(DATASET_NAME).get("reason"));
        } finally {
            setPauseTimeoutForTest(originalTimeout);
        }
    }

    // -----------------------------------------------------------------------------------
    // Exception path
    // -----------------------------------------------------------------------------------

    @Test
    public void givenRestoreThrows_whenRestoreDataset_thenProjectorStillResumed() {
        // Given
        FusekiProjector projector = mock(FusekiProjector.class);
        when(projector.isAtPausePoint()).thenReturn(true);
        registerDrivers(DAP_NAME, mockDriverFor(projector));

        DatasetBackupService_Test.setupExceptionForMethod(DatasetBackupService_Test.RESTORE_TDB,
                                                         "simulated restore failure");

        // When
        cut.restoreDataset(baseDir.resolve("1").toString(), DATASET_NAME, resultNode);

        // Then
        verify(projector, times(1)).requestPause();
        verify(projector, times(1)).requestResume();
    }

    // -----------------------------------------------------------------------------------
    // No-projector path (dataset has no Kafka connector)
    // -----------------------------------------------------------------------------------

    @Test
    public void givenNoProjectorRegistered_whenRestoreDataset_thenSucceedsWithoutPauseCalls() {
        // Given
        // When
        boolean ok = cut.restoreDataset(baseDir.resolve("1").toString(), DATASET_NAME, resultNode);

        // Then
        assertTrue(ok, "restore should succeed when there is no projector to pause");
        assertTrue(DatasetBackupService_Test.getCallCount(DatasetBackupService_Test.RESTORE_TDB) >= 1,
                   "restoreTDB should have been invoked");
    }

    // -----------------------------------------------------------------------------------
    // Real projector + real driver, i.e. the quiet dataset case seen in system-release where a
    // restore aborted with "Timed out ... waiting for Kafka projectors ... to reach a safe pause
    // point".  Every other test here stubs isAtPausePoint(), so only this one exercises the
    // handshake the deployed code actually performs.
    // -----------------------------------------------------------------------------------

    @Test
    public void givenQuietTopicWithRealProjector_whenRestoreDataset_thenPauseHandshakeSucceeds()
            throws Exception {
        // Given -- a real projector driven by a real driver over a caught up topic.  The driver
        // only notifies the projector of the FIRST of a run of consecutive stalls, so we wait until
        // we are several stalls deep before restoring: at that point the pause request can only be
        // observed via the driver's idle() callback.
        QuietEventSource source = new QuietEventSource();
        FusekiProjector projector = FusekiProjector.builder()
                                                  .connector(mockConnector())
                                                  .source(source)
                                                  .dataset(dsg)
                                                  .batchSize(100)
                                                  .maxTransactionDuration(Duration.ofSeconds(30))
                                                  .build();
        ProjectorDriver<Bytes, RdfPayload, Event<Bytes, RdfPayload>> driver =
                ProjectorDriver.<Bytes, RdfPayload, Event<Bytes, RdfPayload>>create()
                               .source(source)
                               .projector(projector)
                               .destination(NullSink.of())
                               .unlimited()
                               .pollTimeout(Duration.ofMillis(200))
                               .build();
        registerDrivers(DAP_NAME, driver);

        CompletableFuture<Void> driverRun = CompletableFuture.runAsync(driver);
        // Fail fast rather than sitting out the production 30s timeout if the handshake regresses
        Duration originalTimeout = setPauseTimeoutForTest(Duration.ofSeconds(5));
        try {
            waitFor(() -> driver.getConsecutiveStalls() >= 3, Duration.ofSeconds(10),
                    "Driver did not stall repeatedly on the quiet source");

            // When
            boolean ok = cut.restoreDataset(baseDir.resolve("1").toString(), DATASET_NAME, resultNode);

            // Then
            assertTrue(ok, "Restore should succeed once the projector reaches its pause point, reason: "
                    + resultNode.path(DATASET_NAME).path("reason").asText("<none>"));
            assertTrue(DatasetBackupService_Test.getCallCount(DatasetBackupService_Test.RESTORE_TDB) >= 1,
                       "restoreTDB should have been invoked");
            // Resume is asynchronous: FKS.resumeProjectors() only clears the pause flag and notifies,
            // the projector thread clears atPausePoint itself once it wakes.  Poll rather than
            // assert instantaneously, otherwise this races the projector thread on a loaded runner.
            waitFor(() -> !projector.isAtPausePoint(), Duration.ofSeconds(10),
                    "Projector was not resumed after the restore");
        } finally {
            setPauseTimeoutForTest(originalTimeout);
            driver.cancel();
            driverRun.get(10, TimeUnit.SECONDS);
        }
    }

    // -----------------------------------------------------------------------------------
    // Reflection helpers
    // -----------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, List<ProjectorDriver<Bytes, RdfPayload, Event<Bytes, RdfPayload>>>> fksDrivers() {
        try {
            Field f = FKS.class.getDeclaredField("DRIVERS");
            f.setAccessible(true);
            return (Map<String, List<ProjectorDriver<Bytes, RdfPayload, Event<Bytes, RdfPayload>>>>) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to reflect FKS.DRIVERS — has the field been renamed?", e);
        }
    }

    @SafeVarargs
    private static void registerDrivers(String datasetName,
                                        ProjectorDriver<Bytes, RdfPayload, Event<Bytes, RdfPayload>>... drivers) {
        fksDrivers().put(datasetName, new ArrayList<>(List.of(drivers)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ProjectorDriver<Bytes, RdfPayload, Event<Bytes, RdfPayload>> mockDriverFor(FusekiProjector projector) {
        ProjectorDriver<Bytes, RdfPayload, Event<Bytes, RdfPayload>> driver = mock(ProjectorDriver.class);
        when(driver.getProjector()).thenReturn((Projector) projector);
        return driver;
    }

    private static Duration setPauseTimeoutForTest(Duration newTimeout) throws Exception {
        Field f = DatasetBackupService.class.getDeclaredField("KAFKA_PAUSE_TIMEOUT");
        f.setAccessible(true);
        Duration previous = (Duration) f.get(null);
        f.set(null, newTimeout);
        return previous;
    }

    private static KConnectorDesc mockConnector() {
        KConnectorDesc connector = mock(KConnectorDesc.class);
        when(connector.getTopics()).thenReturn(List.of("test-topic"));
        when(connector.getMaxTransactionDuration()).thenReturn(Duration.ofSeconds(30));
        when(connector.getBatchSizeBytes()).thenReturn(1024L * 1024L);
        when(connector.getHighLagThreshold()).thenReturn(10_000L);
        when(connector.getLowVolumeBatchSizeThreshold()).thenReturn(100);
        when(connector.getBatchSizeTrackingWindow()).thenReturn(10);
        return connector;
    }

    private static void waitFor(java.util.function.BooleanSupplier condition, Duration timeout,
                                String failureMessage) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
        }
        throw new AssertionError(failureMessage + " (within " + timeout + ")");
    }

    /**
     * An event source that is caught up but not exhausted, i.e. every poll blocks for the timeout
     * and then yields nothing.  Models a quiet Kafka topic, the state a dataset is in when a
     * restore is run on a system that has processed everything.
     */
    private static final class QuietEventSource implements EventSource<Bytes, RdfPayload> {

        private volatile boolean closed = false;

        @Override
        public boolean availableImmediately() {
            return false;
        }

        @Override
        public boolean isExhausted() {
            // Caught up is not the same as exhausted, more events may arrive later
            return this.closed;
        }

        @Override
        public void close() {
            this.closed = true;
        }

        @Override
        public boolean isClosed() {
            return this.closed;
        }

        @Override
        public Event<Bytes, RdfPayload> poll(Duration timeout) {
            try {
                Thread.sleep(timeout.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }

        @Override
        public Long remaining() {
            return 0L;
        }

        @Override
        public void processed(Collection<Event<?, ?>> processedEvents) {
            // No-op
        }
    }
}