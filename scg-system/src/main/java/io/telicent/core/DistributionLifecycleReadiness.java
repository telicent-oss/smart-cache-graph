package io.telicent.core;

import io.telicent.distribution.DistributionLifecycleStateFile;

import java.util.Objects;
import java.util.function.BooleanSupplier;

final class DistributionLifecycleReadiness {

    enum State {
        DISABLED,
        EXTERNAL_ONLY,
        STARTING,
        READY,
        FAILED
    }

    record Snapshot(boolean ready, boolean filteringEnabled, boolean trackerEnabled, State state, String reason) {
    }

    private static final DistributionLifecycleReadiness INSTANCE = new DistributionLifecycleReadiness();

    private volatile boolean filteringEnabled;
    private volatile boolean trackerEnabled;
    private volatile State state = State.DISABLED;
    private volatile String reason = "Distribution lifecycle filtering is disabled.";
    private volatile DistributionLifecycleStateFile stateFile;
    private volatile BooleanSupplier trackerRunningProbe = () -> false;

    static DistributionLifecycleReadiness getInstance() {
        return INSTANCE;
    }

    void reset() {
        this.filteringEnabled = false;
        this.trackerEnabled = false;
        this.state = State.DISABLED;
        this.reason = "Distribution lifecycle filtering is disabled.";
        this.stateFile = null;
        this.trackerRunningProbe = () -> false;
    }

    void configure(boolean filteringEnabled, boolean trackerEnabled, DistributionLifecycleStateFile stateFile,
                   BooleanSupplier trackerRunningProbe) {
        this.filteringEnabled = filteringEnabled;
        this.trackerEnabled = trackerEnabled;
        this.stateFile = stateFile;
        this.trackerRunningProbe = trackerRunningProbe != null ? trackerRunningProbe : () -> false;
        if (!filteringEnabled) {
            this.state = State.DISABLED;
            this.reason = "Distribution lifecycle filtering is disabled.";
        } else if (!trackerEnabled) {
            this.state = State.EXTERNAL_ONLY;
            this.reason = "Distribution lifecycle filtering is enabled and waiting for lifecycle state to become usable.";
        } else {
            this.state = State.STARTING;
            this.reason = "Distribution lifecycle tracker is starting or catching up.";
        }
    }

    void markStarting(String reason) {
        this.state = State.STARTING;
        this.reason = Objects.requireNonNullElse(reason,
                                                 "Distribution lifecycle tracker is starting or catching up.");
    }

    void markReady() {
        this.state = State.READY;
        this.reason = "Distribution lifecycle state is usable.";
    }

    void markFailed(String reason) {
        this.state = State.FAILED;
        this.reason = Objects.requireNonNullElse(reason, "Distribution lifecycle tracker is unavailable.");
    }

    Snapshot snapshot() {
        if (!this.filteringEnabled) {
            return new Snapshot(true, false, false, State.DISABLED, this.reason);
        }

        boolean stateAvailable = this.stateFile != null && this.stateFile.available();
        if (!this.trackerEnabled) {
            if (stateAvailable) {
                return new Snapshot(true, true, false, State.READY, "Distribution lifecycle state is usable.");
            }
            return new Snapshot(false, true, false, State.EXTERNAL_ONLY,
                                "Distribution lifecycle filtering is enabled but lifecycle state is still unavailable.");
        }

        if (this.state == State.READY && !this.trackerRunningProbe.getAsBoolean()) {
            return new Snapshot(false, true, true, State.FAILED,
                                "Distribution lifecycle tracker is no longer running.");
        }

        if (this.state == State.READY && !stateAvailable) {
            return new Snapshot(false, true, true, State.STARTING,
                                "Distribution lifecycle tracker is running but lifecycle state is not yet usable.");
        }

        if (this.state == State.READY) {
            return new Snapshot(true, true, true, State.READY, this.reason);
        }

        return new Snapshot(false, true, true, this.state, this.reason);
    }
}
