package io.telicent.core;

import org.apache.jena.fuseki.kafka.FKRegistry;
import org.apache.jena.fuseki.kafka.FKS;
import org.apache.jena.kafka.KConnectorDesc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Pauses, and later resumes, the Kafka projectors that write into a dataset, e.g. so a maintenance operation can run
 * while no ingest is in progress.
 * <p>
 * Projectors are registered with Fuseki Kafka under their connector's {@code fk:fusekiServiceName}, which may be the
 * dataset itself (e.g. {@code /knowledge}) or one of its endpoints (e.g. {@code /knowledge/upload}). Pausing only the
 * dataset name silently does nothing for the latter (and still reports success), so the registered connectors are
 * matched against the dataset name, and those are paused along with the dataset name itself.
 * </p>
 */
public final class DatasetIngestPause {

    private static final Logger LOG = LoggerFactory.getLogger(DatasetIngestPause.class);

    /**
     * Outcome of trying to pause ingest for a dataset
     */
    public enum Outcome {
        /**
         * All projectors feeding the dataset reached a safe pause point; {@link Handle#close()} must be called to
         * resume them
         */
        PAUSED,
        /**
         * The projectors did not reach a safe pause point in time; they have already been resumed
         */
        TIMED_OUT
    }

    /**
     * The result of a pause, closing it resumes any projectors that were paused (at most once)
     */
    public static final class Handle implements AutoCloseable {
        private final Outcome outcome;
        private final List<String> connectorNames;
        private final Consumer<String> resume;
        private boolean resumed;

        private Handle(Outcome outcome, List<String> connectorNames, Consumer<String> resume) {
            this.outcome = outcome;
            this.connectorNames = List.copyOf(connectorNames);
            this.resume = resume;
            this.resumed = outcome != Outcome.PAUSED;
        }

        public Outcome outcome() {
            return outcome;
        }

        /**
         * @return Whether it is safe to proceed, i.e. any ingest into the dataset is paused
         */
        public boolean isIngestStopped() {
            return outcome != Outcome.TIMED_OUT;
        }

        /**
         * @return Fuseki Kafka connector names (projector registry keys) that this handle paused or tried to pause
         */
        public List<String> connectorNames() {
            return connectorNames;
        }

        @Override
        public synchronized void close() {
            if (resumed) {
                return;
            }
            resumed = true;
            resumeAll(connectorNames, resume);
        }
    }

    private final Supplier<Collection<String>> connectorNames;
    private final Consumer<String> pause;
    private final Consumer<String> resume;
    private final BiPredicate<String, Duration> waitForPause;

    /**
     * Creates an instance backed by the Fuseki Kafka registry
     */
    public DatasetIngestPause() {
        this(() -> FKRegistry.get().getConnectors().stream().map(KConnectorDesc::getDatasetName).toList(),
             FKS::pauseProjectors, FKS::resumeProjectors, FKS::waitForPause);
    }

    DatasetIngestPause(Supplier<Collection<String>> connectorNames, Consumer<String> pause, Consumer<String> resume,
                       BiPredicate<String, Duration> waitForPause) {
        this.connectorNames = Objects.requireNonNull(connectorNames);
        this.pause = Objects.requireNonNull(pause);
        this.resume = Objects.requireNonNull(resume);
        this.waitForPause = Objects.requireNonNull(waitForPause);
    }

    /**
     * Finds the Fuseki Kafka projector names that may write into the given dataset: the dataset name itself plus any
     * registered connector whose service name is the dataset or one of its endpoints
     *
     * @param datasetName Dataset name, e.g. {@code /knowledge}
     * @return Names to pause, e.g. {@code /knowledge} and {@code /knowledge/upload}
     */
    public List<String> connectorsFor(String datasetName) {
        String dataset = normalise(datasetName);
        List<String> matches = new ArrayList<>();
        matches.add(datasetName);
        for (String name : connectorNames.get()) {
            if (name == null) {
                continue;
            }
            String connector = normalise(name);
            if ((connector.equals(dataset) || connector.startsWith(dataset + "/")) && !matches.contains(name)) {
                matches.add(name);
            }
        }
        return matches;
    }

    /**
     * Pauses all Kafka projectors writing into the dataset and waits for them to reach a safe pause point (which
     * commits any batch in progress)
     *
     * @param datasetName Dataset name
     * @param timeout     How long to wait for the projectors to reach a pause point
     * @return Handle that must be closed to resume ingest; if the outcome is {@link Outcome#TIMED_OUT} the projectors
     * have already been resumed
     */
    public Handle pause(String datasetName, Duration timeout) {
        List<String> connectors = connectorsFor(datasetName);
        List<String> paused = new ArrayList<>();
        try {
            for (String connector : connectors) {
                LOG.info("Pausing Kafka ingest {} for dataset {}", connector, datasetName);
                pause.accept(connector);
                paused.add(connector);
            }
            long deadline = System.nanoTime() + timeout.toNanos();
            for (String connector : connectors) {
                Duration remaining = Duration.ofNanos(Math.max(0, deadline - System.nanoTime()));
                if (!waitForPause.test(connector, remaining)) {
                    LOG.warn("Kafka ingest {} for dataset {} did not reach a safe pause point within {}; resuming",
                             connector, datasetName, timeout);
                    resumeAll(paused, resume);
                    return new Handle(Outcome.TIMED_OUT, connectors, resume);
                }
            }
        } catch (RuntimeException e) {
            resumeAll(paused, resume);
            throw e;
        }
        LOG.info("Kafka ingest for dataset {} is paused ({})", datasetName, String.join(", ", connectors));
        return new Handle(Outcome.PAUSED, connectors, resume);
    }

    private static void resumeAll(List<String> connectors, Consumer<String> resume) {
        RuntimeException first = null;
        for (String connector : connectors) {
            try {
                LOG.info("Resuming Kafka ingest {}", connector);
                resume.accept(connector);
            } catch (RuntimeException e) {
                LOG.error("Failed to resume Kafka ingest {}", connector, e);
                if (first == null) {
                    first = e;
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }

    private static String normalise(String name) {
        String n = name.strip();
        while (n.endsWith("/") && n.length() > 1) {
            n = n.substring(0, n.length() - 1);
        }
        return n.startsWith("/") ? n : "/" + n;
    }
}
