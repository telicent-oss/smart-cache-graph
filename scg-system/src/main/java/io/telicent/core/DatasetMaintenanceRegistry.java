package io.telicent.core;

import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphWrapper;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;

import java.time.Instant;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DatasetMaintenanceRegistry {

    static final ConcurrentMap<DatasetGraphSwitchable, MaintenanceIndicator> ACTIVE_MAINTENANCE =
            new ConcurrentHashMap<>();

    public enum MaintenanceOperation {
        COMPACTION("compaction"),
        /**
         * A compaction that serves reads during copying and rejects dataset requests during switching
         */
        RO_COMPACTION("RO Compaction"),
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

    public enum CompactionPhase {
        COPYING, SWITCHING
    }

    // Weak keys avoid retaining datasets after a server is stopped. Gates never reference their dataset key.
    private static final Map<DatasetGraphSwitchable, RequestGate> REQUEST_GATES = new WeakHashMap<>();

    private static synchronized RequestGate gate(DatasetGraphSwitchable dataset) {
        return REQUEST_GATES.computeIfAbsent(dataset, ignored -> new RequestGate());
    }

    private static final class RequestGate {
        int requests;
        boolean switching;
    }

    /** Holds admission for the whole servlet call, including the gap before its transaction begins. */
    public static final class RequestAdmission implements AutoCloseable {
        private final RequestGate gate;
        private final String rejection;
        private final boolean switching;
        private boolean closed;

        private RequestAdmission(RequestGate gate, String rejection, boolean switching) {
            this.gate = gate;
            this.rejection = rejection;
            this.switching = switching;
        }

        public boolean allowed() {
            return rejection == null;
        }

        public String rejection() {
            return rejection;
        }

        /**
         * @return Whether the request was rejected because an RO Compaction is switching to its compacted copy, which
         * normally takes moments, so the request can be retried almost immediately
         */
        public boolean switching() {
            return switching;
        }

        @Override
        public void close() {
            synchronized (gate) {
                if (!closed && allowed()) {
                    gate.requests--;
                    gate.notifyAll();
                }
                closed = true;
            }
        }
    }

    public static RequestAdmission admitRequest(DatasetGraphSwitchable dataset, boolean read) {
        RequestGate gate = gate(dataset);
        synchronized (gate) {
            MaintenanceIndicator maintenance = ACTIVE_MAINTENANCE.get(dataset);
            String rejection = gate.switching ? "RO Compaction switching" : null;
            if (rejection == null && maintenance != null
                    && !(read && maintenance.operation() == MaintenanceOperation.RO_COMPACTION)) {
                rejection = maintenance.operation().displayName();
            }
            if (rejection == null) {
                gate.requests++;
            }
            return new RequestAdmission(gate, rejection, gate.switching);
        }
    }

    public static CompactionPhase compactionPhase(DatasetGraphSwitchable dataset) {
        RequestGate gate = gate(dataset);
        synchronized (gate) {
            return gate.switching ? CompactionPhase.SWITCHING : CompactionPhase.COPYING;
        }
    }

    /** Stops admission before draining accepted requests, so none can queue behind the exclusive lock. */
    static final class SwitchWindow implements AutoCloseable {
        private final RequestGate gate;

        private SwitchWindow(RequestGate gate) {
            this.gate = gate;
            synchronized (gate) {
                if (gate.switching) {
                    throw new IllegalStateException("Dataset is already switching");
                }
                gate.switching = true;
            }
        }

        boolean awaitRequests(long deadline) throws InterruptedException {
            synchronized (gate) {
                while (gate.requests > 0) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        return false;
                    }
                    java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(gate, remaining);
                }
                return true;
            }
        }

        @Override
        public void close() {
            synchronized (gate) {
                gate.switching = false;
                gate.notifyAll();
            }
        }
    }

    static SwitchWindow beginSwitch(DatasetGraphSwitchable dataset) {
        return new SwitchWindow(gate(dataset));
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
