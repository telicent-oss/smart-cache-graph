package io.telicent.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class TestDatasetIngestPause {

    private final List<String> calls = new ArrayList<>();

    private DatasetIngestPause pauser(Collection<String> registered, Set<String> neverPause) {
        Consumer<String> pause = name -> calls.add("pause " + name);
        Consumer<String> resume = name -> calls.add("resume " + name);
        return new DatasetIngestPause(() -> registered, pause, resume,
                                      (name, timeout) -> !neverPause.contains(name));
    }

    @Test
    void givenConnectorOnDatasetOrEndpoint_whenFindingConnectors_thenBothMatchAlongsideDatasetName() {
        DatasetIngestPause pauser = pauser(List.of("/knowledge/upload", "/knowledge", "/ontology", "knowledge2",
                                                   "/knowledge2/upload", "knowledge/", "/catalog"), Set.of());

        List<String> found = pauser.connectorsFor("/knowledge");

        assertEquals(List.of("/knowledge", "/knowledge/upload", "knowledge/"), found);
    }

    @Test
    void givenNoRegisteredConnectors_whenFindingConnectors_thenOnlyDatasetName() {
        assertEquals(List.of("/ds"), pauser(List.of(), Set.of()).connectorsFor("/ds"));
    }

    @Test
    void givenEndpointConnector_whenPausingAndClosing_thenPausesAndResumesEveryMatch() {
        DatasetIngestPause pauser = pauser(List.of("/knowledge/upload", "/other"), Set.of());

        try (DatasetIngestPause.Handle handle = pauser.pause("/knowledge", Duration.ofSeconds(1))) {
            assertEquals(DatasetIngestPause.Outcome.PAUSED, handle.outcome());
            assertTrue(handle.isIngestStopped());
            assertEquals(List.of("pause /knowledge", "pause /knowledge/upload"), calls);
        }

        assertEquals(List.of("pause /knowledge", "pause /knowledge/upload", "resume /knowledge",
                             "resume /knowledge/upload"), calls);
    }

    @Test
    void givenHandle_whenClosedTwice_thenResumesOnce() {
        DatasetIngestPause.Handle handle = pauser(List.of(), Set.of()).pause("/ds", Duration.ofSeconds(1));
        handle.close();
        handle.close();

        assertEquals(List.of("pause /ds", "resume /ds"), calls);
    }

    @Test
    void givenProjectorNeverReachesPausePoint_whenPausing_thenTimesOutAndResumesEverything() {
        DatasetIngestPause pauser = pauser(List.of("/knowledge/upload"), Set.of("/knowledge/upload"));

        DatasetIngestPause.Handle handle = pauser.pause("/knowledge", Duration.ofMillis(10));

        assertEquals(DatasetIngestPause.Outcome.TIMED_OUT, handle.outcome());
        assertFalse(handle.isIngestStopped());
        assertEquals(List.of("pause /knowledge", "pause /knowledge/upload", "resume /knowledge",
                             "resume /knowledge/upload"), calls);
        // Closing a timed out handle must not resume again
        handle.close();
        assertEquals(4, calls.size());
    }

    @Test
    void givenPauseFailsPartWay_whenPausing_thenResumesWhatWasPausedAndRethrows() {
        Consumer<String> pause = name -> {
            if (name.equals("/knowledge/upload")) {
                throw new IllegalStateException("boom");
            }
            calls.add("pause " + name);
        };
        DatasetIngestPause pauser = new DatasetIngestPause(() -> List.of("/knowledge/upload"), pause,
                                                           name -> calls.add("resume " + name), (n, t) -> true);

        assertThrows(IllegalStateException.class, () -> pauser.pause("/knowledge", Duration.ofSeconds(1)));
        assertEquals(List.of("pause /knowledge", "resume /knowledge"), calls);
    }

    @Test
    void givenResumeFails_whenClosing_thenOtherProjectorsStillResumeAndFailureIsRethrown() {
        Consumer<String> resume = name -> {
            calls.add("resume " + name);
            if (name.equals("/knowledge")) {
                throw new IllegalStateException("boom");
            }
        };
        DatasetIngestPause pauser = new DatasetIngestPause(() -> List.of("/knowledge/upload"), name -> { }, resume,
                                                           (n, t) -> true);
        DatasetIngestPause.Handle handle = pauser.pause("/knowledge", Duration.ofSeconds(1));

        assertThrows(IllegalStateException.class, handle::close);
        assertEquals(List.of("resume /knowledge", "resume /knowledge/upload"), calls);
    }

    @Test
    void givenRealRegistry_whenFindingConnectors_thenWorksWithNoConnectors() {
        assertEquals(List.of("/nothing"), new DatasetIngestPause().connectorsFor("/nothing"));
    }
}
