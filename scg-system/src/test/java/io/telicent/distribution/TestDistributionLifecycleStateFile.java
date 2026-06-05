package io.telicent.distribution;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDistributionLifecycleStateFile {

    private Path stateFile;
    private Path tmpFile;
    private Path bakFile;
    private DistributionLifecycleStateFile reader;

    @BeforeEach
    void setUp() throws IOException {
        this.stateFile = Files.createTempFile("scg-test-lifecycle-", ".json");
        this.tmpFile = Path.of(this.stateFile + ".tmp");
        this.bakFile = Path.of(this.stateFile + ".bak");
        this.reader = new DistributionLifecycleStateFile(this.stateFile, null);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(this.stateFile);
        Files.deleteIfExists(this.tmpFile);
        Files.deleteIfExists(this.bakFile);
    }

    @Test
    void activeGraphNodes_returnsActiveDistributions_whenStateFileIsValid() throws IOException {
        Files.writeString(this.stateFile, """
                {
                  "distributions" : {
                    "http://example/a" : "Active",
                    "http://example/b" : "Withdrawn"
                  }
                }
                """, StandardCharsets.UTF_8);

        Set<Node> active = this.reader.activeGraphNodes();

        assertEquals(Set.of(NodeFactory.createURI("http://example/a")), active);
    }

    @Test
    void activeGraphNodes_isEmpty_whenStateFileMissing() {
        // The temp file was created in setUp; remove it so the primary, .tmp and .bak are all absent.
        Path missingFile = this.stateFile.resolveSibling("does-not-exist.json");
        DistributionLifecycleStateFile missingReader = new DistributionLifecycleStateFile(missingFile, null);

        Set<Node> active = missingReader.activeGraphNodes();

        assertTrue(active.isEmpty(), "No state file present -> no active distributions");
    }

    @Test
    void activeGraphNodes_dropsCachedActiveSet_whenAllCandidatesBecomeUnparseable() throws IOException {
        // Prime cache with a valid state file.
        Files.writeString(this.stateFile, """
                {
                  "distributions" : {
                    "http://example/a" : "Active"
                  }
                }
                """, StandardCharsets.UTF_8);

        Set<Node> activeBefore = this.reader.activeGraphNodes();
        assertEquals(Set.of(NodeFactory.createURI("http://example/a")), activeBefore,
                     "Pre-condition: primed cache contains the Active distribution");

        // Corrupt candidates.
        Files.writeString(this.stateFile, "this is not valid json - corruption padding to change size",
                          StandardCharsets.UTF_8);
        Files.writeString(this.tmpFile, "also not valid json", StandardCharsets.UTF_8);
        Files.writeString(this.bakFile, "still not valid json", StandardCharsets.UTF_8);

        Set<Node> activeAfter = this.reader.activeGraphNodes();
        assertTrue(activeAfter.isEmpty(),
                   "All candidates unparseable -> active set must be dropped (fail closed); was " + activeAfter);
    }

    @Test
    void activeGraphNodes_fallsBackToBackup_whenPrimaryIsUnparseable() throws IOException {
        Files.writeString(this.stateFile, "garbage", StandardCharsets.UTF_8);
        Files.writeString(this.bakFile, """
                {
                  "distributions" : {
                    "http://example/from-bak" : "Active"
                  }
                }
                """, StandardCharsets.UTF_8);

        Set<Node> active = this.reader.activeGraphNodes();

        assertEquals(Set.of(NodeFactory.createURI("http://example/from-bak")), active,
                     ".bak should be used when primary fails to parse");
    }
}