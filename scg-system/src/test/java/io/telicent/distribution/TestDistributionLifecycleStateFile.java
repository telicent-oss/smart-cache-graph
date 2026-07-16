package io.telicent.distribution;

import io.telicent.smart.cache.security.data.distribution.DistributionLifecycleStateFile;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void distributionState_returnsConfiguredState_whenDistributionIsKnown() throws IOException {
        Files.writeString(this.stateFile, """
                {
                  "distributions" : {
                    "http://example/a" : "Deleted"
                  }
                }
                """, StandardCharsets.UTF_8);

        assertEquals("Deleted", this.reader.distributionState("http://example/a"));
    }

    @Test
    void distributionStateResult_marksStateAvailable_whenStateFileIsReadable() throws IOException {
        Files.writeString(this.stateFile, """
                {
                  "distributions" : {
                    "http://example/a" : "Active"
                  }
                }
                """, StandardCharsets.UTF_8);

        DistributionLifecycleStateFile.DistributionStateResult result =
                this.reader.distributionStateResult("http://example/a");

        assertTrue(result.available(), "Readable state file should be reported as available");
        assertEquals("Active", result.state());
    }

    @Test
    void distributionState_returnsNull_whenDistributionIsUnknown() throws IOException {
        Files.writeString(this.stateFile, """
                {
                  "distributions" : {
                    "http://example/a" : "Deleted"
                  }
                }
                """, StandardCharsets.UTF_8);

        assertNull(this.reader.distributionState("http://example/unknown"),
                   "Unregistered/unknown distributions have no recorded state");
    }

    @Test
    void distributionState_reflectsFileUpdate_whenStateChanges() throws IOException {
        Files.writeString(this.stateFile, """
                {
                  "distributions" : {
                    "http://example/a" : "Deleted"
                  }
                }
                """, StandardCharsets.UTF_8);
        assertEquals("Deleted", this.reader.distributionState("http://example/a"),
                     "Pre-condition: primed cache reports the distribution as Deleted");

        // Rewrite the file with a different state; the change in size/last-modified must invalidate the cached value.
        Files.writeString(this.stateFile, """
                {
                  "distributions" : {
                    "http://example/a" : "Active"
                  }
                }
                """, StandardCharsets.UTF_8);

        assertEquals("Active", this.reader.distributionState("http://example/a"),
                     "Updated state file must be picked up on the next read");
    }

    @Test
    void distributionState_reflectsSameSizeSameTimestampUpdate_whenContentChanges() throws IOException {
        String deletedState = """
                {
                  "distributions" : {
                    "http://example/a" : "Deleted"
                  }
                }
                """;
        String activeState = """
                {
                  "distributions" : {
                    "http://example/a" : "Active "
                  }
                }
                """;
        assertEquals(deletedState.length(), activeState.length(),
                     "Test fixture must preserve the JSON byte size");

        Files.writeString(this.stateFile, deletedState, StandardCharsets.UTF_8);
        assertEquals("Deleted", this.reader.distributionState("http://example/a"));

        FileTime originalTimestamp = Files.getLastModifiedTime(this.stateFile);
        Files.writeString(this.stateFile, activeState, StandardCharsets.UTF_8);
        assertEquals(Files.size(this.stateFile), deletedState.getBytes(StandardCharsets.UTF_8).length);
        Files.setLastModifiedTime(this.stateFile, originalTimestamp);

        assertEquals("Active", this.reader.distributionState("http://example/a").trim(),
                     "Same-size/same-timestamp content changes must still be reloaded");
    }

    @Test
    void activeGraphNodes_isEmpty_whenStateFileMissing() {
        // The temp file was created in setUp; remove it so the primary, .tmp and .bak are all absent.
        Path missingFile = this.stateFile.resolveSibling("does-not-exist.json");
        DistributionLifecycleStateFile missingReader = new DistributionLifecycleStateFile(missingFile, null);

        Set<Node> active = missingReader.activeGraphNodes();
        DistributionLifecycleStateFile.DistributionStateResult result =
                missingReader.distributionStateResult("http://example/a");

        assertTrue(active.isEmpty(), "No state file present -> no active distributions");
        assertFalse(result.available(), "Missing state file should be reported as unavailable");
        assertNull(result.state(), "Missing state file should yield no distribution state");
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
        assertFalse(this.reader.distributionStateResult("http://example/a").available(),
                    "Unparseable candidates should be reported as unavailable");
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
