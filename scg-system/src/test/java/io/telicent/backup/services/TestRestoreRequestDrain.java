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
import io.telicent.core.DatasetMaintenanceRegistry;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import static io.telicent.backup.services.DatasetBackupService_Test.RESTORE_LABELS;
import static io.telicent.backup.services.DatasetBackupService_Test.RESTORE_TDB;
import static io.telicent.backup.services.DatasetBackupService_Test.getCallCount;
import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that a restore waits for requests admitted before it started before it closes and replaces the dataset's
 * stores.
 * <p>
 * Registering the restore with {@code DatasetMaintenanceRegistry} stops <em>new</em> requests being admitted, but a
 * query admitted moments earlier is still reading the very stores the restore is about to replace. Without a drain
 * the data is pulled out from underneath it.
 * </p>
 * <p>
 * The dataset here is a {@link DatasetGraphSwitchable} because that is what the registry tracks; an unmanaged dataset
 * gets a no-op handle and has no admitted requests to wait for.
 * </p>
 */
class TestRestoreRequestDrain {

    private static final String DATASET_NAME = "drain-test-ds";
    private static final String DAP_NAME = "/" + DATASET_NAME;

    @TempDir
    private Path workDir;

    private String originalBackupDir;
    private Duration originalDrainTimeout;
    private DatasetGraphSwitchable dataset;
    private DatasetBackupService_Test cut;
    private Path restorePath;

    @BeforeEach
    void setup() throws Exception {
        originalBackupDir = BackupUtils.dirBackups;
        originalDrainTimeout = DatasetBackupService.REQUEST_DRAIN_TIMEOUT;
        DatasetBackupService_Test.clear();

        final Path container = Files.createDirectories(workDir.resolve("dsg"));
        Files.createDirectories(container.resolve("Data-0001"));
        dataset = new DatasetGraphSwitchable(container, null, DatasetGraphFactory.createTxnMem());

        final var registry = mock(DataAccessPointRegistry.class);
        final var dap = new DataAccessPoint(DAP_NAME, DataService.newBuilder().dataset(dataset).build());
        when(registry.get(DAP_NAME)).thenReturn(dap);
        when(registry.accessPoints()).thenReturn(List.of(dap));
        cut = new DatasetBackupService_Test(registry);

        restorePath = stageBackup();
    }

    @AfterEach
    void cleanup() {
        BackupUtils.dirBackups = originalBackupDir;
        DatasetBackupService.REQUEST_DRAIN_TIMEOUT = originalDrainTimeout;
        DatasetBackupService_Test.clear();
    }

    @Test
    void givenRequestThatNeverFinishes_whenRestoreDataset_thenAbortsWithoutTouchingTheStores() {
        // Given -- a request admitted before the restore starts, which never completes
        DatasetBackupService.REQUEST_DRAIN_TIMEOUT = Duration.ofMillis(200);
        final var admission = DatasetMaintenanceRegistry.admitRequest(dataset, true);
        assertTrue(admission.allowed());

        // When
        final ObjectNode result = OBJECT_MAPPER.createObjectNode();
        cut.restoreDataset(restorePath.toString(), DATASET_NAME, result);

        // Then
        final var response = result.get(DATASET_NAME);
        assertFalse(response.get("success").asBoolean(), "The restore must not report success");
        assertTrue(response.get("reason").asText().contains("in-flight requests"),
                   "Should say why it gave up, was: " + response.get("reason").asText());
        assertEquals(0, getCallCount(RESTORE_TDB),
                     "The stores must not be touched while a request is still reading them");
        assertEquals(0, getCallCount(RESTORE_LABELS),
                     "The stores must not be touched while a request is still reading them");

        admission.close();
    }

    @Test
    void givenInFlightRequest_whenRestoreDataset_thenWaitsForItToFinishFirst() throws Exception {
        // Given
        DatasetBackupService.REQUEST_DRAIN_TIMEOUT = Duration.ofSeconds(30);
        final var admission = DatasetMaintenanceRegistry.admitRequest(dataset, true);
        assertTrue(admission.allowed());

        final ObjectNode result = OBJECT_MAPPER.createObjectNode();
        final var started = new CountDownLatch(1);
        final var restored = new CompletableFuture<Boolean>();
        final Thread restorer = new Thread(() -> {
            started.countDown();
            try {
                restored.complete(cut.restoreDataset(restorePath.toString(), DATASET_NAME, result));
            } catch (Throwable t) {
                restored.completeExceptionally(t);
            }
        }, "restore");

        // When
        restorer.start();
        assertTrue(started.await(10, TimeUnit.SECONDS));

        // Then -- asserting a negative, so give the restore a fair chance to wrongly proceed before checking
        Thread.sleep(500);
        assertFalse(restored.isDone(), "The restore should still be waiting for the in-flight request");
        assertEquals(0, getCallCount(RESTORE_TDB), "The stores must not be touched before the request finishes");

        admission.close();

        assertTrue(restored.get(30, TimeUnit.SECONDS), "The restore should proceed once the request finishes");
        assertEquals(1, getCallCount(RESTORE_TDB), "The restore should have run after draining");
        assertEquals(1, getCallCount(RESTORE_LABELS), "The restore should have run after draining");
        restorer.join(TimeUnit.SECONDS.toMillis(10));
    }

    private Path stageBackup() throws Exception {
        final Path backupsDir = Files.createDirectories(workDir.resolve("backups"));
        BackupUtils.dirBackups = backupsDir.toString();

        final Path datasetDir = backupsDir.resolve("1").resolve(DATASET_NAME);
        Files.createDirectories(datasetDir.resolve("labels"));
        final Path tdbDir = Files.createDirectories(datasetDir.resolve("tdb"));
        try (var os = Files.newOutputStream(tdbDir.resolve(DATASET_NAME + "_backup.nq.gz"));
             var gz = new GZIPOutputStream(os)) {
            gz.write("<http://example.org/s> <http://example.org/p> <http://example.org/o> .\n"
                             .getBytes(StandardCharsets.UTF_8));
        }
        return backupsDir.resolve("1");
    }

}
