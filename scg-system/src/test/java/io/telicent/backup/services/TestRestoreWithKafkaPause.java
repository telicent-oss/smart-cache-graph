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
import io.telicent.smart.cache.sources.Event;
import org.apache.jena.fuseki.kafka.FKS;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.fuseki.server.DataService;
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
import java.util.List;
import java.util.Map;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("deprecation")
public class TestRestoreWithKafkaPause {

    private static final String DATASET_NAME = "restore-test-ds";
    private static final String DAP_NAME = "/" + DATASET_NAME;

    private DataAccessPointRegistry mockRegistry;
    private DatasetBackupService_Test cut;
    private ObjectNode resultNode;
    private Path baseDir;

    @BeforeEach
    public void setup() throws Exception {
        DatasetBackupService_Test.clear();
        fksDrivers().clear();

        mockRegistry = mock(DataAccessPointRegistry.class);

        DatasetGraphABAC dsg = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
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
}