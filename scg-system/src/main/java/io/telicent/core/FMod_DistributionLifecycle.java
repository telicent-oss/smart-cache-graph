/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.telicent.core;

import io.telicent.distribution.DistributionLifecycleFilters;
import io.telicent.distribution.DistributionLifecycleStateFile;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.distribution.lifecycle.events.listeners.AcknowledgingListener;
import io.telicent.smart.cache.distribution.lifecycle.events.listeners.DistributionLifecycleListener;
import io.telicent.smart.cache.distribution.lifecycle.store.DistributionLifecycleStateStore;
import io.telicent.smart.cache.distribution.lifecycle.store.apps.AppDistributionLifecycleStoreFile;
import io.telicent.smart.cache.distribution.lifecycle.tracker.DistributionLifecycleTracker;
import io.telicent.smart.cache.payloads.LazyEnvelope;
import io.telicent.smart.cache.projectors.Sink;
import io.telicent.smart.cache.sources.Event;
import io.telicent.smart.cache.sources.EventSource;
import io.telicent.smart.cache.sources.kafka.KafkaEventSource;
import io.telicent.smart.cache.sources.kafka.policies.KafkaReadPolicies;
import io.telicent.smart.cache.sources.kafka.serializers.LazyEnvelopeDeserializer;
import io.telicent.smart.cache.sources.kafka.serializers.LazyEnvelopeSerializer;
import io.telicent.smart.cache.sources.kafka.sinks.KafkaSink;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.fuseki.kafka.FKRegistry;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.kafka.KConnectorDesc;
import org.apache.jena.rdf.model.Model;
import org.apache.kafka.common.serialization.UUIDDeserializer;
import org.apache.kafka.common.serialization.UUIDSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A Fuseki module that makes Smart Cache Graph "distribution lifecycle aware".
 * It can be thought of having 2 parts:
 *   1. A distribution lifecycle aware dataset filter on each ABAC dataset
 *   2. A distribution lifecycle tracker that responds to delete messages and acts accordingly.
 */
