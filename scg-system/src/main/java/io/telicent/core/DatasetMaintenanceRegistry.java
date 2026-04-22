package io.telicent.core;

import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphWrapper;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DatasetMaintenanceRegistry {

    static final ConcurrentMap<DatasetGraphSwitchable, MaintenanceIndicator> ACTIVE_MAINTENANCE =
            new ConcurrentHashMap<>();

    public enum MaintenanceOperation {
        COMPACTION("compaction"),
        BACKUP("backup"),
        RESTORE("restore");

        private final String displayName;

        MaintenanceOperation(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public record MaintenanceIndicator(MaintenanceOperation operation,
                                       String datasetName,
                                       String startedAt,
                                       String updatedAt,
                                       String error) {
    }

    public record MaintenanceHandle(DatasetGraphSwitchable dataset,
                                    MaintenanceIndicator indicator) {

        public static MaintenanceHandle noOp() {
            return new MaintenanceHandle(null, null);
        }

        public boolean isTracked() {
            return dataset != null && indicator != null;
        }
    }

    private DatasetMaintenanceRegistry() {
    }

    public static Optional<DatasetGraphSwitchable> getManagedDataset(DatasetGraph dsg) {
        for (;;) {
            if (dsg instanceof DatasetGraphSwitchable switchable) {
                return Optional.of(switchable);
            }
            if (!(dsg instanceof DatasetGraphWrapper wrapper)) {
                return Optional.empty();
            }
            dsg = wrapper.getWrapped();
        }
    }

    public static Optional<MaintenanceHandle> begin(DatasetGraph dsg, String datasetName, MaintenanceOperation operation) {
        Optional<DatasetGraphSwitchable> managed = getManagedDataset(dsg);
        if (managed.isEmpty()) {
            return Optional.of(MaintenanceHandle.noOp());
        }

        Instant now = Instant.now();
        MaintenanceIndicator indicator =
                new MaintenanceIndicator(operation, datasetName, now.toString(), now.toString(), null);
        MaintenanceIndicator existing = ACTIVE_MAINTENANCE.putIfAbsent(managed.get(), indicator);
        if (existing != null) {
            return Optional.empty();
        }
        return Optional.of(new MaintenanceHandle(managed.get(), indicator));
    }

    public static void end(MaintenanceHandle handle) {
        if (handle == null || !handle.isTracked()) {
            return;
        }
        ACTIVE_MAINTENANCE.remove(handle.dataset(), handle.indicator());
    }

    public static Optional<MaintenanceIndicator> findCurrentMaintenance(DatasetGraphSwitchable dsg) {
        return Optional.ofNullable(ACTIVE_MAINTENANCE.get(dsg));
    }

    public static boolean isMaintenanceInProgress(DatasetGraphSwitchable dsg) {
        return ACTIVE_MAINTENANCE.containsKey(dsg);
    }
}
