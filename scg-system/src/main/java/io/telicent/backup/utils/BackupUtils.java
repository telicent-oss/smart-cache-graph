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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.smart.cache.configuration.Configurator;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.riot.WebContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

/**
 * Utility class for carrying out common back-up operations and file I/O.
 */
public class BackupUtils {

    public static final Logger LOG = LoggerFactory.getLogger("BackupUtils");

    /**
     * Configuration parameter for determining location of backups (if unset defaults to PWD/backups)
     */
    public static final String ENV_BACKUPS_DIR = "ENV_BACKUPS_DIR";

    public static String dirBackups;

    public static ObjectMapper MAPPER = new ObjectMapper();

    private BackupUtils() {}

    private static String getBackUpDirProperty() {
        return Configurator.get(ENV_BACKUPS_DIR);
    }

    /**
     * Obtain back up directory path.
     * If not currently set, generate it from available configuration.
     * @return path of back-up directory
     */
    public static String getBackUpDir() {
        if (dirBackups == null) {
            dirBackups = generateBackUpDirPath();
        }
        return dirBackups;
    }

    /**
     * Generate the back-up dir.
     * If not set in configuration use PWD/backups
     * @return the Path of the back-up directory location.
     */
    public static String generateBackUpDirPath() {
        String dirBackupStr = getBackUpDirProperty();

        if (dirBackupStr == null) {
            dirBackupStr = System.getenv("PWD") + "/backups";
            File dir = new File(dirBackupStr);
            dir.mkdir();
            FmtLog.info(LOG, "ENV_BACKUPS_DIR not set!!. Backups folder set to [default] : /backups");
            return dirBackupStr;
        }

        File dir = new File(dirBackupStr);
        if (!dir.exists()) {
            if (dir.mkdir()) {
                FmtLog.info(LOG, "ENV_BACKUPS_DIR : /%s", dirBackupStr);
            } else {
                FmtLog.info(LOG, "ENV_BACKUPS_DIR invalid!!. Backups folder set to [default] : /backups");
                dirBackupStr = System.getenv("PWD") + "/backups";
            }
            return dirBackupStr;
        }

        FmtLog.info(LOG, "ENV_BACKUPS_DIR already exists. Backups folder set to /%s", dirBackupStr);
        return dirBackupStr;
    }

    /**
     * For the given directory find the highest numbered directory
     * @param directoryPath the directory to check
     * @return the highest number (or -1 if unavailable)
     */
    public static int getHighestExistingDirectoryNumber(String directoryPath) {
        if (!createPathIfNotExists(directoryPath)) {
            FmtLog.error(LOG, "Failed to create directory: " + directoryPath);
            return -1;
        }
        File dir = new File(directoryPath);

        return Stream.of(Objects.requireNonNull(dir.listFiles(File::isDirectory)))
                .filter(File::isDirectory)
                .map(File::getName)
                .filter(name -> name.matches("\\d+"))
                .map(Integer::parseInt)
                .max(Comparator.naturalOrder())
                .orElse(0);
    }

    /**
     * For the given directory find the highest numbered directory
     * so far and return +1
     * @param directoryPath the directory to check
     * @return the next number (or -1 if unavailable)
     */
    public static int getNextDirectoryNumber(String directoryPath) {
        int highestSoFar = getHighestExistingDirectoryNumber(directoryPath);
        if (highestSoFar < 0) {
            FmtLog.error(LOG, "Failed to obtain number for : " + directoryPath);
            return -1;
        }
        return highestSoFar + 1;
    }

    /**
     * For the given directory find the highest numbered directory
     * so far and return +1 and create the relevant directory.
     * @param directoryPath the directory to check
     * @return the next number (or -1 if unavailable)
     */
    public static int getNextDirectoryNumberAndCreate(String directoryPath) {
        int nextNumber = getNextDirectoryNumber(directoryPath);
        if (nextNumber < 1) {
            FmtLog.error(LOG, "Failed to obtain number for : " + directoryPath);
            return -1;
        }

        // Create the new directory path
        String newDirectoryPath = directoryPath + "/" + nextNumber;

        // Create the directory
        if (!createPathIfNotExists(newDirectoryPath)) {
            FmtLog.error(LOG, "Failed to create directory: " + newDirectoryPath);
            return -1;
        }
        return nextNumber;
    }

    /**
     * Takes a string path and if it doesn't exist, creates it.
     *
     * @param pathString the path to check
     * @return whether the operation was successful or not
     */
    public static boolean createPathIfNotExists(String pathString) {
        if (null == pathString) {
            return false;
        }
        File path = new File(pathString);
        if (!path.exists()) {
            return path.mkdirs();
        }
        return true;
    }

    /**
     * Takes a string path, checks it exists and is a directory
     *
     * @param pathString the path to check
     * @return whether the operation was successful or not
     */
    public static boolean checkPathExistsAndIsDir(String pathString) {
        if (requestIsEmpty(pathString)) {
            return false;
        }
        File path = new File(pathString);
        if (path.exists() && path.isDirectory()) {
            return true;
        }
        return false;
    }

