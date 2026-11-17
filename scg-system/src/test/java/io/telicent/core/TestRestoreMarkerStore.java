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

import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the durable record of a restore that a dataset carries while it is being restored.
 * <p>
 * The point of that record is to survive the service dying, so "the service died" is simulated throughout by dropping
 * the in-memory state and calling {@link RestoreMarkerStore#recover(DatasetGraphSwitchable, String)}, which is what a
 * fresh server does as it is configured. Everything asserted after that comes from the file the previous "process"
 * left behind.
 */
class TestRestoreMarkerStore {

    private static final String DATASET_NAME = "/ds";

    @TempDir
    private Path workDir;

    private DatasetGraphSwitchable dataset;

    @BeforeEach
    void setup() throws IOException {
        RestoreMarkerStore.MARKERS.clear();
        dataset = switchableDataset("dsg");
    }

    @AfterEach
    void cleanup() {
        RestoreMarkerStore.MARKERS.clear();
    }

    @Test
    void givenRestoreStarted_whenServiceDies_thenTheDatasetIsQuarantinedOnStartup() {
        // Given -- a restore that applied the triple store and then never came back
        RestoreMarkerStore.begin(dataset, DATASET_NAME, "14");
        RestoreMarkerStore.moduleRestored(dataset, "tdb");
        assertTrue(markerFileExists(), "The marker must be on disk, not just in memory");

        // When -- the service restarts
        restart();

        // Then
        final Optional<RestoreMarker> quarantine = RestoreMarkerStore.quarantine(dataset);
        assertTrue(quarantine.isPresent(), "A restore that never finished must withhold the dataset");
        assertEquals(RestoreMarker.State.INCOMPLETE, quarantine.get().state());
        assertEquals(DATASET_NAME, quarantine.get().datasetName());
        assertEquals("14", quarantine.get().backupId());
        assertEquals(List.of("tdb"), quarantine.get().completedModules(),
                     "The record must say how far the restore got");
        assertTrue(markerFileExists(), "The marker must survive the restart that found it");
    }

    @Test
    void givenQuarantinedDataset_whenRestartedAgain_thenStillQuarantined() {
        RestoreMarkerStore.begin(dataset, DATASET_NAME, "14");
        restart();
        restart();

        assertTrue(RestoreMarkerStore.quarantine(dataset).isPresent(),
                   "A quarantine must not be lost by restarting again");
    }

    @Test
    void givenSuccessfulRestore_whenItCompletes_thenNoRecordIsLeft() {
        RestoreMarkerStore.begin(dataset, DATASET_NAME, "14");
        RestoreMarkerStore.moduleRestored(dataset, "tdb");
        RestoreMarkerStore.moduleRestored(dataset, "labels");

        RestoreMarkerStore.complete(dataset, true, null);

        assertFalse(markerFileExists(), "A successful restore must clear the marker");
        restart();
        assertTrue(RestoreMarkerStore.quarantine(dataset).isEmpty());
        assertTrue(RestoreMarkerStore.markers().isEmpty());
    }

    @Test
    void givenRestoreThatFailedWithoutChangingAnything_whenItCompletes_thenRecordedButStillServed() {
        RestoreMarkerStore.begin(dataset, DATASET_NAME, "14");

        RestoreMarkerStore.complete(dataset, false, "Restore file not found");

        // No store was replaced, so there is nothing inconsistent to protect anyone from. The failure was reported to
        // whoever asked for the restore; the record of it outlives the process that did it
        assertTrue(RestoreMarkerStore.quarantine(dataset).isEmpty(),
                   "A restore that changed nothing must not withhold the dataset");
        restart();
        final Optional<RestoreMarker> marker = RestoreMarkerStore.marker(dataset);
        assertTrue(marker.isPresent());
        assertEquals(RestoreMarker.State.FAILED, marker.get().state());
        assertEquals("Restore file not found", marker.get().reason());
        assertTrue(RestoreMarkerStore.quarantine(dataset).isEmpty());
    }

    @Test
    void givenRestoreThatFailedAfterReplacingAStore_whenItCompletes_thenTheDatasetIsWithheld() {
        // One store replaced and another not is the inconsistency this exists for, whether the process survived to
        // report it or not
        RestoreMarkerStore.begin(dataset, DATASET_NAME, "14");
        RestoreMarkerStore.moduleRestored(dataset, "labels");

        RestoreMarkerStore.complete(dataset, false, "Restore file not found: .../tdb/");

        final Optional<RestoreMarker> quarantine = RestoreMarkerStore.quarantine(dataset);
        assertTrue(quarantine.isPresent(), "The labels were replaced and the data was not; that must not be served");
        assertEquals(RestoreMarker.State.INCOMPLETE, quarantine.get().state());
        assertEquals(List.of("labels"), quarantine.get().completedModules());
        restart();
        assertTrue(RestoreMarkerStore.quarantine(dataset).isPresent());
    }

    @Test
    void givenModuleThatStartedAndNeverFinished_whenItCompletes_thenTheDatasetIsWithheld() {
        // Replacing the files of a store is not something a transaction undoes, so a module that began and never said
        // it had finished has left a store nobody can describe. No module reported success, but the dataset's data may
        // now be described by labels that do not belong to it, which is worse than a store that was simply replaced.
        RestoreMarkerStore.begin(dataset, DATASET_NAME, "14");
        RestoreMarkerStore.moduleStarted(dataset, "labels");

        RestoreMarkerStore.complete(dataset, false, "disk on fire");

        final Optional<RestoreMarker> quarantine = RestoreMarkerStore.quarantine(dataset);
        assertTrue(quarantine.isPresent(),
                   "A store left part way through must withhold the dataset even though nothing completed");
        assertEquals(RestoreMarker.State.INCOMPLETE, quarantine.get().state());
        assertTrue(quarantine.get().completedModules().isEmpty());
        assertEquals(List.of("labels"), quarantine.get().unfinishedModules());
        restart();
        final Optional<RestoreMarker> afterRestart = RestoreMarkerStore.quarantine(dataset);
        assertTrue(afterRestart.isPresent(), "Which store was left part way through must survive a restart");
        assertEquals(List.of("labels"), afterRestart.get().unfinishedModules());
    }

    @Test
    void givenModuleThatStartedAndFinished_whenTheRestoreFails_thenNothingIsReportedAsUnfinished() {
        RestoreMarkerStore.begin(dataset, DATASET_NAME, "14");
        RestoreMarkerStore.moduleStarted(dataset, "labels");
        RestoreMarkerStore.moduleRestored(dataset, "labels");

        RestoreMarkerStore.complete(dataset, false, "Restore file not found: .../tdb/");

        // Still withheld - the labels were replaced and the data was not - but the labels store itself is intact
        final Optional<RestoreMarker> quarantine = RestoreMarkerStore.quarantine(dataset);
        assertTrue(quarantine.isPresent());
        assertEquals(List.of("labels"), quarantine.get().completedModules());
        assertTrue(quarantine.get().unfinishedModules().isEmpty(),
                   "A module that finished must not be reported as having been left part way through");
    }

    @Test
    void givenQuarantinedDataset_whenRestoredSuccessfully_thenServedAgain() {
        RestoreMarkerStore.begin(dataset, DATASET_NAME, "14");
        restart();
        assertTrue(RestoreMarkerStore.quarantine(dataset).isPresent());

        RestoreMarkerStore.begin(dataset, DATASET_NAME, "15");
        RestoreMarkerStore.complete(dataset, true, null);

        assertTrue(RestoreMarkerStore.quarantine(dataset).isEmpty(), "A successful restore resolves the quarantine");
        assertFalse(markerFileExists());
    }

    @Test
    void givenQuarantinedDataset_whenRestoreFailsAgain_thenStillQuarantined() {
        RestoreMarkerStore.begin(dataset, DATASET_NAME, "14");
        restart();

        RestoreMarkerStore.begin(dataset, DATASET_NAME, "15");
        RestoreMarkerStore.complete(dataset, false, "Timed out waiting for Kafka projectors");

        final Optional<RestoreMarker> quarantine = RestoreMarkerStore.quarantine(dataset);
        assertTrue(quarantine.isPresent(), "Only a successful restore lifts a quarantine");
        assertEquals("15", quarantine.get().backupId(), "The record must describe the latest attempt");
        restart();
        assertTrue(RestoreMarkerStore.quarantine(dataset).isPresent());
    }

    /**
     * Restoring successfully is the only way out of a quarantine - there is deliberately no API for accepting a
     * dataset whose state nobody can describe - so it has to put the dataset back into service for good.
     */
    @Test
    void givenQuarantinedDataset_whenRestoredSuccessfully_thenServedAgainAfterRestart() {
        RestoreMarkerStore.begin(dataset, DATASET_NAME, "14");
        restart();
        assertTrue(RestoreMarkerStore.quarantine(dataset).isPresent(), "The interrupted restore should withhold it");

        RestoreMarkerStore.begin(dataset, DATASET_NAME, "15");
        RestoreMarkerStore.moduleRestored(dataset, "tdb");
        RestoreMarkerStore.complete(dataset, true, null);

        assertTrue(RestoreMarkerStore.quarantine(dataset).isEmpty(), "A successful restore must lift the quarantine");
        assertFalse(markerFileExists(), "It must remove the record, not just forget it");
        restart();
        assertTrue(RestoreMarkerStore.quarantine(dataset).isEmpty());
    }

    @Test
    void givenDatasetThatWasNeverRestored_whenStartingUp_thenNothingIsRecorded() {
        restart();

        assertTrue(RestoreMarkerStore.marker(dataset).isEmpty());
        assertTrue(RestoreMarkerStore.markers().isEmpty());
    }

    @Test
    void givenMalformedMarker_whenStartingUp_thenItIsIgnored() throws IOException {
        Files.writeString(markerPath(), "not a marker");

        restart();

        assertTrue(RestoreMarkerStore.quarantine(dataset).isEmpty(),
                   "A marker that cannot be understood must not wedge a dataset");
    }

    @Test
    void givenDatasetThisServerDoesNotManage_whenRestored_thenEveryOperationIsANoOp() {
        RestoreMarkerStore.begin(null, DATASET_NAME, "14");
        RestoreMarkerStore.moduleRestored(null, "tdb");
        RestoreMarkerStore.complete(null, false, "failed");
        RestoreMarkerStore.recover(null, DATASET_NAME);

        assertTrue(RestoreMarkerStore.marker(null).isEmpty());
        assertTrue(RestoreMarkerStore.markers().isEmpty());
    }

    @Test
    void givenInMemoryDataset_whenRestored_thenNothingIsWrittenToDisk() {
        // A TDB2 dataset held in memory has no container directory to keep a marker in, and nothing about it survives
        // the process in any case
        final DatasetGraphSwitchable inMemory =
                (DatasetGraphSwitchable) org.apache.jena.tdb2.TDB2Factory.createDataset().asDatasetGraph();

        RestoreMarkerStore.begin(inMemory, DATASET_NAME, "14");
        RestoreMarkerStore.moduleRestored(inMemory, "tdb");
        RestoreMarkerStore.complete(inMemory, true, null);
        RestoreMarkerStore.recover(inMemory, DATASET_NAME);

        assertTrue(RestoreMarkerStore.marker(inMemory).isEmpty());
    }

    @Test
    void givenTwoDatasets_whenOneIsQuarantined_thenTheOtherIsUnaffected() throws IOException {
        final DatasetGraphSwitchable other = switchableDataset("other-dsg");
        RestoreMarkerStore.begin(dataset, DATASET_NAME, "14");

        restart();
        RestoreMarkerStore.recover(other, "/other");

        assertTrue(RestoreMarkerStore.quarantine(dataset).isPresent());
        assertTrue(RestoreMarkerStore.quarantine(other).isEmpty());
    }

    /**
     * Simulates the service stopping and coming back: the in-memory state goes, and the new process reads whatever is
     * on disk as it configures the dataset.
     */
    private void restart() {
        RestoreMarkerStore.MARKERS.clear();
        RestoreMarkerStore.recover(dataset, DATASET_NAME);
    }

    private Path markerPath() {
        return RestoreMarkerStore.markerFile(dataset).toPath();
    }

    private boolean markerFileExists() {
        return Files.isRegularFile(markerPath());
    }

    private DatasetGraphSwitchable switchableDataset(String name) throws IOException {
        final Path container = Files.createDirectories(workDir.resolve(name));
        Files.createDirectories(container.resolve("Data-0001"));
        return new DatasetGraphSwitchable(container, null, DatasetGraphFactory.createTxnMem());
    }
}
