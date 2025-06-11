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
package io.telicent.backup.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class TestJsonFileUtils {

    @TempDir
    Path tempDir;

    private ObjectNode testObjectNode;

    @BeforeEach
    void setUp() {
        testObjectNode = JsonFileUtils.OBJECT_MAPPER.createObjectNode();
        testObjectNode.put("id", 123);
        testObjectNode.put("name", "TestUser");
        testObjectNode.putObject("details").put("email", "test@example.com");
    }

    @Test
    @DisplayName("Should write ObjectNode to file with pretty printing (Path overload)")
    void testWriteObjectNodeToFile_Path() throws IOException {
        Path outputFile = tempDir.resolve("pretty_output.json");

        JsonFileUtils.writeObjectNodeToFile(testObjectNode, outputFile);

        assertTrue(Files.exists(outputFile), "Output file should exist");
        String content = Files.readString(outputFile);

        assertTrue(content.contains("{\n  \"id\" : 123,\n"));
        assertTrue(content.contains("  \"name\" : \"TestUser\",\n"));
        assertTrue(content.contains("  \"details\" : {\n    \"email\" : \"test@example.com\"\n  }\n}"));

        JsonNode readNode = JsonFileUtils.OBJECT_MAPPER.readTree(content);
        assertEquals(testObjectNode, readNode, "Read JSON should match written ObjectNode");
    }

    @Test
    @DisplayName("Should throw NullPointerException if objectNode is null (Path overload)")
    void testWriteObjectNodeToFile_Path_NullObjectNode() {
        Path outputFile = tempDir.resolve("null_node.json");
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> JsonFileUtils.writeObjectNodeToFile(null, outputFile));
        assertEquals("ObjectNode cannot be null.", thrown.getMessage());
        assertFalse(Files.exists(outputFile), "File should not be created");
    }

    @Test
    @DisplayName("Should throw NullPointerException if filePath is null (Path overload)")
    void testWriteObjectNodeToFile_Path_NullFilePath() {
        assertThrows(RuntimeException.class, () -> JsonFileUtils.writeObjectNodeToFile(testObjectNode, (String) null));
    }

    @Test
    @DisplayName("Should catch IOException and rethrow as RuntimeException (Path overload)")
    void testWriteObjectNodeToFile_Path_IOException() {
        Path nonExistentParentPath = tempDir.resolve("non_existent_dir").resolve("file.json");

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> JsonFileUtils.writeObjectNodeToFile(testObjectNode, nonExistentParentPath));

        assertInstanceOf(IOException.class, thrown.getCause());
        String errorMessage = thrown.getCause().getMessage();
        assertTrue(errorMessage.contains("No such file or directory") || errorMessage.contains("The system cannot find the path specified"),
                "Error message should indicate missing directory: " + errorMessage);
        assertFalse(Files.exists(nonExistentParentPath), "File should not be created");
    }

    @Test
    @DisplayName("Should write ObjectNode to file using String path overload")
    void testWriteObjectNodeToFile_String() throws IOException {
        String outputFileString = tempDir.resolve("string_output.json").toString();
        Path expectedOutputPath = Path.of(outputFileString);

        JsonFileUtils.writeObjectNodeToFile(testObjectNode, outputFileString);

        assertTrue(Files.exists(expectedOutputPath), "Output file should exist");
        String content = Files.readString(expectedOutputPath);
        assertTrue(content.contains("{\n  \"id\" : 123,\n"));

        JsonNode readNode = JsonFileUtils.OBJECT_MAPPER.readTree(content);
        assertEquals(testObjectNode, readNode, "Read JSON should match written ObjectNode");
    }

    @Test
    @DisplayName("Should throw NullPointerException if objectNode is null (String overload)")
    void testWriteObjectNodeToFile_String_NullObjectNode() {
        String outputFileString = tempDir.resolve("null_node_string.json").toString();
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> JsonFileUtils.writeObjectNodeToFile(null, outputFileString));
        assertEquals("ObjectNode cannot be null.", thrown.getMessage());
        assertFalse(Files.exists(Path.of(outputFileString)), "File should not be created");
    }

    @Test
    @DisplayName("Should throw NullPointerException if filePath is null (String overload)")
    void testWriteObjectNodeToFile_String_NullFilePath() {
        assertThrows(RuntimeException.class, () -> JsonFileUtils.writeObjectNodeToFile(testObjectNode, (String) null));
    }

    @Test
    @DisplayName("Should catch IOException and rethrow as RuntimeException (String overload)")
    void testWriteObjectNodeToFile_String_IOException() {
        String nonExistentParentPathString = tempDir.resolve("another_non_existent_dir").resolve("file.json").toString();

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> JsonFileUtils.writeObjectNodeToFile(testObjectNode, nonExistentParentPathString));

        assertInstanceOf(IOException.class, thrown.getCause());
        String errorMessage = thrown.getCause().getMessage();
        assertTrue(errorMessage.contains("No such file or directory") || errorMessage.contains("The system cannot find the path specified"),
                "Error message should indicate missing directory: " + errorMessage);
        assertFalse(Files.exists(Path.of(nonExistentParentPathString)), "File should not be created");
    }
}