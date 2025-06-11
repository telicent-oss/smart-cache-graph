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

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.riot.WebContent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static io.telicent.backup.utils.BackupUtils.*;
import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TestBackupUtils {

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setup() {
        dirBackups = null;
        Configurator.reset();
    }

    @AfterEach
    public void cleanup() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    private final String EXPECTED_DEFAULT_DIR = System.getenv("PWD") + "/backups";

    private void setBackUpDirProperty(String value) {
        Properties properties = new Properties();
        properties.put(ENV_BACKUPS_DIR, value);
        Configurator.addSource(new PropertiesSource(properties));
    }

    @Test
    @DisplayName("Tests that generateBackUpDirPath creates the default backup directory if not specified")
    public void test_generateBackUpDirPath_defaultBackupDir() {
        // given
        // when
        String dirPath = generateBackUpDirPath();
        // then
        assertTrue(new File(dirPath).exists());
    }

    @Test
    @DisplayName("Tests that generateBackUpDirPath uses the default backup directory when an empty property is set")
    public void test_generateBackUpDirPath_emptyProperty() {
        // given
        setBackUpDirProperty("");
        // when
        String dirPath = generateBackUpDirPath();
        // then
        assertEquals(EXPECTED_DEFAULT_DIR, dirPath);
        assertTrue(new File(dirPath).exists());
    }

    @Test
    @DisplayName("Tests that generateBackUpDirPath uses the default backup directory when an invalid property is set")
    public void test_generateBackUpDirPath_invalidProperty() {
        // given
        setBackUpDirProperty("/non/existent/directory");
        // when
        String dirPath = generateBackUpDirPath();
        // then
        assertEquals(EXPECTED_DEFAULT_DIR, dirPath);
        assertTrue(new File(dirPath).exists());
    }

    @Test
    @DisplayName("Tests that generateBackUpDirPath returns an existing backup directory path")
    public void test_generateBackUpDirPath_existingBackupDir() throws IOException {
        // given
        Path tempBackupDir = Files.createTempDirectory(tempDir, "test_backup_dir_existing");
        tempBackupDir.toFile().deleteOnExit();
        setBackUpDirProperty(tempBackupDir.toString());
        // when
        String dirPath = generateBackUpDirPath();
        // then
        assertEquals(tempBackupDir.toString(), dirPath);
    }

    @Test
    @DisplayName("Tests that generateBackUpDirPath creates a new backup directory if it doesn't exist")
    public void test_generateBackUpDirPath_createBackupDir() {
        // given
        Path newBackupDir = tempDir.resolve("new_backup_dir");
        setBackUpDirProperty(newBackupDir.toString());

        // when
        String dirPath = generateBackUpDirPath();

        // then
        assertEquals(newBackupDir.toString(), dirPath);
        assertTrue(Files.exists(newBackupDir));
    }

    @Test
    @DisplayName("Tests that getBackUpDir generates and returns the path on the first call")
    public void test_getBackUpDir_firstCallGeneratesPath() {
        // given
        assertNull(dirBackups);
        // when
        String dirPath = getBackUpDir();
        // then
        assertNotNull(dirBackups);
        assertEquals(dirPath, dirBackups);
        assertTrue(new File(dirPath).exists());
    }

    @Test
    @DisplayName("Tests that getBackUpDir returns the cached path on subsequent calls")
    public void test_getBackUpDir_subsequentCallsReturnCachedPath() {
        // given
        String firstCallPath = getBackUpDir();
        // when
        String secondCallPath = getBackUpDir();
        // then
        assertEquals(firstCallPath, secondCallPath);
        assertEquals(firstCallPath, dirBackups);
    }

    @Test
    @DisplayName("Tests checkPathExistsAndIsDir with an existing directory")
    public void test_checkPathExistsAndIsDir_existingDirectory() throws IOException {
        // given
        // when
        Path testDir = Files.createDirectory(tempDir.resolve("test_dir"));
        // then
        assertTrue(checkPathExistsAndIsDir(testDir.toAbsolutePath().toString()));
    }

    @Test
    @DisplayName("Tests checkPathExistsAndIsDir with a non-existent directory")
    public void test_checkPathExistsAndIsDir_nonexistentDirectory() {
        // given
        // when
        // then
        assertFalse(checkPathExistsAndIsDir("nonexistent_dir"));
    }

    @Test
    @DisplayName("Tests checkPathExistsAndIsDir with an existing file")
    public void test_checkPathExistsAndIsDir_existingFile() throws IOException {
        // given
        // when
        Path testFile = Files.createFile(tempDir.resolve("test_file.txt"));
        // then
        assertFalse(checkPathExistsAndIsDir(testFile.toAbsolutePath().toString()));
    }

    @Test
    @DisplayName("Tests checkPathExistsAndIsDir with a null path")
    public void test_checkPathExistsAndIsDir_nullPath() {
        // given
        // when
        // then
        assertFalse(checkPathExistsAndIsDir(null));
    }

    @Test
    @DisplayName("Tests checkPathExistsAndIsDir with an empty path")
    public void test_checkPathExistsAndIsDir_emptyPath() {
        // given
        // when
        // then
        assertFalse(checkPathExistsAndIsDir(""));
    }


    @Test
    @DisplayName("Tests checkPathExistsAndIsFile with a null path")
    public void test_checkPathExistsAndIsFile_nullPath() {
        // given
        // when
        // then
        assertFalse(checkPathExistsAndIsFile(null));
    }

    @Test
    @DisplayName("Tests checkPathExistsAndIsFile with an empty path")
    public void test_checkPathExistsAndIsFile_emptyPath() {
        // given
        // when
        // then
        assertFalse(checkPathExistsAndIsFile(""));
    }

    @Test
    @DisplayName("Tests checkPathExistsAndIsFile with a root slash path")
    public void test_checkPathExistsAndIsFile_slashPath() {
        // given
        // when
        // then
        assertFalse(checkPathExistsAndIsFile("/"));
    }

    @Test
    @DisplayName("Tests checkPathExistsAndIsFile with an existing file")
    public void test_checkPathExistsAndIsFile_existingFile() throws IOException {
        // given
        // when
        Path testFile = Files.createFile(tempDir.resolve("temp2.txt"));
        // then
        assertTrue(checkPathExistsAndIsFile(testFile.toAbsolutePath().toString()));
    }

    @Test
    @DisplayName("Tests checkPathExistsAndIsFile with a non-existent file")
    public void test_checkPathExistsAndIsFile_nonexistentFile() {
        // given
        // when
        // then
        assertFalse(checkPathExistsAndIsFile("nonexistent_file.txt"));
    }

    @Test
    @DisplayName("Tests checkPathExistsAndIsFile with an existing directory")
    public void test_checkPathExistsAndIsFile_existingDirectory() throws IOException {
        // given
        // when
        Path testDir = Files.createDirectory(tempDir.resolve("temp2_dir"));
        // then
        assertFalse(checkPathExistsAndIsFile(testDir.toAbsolutePath().toString()));
    }

    @Test
    @DisplayName("Tests requestIsEmpty with a null request name")
    public void test_requestIsEmpty_NullRequestName() {
        // given
        // when
        // then
        assertTrue(requestIsEmpty(null));
    }

    @Test
    @DisplayName("Tests requestIsEmpty with empty or blank request names")
    public void test_requestIsEmpty_EmptyRequestName() {
        // given
        // when
        // then
        assertTrue(requestIsEmpty(""));
        assertTrue(requestIsEmpty(" "));
        assertTrue(requestIsEmpty("   "));
    }

    @Test
    @DisplayName("Tests requestIsEmpty with a slash request name")
    public void test_requestIsEmpty_SlashRequestName() {
        // given
        // when
        // then
        assertTrue(requestIsEmpty("/"));
        assertTrue(requestIsEmpty(" / "));
    }

    @Test
    @DisplayName("Tests requestIsEmpty with a non-empty request name")
    public void test_requestIsEmpty_NonEmptyRequestName() {
        // given
        // when
        // then
        assertFalse(requestIsEmpty("test"));
        assertFalse(requestIsEmpty("test/"));
        assertFalse(requestIsEmpty("/test"));
    }

    @Test
    @DisplayName("Tests processResponse for a successful JSON response")
    public void test_processResponse_successfulResponse() throws IOException {
        // given
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);

        ObjectNode jsonResponse = OBJECT_MAPPER.createObjectNode();
        jsonResponse.put("key", "value");

        when(response.getOutputStream()).thenReturn(outputStream);

        // when
        processResponse(response, jsonResponse);

        // then
        verify(response).setContentType(WebContent.contentTypeJSON);
        verify(response).setCharacterEncoding(WebContent.charsetUTF8);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Tests processResponse when an IOException occurs during writing")
    public void test_processResponse_IOException() throws IOException {
        // given
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);

        ObjectNode jsonResponse = OBJECT_MAPPER.createObjectNode();
        jsonResponse.put("key", "value");

        when(response.getOutputStream()).thenReturn(outputStream);
        doThrow(new IOException("error")).when(outputStream).print(anyString());

        // when
        processResponse(response, jsonResponse);

        // then
        verify(response).setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("Tests deleteDirectoryRecursively with an empty directory")
    public void test_deleteDirectoryRecursively_deleteEmptyDirectory() throws IOException {
        // given
        Path tempDirForTest = Files.createTempDirectory(tempDir, "test_empty_dir_1");
        File directory = tempDirForTest.toFile();
        // when
        deleteDirectoryRecursively(directory);
        // then
        assertFalse(directory.exists());
    }

    @Test
    @DisplayName("Tests deleteDirectoryRecursively with a non-empty directory")
    public void test_deleteDirectoryRecursively_deleteNonEmptyDirectory() throws IOException {
        // given
        Path tempDirForTest = Files.createTempDirectory(tempDir, "test_empty_dir_2");
        File directory = tempDirForTest.toFile();

        File file1 = new File(directory, "file1.txt");
        assertTrue(file1.createNewFile());

        File file2 = new File(directory, "file2.txt");
        assertTrue(file2.createNewFile());

        File subDir = new File(directory, "subDir");
        assertTrue(subDir.mkdir());
        File subFile = new File(subDir, "subFile.txt");
        assertTrue(subFile.createNewFile());

        // when
        deleteDirectoryRecursively(directory);

        // then
        assertFalse(directory.exists());
        assertFalse(file1.exists());
        assertFalse(file2.exists());
        assertFalse(subDir.exists());
        assertFalse(subFile.exists());
    }

    @Test
    @DisplayName("Tests deleteDirectoryRecursively with a file (should delete the file)")
    public void test_deleteDirectoryRecursively_deleteFile() throws IOException {
        // given
        Path tempDirForTest = Files.createTempDirectory(tempDir, "test_empty_dir_3");
        File file = tempDirForTest.resolve("temp.txt").toFile();
        assertTrue(file.createNewFile());

        // when
        deleteDirectoryRecursively(file);

        // then
        assertFalse(file.exists());
    }

    @Test
    @DisplayName("Tests deleteDirectoryRecursively with a null directory path")
    public void test_deleteDirectoryRecursively_deleteNullDirectory() {
        // given
        // when
        // then
        deleteDirectoryRecursively((String) null);
    }

    @Test
    @DisplayName("Tests deleteDirectoryRecursively with a non-existent directory")
    public void test_deleteDirectoryRecursively_nonExistentDirectory() {
        // given
        // when
        // then
        deleteDirectoryRecursively(new File("/does/not/exist/surely"));
    }

    @Test
    @DisplayName("Tests getNextDirectoryNumberAndCreate with an empty directory")
    public void test_getNextDirectoryNumberAndCreate_emptyDirectory() throws IOException {
        // given
        Path parentDir = Files.createDirectory(tempDir.resolve("next_and_create_empty"));
        // when
        int nextNum = getNextDirectoryNumberAndCreate(parentDir.toAbsolutePath().toString());
        // then
        assertEquals(1, nextNum);
        assertTrue(Files.exists(parentDir.resolve("1")));
        assertTrue(Files.isDirectory(parentDir.resolve("1")));
    }

    @Test
    @DisplayName("Tests getNextDirectoryNumberAndCreate with existing numbered directories")
    public void test_getNextDirectoryNumberAndCreate_withExistingDirectories() throws IOException {
        // given
        Path parentDir = Files.createDirectory(tempDir.resolve("next_and_create_existing"));
        Files.createDirectory(parentDir.resolve("1"));
        Files.createDirectory(parentDir.resolve("5"));
        // when
        int nextNum = getNextDirectoryNumberAndCreate(parentDir.toAbsolutePath().toString());
        // then
        assertEquals(6, nextNum);
        assertTrue(Files.exists(parentDir.resolve("6")));
        assertTrue(Files.isDirectory(parentDir.resolve("6")));
    }

    @Test
    @DisplayName("Tests getNextDirectoryNumberAndCreate with a null path")
    public void test_getNextDirectoryNumberAndCreate_nullPath() {
        // given
        // when
        // then
        assertEquals(-1, getNextDirectoryNumberAndCreate(null));
    }

    @Test
    @DisplayName("Tests getNextDirectoryNumberAndCreate when directory creation fails")
    public void test_getNextDirectoryNumberAndCreate_creationFails() {
        // given
        try (MockedStatic<BackupUtils> mocked = Mockito.mockStatic(BackupUtils.class, CALLS_REAL_METHODS)) {
            mocked.when(() -> BackupUtils.createPathIfNotExists(anyString())).thenReturn(true).thenReturn(false); // First call for parent succeeds, second for child fails
            // when
            int actual = getNextDirectoryNumberAndCreate("/temp/path/that/fails/creation");
            // then
            assertEquals(-1, actual);
        }
    }

    @Test
    @DisplayName("Tests getNextDirectoryNumberAndCreate when getting the next number fails")
    public void test_getNextDirectoryNumberAndCreate_getNextNumberFails() {
        // given
        try (MockedStatic<BackupUtils> mocked = Mockito.mockStatic(BackupUtils.class, CALLS_REAL_METHODS)) {
            // when
            int actual = getNextDirectoryNumberAndCreate("/some/path");
            // then
            assertEquals(-1, actual);
        }
    }

    @Test
    @DisplayName("Tests createPathIfNotExists with a null path")
    public void test_createPathIfNotExists_nullPath() {
        // given
        // when
        // then
        assertFalse(createPathIfNotExists(null));
    }

    @Test
    @DisplayName("Tests createPathIfNotExists when the path already exists")
    public void test_createPathIfNotExists_pathAlreadyExists() throws IOException {
        // given
        Path existingDir = Files.createDirectory(tempDir.resolve("existing_path"));
        // when
        // then
        assertTrue(createPathIfNotExists(existingDir.toAbsolutePath().toString()));
    }

    @Test
    @DisplayName("Tests createPathIfNotExists to create a new path")
    public void test_createPathIfNotExists_createNewPath() {
        // given
        Path newDir = tempDir.resolve("new_path");
        // when
        // then
        assertFalse(Files.exists(newDir));
        assertTrue(createPathIfNotExists(newDir.toAbsolutePath().toString()));
        assertTrue(Files.exists(newDir));
        assertTrue(Files.isDirectory(newDir));
    }

    @Test
    @DisplayName("Tests createPathIfNotExists to create nested paths")
    public void test_createPathIfNotExists_createNestedPaths() {
        // given
        Path newNestedDir = tempDir.resolve("new_parent").resolve("new_child");
        // when
        // then
        assertFalse(Files.exists(newNestedDir));
        assertTrue(createPathIfNotExists(newNestedDir.toAbsolutePath().toString()));
        assertTrue(Files.exists(newNestedDir));
        assertTrue(Files.isDirectory(newNestedDir));
    }

    @Test
    @DisplayName("Tests getSubdirectoryNames with a non-existent directory")
    public void test_getSubdirectoryNames_nonExistentDirectory() {
        // given
        List<String> results = getSubdirectoryNames("/Folder/does/not/exist");
        // when
        // then
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Tests getSubdirectoryNames with a path that is not a directory")
    public void test_getSubdirectoryNames_notDirectory() throws IOException {
        // given
        Path tempFile = Files.createFile(tempDir.resolve("temp_file_for_subdir_test.txt"));
        // when
        List<String> results = getSubdirectoryNames(tempFile.toAbsolutePath().toString());
        // then
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Tests getSubdirectoryNames with an empty directory")
    public void test_getSubdirectoryNames_emptyDirectory() throws IOException {
        // given
        Path testDir = Files.createDirectory(tempDir.resolve("empty_subdir_test"));
        // when
        List<String> results = getSubdirectoryNames(testDir.toAbsolutePath().toString());
        // then
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Tests getSubdirectoryNames with a populated directory")
    public void test_getSubdirectoryNames_populatedDirectory() throws IOException {
        // given
        Path testDir = Files.createDirectory(tempDir.resolve("populated_subdir_test"));
        Files.createDirectory(testDir.resolve("sub1"));
        Files.createDirectory(testDir.resolve("sub2"));
        Files.createFile(testDir.resolve("file.txt"));
        // when
        List<String> results = getSubdirectoryNames(testDir.toAbsolutePath().toString());
        // then
        assertEquals(2, results.size());
        assertTrue(results.contains("sub1"));
        assertTrue(results.contains("sub2"));
    }

    @Test
    @DisplayName("Tests populateNodeFromDir with a null file input")
    public void test_populateNodeFromDir_nullFile() {
        // given
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        // when
        populateNodeFromDir(null, node);
        // then
        assertTrue(node.isEmpty());
    }

    @Test
    @DisplayName("Tests populateNodeFromDir with a non-existent directory")
    public void test_populateNodeFromDir_nonExistentDirectory() {
        // given
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        // when
        populateNodeFromDir(new File("/non/existent/dir_for_populate"), node);
        // then
        assertTrue(node.isEmpty());
    }

    @Test
    @DisplayName("Tests populateNodeFromDir with a path that is not a directory")
    public void test_populateNodeFromDir_notDirectory() throws IOException {
        // given
        Path tempFile = Files.createFile(tempDir.resolve("test_file_for_populate"));
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        // when
        populateNodeFromDir(tempFile.toFile(), node);
        // then
        assertTrue(node.isEmpty());
    }

    @Test
    @DisplayName("Tests populateNodeFromDir with an empty directory")
    public void test_populateNodeFromDir_emptyDirectory() throws IOException {
        // given
        Path testDir = Files.createDirectory(tempDir.resolve("empty_dir_populate"));
        // when
        ObjectNode resultNode = populateNodeFromDir(testDir.toAbsolutePath().toString());
        // then
        assertNotNull(resultNode);
        assertTrue(resultNode.isEmpty());
    }

    @Test
    @DisplayName("Tests populateNodeFromDir with files and subdirectories")
    public void test_populateNodeFromDir_withFilesAndSubdirectories() throws IOException {
        // given
        Path rootPath = Files.createDirectory(tempDir.resolve("root_dir"));
        Path subDir1 = Files.createDirectory(rootPath.resolve("sub1"));
        Files.createFile(subDir1.resolve("fileA.txt"));
        Files.createFile(subDir1.resolve("fileB.txt"));
        Path subDir2 = Files.createDirectory(rootPath.resolve("sub2"));
        Files.createFile(subDir2.resolve("fileC.json"));
        Files.createFile(rootPath.resolve("rootFile.txt"));

        // when
        ObjectNode resultNode = populateNodeFromDir(rootPath.toAbsolutePath().toString());

        // then
        assertNotNull(resultNode);
        assertTrue(resultNode.has("sub1"));
        assertTrue(resultNode.has("sub2"));
        assertTrue(resultNode.has("files")); // Files directly in root_dir

        ObjectNode sub1Node = (ObjectNode) resultNode.get("sub1");
        assertNotNull(sub1Node);
        assertTrue(sub1Node.has("files"));
        assertEquals(2, sub1Node.get("files").size());
        assertTrue(sub1Node.get("files").get(0).asText().equals("fileA.txt") || sub1Node.get("files").get(0).asText().equals("fileB.txt"));

        ObjectNode sub2Node = (ObjectNode) resultNode.get("sub2");
        assertNotNull(sub2Node);
        assertTrue(sub2Node.has("files"));
        assertEquals(1, sub2Node.get("files").size());
        assertEquals("fileC.json", sub2Node.get("files").get(0).asText());

        assertTrue(resultNode.get("files").isArray());
        assertEquals(1, resultNode.get("files").size());
        assertEquals("rootFile.txt", resultNode.get("files").get(0).asText());
    }

    @Test
    @DisplayName("Tests populateNodeFromDirNumerically with an empty directory")
    public void test_populateNodeFromDirNumerically_emptyDirectory() throws IOException {
        // given
        Path testDir = Files.createDirectory(tempDir.resolve("empty_numeric_populate"));
        // when
        ObjectNode resultNode = populateNodeFromDirNumerically(testDir.toAbsolutePath().toString());
        // then
        assertNotNull(resultNode);
        assertTrue(resultNode.isEmpty());
    }

    @Test
    @DisplayName("Tests populateNodeFromDirNumerically with numbered subdirectories and files")
    public void test_populateNodeFromDirNumerically_withNumberedSubdirectories() throws IOException {
        // given
        Path rootPath = Files.createDirectory(tempDir.resolve("numeric_dir"));
        Files.createDirectory(rootPath.resolve("10"));
        Files.createDirectory(rootPath.resolve("2"));
        Files.createDirectory(rootPath.resolve("1"));
        Files.createDirectory(rootPath.resolve("abc")); // Non-numeric

        Files.createFile(rootPath.resolve("file.txt"));
        Files.createFile(rootPath.resolve("file2.txt"));

        // when
        ObjectNode resultNode = populateNodeFromDirNumerically(rootPath.toAbsolutePath().toString());

        // then
        assertNotNull(resultNode);
        assertTrue(resultNode.has("1"));
        assertTrue(resultNode.has("2"));
        assertTrue(resultNode.has("10"));
        assertTrue(resultNode.has("abc"));
        assertTrue(resultNode.has("files"));

        assertEquals(2, resultNode.get("files").size());
        assertTrue(resultNode.get("files").get(0).asText().equals("file.txt") || resultNode.get("files").get(0).asText().equals("file2.txt"));
    }

    @Test
    @DisplayName("Tests populateNodeFromDirNumerically with a non-existent directory")
    public void test_populateNodeFromDirNumerically_nonExistentDirectory() {
        // given
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        // when
        populateNodeFromDirNumerically(new File("/non/existent/numeric/dir"), node);
        // then
        assertTrue(node.isEmpty());
    }

    @Test
    @DisplayName("Tests populateNodeFromDirNumerically when listFiles returns null")
    public void test_populateNodeFromDirNumerically_nullFileArray() {
        // given
        File mockDir = mock(File.class);
        when(mockDir.listFiles()).thenReturn(null);
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        // when
        populateNodeFromDirNumerically(mockDir, node);
        // then
        assertTrue(node.isEmpty());
    }

    @Test
    @DisplayName("Tests getNumberedFilesBySuffix with an empty directory")
    public void test_getNumberedFilesBySuffix_emptyDirectory() throws IOException {
        // given
        Path testDir = Files.createDirectory(tempDir.resolve("empty_suffix_dir"));
        // when
        List<Path> files = getNumberedFilesBySuffix(testDir.toAbsolutePath().toString(), "_info.json");
        // then
        assertTrue(files.isEmpty());
    }

    @Test
    @DisplayName("Tests getNumberedFilesBySuffix with no matching files")
    public void test_getNumberedFilesBySuffix_noMatchingFiles() throws IOException {
        // given
        Path testDir = Files.createDirectory(tempDir.resolve("no_match_suffix_dir"));
        Files.createFile(testDir.resolve("1_data.txt"));
        Files.createFile(testDir.resolve("other_info.json"));
        // when
        List<Path> files = getNumberedFilesBySuffix(testDir.toAbsolutePath().toString(), "_info.json");
        // then
        assertTrue(files.isEmpty());
    }

    @Test
    @DisplayName("Tests getNumberedFilesBySuffix with matching numbered files")
    public void test_getNumberedFilesBySuffix_withMatchingFiles() throws IOException {
        // given
        Path testDir = Files.createDirectory(tempDir.resolve("match_suffix_dir"));
        Files.createFile(testDir.resolve("1_info.json"));
        Files.createFile(testDir.resolve("10_info.json"));
        Files.createFile(testDir.resolve("2_info.json"));
        Files.createFile(testDir.resolve("non_numeric_info.json")); // Should be ignored
        Files.createFile(testDir.resolve("1_other.txt")); // Should be ignored

        // when
        List<Path> files = getNumberedFilesBySuffix(testDir.toAbsolutePath().toString(), "_info.json");

        // then
        assertEquals(3, files.size());
        assertEquals("1_info.json", files.get(0).getFileName().toString());
        assertEquals("2_info.json", files.get(1).getFileName().toString());
        assertEquals("10_info.json", files.get(2).getFileName().toString());
    }

    @Test
    @DisplayName("Tests getNumberedFilesBySuffix with a non-existent directory")
    public void test_getNumberedFilesBySuffix_nonExistentDirectory() {
        // given
        // when
        List<Path> files = getNumberedFilesBySuffix("/non/existent/path/for/suffix", "_info.json");
        // then
        assertTrue(files.isEmpty());
    }

    @Test
    @DisplayName("Tests getNumberedFilesBySuffix with a null directory path")
    public void test_getNumberedFilesBySuffix_nullDirectoryPath() {
        // given
        // when
        List<Path> files = getNumberedFilesBySuffix(null, "_info.json");
        // then
        assertTrue(files.isEmpty());
    }

    @Test
    @DisplayName("Tests getNumberedFilesBySuffix with a null suffix")
    public void test_getNumberedFilesBySuffix_nullSuffix() {
        // given
        Path testDir = tempDir;
        // when
        List<Path> files = getNumberedFilesBySuffix(testDir.toAbsolutePath().toString(), null);
        // then
        assertTrue(files.isEmpty());
    }

    @Test
    @DisplayName("Tests getNumberedFilesBySuffix with an empty suffix")
    public void test_getNumberedFilesBySuffix_emptySuffix() {
        // given
        Path testDir = tempDir;
        // when
        List<Path> files = getNumberedFilesBySuffix(testDir.toAbsolutePath().toString(), "");
        // then
        assertTrue(files.isEmpty());
    }

    @Test
    @DisplayName("Tests getNumberedFilesBySuffix when the path is a file, not a directory")
    public void test_getNumberedFilesBySuffix_pathIsFile() throws IOException {
        // given
        Path testFile = Files.createFile(tempDir.resolve("a_file.txt"));
        // when
        List<Path> files = getNumberedFilesBySuffix(testFile.toAbsolutePath().toString(), ".txt");
        // then
        assertTrue(files.isEmpty());
    }

    @Test
    @DisplayName("Tests getNumberedFilesBySuffix with a directory containing no files")
    public void test_getNumberedFilesBySuffix_directoryWithNoFiles() throws IOException {
        // given
        Path testDir = Files.createDirectory(tempDir.resolve("dir_no_files"));
        Files.createDirectory(testDir.resolve("subdir"));
        // when
        List<Path> files = getNumberedFilesBySuffix(testDir.toAbsolutePath().toString(), "_info.json");
        // then
        assertTrue(files.isEmpty());
    }

    @Test
    @DisplayName("Tests populateObjectNodeFromNumberedFiles successfully populates the node")
    public void test_populateObjectNodeFromNumberedFiles_success() throws IOException {
        // given
        Path testDir = Files.createDirectory(tempDir.resolve("populate_node_success"));
        Files.writeString(testDir.resolve("1_data.json"), "{\"name\": \"item1\"}");
        Files.writeString(testDir.resolve("3_data.json"), "{\"value\": 123}");
        Files.writeString(testDir.resolve("2_data.json"), "{\"status\": \"active\"}");

        ObjectNode targetNode = OBJECT_MAPPER.createObjectNode();
        // when
        populateObjectNodeFromNumberedFiles(targetNode, testDir.toAbsolutePath().toString(), "_data.json");

        // then
        assertNotNull(targetNode);
        assertTrue(targetNode.has("1"));
        assertTrue(targetNode.has("2"));
        assertTrue(targetNode.has("3"));
        assertEquals("item1", targetNode.get("1").get("name").asText());
        assertEquals("active", targetNode.get("2").get("status").asText());
        assertEquals(123, targetNode.get("3").get("value").asInt());
    }

    @Test
    @DisplayName("Tests populateObjectNodeFromNumberedFiles with an empty directory")
    public void test_populateObjectNodeFromNumberedFiles_emptyDirectory() throws IOException {
        // given
        Path testDir = Files.createDirectory(tempDir.resolve("populate_node_empty"));
        ObjectNode targetNode = OBJECT_MAPPER.createObjectNode();
        // when
        populateObjectNodeFromNumberedFiles(targetNode, testDir.toAbsolutePath().toString(), "_data.json");
        // then
        assertTrue(targetNode.isEmpty());
    }

    @Test
    @DisplayName("Tests populateObjectNodeFromNumberedFiles with non-matching files")
    public void test_populateObjectNodeFromNumberedFiles_nonMatchingFiles() throws IOException {
        // given
        Path testDir = Files.createDirectory(tempDir.resolve("populate_node_no_match"));
        Files.writeString(testDir.resolve("1.json"), "{}");
        Files.writeString(testDir.resolve("abc_data.json"), "{}");
        ObjectNode targetNode = OBJECT_MAPPER.createObjectNode();
        // when
        populateObjectNodeFromNumberedFiles(targetNode, testDir.toAbsolutePath().toString(), "_data.json");
        // then
        assertTrue(targetNode.isEmpty());
    }

    @Test
    @DisplayName("Tests populateObjectNodeFromNumberedFiles with a null target node (should throw NullPointerException)")
    public void test_populateObjectNodeFromNumberedFiles_nullTargetNode() {
        // given
        // when
        // then
        assertThrows(NullPointerException.class, () -> populateObjectNodeFromNumberedFiles(null, tempDir.toAbsolutePath().toString(), "_data.json"));
    }

@Test
    @DisplayName("Tests populateObjectNodeFromNumberedFiles with a null directory path (should throw NullPointerException)")
    public void test_populateObjectNodeFromNumberedFiles_nullDirectoryPath() {
        // given
        // when
        // then
        assertThrows(NullPointerException.class, () -> populateObjectNodeFromNumberedFiles(OBJECT_MAPPER.createObjectNode(), null, "_data.json"));
    }

    @Test
    @DisplayName("Tests populateObjectNodeFromNumberedFiles with a null suffix (should throw NullPointerException)")
    public void test_populateObjectNodeFromNumberedFiles_nullSuffix() {
        // given
        // when
        // then
        assertThrows(NullPointerException.class, () -> populateObjectNodeFromNumberedFiles(OBJECT_MAPPER.createObjectNode(), tempDir.toAbsolutePath().toString(), null));
    }

    @Test
    @DisplayName("Tests populateObjectNodeFromNumberedFiles with invalid JSON content (should throw IOException)")
    public void test_populateObjectNodeFromNumberedFiles_invalidJson() throws IOException {
        // given
        Path testDir = Files.createDirectory(tempDir.resolve("populate_node_invalid_json"));
        Files.writeString(testDir.resolve("1_data.json"), "{invalid json");
        ObjectNode targetNode = OBJECT_MAPPER.createObjectNode();
        // when
        // then
        assertThrows(IOException.class, () -> populateObjectNodeFromNumberedFiles(targetNode, testDir.toAbsolutePath().toString(), "_data.json"));
        assertTrue(targetNode.isEmpty());
    }

    @Test
    @DisplayName("Tests populateObjectNodeFromNumberedFiles when an IOException occurs during file read")
    public void test_populateObjectNodeFromNumberedFiles_ioExceptionDuringRead() throws IOException {
        Path testDir = Files.createDirectory(tempDir.resolve("populate_node_read_error"));
        Path filePath = testDir.resolve("1_data.json");
        Files.writeString(filePath, "{\"key\": \"value\"}");

        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class, CALLS_REAL_METHODS)) {
            mockedFiles.when(() -> Files.readString(filePath)).thenThrow(new IOException("Simulated read error"));

            ObjectNode targetNode = OBJECT_MAPPER.createObjectNode();
            assertThrows(IOException.class, () -> populateObjectNodeFromNumberedFiles(targetNode, testDir.toAbsolutePath().toString(), "_data.json"));
            assertTrue(targetNode.isEmpty());
        }
    }

    @Test
    @DisplayName("Tests getObjectNodeFromNumberedFiles successfully retrieves and populates the node")
    public void test_getObjectNodeFromNumberedFiles_success() throws IOException {
        // given
        Path testDir = Files.createDirectory(tempDir.resolve("get_node_success"));
        Files.writeString(testDir.resolve("10_event.json"), "{\"id\": 10}");
        Files.writeString(testDir.resolve("5_event.json"), "{\"type\": \"start\"}");

        // when
        ObjectNode resultNode = getObjectNodeFromNumberedFiles(testDir.toAbsolutePath().toString(), "_event.json");

        // then
        assertNotNull(resultNode);
        assertTrue(resultNode.has("5"));
        assertTrue(resultNode.has("10"));
        assertEquals("start", resultNode.get("5").get("type").asText());
        assertEquals(10, resultNode.get("10").get("id").asInt());
    }

    @Test
    @DisplayName("Tests getObjectNodeFromNumberedFiles propagates IOException as RuntimeException")
    public void test_getObjectNodeFromNumberedFiles_ioExceptionPropagatedAsRuntimeException() throws IOException {
        // given
        Path testDir = Files.createDirectory(tempDir.resolve("get_node_io_error"));
        Path filePath = testDir.resolve("1_item.json");
        Files.writeString(filePath, "{\"key\": \"value\"}");
        // when
        // then
        try (MockedStatic<BackupUtils> mockedBackupUtils = Mockito.mockStatic(BackupUtils.class, CALLS_REAL_METHODS)) {
            mockedBackupUtils.when(() -> BackupUtils.populateObjectNodeFromNumberedFiles(any(ObjectNode.class), anyString(), anyString()))
                    .thenThrow(new IOException("Simulated IO exception for get"));

            assertThrows(RuntimeException.class, () -> getObjectNodeFromNumberedFiles(testDir.toAbsolutePath().toString(), "_item.json"));
        }
    }

    @Test
    @DisplayName("Tests handleError successfully handles an exception and sets response status/content")
    public void test_handleError_successfulHandling() throws IOException {
        // given
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(outputStream);

        ObjectNode jsonResponse = OBJECT_MAPPER.createObjectNode();
        Exception exception = new RuntimeException("Test error message");

        // when
        handleError(response, jsonResponse, exception);

        // then
        verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertTrue(jsonResponse.has("error"));
        assertEquals("Test error message", jsonResponse.get("error").asText());
        verify(response).setContentType(WebContent.contentTypeJSON);
        verify(response).setCharacterEncoding(WebContent.charsetUTF8);
    }

    @Test
    @DisplayName("Tests handleError when an IOException occurs during processing the error response")
    public void test_handleError_ioExceptionDuringProcessResponse() throws IOException {
        // given
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(outputStream);
        doThrow(new IOException("Output error")).when(outputStream).print(anyString());

        ObjectNode jsonResponse = OBJECT_MAPPER.createObjectNode();
        Exception exception = new RuntimeException("Another test error");

        // when
        handleError(response, jsonResponse, exception);

        // then
        verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertTrue(jsonResponse.has("error"));
        assertEquals("Another test error", jsonResponse.get("error").asText());
    }

    @Test
    @DisplayName("Tests cleanUp with a single file")
    public void test_cleanUp_singleFile() throws IOException {
        // given
        Path fileToClean = Files.createFile(tempDir.resolve("file_to_clean.txt"));
        List<Path> paths = Collections.singletonList(fileToClean);
        // when
        cleanUp(paths);
        // then
        assertFalse(Files.exists(fileToClean));
    }

    @Test
    @DisplayName("Tests cleanUp with multiple files and directories")
    public void test_cleanUp_multipleFilesAndDirectories() throws IOException {
        // given
        Path file1 = Files.createFile(tempDir.resolve("file1.txt"));
        Path dir1 = Files.createDirectory(tempDir.resolve("dir1"));
        Path file2 = Files.createFile(dir1.resolve("file2.txt"));
        Path dir2 = Files.createDirectory(tempDir.resolve("dir2"));
        Path subDir1 = Files.createDirectory(dir2.resolve("sub_dir1"));
        Path file3 = Files.createFile(subDir1.resolve("file3.txt"));

        List<Path> paths = Arrays.asList(file1, dir1, dir2);
        // when
        cleanUp(paths);

        // then
        assertFalse(Files.exists(file1));
        assertFalse(Files.exists(file2));
        assertFalse(Files.exists(dir1));
        assertFalse(Files.exists(file3));
        assertFalse(Files.exists(subDir1));
        assertFalse(Files.exists(dir2));
    }

    @Test
    @DisplayName("Tests cleanUp with a non-existent path")
    public void test_cleanUp_nonExistentPath() {
        // given
        Path nonExistentPath = tempDir.resolve("non_existent_file.txt");
        List<Path> paths = Collections.singletonList(nonExistentPath);
        // when
        cleanUp(paths);
        // then
        assertFalse(Files.exists(nonExistentPath));
    }

    @Test
    @DisplayName("Tests cleanUp with an empty list of paths")
    public void test_cleanUp_emptyList() {
        // given
        List<Path> paths = Collections.emptyList();
        // when
        // then
        cleanUp(paths);
    }
}