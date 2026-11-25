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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.backup.services.DatasetBackupService;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.payloads.RdfPayload;
import io.telicent.smart.cache.projectors.Sink;
import io.telicent.smart.cache.distribution.lifecycle.config.DistributionLifecycleConfiguration;
import io.telicent.smart.cache.distribution.lifecycle.store.DistributionLifecycleStateStore;
import io.telicent.smart.cache.security.data.labels.DatasetGraphLabelled;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPlugin;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPluginLoader;
import io.telicent.smart.cache.sources.Event;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.atlas.io.IOX;
import org.apache.jena.fuseki.kafka.FKRegistry;
import org.apache.jena.fuseki.kafka.FKS;
import org.apache.jena.fuseki.kafka.FMod_FusekiKafka;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.server.Endpoint;
import org.apache.jena.kafka.KConnectorDesc;
import org.apache.jena.kafka.SysJenaKafka;
import org.apache.jena.kafka.common.FusekiOffsetStore;
import org.apache.jena.kafka.common.FusekiSink;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.kafka.common.utils.Bytes;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import static io.telicent.backup.services.DatasetBackupService.sanitiseName;
import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static io.telicent.otel.FMod_OpenTelemetry.fixupName;
import static io.telicent.smart.cache.distribution.lifecycle.config.DistributionLifecycleConfiguration.DISTRIBUTION_LIFECYCLE_STATE_FILE;
import static org.apache.jena.kafka.FusekiKafka.LOG;

/**
 * Extension of {@link FMod_FusekiKafka} that directly applies a Kafka message batch.
 */
public class FMod_FusekiKafkaSCG extends FMod_FusekiKafka {

    // JSON response key
    private static final String SUCCESS = "success";

    private final FMod_DistributionLifecycle distributionLifecycle;
    @SuppressWarnings("java:S3077")
    private volatile DistributionLifecycleStateStore ingestStateStore;
    private ExternalDistributionLifecycleStateStore externalStateStore;
    private boolean lifecycleGated;
    private volatile boolean stopped;

    public FMod_FusekiKafkaSCG() {
        this(new FMod_DistributionLifecycle());
    }

    /**
     * Creates the module
     *
     * @param distributionLifecycle The distribution lifecycle module registered with the same server.  When lifecycle
     *                              tracking is enabled Kafka ingest waits for this module's tracker to catch up.
     */
    public FMod_FusekiKafkaSCG(FMod_DistributionLifecycle distributionLifecycle) {
        this.distributionLifecycle = Objects.requireNonNull(distributionLifecycle);
    }

    @Override
    public void prepare(FusekiServer.Builder builder, Set<String> names, Model configModel) {
        super.prepare(builder, names, configModel);
        DatasetBackupService.registerMethods("kafka", this::backupKafka, this::restoreKafka);
    }

    @Override
    public void serverBeforeStarting(FusekiServer server) {
        this.lifecycleGated = isLifecycleGated();
        if (!this.lifecycleGated) {
            super.serverBeforeStarting(server);
        }
        // Otherwise connectors are started in serverAfterStarting(), once HTTP is up and the lifecycle tracker, started
        // by FMod_DistributionLifecycle's own serverBeforeStarting(), has caught up.
    }

    /**
     * Starts lifecycle-gated Kafka connectors.
     * <p>
     * Fuseki calls this on the same thread as {@link #serverBeforeStarting(FusekiServer)}, so the parent's per-thread
     * connector list is still available.  Jetty is already serving HTTP, so readiness reports progress while we wait.
     * Waiting blocks {@link FusekiServer#start()} until the tracker has caught up, but never holds a dataset
     * transaction.
     * </p>
     */
    @Override
    public void serverAfterStarting(FusekiServer server) {
        if (!this.lifecycleGated) {
            return;
        }
        try {
            if (!this.distributionLifecycle.startupInitiated()) {
                throw new IllegalStateException(
                        "Lifecycle gated Kafka ingest requires the FMod_DistributionLifecycle instance given to this "
                        + "module to be registered with the same server");
            }
            try {
                this.ingestStateStore = this.distributionLifecycle.awaitIngestStateStore();
            } catch (IllegalStateException e) {
                if (!this.stopped) {
                    // Leave HTTP running but the service unready, so the cause is visible rather than crash looping
                    this.distributionLifecycle.ingestStartupFailed(e);
                    LOG.error("Kafka ingest will not start because distribution lifecycle state is unavailable", e);
                }
                return;
            }
            if (!this.stopped) {
                startKafkaConnectors(server);
            }
        } catch (RuntimeException e) {
            // Misconfiguration, e.g. duplicate consumer groups, must still fail startup
            server.stop();
            throw e;
        }
    }

