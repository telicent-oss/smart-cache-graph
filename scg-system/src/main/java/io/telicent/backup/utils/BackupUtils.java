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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.utils.ServletUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.apache.jena.atlas.logging.FmtLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;

/**
 * Utility class for carrying out common back-up operations and file I/O.
 */
public class BackupUtils extends ServletUtils {

    public static final Logger LOG = LoggerFactory.getLogger("BackupUtils");

    private static final Pattern NUMBERED_ITEM_PATTERN = Pattern.compile("^(\\d+)(\\.zip)?$");

    /**
     * Configuration parameter for determining location of backups (if unset defaults to PWD/backups)
     */
    public static final String ENV_BACKUPS_DIR = "ENV_BACKUPS_DIR";

    public static String dirBackups;

    private BackupUtils() {
    }

    private static String getBackUpDirProperty() {
        return Configurator.get(ENV_BACKUPS_DIR);
    }

    /**
     * Obtain back up directory path.
     * If not currently set, generate it from available configuration.
     *
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
     *
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

    public static int getHighestDirectoryNumber(String directoryPath) {
        if (!checkPathExistsAndIsDir(directoryPath)) {
            String errorMsg = "Base dir does not exist properly: " + directoryPath;
            FmtLog.error(LOG, errorMsg);
            throw new BackupException(errorMsg);
        }
        File baseDir = new File(directoryPath);

        int maxNumber = 0;

        File[] files = baseDir.listFiles();
        if (files != null) {
            maxNumber = Arrays.stream(files)
                    .map(File::getName)
                    .map(BackupUtils::extractNumberFromNumberedItem) // Modified line
                    .filter(Optional::isPresent)
                    .mapToInt(Optional::get)
                    .max()
                    .orElse(0);
        }
        return maxNumber;
    }

    /**
     * For the given directory find the highest numbered directory or zip
     *
     * @param directoryPath the directory to check
     * @return the highest number (or -1 if unavailable)
     */
    public static int getNextDirectoryNumberAndCreate(String directoryPath) {
        if (!checkPathExistsAndIsDir(directoryPath)) {
            String errorMsg = "Base dir does not exist properly: " + directoryPath;
            FmtLog.error(LOG, errorMsg);
            throw new BackupException(errorMsg);
        }
        File baseDir = new File(directoryPath);

        int nextNumber = getHighestDirectoryNumber(directoryPath) + 1;
        String newDirectoryName = String.valueOf(nextNumber);
        File newDirectory = new File(baseDir, newDirectoryName);

        if (!newDirectory.mkdir()) {
            String errorMsg = "Failed to create new directory: " + newDirectory.getAbsolutePath();
            FmtLog.error(LOG, errorMsg);
            throw new BackupException(errorMsg);
        }

        LOG.info("Created new directory: {}", newDirectory.getAbsolutePath());
        return nextNumber;
    }

