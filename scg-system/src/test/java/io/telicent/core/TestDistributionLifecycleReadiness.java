package io.telicent.core;

import io.telicent.distribution.DistributionLifecycleStateFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDistributionLifecycleReadiness {

    private final DistributionLifecycleReadiness readiness = DistributionLifecycleReadiness.getInstance();

    @AfterEach
    void cleanup() {
        this.readiness.reset();
    }

    @Test
    void snapshot_whenFilteringDisabled_isReady() {
        this.readiness.configure(false, false, null, () -> false);

        DistributionLifecycleReadiness.Snapshot snapshot = this.readiness.snapshot();

        assertTrue(snapshot.ready());
        assertFalse(snapshot.filteringEnabled());
        assertEquals(DistributionLifecycleReadiness.State.DISABLED, snapshot.state());
    }

    @Test
    void snapshot_whenExternalStateUnavailable_isUnready() {
        Path missing = Path.of("target", "missing-lifecycle-state.json");
        this.readiness.configure(true, false, new DistributionLifecycleStateFile(missing, null), () -> false);

        DistributionLifecycleReadiness.Snapshot snapshot = this.readiness.snapshot();

        assertFalse(snapshot.ready());
        assertEquals(DistributionLifecycleReadiness.State.EXTERNAL_ONLY, snapshot.state());
        assertTrue(snapshot.reason().contains("still unavailable"));
    }

    @Test
    void snapshot_whenExternalStateAvailable_isReady() throws IOException {
        Path stateFile = Files.createTempFile("scg-readiness-", ".json");
        writeLifecycleStateFile(stateFile);
        this.readiness.configure(true, false, new DistributionLifecycleStateFile(stateFile, null), () -> false);

        DistributionLifecycleReadiness.Snapshot snapshot = this.readiness.snapshot();

        assertTrue(snapshot.ready());
        assertEquals(DistributionLifecycleReadiness.State.READY, snapshot.state());
    }

    @Test
    void snapshot_whenTrackerStarting_isUnready() throws IOException {
        Path stateFile = Files.createTempFile("scg-readiness-", ".json");
        this.readiness.configure(true, true, new DistributionLifecycleStateFile(stateFile, null), () -> true);
        this.readiness.markStarting("Distribution lifecycle tracker is starting or catching up.");

        DistributionLifecycleReadiness.Snapshot snapshot = this.readiness.snapshot();

        assertFalse(snapshot.ready());
        assertEquals(DistributionLifecycleReadiness.State.STARTING, snapshot.state());
        assertTrue(snapshot.reason().contains("starting"));
    }

    @Test
    void snapshot_whenTrackerFailed_isUnready() throws IOException {
        Path stateFile = Files.createTempFile("scg-readiness-", ".json");
        this.readiness.configure(true, true, new DistributionLifecycleStateFile(stateFile, null), () -> false);
        this.readiness.markFailed("Distribution lifecycle tracker is unavailable: lifecycle topic missing");

        DistributionLifecycleReadiness.Snapshot snapshot = this.readiness.snapshot();

        assertFalse(snapshot.ready());
        assertEquals(DistributionLifecycleReadiness.State.FAILED, snapshot.state());
        assertTrue(snapshot.reason().contains("unavailable"));
    }

    @Test
    void snapshot_whenTrackerReadyAndStateAvailable_isReady() throws IOException {
        Path stateFile = Files.createTempFile("scg-readiness-", ".json");
        writeLifecycleStateFile(stateFile);
        this.readiness.configure(true, true, new DistributionLifecycleStateFile(stateFile, null), () -> true);
        this.readiness.markReady();

        DistributionLifecycleReadiness.Snapshot snapshot = this.readiness.snapshot();

        assertTrue(snapshot.ready());
        assertEquals(DistributionLifecycleReadiness.State.READY, snapshot.state());
    }

    private static void writeLifecycleStateFile(Path stateFile) throws IOException {
        Files.writeString(stateFile, """
                {
                  "application": "scg-test",
                  "distributions": {
                    "urn:distribution:one": "Active"
                  }
                }
                """, StandardCharsets.UTF_8);
    }
}