    @Override
    public void serverStopped(FusekiServer server) {
        this.stopped = true;
        try {
            super.serverStopped(server);
        } finally {
            closeExternalStateStore();
        }
    }

    private synchronized ExternalDistributionLifecycleStateStore externalStateStore(Path path, String application) {
        if (this.stopped) {
            throw new IllegalStateException("Kafka module is stopped");
        }
        if (this.externalStateStore == null) {
            this.externalStateStore = new ExternalDistributionLifecycleStateStore(path, application);
        }
        return this.externalStateStore;
    }

    private synchronized void closeExternalStateStore() {
        if (this.externalStateStore != null) {
            this.externalStateStore.close();
            this.externalStateStore = null;
        }
    }

    private static boolean isLifecycleGated() {
        return Configurator.get(FMod_DistributionLifecycle.ROUTE_TO_NAMED_GRAPHS, Boolean::parseBoolean, false)
               && DistributionLifecycleConfiguration.isEnabled();
    }

    @Override
    protected Function<DatasetGraph, Sink<Event<Bytes, RdfPayload>>> getSinkBuilder() {
        final boolean routeToNamedGraphs = Configurator.get(FMod_DistributionLifecycle.ROUTE_TO_NAMED_GRAPHS, Boolean::parseBoolean, false);
        final String lifecycleStateFileName = Configurator.get(DISTRIBUTION_LIFECYCLE_STATE_FILE);
        final DistributionLifecycleStateStore stateStore;
        if (!routeToNamedGraphs) {
            stateStore = null;
        } else if (DistributionLifecycleConfiguration.isEnabled()) {
            stateStore = Objects.requireNonNull(this.ingestStateStore,
                                               "Lifecycle tracker must be ready before building Kafka sinks");
        } else {
            stateStore = StringUtils.isNotBlank(lifecycleStateFileName)
                         ? externalStateStore(Path.of(lifecycleStateFileName),
                                 Configurator.get(FMod_DistributionLifecycle.DISTRIBUTION_LIFECYCLE_APP_ID)) : null;
        }
        return dsg -> {
            final DataSecurityPlugin dataSecurityPlugin = DataSecurityPluginLoader.load();
            final Optional<DatasetGraphLabelled> labelled = dataSecurityPlugin.prepareLabelledDataset(dsg);
            Sink<Event<Bytes, RdfPayload>> sink = labelled.isPresent()
                    ? new SmartCacheGraphSink(labelled.get(), routeToNamedGraphs)
                    : FusekiSink.builder().dataset(dsg).build();
            if (stateStore == null) {
                return sink;
            }
            var acceptance = DistributionLifecycleConfiguration.<Bytes, RdfPayload>acceptanceSinkBuilder(stateStore);
            // SC-Graph retains its existing policy of ingesting events for distributions it has no state for, unless
            // strict registration is opted into.  Effectively the state store contract now treats "unknown" and
            // "Unregistered" as the same state. Previously the sink rejected explicitly Unregistered distributions.
            if (!Boolean.parseBoolean(Configurator.get(DistributionLifecycleConfiguration.REJECT_UNREGISTERED))) {
                acceptance.acceptUnregistered();
            }
            return acceptance.destination(sink).build();
        };
    }

    @Override
    protected String logMessage() {
        return String.format("Fuseki-Kafka Connector Module SCG (%s)", SysJenaKafka.VERSION);
    }

    public void backupKafka(DataAccessPoint dataAccessPoint, String path, ObjectNode resultNode) {
        String dataset = dataAccessPoint.getName();
        List<KConnectorDesc> kafkaConnectionList = obtainKafkaConnection(dataset, dataAccessPoint.getDataService());
        ArrayNode nodeList = OBJECT_MAPPER.createArrayNode();
        for (KConnectorDesc conn : kafkaConnectionList) {
            nodeList.add(backupKafkaConnection(conn, path));
        }
        resultNode.set(dataset, nodeList);
    }

