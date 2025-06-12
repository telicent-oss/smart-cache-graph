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

import java.io.File;
import java.util.*;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.backup.services.DatasetBackupService;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.smart.cache.payloads.RdfPayload;
import io.telicent.smart.cache.projectors.Sink;
import io.telicent.smart.cache.sources.Event;
import io.telicent.smart.cache.sources.kafka.KafkaEventSource;
import org.apache.jena.atlas.io.IOX;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.kafka.FKS;
import org.apache.jena.fuseki.kafka.FMod_FusekiKafka;
import org.apache.jena.fuseki.kafka.FusekiKafkaException;
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

import static io.telicent.backup.services.DatasetBackupService.sanitiseName;
import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static org.apache.jena.kafka.FusekiKafka.LOG;

/**
 * Extension of {@link FMod_FusekiKafka} that directly applies a Kafka message batch.
 */
public class FMod_FusekiKafkaSCG extends FMod_FusekiKafka {

    public FMod_FusekiKafkaSCG() {
        super();
    }

    Map<String, KConnectorDesc> connectors = new HashMap<>();

    @Override
    public void prepare(FusekiServer.Builder builder, Set<String> names, Model configModel) {
        super.prepare(builder, names, configModel);
        DatasetBackupService.registerMethods("kafka", this::backupKafka, this::restoreKafka);
    }

    @Override
    protected Function<DatasetGraph, Sink<Event<Bytes, RdfPayload>>> getSinkBuilder() {
        return dsg -> {
            if (dsg instanceof DatasetGraphABAC dsgABAC) {
                // For ABAC enabled datasets use our custom sink that applies labels
                return new SmartCacheGraphSink(dsgABAC);
            } else {
                // For non-ABAC datasets use the default Fuseki Kafka sink
                return FusekiSink.builder().dataset(dsg).build();
            }
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

    private List<KConnectorDesc> obtainKafkaConnection(String dataset, DataService dataService) {
        List<KConnectorDesc> kafkaConnections = new ArrayList<>();
        KConnectorDesc conn = connectors.get(dataset);
        if (conn != null) {
            kafkaConnections.add(conn);
        }
        for (Endpoint endpoint : dataService.getEndpoints()) {
            String path = dataset + "/" + endpoint.getName();
            conn = connectors.get(path);
            if (conn != null) {
                kafkaConnections.add(conn);
            }
        }
        return kafkaConnections;
    }

    private ObjectNode backupKafkaConnection(KConnectorDesc conn, String path) {
        ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
        String sanitizedDataset = sanitiseName(conn.getDatasetName());
        resultNode.put("name", sanitizedDataset);
        String filename = path + "/" + sanitizedDataset + ".json";
        IOX.copy(conn.getStateFile(), filename);
        resultNode.put("source", conn.getStateFile());
        resultNode.put("destination", filename);
        resultNode.put("success", true);
        return resultNode;
    }

    private ObjectNode restoreKafkaConnection(KConnectorDesc conn, String dataset, String path) {
        ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
        String sanitizedDataset = sanitiseName(conn.getDatasetName());
        resultNode.put("name", sanitizedDataset);
        String filename = path + "/" + sanitizedDataset + ".json";
        File offsetStoreFile = new File(filename);
        if (offsetStoreFile.length() == 0) {
            String errorMessage =
                    String.format("Unable to restore Kafka for dataset (%s) as restore file (%s) not suitable. ",
                                  dataset, filename);
            FmtLog.info(LOG, errorMessage);
            resultNode.put("success", false);
            resultNode.put("reason", errorMessage);
            return resultNode;
        }
        FusekiOffsetStore offsetStore = FusekiOffsetStore.builder()
                                                         .datasetName(dataset)
                                                         .consumerGroup(conn.getConsumerGroupId())
                                                         .stateFile(offsetStoreFile)
                                                         .build();
        FKS.restoreOffsetForDataset(dataset, offsetStore);
        // Convert offsets for result appropriately, there could be many offsets depending on the topic(s) and
        // partitions(s)
        ObjectNode offsets = OBJECT_MAPPER.createObjectNode();
        for (Map.Entry<String, Object> offset : offsetStore.offsets()) {
            if (offset.getValue() instanceof Long longOffset) {
                offsets.put(offset.getKey(), longOffset);
            }
        }
        resultNode.set("offsets", offsets);
        resultNode.put("success", true);
        return resultNode;
    }
}
