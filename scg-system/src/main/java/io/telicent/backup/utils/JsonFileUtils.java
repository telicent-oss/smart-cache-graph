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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public class JsonFileUtils {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Writes an ObjectNode to a specified file.
     *
     * @param objectNode The ObjectNode to write.
     * @param filePath   The Path to the file where the JSON will be written.
     */
    public static void writeObjectNodeToFile(ObjectNode objectNode, Path filePath) {
        Objects.requireNonNull(objectNode, "ObjectNode cannot be null.");
        Objects.requireNonNull(filePath, "File path cannot be null.");
        try{
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), objectNode);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes an ObjectNode to a specified file (String path)
     *
     * @param objectNode The ObjectNode to write.
     * @param filePath   The String path to the file where the JSON will be written.
     */
    public static void writeObjectNodeToFile(ObjectNode objectNode, String filePath){
        writeObjectNodeToFile(objectNode, Path.of(filePath));
    }
}