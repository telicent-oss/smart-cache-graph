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

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.kafka.KConnectorDesc;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.telicent.backup.utils.BackupUtils.MAPPER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestFusekiKafkaSCG {

    FMod_FusekiKafkaSCG cut = new FMod_FusekiKafkaSCG();

    @Test
    public void test_backupKafka_emptyConnectors() {
        // given
        ObjectNode node = MAPPER.createObjectNode();

        DataAccessPoint mockDataAccessPoint = mock(DataAccessPoint.class);
        when(mockDataAccessPoint.getName()).thenReturn("missing dataset");


        // when
        cut.backupKafka(mockDataAccessPoint, "path doesn't matter", node);

        // then
        assertFalse(node.isEmpty());
        assertTrue(node.has("success"));
        assertTrue(node.has("reason"));
        assertFalse(node.get("success").asBoolean());
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

        ObjectNode node = MAPPER.createObjectNode();

        KConnectorDesc mockDesc = mock(KConnectorDesc.class);
        when(mockDesc.getStateFile()).thenReturn(stateFile.getAbsolutePath());

        cut.connectors.put("/matchingdataset", mockDesc);

        DataAccessPoint mockDataAccessPoint = mock(DataAccessPoint.class);
        when(mockDataAccessPoint.getName()).thenReturn("/matchingdataset/upload");

        // when
        cut.backupKafka(mockDataAccessPoint, tempDir.toString(), node);

        // then
        assertFalse(node.isEmpty());
        assertTrue(node.has("success"));
        assertFalse(node.has("reason"));
        assertTrue(node.get("success").asBoolean());
        File copiedFile = new File(tempDir.toString(), "matchingdataset_upload.json");
        assertTrue(copiedFile.exists());

    }

    @Test
    public void test_restoreKafka_emptyConnectors() {
        // given
        ObjectNode node = MAPPER.createObjectNode();

        DataAccessPoint mockDataAccessPoint = mock(DataAccessPoint.class);
        when(mockDataAccessPoint.getName()).thenReturn("missing dataset");

        // when
        cut.restoreKafka(mockDataAccessPoint, "path doesn't matter", node);

        // then
        assertFalse(node.isEmpty());
        assertTrue(node.has("success"));
        assertTrue(node.has("reason"));
        assertFalse(node.get("success").asBoolean());
    }

    @Test
    public void test_restoreKafka_fileDoesntExist() throws IOException {
        // given
        Path tempDir = Files.createTempDirectory("test_restore_dir");
        tempDir.toFile().deleteOnExit();

        ObjectNode node = MAPPER.createObjectNode();
        KConnectorDesc mockDesc = mock(KConnectorDesc.class);

        cut.connectors.put("matching dataset", mockDesc);

        DataAccessPoint mockDataAccessPoint = mock(DataAccessPoint.class);
        when(mockDataAccessPoint.getName()).thenReturn("matching dataset");

        // when
        cut.restoreKafka(mockDataAccessPoint, tempDir.toString(), node);

        // then
        assertFalse(node.isEmpty());
        assertTrue(node.has("success"));
        assertTrue(node.has("reason"));
        assertFalse(node.get("success").asBoolean());
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

        ObjectNode node = MAPPER.createObjectNode();
        KConnectorDesc mockDesc = mock(KConnectorDesc.class);

        cut.connectors.put("/matchingdataset", mockDesc);

        DataAccessPoint mockDataAccessPoint = mock(DataAccessPoint.class);
        when(mockDataAccessPoint.getName()).thenReturn("/matchingdataset/upload");

        // when
        cut.restoreKafka(mockDataAccessPoint, tempDir.toString(), node);

        // then
        assertFalse(node.isEmpty());
        assertTrue(node.has("success"));
        assertFalse(node.has("reason"));
        assertTrue(node.get("success").asBoolean());
    }
}