    /**
     * Extracts a number from a given file or directory name if it matches the pattern of a number
     * or a numbered zip file.
     *
     * @param name The name of the file or directory.
     * @return An Optional containing the extracted number, or empty if no number can be extracted.
     */
    private static Optional<Integer> extractNumberFromNumberedItem(String name) { // New method
        Matcher matcher = NUMBERED_ITEM_PATTERN.matcher(name);
        if (matcher.matches()) {
            try {
                return Optional.of(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException e) {
                // Should not happen if regex matches, but good to handle defensively
                LOG.warn("Failed to parse number from matched item: {}", name, e);
                return Optional.empty();
            }
        }
        return Optional.empty();
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
        ObjectNode rootNode = OBJECT_MAPPER.createObjectNode();
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
     * @param path the directory to scan
     * @return an object node of the contents
     */
    public static ObjectNode populateNodeFromDirNumerically(String path) {
        ObjectNode rootNode = OBJECT_MAPPER.createObjectNode();
        File rootDir = new File(path);
        populateNodeFromDirNumerically(rootDir, rootNode);
        return rootNode;
    }

    /**
     * Populate a JSON node with the contents of the file directory
     * but in numerical order
     *
     * @param dir  the directory to scan
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
     * Iterate recursively over given files adding details to node
     *
     * @param files list of files
     * @param node  node to add details too
     */
    static void processFiles(File[] files, ObjectNode node) {
        for (File file : files) {
            if (file.isDirectory()) {
                ObjectNode childNode = OBJECT_MAPPER.createObjectNode();
                node.set(file.getName(), childNode);
                populateNodeFromDir(file, childNode);
            } else {
                ArrayNode filesNode = node.withArray("files");
                filesNode.add(file.getName());
            }
        }
    }

    /**
     * Lists files in a given directory that match a specific numeric prefix and suffix,
     * returning them in numerical order based on their prefix.
     * For example, given suffix "_info.json", it will find "1_info.json", "10_info.json", etc.
     *
     * @param directoryPath The path to the directory to scan.
     * @param suffix        The suffix to match (e.g., "_info.json").
     * @return A sorted list of Paths to the matching files. Returns an empty list if the directory
     * does not exist, is not a directory, or contains no matching files.
     */
    public static List<Path> getNumberedFilesBySuffix(String directoryPath, String suffix) {
        List<Path> matchingFiles = new ArrayList<>();
        if (requestIsEmpty(directoryPath) || suffix == null || suffix.isEmpty()) {
            return matchingFiles;
        }

        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            return matchingFiles;
        }

        Pattern pattern = Pattern.compile("^(\\d+)" + Pattern.quote(suffix) + "$");

        File[] files = directory.listFiles();
        if (files != null) {
            Stream.of(files)
                  .filter(File::isFile)
                  .filter(file -> {
                      Matcher matcher = pattern.matcher(file.getName());
                      return matcher.matches();
                  })
                  .sorted(Comparator.comparingInt(file -> {
                      Matcher matcher = pattern.matcher(file.getName());
                      if (matcher.matches()) {
                          return Integer.parseInt(matcher.group(1));
                      }
                      return Integer.MAX_VALUE;
                  }))
                  .map(File::toPath)
                  .forEach(matchingFiles::add);
        }
        return matchingFiles;
    }

    /**
     * Reads JSON content from numerically ordered files (e.g., "1_info.json", "2_info.json")
     * into a single ObjectNode. Each file's content is added as a child of the
     * provided ObjectNode, using the file's numerical prefix as the key.
     *
     * @param targetNode    The ObjectNode to which the file contents will be added.
     * @param directoryPath The path to the directory containing the files.
     * @param suffix        The suffix of the files to read (e.g., "_info.json").
     * @throws IOException If an I/O error occurs while reading a file or parsing JSON.
     * @throws NullPointerException If targetNode, directoryPath, or suffix is null.
     */
    public static void populateObjectNodeFromNumberedFiles(ObjectNode targetNode, String directoryPath, String suffix) throws IOException {
        Objects.requireNonNull(targetNode, "Target ObjectNode cannot be null.");
        Objects.requireNonNull(directoryPath, "Directory path cannot be null.");
        Objects.requireNonNull(suffix, "Suffix cannot be null.");

        List<Path> orderedFiles = getNumberedFilesBySuffix(directoryPath, suffix);
        Pattern pattern = Pattern.compile("^(\\d+)" + Pattern.quote(suffix) + "$");

        for (Path filePath : orderedFiles) {
            String fileName = filePath.getFileName().toString();
            Matcher matcher = pattern.matcher(fileName);
            if (matcher.matches()) {
                String numericKey = matcher.group(1);

                try {
                    JsonNode fileContent = OBJECT_MAPPER.readTree(Files.readString(filePath));
                    targetNode.set(numericKey, fileContent);
                } catch (IOException e) {
                    FmtLog.error(LOG, "Error reading or parsing JSON from file %s: %s", filePath, e.getMessage());
                    throw e;
                }
            }
        }
    }

    /**
     * Reads JSON content from numerically ordered files (e.g., "1_info.json", "2_info.json")
     * and returns a new ObjectNode containing their combined contents.
     * Each file's content is added as a child of the returned ObjectNode,
     * using the file's numerical prefix as the key.
     *
     * @param directoryPath The path to the directory containing the files.
     * @param suffix        The suffix of the files to read (e.g., "_info.json").
     * @return A new ObjectNode containing the combined JSON contents, or an empty ObjectNode if no files are found.
     */
    public static ObjectNode getObjectNodeFromNumberedFiles(String directoryPath, String suffix) {
        ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
        try {
            populateObjectNodeFromNumberedFiles(resultNode, directoryPath, suffix);
        } catch (IOException e) {
            FmtLog.error(LOG, "Error reading or parsing JSON from file %s: %s", directoryPath, e.getMessage());
            throw new RuntimeException(e);
        }
        return resultNode;
    }

    /**
     * Delete everything within the given directory using wildcard
     * @param deletePath path to delete
     * @param pattern regular expression for files
     */
    public static void deleteFilesRegEx(String deletePath, String pattern) {
        Path delDir = Path.of(deletePath);
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

        try (Stream<Path> walk = Files.walk(delDir)) {
            walk.filter(path -> Files.isRegularFile(path) && matcher.matches(path.getFileName()))
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Handle deletion failure for individual files
                            LOG.error("Failed to delete file: {}. Reason: {}", path, e.getMessage());
                            // You could also choose to rethrow a custom exception or accumulate failures
                        }
                    });
        } catch (UncheckedIOException | IOException e) {
            LOG.error("Failed to delete files from {}. Reason: {}", deletePath, e.getMessage());
        }
    }

    /**
     * Delete everything within the given directory
     * @param deletePath path to delete
     */
    public static void deleteDirectoryRecursively(String deletePath) {
        if (null == deletePath || deletePath.isEmpty()) {
            return;
        }
        deleteDirectoryRecursively(new File(deletePath));
    }

    /**
     * For the given directory, delete it and it's contents.
     *
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

    /**
     * Deletes each of the entries provided
     * @param paths a list of directories
     */
    public static void cleanUp(final List<Path> paths) {
        for (Path path : paths) {
            FileUtils.deleteQuietly(path.toFile());
        }
    }

    /**
     * Reads the kafka offset from backup file
     * @param directoryPath the path to the kafka directory in the backup
     */
    public static Optional<Integer> readKafkaStateOffset(String directoryPath) {
        try {
            Path dir = Paths.get(directoryPath);
            if (!Files.isDirectory(dir)) {
                LOG.warn("Directory {} is not readable", directoryPath);
                return Optional.empty();
            }
            if ( !Files.isReadable(dir)) {
                LOG.warn("Directory {} is not readable", directoryPath);
                return Optional.empty();
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, Files::isRegularFile)) {
                int fileCount = 0;
                Path targetFile = null;
                for (Path file : stream) {
                    fileCount++;
                    if (fileCount > 1) {
                        throw new IllegalArgumentException("More than one kafka state file in " + directoryPath);
                    }
                    targetFile = file;
                }

                if (targetFile == null) {
                    LOG.warn("No kafka state file found in {}", directoryPath);
                    return Optional.empty();
                }
                String content = Files.readString(targetFile).trim();
                if (content.isEmpty()) {
                    LOG.warn("Kafka state file {} is empty", targetFile);
                    return Optional.empty();
                }

                ObjectMapper mapper = new ObjectMapper();
                Map state = mapper.readValue(content, Map.class);
                LOG.info("File content: {}", content);
                Object offsetObj = state.get("offset");
                if (offsetObj instanceof Number) {
                    return Optional.of(((Number) offsetObj).intValue());
                } else if (offsetObj != null) {
                    try {
                        return Optional.of(Integer.parseInt(offsetObj.toString()));
                    }
                    catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                }
                return Optional.empty();

            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Reads the ZoneDateTime time from a JSON file
     * @param filePath path to the info file
     * @param fieldName name of the time field (start-time, end-time)
     */
    public static Optional<ZonedDateTime> readTime(String filePath, String fieldName) {
        try {
            String content = Files.readString(Path.of(filePath));
            JsonNode rootNode = OBJECT_MAPPER.readTree(content);
            if (!rootNode.has(fieldName)) {
                return Optional.empty();
            }
            String dateTimeString = rootNode.get(fieldName).asText();
            return Optional.of(ZonedDateTime.parse(dateTimeString));
        } catch (IOException ex) {
            LOG.warn("Error reading file: {}", ex.getMessage());
            return Optional.empty();
        } catch (DateTimeParseException ex) {
            LOG.warn("Invalid date format in field '{}': {}", fieldName, ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            LOG.warn("Unexpected error: {}", ex.getMessage());
            return Optional.empty();
        }
    }

}