    /**
     * Takes a string path, checks it exists and is a file
     *
     * @param pathString the path to check
     * @return whether the operation was successful or not
     */
    public static boolean checkPathExistsAndIsFile(String pathString) {
        if (requestIsEmpty(pathString)) {
            return false;
        }
        File path = new File(pathString);
        if (path.exists() && path.isFile()) {
            return true;
        }
        return false;
    }

    /**
     * Takes a string path and returns the list of sub-directories
     * as a list of strings
     *
     * @param pathString the path to check
     * @return a list of the subdirectory names (as strings)
     */
    public static List<String> getSubdirectoryNames(String pathString) {
        List<String> subdirectoryNames = new ArrayList<>();

        File directory = new File(pathString);
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        subdirectoryNames.add(file.getName());
                    }
                }
            }
        }
        return subdirectoryNames;
    }

    /**
     * Populate a JSON node with the contents of the file directory
     *
     * @param path the directory to scan
     * @return a node containing the directory contents
     */
    public static ObjectNode populateNodeFromDir(String path) {
        ObjectNode rootNode = MAPPER.createObjectNode();
        File rootDir = new File(path);
        populateNodeFromDir(rootDir, rootNode);
        return rootNode;
    }

    /**
     * Populate a JSON node with the contents of the file directory
     *
     * @param dir  the directory to scan
     * @param node the node to populate
     */
    public static void populateNodeFromDir(File dir, ObjectNode node) {
        if (null == dir || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (null == files) {
            return;
        }

        processFiles(files, node);
    }

    /**
     * Populate a JSON node with the contents of the file directory
     * but in numerical order
     *
     * @param path  the directory to scan
     * @return an object node of the contents
     */
    public static ObjectNode populateNodeFromDirNumerically(String path) {
        ObjectNode rootNode = MAPPER.createObjectNode();
        File rootDir = new File(path);
        populateNodeFromDirNumerically(rootDir, rootNode);
        return rootNode;
    }

    /**
     * Populate a JSON node with the contents of the file directory
     * but in numerical order
     *
     * @param dir the directory to scan
     * @param node the node to populate
     */
    public static void populateNodeFromDirNumerically(File dir, ObjectNode node) {
        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files, Comparator.comparingInt(file -> {
                try {
                    return Integer.parseInt(file.getName());
                } catch (NumberFormatException e) {
                    return Integer.MAX_VALUE; // Non-numeric directories will be placed last
                }
            }));

            processFiles(files, node);
        }
    }

    /**
     * Iterate recursively over given files adding details ot node
     * @param files list of files
     * @param node node to add details too
     */
    static void processFiles(File[] files,ObjectNode node) {
        for (File file : files) {
            if (file.isDirectory()) {
                ObjectNode childNode = MAPPER.createObjectNode();
                node.set(file.getName(), childNode);
                populateNodeFromDir(file, childNode);
            } else {
                ArrayNode filesNode = node.withArray("files");
                filesNode.add(file.getName());
            }
        }
    }

    /**
     * For the given directory, delete it and it's contents.
     * @param directory the directory to remove
     */
    public static void deleteDirectoryRecursively(File directory) {
        if (null == directory || !directory.exists()) {
            return;
        }
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectoryRecursively(file);
                }
            }
        }
        directory.delete();
    }

    /**
     * Populate an HTTP Response from given JSON Node
     *
     * @param response     Response to populate
     * @param jsonResponse Response data (in JSON form).
     */
    public static void processResponse(HttpServletResponse response, ObjectNode jsonResponse) {
        String jsonOutput;
        try (ServletOutputStream out = response.getOutputStream()) {
            jsonOutput = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonResponse);
            response.setContentLength(jsonOutput.length());
            response.setContentType(WebContent.contentTypeJSON);
            response.setCharacterEncoding(WebContent.charsetUTF8);
            out.print(jsonOutput);
        } catch (IOException ex) {
            response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
        }
    }

    /**
     * Populate an HTTP Response with error data
     *
     * @param response     Response to populate
     * @param jsonResponse Response data (in JSON form).
     * @param exception    Exception encountered
     */
    public static void handleError(HttpServletResponse response, ObjectNode jsonResponse, Exception exception) {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        jsonResponse.put("error", exception.getMessage());
        processResponse(response, jsonResponse);
    }

    /**
     * Checks to see if the requested parameter is empty or just a '/'
     * which we treat as equivalent
     *
     * @param requestName the requested dataset (if provided)
     * @return true if empty, false if set
     */
    public static boolean requestIsEmpty(String requestName) {
        if (requestName == null) {
            return true;
        } else if (requestName.trim().isEmpty()) {
            return true;
        } else return requestName.trim().equals("/");
    }
}
