package io.telicent.core;

import io.telicent.smart.cache.security.data.distribution.DistributionLifecycleStateFile;
import io.telicent.smart.cache.security.data.distribution.DistributionLifecycleStateFile.DistributionStateResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static io.telicent.core.DistributionIngestGate.Decision.DROP;
import static io.telicent.core.DistributionIngestGate.Decision.INGEST;
import static io.telicent.core.DistributionIngestGate.Decision.REJECT;

public class TestDistributionIngestGate {

    private static final String D = "http://example/distribution";
    private static final Duration GRACE = Duration.ofSeconds(30);

    private DistributionLifecycleStateFile stateFile;
    private final AtomicLong clock = new AtomicLong();
    private int sleeps;

    @BeforeEach
    public void setup() {
        this.stateFile = Mockito.mock(DistributionLifecycleStateFile.class);
        this.clock.set(0);
        this.sleeps = 0;
    }

    private DistributionIngestGate gate(boolean rejectUnregistered, Duration grace) {
        return new DistributionIngestGate(this.stateFile, rejectUnregistered, grace, millis -> {
            this.sleeps++;
            this.clock.addAndGet(Duration.ofMillis(millis).toNanos());
        }, this.clock::get);
    }

    private static DistributionStateResult known(String state) {
        return new DistributionStateResult(state, true);
    }

    private static final DistributionStateResult UNKNOWN = new DistributionStateResult(null, true);
    private static final DistributionStateResult UNAVAILABLE = new DistributionStateResult(null, false);

    private void state(DistributionStateResult result) {
        Mockito.when(this.stateFile.distributionStateResult(D)).thenReturn(result);
    }

    @Test
    public void givenViableStates_whenDeciding_thenIngestedWithoutWaiting() {
        DistributionIngestGate gate = gate(true, GRACE);
        for (String s : new String[] { "Registered", "Active", "Withdrawn" }) {
            state(known(s));
            Assertions.assertEquals(INGEST, gate.decide(D).decision(), s);
        }
        Assertions.assertEquals(0, this.sleeps);
    }

    @Test
    public void givenStateUnavailable_whenDeciding_thenEveryEventRejectedWithoutWaiting() {
        DistributionIngestGate gate = gate(true, GRACE);
        state(UNAVAILABLE);
        for (int i = 0; i < 3; i++) {
            DistributionIngestGate.Outcome outcome = gate.decide(D);
            Assertions.assertEquals(REJECT, outcome.decision());
            Assertions.assertTrue(outcome.reason().contains("unavailable"));
        }
        Assertions.assertEquals(0, this.sleeps);
    }

    @Test
    public void givenDeleted_whenDeciding_thenFirstRejectedAndRestDropped() {
        DistributionIngestGate gate = gate(true, GRACE);
        state(known("Deleted"));
        DistributionIngestGate.Outcome first = gate.decide(D);
        Assertions.assertEquals(REJECT, first.decision());
        Assertions.assertTrue(first.reason().contains("Deleted"));
        Assertions.assertEquals(DROP, gate.decide(D).decision());
        Assertions.assertEquals(DROP, gate.decide(D).decision());
        Assertions.assertEquals(0, this.sleeps, "Deleted must be rejected without waiting");
    }

    @Test
    public void givenUnknownThroughoutGrace_whenDeciding_thenWaitsOnceThenRejectsEveryEvent() {
        DistributionIngestGate gate = gate(true, GRACE);
        state(UNKNOWN);

        DistributionIngestGate.Outcome first = gate.decide(D);
        Assertions.assertEquals(REJECT, first.decision());
        Assertions.assertTrue(first.reason().contains("Unregistered"));
        int firstWait = this.sleeps;
        Assertions.assertTrue(firstWait > 0, "Expected the first event to wait for the grace period");
        Assertions.assertTrue(this.clock.get() >= GRACE.toNanos(), "Expected the whole grace period to be spent");

        // Later events are still rejected (never silently dropped), and don't wait again
        Assertions.assertEquals(REJECT, gate.decide(D).decision());
        Assertions.assertEquals(REJECT, gate.decide(D).decision());
        Assertions.assertEquals(firstWait, this.sleeps);
    }