    public void restoreKafka(DataAccessPoint dataAccessPoint, String path, ObjectNode resultNode) {
        String dataset = dataAccessPoint.getName();
        List<KConnectorDesc> kafkaConnectionList = obtainKafkaConnection(dataset, dataAccessPoint.getDataService());
        ArrayNode nodeList = OBJECT_MAPPER.createArrayNode();
        for (KConnectorDesc conn : kafkaConnectionList) {
            nodeList.add(restoreKafkaConnection(conn, dataset, path));
        }
        resultNode.set(dataset, nodeList);
    }

    /**
     * Finds the Kafka connectors writing to a dataset, either directly or via one of its endpoints
     * <p>
     * Connectors are looked up in the {@link FKRegistry}, where Fuseki Kafka registers every connector it starts.
     * </p>
     *
     * @param dataset     Dataset name, e.g. {@code /knowledge}
     * @param dataService Dataset's data service
     * @return Connectors, in the order: the dataset itself, then its endpoints
     */
    private List<KConnectorDesc> obtainKafkaConnection(String dataset, DataService dataService) {
        List<String> destinations = new ArrayList<>();
        destinations.add(DataAccessPoint.canonical(dataset));
        for (Endpoint endpoint : dataService.getEndpoints()) {
            destinations.add(DataAccessPoint.canonical(dataset + "/" + endpoint.getName()));
        }

        Collection<KConnectorDesc> registered = FKRegistry.get().getConnectors();
        List<KConnectorDesc> kafkaConnections = new ArrayList<>();
        for (String destination : destinations) {
            for (KConnectorDesc conn : registered) {
                if (destination.equals(DataAccessPoint.canonical(conn.getDatasetName())) && !kafkaConnections.contains(conn)) {
                    kafkaConnections.add(conn);
                }
            }
        }
        return kafkaConnections;
    }

    private ObjectNode backupKafkaConnection(KConnectorDesc conn, String path) {
        ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
        String sanitizedDataset = fixupName(sanitiseName(conn.getDatasetName()));
        resultNode.put("name", sanitizedDataset);
        String filename = path + "/" + sanitizedDataset + ".json";
        IOX.copy(conn.getStateFile(), filename);
        resultNode.put("source", conn.getStateFile());
        resultNode.put("destination", filename);
        resultNode.put(SUCCESS, true);
        return resultNode;
    }

    private ObjectNode restoreKafkaConnection(KConnectorDesc conn, String dataset, String path) {
        ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
        String sanitizedDataset = fixupName(sanitiseName(conn.getDatasetName()));
        resultNode.put("name", sanitizedDataset);
        String filename = path + "/" + sanitizedDataset + ".json";
        File offsetStoreFile = new File(filename);
        if (offsetStoreFile.length() == 0) {
            String errorMessage = String.format(
                    "Unable to restore Kafka for dataset (%s) as restore file (%s) not suitable.",
                    dataset, filename);
            LOG.warn("Unable to restore Kafka for dataset {} as restore file {} not suitable.", dataset, filename);
            resultNode.put(SUCCESS, false);
            resultNode.put("reason", errorMessage);
            return resultNode;
        }
        FusekiOffsetStore offsetStore = FusekiOffsetStore.builder()
                                                         .datasetName(dataset)
                                                         .consumerGroup(conn.getConsumerGroupId())
                                                         .stateFile(offsetStoreFile)
                                                         .build();
        FKS.restoreOffsetForDataset(conn.getDatasetName(), offsetStore);
        // Convert offsets for result appropriately, there could be many offsets depending on the topic(s) and
        // partitions(s)
        ObjectNode offsets = OBJECT_MAPPER.createObjectNode();
        for (Map.Entry<String, Object> offset : offsetStore.offsets()) {
            if (offset.getValue() instanceof Long longOffset) {
                offsets.put(offset.getKey(), longOffset);
            }
        }
        resultNode.set("offsets", offsets);
        resultNode.put(SUCCESS, true);
        return resultNode;
    }
}
