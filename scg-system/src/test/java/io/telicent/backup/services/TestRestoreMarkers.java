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
import io.telicent.core.RestoreMarker;
import io.telicent.core.RestoreMarkerStore;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.system.Txn;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import static io.telicent.backup.services.DatasetBackupService_Test.RESTORE_TDB;
import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that a restore records on disk that it is under way, so that one interrupted by the service dying leaves
 * something behind to say the dataset may be half restored.
 * <p>
 * The dataset's stores are not all restored by the same mechanism - the triple store inside a transaction that is
 * discarded if it never commits, the security labels store by replacing its files - so an abnormal termination between
 * them leaves the labels describing data that is no longer there. The marker is what makes that detectable afterwards
 * instead of silent.
 * </p>
 */
class TestRestoreMarkers {

    private static final String DATASET_NAME = "marker-test-ds";
    private static final String DAP_NAME = "/" + DATASET_NAME;

    @TempDir
    private Path workDir;

    private String originalBackupDir;
    private DatasetGraphSwitchable dataset;
    private MarkerWatchingService cut;
    private DataAccessPointRegistry registry;
    private Path restorePath;

    @BeforeEach
    void setup() throws Exception {
        originalBackupDir = BackupUtils.dirBackups;
        DatasetBackupService_Test.clear();

        final Path container = Files.createDirectories(workDir.resolve("dsg"));
        Files.createDirectories(container.resolve("Data-0001"));
        dataset = new DatasetGraphSwitchable(container, null, DatasetGraphFactory.createTxnMem());

        registry = mock(DataAccessPointRegistry.class);
        final DataAccessPoint dap = new DataAccessPoint(DAP_NAME, DataService.newBuilder().dataset(dataset).build());
        when(registry.get(DAP_NAME)).thenReturn(dap);
        when(registry.accessPoints()).thenReturn(List.of(dap));
        cut = new MarkerWatchingService(registry);

        restorePath = stageBackup();
    }

    @AfterEach
    void cleanup() {
        BackupUtils.dirBackups = originalBackupDir;
        DatasetBackupService.REQUEST_DRAIN_TIMEOUT = Duration.ofSeconds(30);
        DatasetBackupService_Test.clear();
        // A successful restore is the only thing that removes a marker, so it is also how a test tidies up after itself
        RestoreMarkerStore.complete(dataset, true, null);
    }

    @Test
    void givenRestore_whenItReachesTheFirstStore_thenTheMarkerIsAlreadyOnDisk() {
        restore();

        assertEquals(1, DatasetBackupService_Test.getCallCount(RESTORE_TDB), "The restore should have run");
        assertNotNull(cut.markerWhenStoreTouched, "The marker must be written before the first store is touched");
        assertTrue(cut.markerWhenStoreTouched.contains("state=IN_PROGRESS"),
                   "The marker must say a restore is under way, was: " + cut.markerWhenStoreTouched);
        assertTrue(cut.markerWhenStoreTouched.contains("dataset=" + DAP_NAME),
                   "The marker must identify the dataset, was: " + cut.markerWhenStoreTouched);
        assertTrue(cut.markerWhenStoreTouched.contains("backupId=1"),
                   "The marker must identify the backup being restored, was: " + cut.markerWhenStoreTouched);
    }

    @Test
    void givenSuccessfulRestore_whenFinished_thenNoMarkerIsLeftBehind() {
        final ObjectNode result = restore();

        assertTrue(result.get(DATASET_NAME).path("success").asBoolean(true), "The restore should have succeeded");
        assertFalse(markerFileExists(), "A restore that finished must not leave a dataset looking half restored");
        assertTrue(RestoreMarkerStore.quarantine(dataset).isEmpty());
    }

    @Test
    void givenRestoreWhereTheLabelsAreReplacedButTheDataIsNot_whenFinished_thenTheDatasetIsWithheld() {
        // The labels store is replaced by swapping its files, which no transaction undoes; the data is not restored
        // at all. That is the inconsistency this exists for, and it is not safe to go on serving labels that describe
        // data that was never put there.
        DatasetBackupService_Test.setupExceptionForMethod(RESTORE_TDB, "disk on fire");

        restore();

        final Optional<RestoreMarker> marker = RestoreMarkerStore.quarantine(dataset);
        assertTrue(marker.isPresent(), "A restore that replaced one store and not the other must withhold the dataset");
        assertEquals(RestoreMarker.State.INCOMPLETE, marker.get().state());
        assertEquals("disk on fire", marker.get().reason());
        assertEquals(List.of("labels"), marker.get().completedModules());
        assertTrue(markerFileExists(), "The record must survive a restart");
    }

    @Test
    void givenLabelsRestoreThatFailsPartWayThrough_whenFinished_thenTheDatasetIsWithheld() throws IOException {
        // Restoring the labels store replaces its files, so a failure *inside* it leaves a store that is neither the
        // old one nor the new one. No module reported success, but the dataset's data may now be described by labels
        // that do not belong to it, which is the thing that must never be served.
        DatasetBackupService_Test.setupExceptionForMethod(DatasetBackupService_Test.RESTORE_LABELS, "disk on fire");
        // Nothing for the tdb module to do, so the only store anything happened to is the labels store
        removeFromBackup("tdb");

        restore();

        final Optional<RestoreMarker> marker = RestoreMarkerStore.quarantine(dataset);
        assertTrue(marker.isPresent(),
                   "A labels restore that began and did not finish must withhold the dataset, even though no module "
                           + "reported success");
        assertEquals(RestoreMarker.State.INCOMPLETE, marker.get().state());
        assertTrue(marker.get().completedModules().isEmpty(), "Nothing completed");
        assertEquals(List.of("labels"), marker.get().unfinishedModules(),
                     "The record must say which store was left part way through");
        assertTrue(markerFileExists(), "The record must survive a restart");
    }