public class FMod_DistributionLifecycle implements FusekiModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(FMod_DistributionLifecycle.class);

    public static final String ROUTE_TO_NAMED_GRAPHS = "ROUTE_TO_NAMED_GRAPHS";

    public static final String ENABLE_DISTRIBUTION_LIFECYCLE = "ENABLE_DISTRIBUTION_LIFECYCLE";

    public static final String DISTRIBUTION_LIFECYCLE_STATE_FILE = "DISTRIBUTION_LIFECYCLE_STATE_FILE";
    public static final String DISTRIBUTION_LIFECYCLE_APP_ID = "DISTRIBUTION_LIFECYCLE_APP_ID";
    public static final String DISTRIBUTION_LIFECYCLE_TOPIC = "DISTRIBUTION_LIFECYCLE_TOPIC";
    public static final String DISTRIBUTION_LIFECYCLE_DLQ_TOPIC = "DISTRIBUTION_LIFECYCLE_DLQ_TOPIC";
    public static final String DISTRIBUTION_LIFECYCLE_CONSUMER_GROUP = "DISTRIBUTION_LIFECYCLE_CONSUMER_GROUP";
    public static final String DISTRIBUTION_LIFECYCLE_BOOTSTRAP_SERVERS = "DISTRIBUTION_LIFECYCLE_BOOTSTRAP_SERVERS";

    static final String DEFAULT_TOPIC = "distribution-lifecycle";
    static final String DEFAULT_DLQ_TOPIC = "distribution-lifecycle.dlq";
    static final String DEFAULT_CONSUMER_GROUP = "smart-cache-graph-distribution-lifecycle";
    static final String DEFAULT_STATE_FILE = "distribution-lifecycle.state";
    static final String DEFAULT_APP_ID = "smart-cache-graph";
    private static final long TRACKER_STARTUP_RETRY_DELAY_MS = 1_000L;
    private static final AtomicInteger TRACKER_STARTER_THREAD_ID = new AtomicInteger();

    private final DistributionLifecycleReadiness readiness = DistributionLifecycleReadiness.getInstance();
    private DistributionLifecycleTracker tracker;
    private DistributionLifecycleStateStore stateStore;
    private ExecutorService trackerStarter;

    @Override
    public String name() {
        return "Distribution Lifecycle";
    }

    @Override
    public void configured(FusekiServer.Builder serverBuilder, DataAccessPointRegistry dapRegistry, Model configModel) {
        if (StringUtils.isBlank(Configurator.get(DISTRIBUTION_LIFECYCLE_STATE_FILE))) {
            return;
        }
        if (!routeToNamedGraphsEnabled()) {
            LOGGER.warn("Distribution lifecycle filtering ignored because {} is disabled", ROUTE_TO_NAMED_GRAPHS);
            return;
        }
        if (dapRegistry == null) {
            return;
        }
        // NB - Deliberately the RAW configured value, not applicationId(): the read side only verifies the state
        //      file's application when an id has been explicitly configured, otherwise it is lenient and accepts any
        //      state file.  The tracker (write side) uses applicationId() as it must tag the file with a concrete id,
        //      and its default keeps the two sides consistent when an id is explicitly configured or left unset.
        String application = Configurator.get(DISTRIBUTION_LIFECYCLE_APP_ID);
        String stateFile = stateFilePath();
        for (DataAccessPoint dap : dapRegistry.accessPoints()) {
            if (dap.getDataService().getDataset() instanceof DatasetGraphABAC abacDataset) {
                DistributionLifecycleFilters.installIfConfigured(abacDataset, application, stateFile);
            }
        }
    }

    @Override
    public void serverBeforeStarting(FusekiServer server) {
        configureReadiness();

        if (!isEnabled()) {
            LOGGER.debug("Distribution lifecycle tracker disabled (ENABLE_DISTRIBUTION_LIFECYCLE=false)");
            return;
        }
        if (!routeToNamedGraphsEnabled()) {
            LOGGER.warn("Distribution lifecycle tracker ignored because ROUTE_TO_NAMED_GRAPHS is disabled");
            return;
        }
        if (this.tracker != null || this.trackerStarter != null) {
            LOGGER.warn("Distribution lifecycle tracker already started");
            return;
        }

        String bootstrapServers =
                selectBootstrapServers(connectors(), Configurator.get(DISTRIBUTION_LIFECYCLE_BOOTSTRAP_SERVERS));
        if (StringUtils.isBlank(bootstrapServers)) {
            LOGGER.warn(
                    "Distribution lifecycle tracker is enabled but no Kafka bootstrap servers could be determined.");
            this.readiness.markFailed(
                    "Distribution lifecycle tracker is enabled but no Kafka bootstrap servers could be determined.");
            return;
        }
        Properties kafkaProperties = connectionPropertiesFor(bootstrapServers);

        String application = applicationId();
        String topic = configOrDefault(DISTRIBUTION_LIFECYCLE_TOPIC, DEFAULT_TOPIC);
        String dlqTopic = configOrDefault(DISTRIBUTION_LIFECYCLE_DLQ_TOPIC, DEFAULT_DLQ_TOPIC);
        String consumerGroup = consumerGroup();
        String stateFile = stateFilePath();

        this.readiness.markStarting("Distribution lifecycle tracker is starting or catching up.");
        ExecutorService starter = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "distribution-lifecycle-startup-"
                    + TRACKER_STARTER_THREAD_ID.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        this.trackerStarter = starter;
        starter.submit(() -> startupTracker(starter, server, bootstrapServers, kafkaProperties, application, topic,
                                            dlqTopic, consumerGroup, stateFile));
    }

    @Override
    public void serverStopped(FusekiServer server) {
        closeTrackerStarter();
        closeTracker();
        this.readiness.reset();
    }

    /**
     * Stops the tracker (by closing the Kafka event source and sinks)
     * Closes the state store so its state is flushed.
     */
    private void closeTracker() {
        if (this.tracker != null) {
            try {
                this.tracker.close();
            } finally {
                this.tracker = null;
            }
        }
        if (this.stateStore != null) {
            try {
                this.stateStore.close();
            } catch (Exception e) {
                LOGGER.warn("Error closing distribution lifecycle state store", e);
            } finally {
                this.stateStore = null;
            }
        }
    }

    private void closeTrackerStarter() {
        if (this.trackerStarter != null) {
            this.trackerStarter.shutdownNow();
            try {
                this.trackerStarter.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                this.trackerStarter = null;
            }
        }
    }

    /**
     * Indicates whether the lifecycle tracker is currently running.
     * @return True if the tracker has been started, false otherwise
     */
    boolean isRunning() {
        return this.tracker != null && this.tracker.isRunning();
    }

    private void startupTracker(ExecutorService starter, FusekiServer server, String bootstrapServers,
                                Properties kafkaProperties,
                                String application, String topic, String dlqTopic, String consumerGroup,
                                String stateFile) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    createTracker(server, bootstrapServers, kafkaProperties, application, topic, dlqTopic,
                                  consumerGroup, stateFile);
                    this.stateStore.flush();
                    this.readiness.markReady();
                    LOGGER.info(
                            "Distribution lifecycle tracker enabled: consuming topic '{}' from {} (consumer group '{}', application '{}')",
                            topic, bootstrapServers, consumerGroup, application);
                    return;
                } catch (RuntimeException e) {
                    closeTracker();
                    if (isCatchUpFailure(e)) {
                        this.readiness.markStarting(
                                "Distribution lifecycle tracker is still catching up; lifecycle state is not yet usable.");
                        LOGGER.warn("Distribution lifecycle tracker is still catching up; retrying startup", e);
                        Thread.sleep(TRACKER_STARTUP_RETRY_DELAY_MS);
                    } else {
                        this.readiness.markFailed("Distribution lifecycle tracker is unavailable: " + rootMessage(e));
                        LOGGER.error("Failed to start distribution lifecycle tracker; lifecycle events will NOT be processed", e);
                        return;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            starter.shutdown();
            if (this.trackerStarter == starter) {
                this.trackerStarter = null;
            }
        }
    }

    private void createTracker(FusekiServer server, String bootstrapServers, Properties kafkaProperties,
                               String application, String topic, String dlqTopic, String consumerGroup,
                               String stateFile) {
        this.stateStore = AppDistributionLifecycleStoreFile.builder()
                                                           .app(application)
                                                           .stateFile(new File(stateFile))
                                                           .build();

        DistributionLifecycleListener graphDeletion =
                new DistributionGraphDeletionListener(() -> abacDatasets(server));
        DistributionLifecycleListener listener =
                AcknowledgingListener.builder()
                                     .application(application)
                                     .version(SmartCacheGraph.VERSION)
                                     .listener(graphDeletion)
                                     .sink(lifecycleSink(bootstrapServers, topic, kafkaProperties))
                                     .stateStore(this.stateStore)
                                     .build();

        EventSource<UUID, LazyEnvelope> source = KafkaEventSource.<UUID, LazyEnvelope>create()
                                                                 .bootstrapServers(bootstrapServers)
                                                                 .consumerConfig(kafkaProperties)
                                                                 .topic(topic)
                                                                 .consumerGroup(consumerGroup)
                                                                 .readPolicy(KafkaReadPolicies.fromEarliest())
                                                                 .commitOnProcessed()
                                                                 .keyDeserializer(UUIDDeserializer.class)
                                                                 .valueDeserializer(LazyEnvelopeDeserializer.class)
                                                                 .build();

        Sink<Event<UUID, LazyEnvelope>> dlq =
                StringUtils.isBlank(dlqTopic) ? null : lifecycleSink(bootstrapServers, dlqTopic, kafkaProperties);

        this.tracker = DistributionLifecycleTracker.builder()
                                                   .application(application)
                                                   .eventSource(source)
                                                   .dlq(dlq)
                                                   .listenerThreads(1)
                                                   .listeners(List.of(listener))
                                                   .stateStore(this.stateStore)
                                                   .build();
    }

    private void configureReadiness() {
        String configuredStateFile = Configurator.get(DISTRIBUTION_LIFECYCLE_STATE_FILE);
        boolean filteringEnabled = StringUtils.isNotBlank(configuredStateFile) && routeToNamedGraphsEnabled();
        boolean trackerEnabled = filteringEnabled && isEnabled();
        DistributionLifecycleStateFile stateFile = filteringEnabled
                                                   ? new DistributionLifecycleStateFile(Path.of(stateFilePath()),
                                                                                        Configurator.get(
                                                                                                DISTRIBUTION_LIFECYCLE_APP_ID))
                                                   : null;
        this.readiness.configure(filteringEnabled, trackerEnabled, stateFile,
                                 () -> this.tracker != null && this.tracker.isRunning());
    }

    private static boolean isCatchUpFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains("cannot make up to date decisions about distribution lifecycle")) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return StringUtils.defaultIfBlank(current.getMessage(), error.getClass().getSimpleName());
    }

    /**
     * Returns the current set of ABAC datasets registered with the server.
     * @param server Fuseki server
     * @return ABAC datasets
     */
    static Collection<DatasetGraphABAC> abacDatasets(FusekiServer server) {
        List<DatasetGraphABAC> datasets = new ArrayList<>();
        DataAccessPointRegistry registry = server.getDataAccessPointRegistry();
        if (registry == null) {
            return datasets;
        }
        for (DataAccessPoint dataAccessPoint : registry.accessPoints()) {
            if (dataAccessPoint.getDataService().getDataset() instanceof DatasetGraphABAC abac) {
                datasets.add(abac);
            }
        }
        return datasets;
    }

    /**
     * Determine Kafka bootstrap servers for the lifecycle data.
     *
     * @param connectors Previously configured Kafka connectors
     * @param explicit   Explicit configuration - to use if set.
     * @return Bootstrap server(s) to use.
     *
     * Note: We only work with 1 connector (i.e. 1 cluster).
     */
    static String selectBootstrapServers(Collection<KConnectorDesc> connectors, String explicit) {
        if (StringUtils.isNotBlank(explicit)) {
            return explicit;
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (KConnectorDesc connector : connectors) {
            if (StringUtils.isNotBlank(connector.getBootstrapServers())) {
                distinct.add(connector.getBootstrapServers());
            }
        }
        if (distinct.isEmpty()) {
            return null;
        }
        if (distinct.size() > 1) {
            LOGGER.error(
                    "Configured Fuseki-Kafka connectors target multiple Kafka clusters {}; set {} to indicate which cluster hosts the distribution lifecycle topic, or have the connectors share a single fk:Cluster definition",
                    distinct, DISTRIBUTION_LIFECYCLE_BOOTSTRAP_SERVERS);
            return null;
        }
        return distinct.iterator().next();
    }

    /**
     * Extracts just the Kafka connection properties from a connector's configuration.
     *
     * @param connector Kafka connector descriptor
     * @return Connection/security properties
     */
    static Properties connectionProperties(KConnectorDesc connector) {
        Properties source = connector.getKafkaConsumerProps();
        Properties connection = new Properties();
        if (source != null) {
            for (String key : source.stringPropertyNames()) {
                if (key.startsWith("security.") || key.startsWith("sasl.") || key.startsWith("ssl.")) {
                    connection.setProperty(key, source.getProperty(key));
                }
            }
        }
        return connection;
    }

    private Properties connectionPropertiesFor(String bootstrapServers) {
        for (KConnectorDesc connector : connectors()) {
            if (bootstrapServers.equals(connector.getBootstrapServers())) {
                return connectionProperties(connector);
            }
        }
        return new Properties();
    }

    private static Collection<KConnectorDesc> connectors() {
        Collection<KConnectorDesc> connectors = FKRegistry.get().getConnectors();
        return connectors == null ? List.of() : connectors;
    }

    private KafkaSink<UUID, LazyEnvelope> lifecycleSink(String bootstrapServers, String topic, Properties props) {
        return KafkaSink.<UUID, LazyEnvelope>create()
                        .bootstrapServers(bootstrapServers)
                        .topic(topic)
                        .producerConfig(props)
                        .keySerializer(UUIDSerializer.class)
                        .valueSerializer(LazyEnvelopeSerializer.class)
                        .async()
                        .lingerMs(50)
                        .build();
    }

    public static String applicationId() {
        return configOrDefault(DISTRIBUTION_LIFECYCLE_APP_ID, DEFAULT_APP_ID);
    }

    public static String stateFilePath() {
        return configOrDefault(DISTRIBUTION_LIFECYCLE_STATE_FILE, DEFAULT_STATE_FILE);
    }

    public static String consumerGroup() {
        return StringUtils.defaultIfBlank(Configurator.get(DISTRIBUTION_LIFECYCLE_CONSUMER_GROUP),
                                          DEFAULT_CONSUMER_GROUP + "-" + applicationId());
    }

    private static boolean isEnabled() {
        return Configurator.get(ENABLE_DISTRIBUTION_LIFECYCLE, Boolean::parseBoolean, false);
    }

    private static boolean routeToNamedGraphsEnabled() {
        return Configurator.get(ROUTE_TO_NAMED_GRAPHS, Boolean::parseBoolean, false);
    }

    private static String configOrDefault(String key, String defaultValue) {
        return StringUtils.defaultIfBlank(Configurator.get(key), defaultValue);
    }
}