    @Test
    public void givenExplicitUnregistered_whenDeciding_thenTreatedLikeUnknown() {
        DistributionIngestGate gate = gate(true, Duration.ZERO);
        state(known("Unregistered"));
        Assertions.assertEquals(REJECT, gate.decide(D).decision());
        Assertions.assertEquals(REJECT, gate.decide(D).decision());
    }

    @Test
    public void givenRegistrationArrivesDuringGrace_whenDeciding_thenIngested() {
        DistributionIngestGate gate = gate(true, GRACE);
        Mockito.when(this.stateFile.distributionStateResult(D))
               .thenReturn(UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, known("Registered"));

        Assertions.assertEquals(INGEST, gate.decide(D).decision());
        Assertions.assertTrue(this.sleeps > 0 && this.clock.get() < GRACE.toNanos(),
                              "Expected a short wait, not the full grace period");
    }

    @Test
    public void givenUnknownThenRegistered_whenDeciding_thenIngestedAgainAndGraceResets() {
        DistributionIngestGate gate = gate(true, GRACE);
        state(UNKNOWN);
        Assertions.assertEquals(REJECT, gate.decide(D).decision());

        state(known("Active"));
        Assertions.assertEquals(INGEST, gate.decide(D).decision());

        state(UNKNOWN);
        int before = this.sleeps;
        Assertions.assertEquals(REJECT, gate.decide(D).decision());
        Assertions.assertTrue(this.sleeps > before, "Expected a fresh grace period");
    }

    @Test
    public void givenDeletedThenReactivated_whenDeciding_thenRejectionStateCleared() {
        DistributionIngestGate gate = gate(true, GRACE);
        state(known("Deleted"));
        Assertions.assertEquals(REJECT, gate.decide(D).decision());
        state(known("Active"));
        Assertions.assertEquals(INGEST, gate.decide(D).decision());
        state(known("Deleted"));
        Assertions.assertEquals(REJECT, gate.decide(D).decision(),
                                "A fresh deletion should reject its first event again");
    }

    @Test
    public void givenRejectUnregisteredDisabled_whenDeciding_thenUnknownIngestedButDeletedStillRejected() {
        DistributionIngestGate gate = gate(false, GRACE);
        state(UNKNOWN);
        Assertions.assertEquals(INGEST, gate.decide(D).decision());
        Assertions.assertEquals(0, this.sleeps);
        state(known("Deleted"));
        Assertions.assertEquals(REJECT, gate.decide(D).decision());
    }

    @Test
    public void givenDeletedDuringGrace_whenDeciding_thenTreatedAsDeleted() {
        DistributionIngestGate gate = gate(true, GRACE);
        Mockito.when(this.stateFile.distributionStateResult(D)).thenReturn(UNKNOWN, UNKNOWN, known("Deleted"));
        Assertions.assertEquals(REJECT, gate.decide(D).decision());
        Assertions.assertEquals(DROP, gate.decide(D).decision());
    }

    @Test
    public void givenStateBecomesUnavailableDuringGrace_whenDeciding_thenRejected() {
        DistributionIngestGate gate = gate(true, GRACE);
        Mockito.when(this.stateFile.distributionStateResult(D)).thenReturn(UNKNOWN, UNAVAILABLE);
        DistributionIngestGate.Outcome outcome = gate.decide(D);
        Assertions.assertEquals(REJECT, outcome.decision());
        Assertions.assertTrue(outcome.reason().contains("unavailable"));
    }

    @Test
    public void givenInterruptedWhileWaiting_whenDeciding_thenRejectsAndKeepsInterruptFlag() {
        DistributionIngestGate gate = new DistributionIngestGate(this.stateFile, true, GRACE, millis -> {
            throw new InterruptedException();
        }, this.clock::get);
        state(UNKNOWN);
        try {
            Assertions.assertEquals(REJECT, gate.decide(D).decision());
            Assertions.assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
