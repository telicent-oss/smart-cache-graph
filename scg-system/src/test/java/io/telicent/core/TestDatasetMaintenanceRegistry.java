package io.telicent.core;

import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDatasetMaintenanceRegistry {

    @AfterEach
    void tearDown() {
        DatasetMaintenanceRegistry.ACTIVE_MAINTENANCE.clear();
    }

    @Test
    void begin_whenDatasetIsUnmanaged_returnsNoOpHandleWithoutTracking() {
        Optional<DatasetMaintenanceRegistry.MaintenanceHandle> handle =
                DatasetMaintenanceRegistry.begin(DatasetGraphFactory.createTxnMem(), "/ds",
                                                 DatasetMaintenanceRegistry.MaintenanceOperation.BACKUP);

        assertTrue(handle.isPresent());
        assertFalse(handle.get().isTracked());
        assertTrue(DatasetMaintenanceRegistry.ACTIVE_MAINTENANCE.isEmpty());

        DatasetMaintenanceRegistry.end(handle.get());

        assertTrue(DatasetMaintenanceRegistry.ACTIVE_MAINTENANCE.isEmpty());
    }

    @Test
    void begin_whenManagedDatasetAlreadyTracked_returnsEmpty() throws IOException {
        DatasetGraphSwitchable dataset = createPersistentSwitchableDataset();
        Optional<DatasetMaintenanceRegistry.MaintenanceHandle> first =
                DatasetMaintenanceRegistry.begin(dataset, "/ds",
                                                 DatasetMaintenanceRegistry.MaintenanceOperation.BACKUP);

        assertTrue(first.isPresent());
        assertTrue(first.get().isTracked());
        assertTrue(DatasetMaintenanceRegistry.isMaintenanceInProgress(dataset));

        Optional<DatasetMaintenanceRegistry.MaintenanceHandle> second =
                DatasetMaintenanceRegistry.begin(dataset, "/ds",
                                                 DatasetMaintenanceRegistry.MaintenanceOperation.RESTORE);

        assertTrue(second.isEmpty());

        DatasetMaintenanceRegistry.end(first.get());

        assertFalse(DatasetMaintenanceRegistry.isMaintenanceInProgress(dataset));
    }

    // -----------------------------------------------------------------------------------
    // Draining requests admitted before a maintenance operation started.  begin() stops new
    // requests being admitted, so the count can only fall and the wait is bounded.
    // -----------------------------------------------------------------------------------

    @Test
    void awaitRequests_whenHandleIsUntracked_returnsImmediately() throws InterruptedException {
        assertTrue(DatasetMaintenanceRegistry.awaitRequests(DatasetMaintenanceRegistry.MaintenanceHandle.noOp(),
                                                            Duration.ofMillis(1)),
                   "An unmanaged dataset has no admitted requests to wait for");
    }

    @Test
    void awaitRequests_whenNothingInFlight_returnsImmediately() throws Exception {
        DatasetGraphSwitchable dataset = createPersistentSwitchableDataset();
        DatasetMaintenanceRegistry.MaintenanceHandle handle =
                DatasetMaintenanceRegistry.begin(dataset, "/ds",
                                                 DatasetMaintenanceRegistry.MaintenanceOperation.RESTORE)
                                          .orElseThrow();

        assertTrue(DatasetMaintenanceRegistry.awaitRequests(handle, Duration.ofMillis(1)));

        DatasetMaintenanceRegistry.end(handle);
    }

    @Test
    void awaitRequests_whenRequestInFlight_waitsForItToFinish() throws Exception {
        DatasetGraphSwitchable dataset = createPersistentSwitchableDataset();
        // Admitted before the maintenance begins, exactly like a query already running when a restore is requested
        DatasetMaintenanceRegistry.RequestAdmission admission =
                DatasetMaintenanceRegistry.admitRequest(dataset, true);
        assertTrue(admission.allowed());

        DatasetMaintenanceRegistry.MaintenanceHandle handle =
                DatasetMaintenanceRegistry.begin(dataset, "/ds",
                                                 DatasetMaintenanceRegistry.MaintenanceOperation.RESTORE)
                                          .orElseThrow();
        assertFalse(DatasetMaintenanceRegistry.admitRequest(dataset, true).allowed(),
                    "New requests must already be rejected, otherwise the drain could never finish");

        assertFalse(DatasetMaintenanceRegistry.awaitRequests(handle, Duration.ofMillis(100)),
                    "Must keep waiting while the admitted request is still running");

        Thread finisher = new Thread(admission::close, "finish-request");
        finisher.start();

        assertTrue(DatasetMaintenanceRegistry.awaitRequests(handle, Duration.ofSeconds(10)),
                   "Must return once the admitted request finishes");

        finisher.join();
        DatasetMaintenanceRegistry.end(handle);
    }

    @Test
    void awaitRequests_whenRequestNeverFinishes_timesOut() throws Exception {
        DatasetGraphSwitchable dataset = createPersistentSwitchableDataset();
        DatasetMaintenanceRegistry.RequestAdmission admission =
                DatasetMaintenanceRegistry.admitRequest(dataset, true);
        assertTrue(admission.allowed());

        DatasetMaintenanceRegistry.MaintenanceHandle handle =
                DatasetMaintenanceRegistry.begin(dataset, "/ds",
                                                 DatasetMaintenanceRegistry.MaintenanceOperation.RESTORE)
                                          .orElseThrow();

        assertFalse(DatasetMaintenanceRegistry.awaitRequests(handle, Duration.ofMillis(200)),
                    "A request that never finishes must time out rather than block the restore forever");

        admission.close();
        DatasetMaintenanceRegistry.end(handle);
    }

    private static DatasetGraphSwitchable createPersistentSwitchableDataset() throws IOException {
        Path container = Files.createTempDirectory("maintenance-registry-dsg");
        Path dataDir = container.resolve("Data-0001");
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve("marker.txt"), "x", StandardCharsets.UTF_8);
        return new DatasetGraphSwitchable(container, null, DatasetGraphFactory.createTxnMem());
    }
}