    @Test
    void givenRestoreThatFailsWithoutTouchingAnything_whenFinished_thenRecordedButTheDatasetIsStillServed()
            throws IOException {
        // The tdb module fails, and the backup holds nothing for the labels module, so it is skipped rather than
        // entered. Nothing was changed, so there is nothing to protect anyone from; the caller was told it failed.
        DatasetBackupService_Test.setupExceptionForMethod(RESTORE_TDB, "disk on fire");
        removeFromBackup("labels");

        restore();

        final Optional<RestoreMarker> marker = RestoreMarkerStore.marker(dataset);
        assertTrue(marker.isPresent(), "A failed restore must leave a record of itself");
        assertEquals(RestoreMarker.State.FAILED, marker.get().state());
        assertTrue(marker.get().completedModules().isEmpty());
        assertTrue(marker.get().unfinishedModules().isEmpty(), "Nothing was left part way through");
        assertTrue(RestoreMarkerStore.quarantine(dataset).isEmpty(),
                   "A restore that changed nothing must not withhold the dataset");
    }

    @Test
    void givenModuleThatAbortsTheRestoreTransaction_whenItFails_thenNoFurtherStoreIsTouched() {
        // Txn.exec() aborts and ends the transaction on its way out of a failure, whether or not it was the call that
        // began it, so a module that throws from inside one leaves the restore with no transaction at all. Carrying on
        // would run the labels restore unprotected, and replacing its files is not something anything can undo.
        final AbortingService aborting = new AbortingService(registry);
        final ObjectNode result = OBJECT_MAPPER.createObjectNode();

        aborting.restoreDataset(restorePath.toString(), DATASET_NAME, result);

        assertEquals(0, DatasetBackupService_Test.getCallCount(DatasetBackupService_Test.RESTORE_LABELS),
                     "The labels store must not be replaced after the restore's transaction has been abandoned");
        assertFalse(result.get(DATASET_NAME).path("success").asBoolean(true), "The restore must report failure");
        final Optional<RestoreMarker> marker = RestoreMarkerStore.marker(dataset);
        assertTrue(marker.isPresent());
        assertEquals(RestoreMarker.State.FAILED, marker.get().state(),
                     "The data was rolled back and nothing else was touched, so the dataset is consistent");
        assertTrue(RestoreMarkerStore.quarantine(dataset).isEmpty());
    }

    /**
     * Removes one module's directory from the staged backup, so that the module is skipped rather than entered
     */
    private void removeFromBackup(String module) throws IOException {
        try (var paths = Files.walk(restorePath.resolve(DATASET_NAME).resolve(module))) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
    }

    @Test
    void givenRestoreAbortedBeforeTouchingTheStores_whenFinished_thenNoMarkerIsWritten() {
        // A restore that gives up waiting for in-flight requests never touches a store, so there is nothing to record
        DatasetBackupService.REQUEST_DRAIN_TIMEOUT = Duration.ofMillis(200);
        final var admission = io.telicent.core.DatasetMaintenanceRegistry.admitRequest(dataset, true);
        assertTrue(admission.allowed());
        try {
            final ObjectNode result = restore();

            assertFalse(result.get(DATASET_NAME).get("success").asBoolean(), "The restore should have aborted");
            assertEquals(0, DatasetBackupService_Test.getCallCount(RESTORE_TDB));
            assertFalse(markerFileExists(), "Nothing was restored, so nothing should be recorded");
            assertTrue(RestoreMarkerStore.marker(dataset).isEmpty());
        } finally {
            admission.close();
        }
    }

    private ObjectNode restore() {
        final ObjectNode result = OBJECT_MAPPER.createObjectNode();
        cut.restoreDataset(restorePath.toString(), DATASET_NAME, result);
        return result;
    }

    private boolean markerFileExists() {
        return Files.isRegularFile(RestoreMarkerStore.markerFile(dataset).toPath());
    }

    private Path stageBackup() throws IOException {
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

    /**
     * Fails the way the real triple store restore fails - from inside a transaction - so that the restore is left
     * with no transaction to protect the stores it has not reached yet.
     */
    private static final class AbortingService extends DatasetBackupService_Test {

        private AbortingService(DataAccessPointRegistry dapRegistry) {
            super(dapRegistry);
        }

        @Override
        void executeRestoreTDB(DatasetGraph dsg, String tdbRestoreFile) {
            DatasetBackupService_Test.incrementMethodCall(RESTORE_TDB);
            // Exactly what the real one does when the backup it is parsing turns out to be truncated
            Txn.executeWrite(dsg, () -> {
                throw new RuntimeException("backup truncated");
            });
        }
    }

    /**
     * Captures what the marker file said at the moment the first store was restored, which is the thing that has to be
     * true for an interrupted restore to be detectable at all.
     */
    private final class MarkerWatchingService extends DatasetBackupService_Test {

        private String markerWhenStoreTouched;

        private MarkerWatchingService(DataAccessPointRegistry dapRegistry) {
            super(dapRegistry);
        }

        @Override
        void executeRestoreTDB(DatasetGraph dsg, String tdbRestoreFile) {
            final Path marker = RestoreMarkerStore.markerFile(dataset).toPath();
            if (Files.isRegularFile(marker)) {
                try {
                    markerWhenStoreTouched = Files.readString(marker);
                } catch (IOException e) {
                    markerWhenStoreTouched = "unreadable: " + e.getMessage();
                }
            }
            super.executeRestoreTDB(dsg, tdbRestoreFile);
        }
    }

}
