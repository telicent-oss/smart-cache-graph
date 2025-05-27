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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.server.Endpoint;
import org.apache.jena.kafka.KConnectorDesc;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestFusekiKafkaSCG {

    FMod_FusekiKafkaSCG cut = new FMod_FusekiKafkaSCG();

    @Test
    public void test_backupKafka_emptyConnectors() {
        // given
        String dataset = "missing dataset";
        DataAccessPoint mockDataAccessPoint = mock(DataAccessPoint.class);
        DataService mockDataService = mock(DataService.class);
        when(mockDataAccessPoint.getDataService()).thenReturn(mockDataService);
        when(mockDataService.getEndpoints()).thenReturn(List.of());
        when(mockDataAccessPoint.getName()).thenReturn(dataset);

        // when
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        cut.backupKafka(mockDataAccessPoint, "path doesn't matter", node);

        // then
        assertFalse(node.isEmpty());
        assertTrue(node.has(dataset));
        assertTrue(node.get(dataset).isEmpty());
    }

    @Test
    public void test_backupKafka_noMatches() {
        // given
        String dataset = "missing dataset";
        DataAccessPoint mockDataAccessPoint = mock(DataAccessPoint.class);
        DataService mockDataService = mock(DataService.class);
        when(mockDataAccessPoint.getDataService()).thenReturn(mockDataService);
        Endpoint mockEndpoint = mock(Endpoint.class);
        when(mockEndpoint.getName()).thenReturn("cantmatch");
        Endpoint mockEndpoint2 = mock(Endpoint.class);
        when(mockEndpoint2.getName()).thenReturn("wontmatch");
        when(mockDataService.getEndpoints()).thenReturn(List.of(mockEndpoint, mockEndpoint2));
        when(mockDataAccessPoint.getName()).thenReturn(dataset);

        // when
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        cut.backupKafka(mockDataAccessPoint, "path doesn't matter", node);

        // then
        assertFalse(node.isEmpty());
        assertTrue(node.has(dataset));
        assertTrue(node.get(dataset).isEmpty());
    }


    @Test
    public void test_backupKafka_happyPath() throws IOException {
        // given
        Path tempDir = Files.createTempDirectory("test_backup_dir");
        tempDir.toFile().deleteOnExit();
        File stateFile = new File(tempDir.toString(), "kafka.state");
        assertTrue(stateFile.createNewFile());
        stateFile.deleteOnExit();

        String jsonString = """
                {
                  "dataset" : "/matchingdataset" ,
                  "endpoint" : "" ,
                  "topic" : "test" ,
                  "offset" : 18723
                }
                """;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(stateFile))) {
            writer.write(jsonString);
        }

        ObjectNode node = OBJECT_MAPPER.createObjectNode();

        String localPath = "/matchingdataset/sendkafka";

        KConnectorDesc mockDesc = mock(KConnectorDesc.class);
        when(mockDesc.getStateFile()).thenReturn(stateFile.getAbsolutePath());
        when(mockDesc.getLocalDispatchPath()).thenReturn(localPath);

        cut.connectors.put(localPath, mockDesc);

        DataAccessPoint mockDataAccessPoint = mock(DataAccessPoint.class);
        DataService mockDataService = mock(DataService.class);
        when(mockDataAccessPoint.getDataService()).thenReturn(mockDataService);
        Endpoint mockEndpoint = mock(Endpoint.class);
        when(mockEndpoint.getName()).thenReturn("sendkafka");
        when(mockDataService.getEndpoints()).thenReturn(List.of(mockEndpoint));
        when(mockDataAccessPoint.getName()).thenReturn("/matchingdataset");

        // when
        cut.backupKafka(mockDataAccessPoint, tempDir.toString(), node);

        // then
        assertFalse(node.isEmpty());
        assertTrue(node.has("/matchingdataset"));
        assertTrue(node.get("/matchingdataset").isArray());
        JsonNode listNode = node.get("/matchingdataset");
        assertEquals(1, listNode.size());
        JsonNode entryNode = listNode.get(0);
        assertTrue(entryNode.has("success"));
        assertTrue(entryNode.get("success").asBoolean());
        assertFalse(entryNode.has("reason"));
        assertTrue(entryNode.has("name"));
        assertTrue(entryNode.get("name").isTextual());
        assertEquals("matchingdataset_sendkafka", entryNode.get("name").asText());
        File copiedFile = new File(tempDir.toString(), "matchingdataset_sendkafka.json");
        assertTrue(copiedFile.exists());

    }

    @Test
    public void test_backupKafka_happyPath_multipleMatch() throws IOException {
        // given
        Path tempDir = Files.createTempDirectory("test_backup_dir");
        tempDir.toFile().deleteOnExit();
        File stateFile = new File(tempDir.toString(), "kafka.state");
        assertTrue(stateFile.createNewFile());
        stateFile.deleteOnExit();

        File stateFile2 = new File(tempDir.toString(), "kafka2.state");
        assertTrue(stateFile2.createNewFile());
        stateFile2.deleteOnExit();


        String jsonString = """
                {
                  "dataset" : "/matchingdataset" ,
                  "endpoint" : "" ,
                  "topic" : "test" ,
                  "offset" : 18723
                }
                """;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(stateFile))) {
            writer.write(jsonString);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(stateFile2))) {
            writer.write(jsonString);
        }

        String localPath = "/matchingdataset/upload";
        String localPath2 = "/matchingdataset/different_upload";

        KConnectorDesc mockDesc = mock(KConnectorDesc.class);
        when(mockDesc.getStateFile()).thenReturn(stateFile.getAbsolutePath());
        when(mockDesc.getLocalDispatchPath()).thenReturn(localPath);
        KConnectorDesc mockDesc2 = mock(KConnectorDesc.class);
        when(mockDesc2.getStateFile()).thenReturn(stateFile.getAbsolutePath());
        when(mockDesc2.getLocalDispatchPath()).thenReturn(localPath2);

        cut.connectors.put(localPath, mockDesc);
        cut.connectors.put(localPath2, mockDesc2);

        String dataset = "/matchingdataset";
        DataAccessPoint mockDataAccessPoint = mock(DataAccessPoint.class);
        DataService mockDataService = mock(DataService.class);
        when(mockDataAccessPoint.getDataService()).thenReturn(mockDataService);
        Endpoint mockEndpoint = mock(Endpoint.class);
        when(mockEndpoint.getName()).thenReturn("upload");
        Endpoint mockEndpoint2 = mock(Endpoint.class);
        when(mockEndpoint2.getName()).thenReturn("missing");
        Endpoint mockEndpoint3 = mock(Endpoint.class);
        when(mockEndpoint3.getName()).thenReturn("different_upload");
        when(mockDataService.getEndpoints()).thenReturn(List.of(mockEndpoint, mockEndpoint2, mockEndpoint3));
        when(mockDataAccessPoint.getName()).thenReturn(dataset);

        // when
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        cut.backupKafka(mockDataAccessPoint, tempDir.toString(), node);

        // then
        assertFalse(node.isEmpty());
        assertTrue(node.has(dataset));
        assertFalse(node.get(dataset).isEmpty());
        ArrayNode listNode = node.withArrayProperty(dataset);
        assertEquals(2, listNode.size());
        for (int i = 0; i < listNode.size(); i++) {
            JsonNode entryNode = listNode.get(i);
            assertTrue(entryNode.isObject());
            assertTrue(entryNode.has("success"));
            assertFalse(entryNode.has("reason"));
            assertTrue(entryNode.get("success").asBoolean());
            assertTrue(entryNode.has("name"));
            assertTrue(entryNode.get("name").isTextual());
        }
        File copiedFile = new File(tempDir.toString(), "matchingdataset_upload.json");
        assertTrue(copiedFile.exists());
        File copiedFile2 = new File(tempDir.toString(), "matchingdataset_different_upload.json");
        assertTrue(copiedFile2.exists());
    }

    @Test
    public void test_restoreKafka_emptyConnectors() {
        // given
        String dataset = "missing dataset";
        DataAccessPoint mockDataAccessPoint = mock(DataAccessPoint.class);
        DataService mockDataService = mock(DataService.class);
        when(mockDataAccessPoint.getDataService()).thenReturn(mockDataService);
        when(mockDataService.getEndpoints()).thenReturn(List.of());
        when(mockDataAccessPoint.getName()).thenReturn(dataset);

        // when
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        cut.restoreKafka(mockDataAccessPoint, "path doesn't matter", node);

        // then
        assertFalse(node.isEmpty());
        assertTrue(node.has(dataset));
        assertTrue(node.get(dataset).isEmpty());
    }

    @Test
    public void test_restoreKafka_noMatches() {
        // given
        String dataset = "missing dataset";
        DataAccessPoint mockDataAccessPoint = mock(DataAccessPoint.class);
        DataService mockDataService = mock(DataService.class);
        when(mockDataAccessPoint.getDataService()).thenReturn(mockDataService);
        Endpoint mockEndpoint = mock(Endpoint.class);
        when(mockEndpoint.getName()).thenReturn("wontmatch");
        Endpoint mockEndpoint2 = mock(Endpoint.class);
        when(mockEndpoint2.getName()).thenReturn("cantmatch");
        when(mockDataService.getEndpoints()).thenReturn(List.of(mockEndpoint, mockEndpoint2));
        when(mockDataAccessPoint.getName()).thenReturn(dataset);

        // when
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        cut.restoreKafka(mockDataAccessPoint, "path doesn't matter", node);

        // then
        assertFalse(node.isEmpty());
        assertTrue(node.has(dataset));
        assertTrue(node.get(dataset).isEmpty());
    }


    @Test
    public void test_restoreKafka_fileDoesntExist() throws IOException {
        // given
        Path tempDir = Files.createTempDirectory("test_restore_dir");
        tempDir.toFile().deleteOnExit();

        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        KConnectorDesc mockDesc = mock(KConnectorDesc.class);

        String dataset = "matching dataset";

        cut.connectors.put(dataset, mockDesc);

        DataAccessPoint mockDataAccessPoint = mock(DataAccessPoint.class);
        DataService mockDataService = mock(DataService.class);
        when(mockDataAccessPoint.getDataService()).thenReturn(mockDataService);
        when(mockDataService.getEndpoints()).thenReturn(List.of());
        when(mockDataAccessPoint.getName()).thenReturn(dataset);

        // when
        cut.restoreKafka(mockDataAccessPoint, tempDir.toString(), node);

        // then
        assertFalse(node.isEmpty());
        assertTrue(node.has(dataset));
        JsonNode listNode = node.get(dataset);
        assertFalse(listNode.isEmpty());
        assertTrue(listNode.isArray());
        JsonNode entryNode = listNode.get(0);
        assertTrue(entryNode.has("success"));
        assertFalse(entryNode.get("success").asBoolean());
        assertTrue(entryNode.has("reason"));
        assertTrue(entryNode.get("reason").asText().startsWith("Unable to restore Kafka for dataset"));
    }

    @Test
    public void test_restoreKafka_happyPath() throws IOException {
        // given
        Path tempDir = Files.createTempDirectory("test_restore_dir2");
        tempDir.toFile().deleteOnExit();

        File stateFile = new File(tempDir.toString(), "matchingdataset_upload.json");
        assertTrue(stateFile.createNewFile());
        stateFile.deleteOnExit();

        String jsonString = """
                {
                  "dataset" : "/matchingdataset" ,
                  "endpoint" : "" ,
                  "topic" : "test" ,
                  "offset" : 18723
                }
                """;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(stateFile))) {
            writer.write(jsonString);
        }

        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        KConnectorDesc mockDesc = mock(KConnectorDesc.class);
        String localPath = "/matchingdataset/upload";
        when(mockDesc.getLocalDispatchPath()).thenReturn(localPath);

        cut.connectors.put("/matchingdataset", mockDesc);

        DataAccessPoint mockDataAccessPoint = mock(DataAccessPoint.class);
        DataService mockDataService = mock(DataService.class);
        when(mockDataAccessPoint.getDataService()).thenReturn(mockDataService);
        Endpoint mockEndpoint = mock(Endpoint.class);
        when(mockEndpoint.getName()).thenReturn("upload");
        when(mockDataService.getEndpoints()).thenReturn(List.of(mockEndpoint));
        when(mockDataAccessPoint.getName()).thenReturn("/matchingdataset");

        // when
        cut.restoreKafka(mockDataAccessPoint, tempDir.toString(), node);

        // then
        assertFalse(node.isEmpty());
        assertTrue(node.has("/matchingdataset"));
        assertTrue(node.get("/matchingdataset").isArray());
        JsonNode listNode = node.get("/matchingdataset");
        assertEquals(1, listNode.size());
        JsonNode entryNode = listNode.get(0);
        assertTrue(entryNode.has("success"));
        assertTrue(entryNode.get("success").asBoolean());
        assertFalse(entryNode.has("reason"));
    }

    @Test
    public void test_restoreKafka_happyPath_multipleMatches() throws IOException {
        // given
        Path tempDir = Files.createTempDirectory("test_restore_dir2");
        tempDir.toFile().deleteOnExit();

        File stateFile = new File(tempDir.toString(), "matchingdataset_upload.json");
        assertTrue(stateFile.createNewFile());
        stateFile.deleteOnExit();

        File stateFile2 = new File(tempDir.toString(), "matchingdataset_different_upload.json");
        assertTrue(stateFile2.createNewFile());
        stateFile2.deleteOnExit();

        String jsonString = """
                {
                  "dataset" : "/matchingdataset" ,
                  "endpoint" : "" ,
                  "topic" : "test" ,
                  "offset" : 18723
                }
                """;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(stateFile))) {
            writer.write(jsonString);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(stateFile2))) {
            writer.write(jsonString);
        }

        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        KConnectorDesc mockDesc = mock(KConnectorDesc.class);
        String localPath = "/matchingdataset/upload";
        when(mockDesc.getLocalDispatchPath()).thenReturn(localPath);
        KConnectorDesc mockDesc2 = mock(KConnectorDesc.class);
        String localPath2 = "/matchingdataset/different_upload";
        when(mockDesc2.getLocalDispatchPath()).thenReturn(localPath2);

        cut.connectors.put(localPath, mockDesc);
        cut.connectors.put(localPath2, mockDesc2);

        DataAccessPoint mockDataAccessPoint = mock(DataAccessPoint.class);
        DataService mockDataService = mock(DataService.class);
        when(mockDataAccessPoint.getDataService()).thenReturn(mockDataService);
        Endpoint mockEndpoint = mock(Endpoint.class);
        when(mockEndpoint.getName()).thenReturn("upload");
        Endpoint mockEndpoint2 = mock(Endpoint.class);
        when(mockEndpoint2.getName()).thenReturn("different_upload");
        Endpoint mockEndpoint3 = mock(Endpoint.class);
        when(mockEndpoint3.getName()).thenReturn("no match");
        when(mockDataService.getEndpoints()).thenReturn(List.of(mockEndpoint, mockEndpoint2, mockEndpoint3));
        when(mockDataAccessPoint.getName()).thenReturn("/matchingdataset");

        // when
        cut.restoreKafka(mockDataAccessPoint, tempDir.toString(), node);

        // then
        assertFalse(node.isEmpty());
        assertTrue(node.has("/matchingdataset"));
        assertTrue(node.get("/matchingdataset").isArray());
        JsonNode listNode = node.get("/matchingdataset");
        assertEquals(2, listNode.size());
        JsonNode entryNode1 = listNode.get(0);
        assertFalse(entryNode1.isNull());
        assertTrue(entryNode1.has("success"));
        assertTrue(entryNode1.get("success").asBoolean());
        assertFalse(entryNode1.has("reason"));
        JsonNode entryNode2 = listNode.get(1);
        assertFalse(entryNode2.isNull());
        assertTrue(entryNode2.has("success"));
        assertTrue(entryNode2.get("success").asBoolean());
        assertFalse(entryNode2.has("reason"));
    }
}
