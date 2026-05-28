package io.telicent.distribution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.telicent.smart.cache.distribution.lifecycle.DistributionLifecycleState;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Reads the active distribution set from the lifecycle state file. */
public class DistributionLifecycleStateFile {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributionLifecycleStateFile.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TMP_EXTENSION = ".tmp";
    private static final String BAK_EXTENSION = ".bak";
    private static final Cache EMPTY_CACHE = new Cache(null, null, Set.of());

    private final Path stateFile;
    private final String applicationId;
    private volatile Cache cache = EMPTY_CACHE;

    DistributionLifecycleStateFile(Path stateFile, String applicationId) {
        this.stateFile = Objects.requireNonNull(stateFile, "stateFile cannot be null");
        this.applicationId = StringUtils.trimToNull(applicationId);
    }

    Set<Node> activeGraphNodes() {
        refresh();
        return this.cache.activeGraphs();
    }

    private synchronized void refresh() {
        List<Path> candidates = candidateFiles();
        if (candidates.isEmpty()) {
            this.cache = EMPTY_CACHE;
            return;
        }

        for (Path candidate : candidates) {
            try {
                byte[] content = Files.readAllBytes(candidate);
                String fingerprint = fingerprint(content);
                Cache current = this.cache;
                if (Objects.equals(current.source(), candidate) && Objects.equals(current.fingerprint(), fingerprint)) {
                    return;
                }

                Set<Node> activeGraphs = loadActiveGraphs(candidate, content);
                this.cache = new Cache(candidate, fingerprint, activeGraphs);
                return;
            } catch (IOException | IllegalArgumentException e) {
                LOGGER.warn("Failed to load distribution lifecycle state from {}", candidate, e);
            }
        }
    }

    private List<Path> candidateFiles() {
        List<Path> candidates = new ArrayList<>(3);
        addCandidate(candidates, this.stateFile);
        addCandidate(candidates, Path.of(this.stateFile.toString() + TMP_EXTENSION));
        addCandidate(candidates, Path.of(this.stateFile.toString() + BAK_EXTENSION));
        return candidates;
    }

    private static void addCandidate(List<Path> candidates, Path path) {
        if (Files.isRegularFile(path)) {
            candidates.add(path);
        }
    }

    private Set<Node> loadActiveGraphs(Path candidate, byte[] content) throws IOException {
        try (InputStream input = new java.io.ByteArrayInputStream(content)) {
            JsonNode root = MAPPER.readTree(input);
            verifyApplication(root, candidate);

            JsonNode distributions = root.path("distributions");
            if (!distributions.isObject()) {
                return Set.of();
            }

            Set<Node> activeGraphs = new LinkedHashSet<>();

            distributions.properties().forEach(entry ->  {
                if (!DistributionLifecycleState.Active.name().equals(entry.getValue().asText())) {
                    return;
                }
                activeGraphs.add(NodeFactory.createURI(entry.getKey()));
            });
            return Collections.unmodifiableSet(activeGraphs);
        }
    }

    private void verifyApplication(JsonNode root, Path candidate) {
        if (this.applicationId == null) {
            return;
        }

        String fileApplication = root.path("application").asText(null);
        if (!Objects.equals(this.applicationId, fileApplication)) {
            throw new IllegalArgumentException(
                    "Lifecycle state file " + candidate + " is for application " + fileApplication
                    + " not expected application " + this.applicationId);
        }
    }

    private static String fingerprint(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }

    private record Cache(Path source, String fingerprint, Set<Node> activeGraphs) {
    }
}
