package io.telicent.core;

import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static DatasetGraphSwitchable createPersistentSwitchableDataset() throws IOException {
        Path container = Files.createTempDirectory("maintenance-registry-dsg");
        Path dataDir = container.resolve("Data-0001");
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve("marker.txt"), "x", StandardCharsets.UTF_8);
        return new DatasetGraphSwitchable(container, null, DatasetGraphFactory.createTxnMem());
    }
}
