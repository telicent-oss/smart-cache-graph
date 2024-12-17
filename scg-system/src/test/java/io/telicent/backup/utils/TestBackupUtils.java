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
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.riot.WebContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static io.telicent.backup.utils.BackupUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

public class TestBackupUtils {

    @BeforeEach
    public void setup() {
        dirBackups = null;
        Configurator.reset();
    }

    private final String EXPECTED_DEFAULT_DIR = System.getenv("PWD") + "/backups";

    private void setBackUpDirProperty(String value) {
        Properties properties = new Properties();
        properties.put(ENV_BACKUPS_DIR, value);
        Configurator.addSource(new PropertiesSource(properties));
    }

    @Test
    public void test_generateBackUpDirPath_defaultBackupDir() {
        // given
        // when
        String dirPath = generateBackUpDirPath();
        // then
        assertTrue(new File(dirPath).exists());
    }

    @Test
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
    public void test_generateBackUpDirPath_invalidProperty() {
        // given
//        setBackUpDirProperty("?:|");
        setBackUpDirProperty("/non/existent/directory");
        // when
        String dirPath = generateBackUpDirPath();
        // then
        assertEquals(EXPECTED_DEFAULT_DIR, dirPath);
        assertTrue(new File(dirPath).exists());
    }

    @Test
    public void test_generateBackUpDirPath_existingBackupDir() throws IOException {
        // given
        Path tempDir = Files.createTempDirectory("test_backup_dir");
        tempDir.toFile().deleteOnExit();
        setBackUpDirProperty(tempDir.toString());
        // when
        String dirPath = generateBackUpDirPath();
        // then
        assertEquals(tempDir.toString(), dirPath);
    }

    @Test
    public void test_generateBackUpDirPath_createBackupDir() {
        // given
        String tempDir = System.getProperty("java.io.tmpdir") + "/new_backup_dir";
        setBackUpDirProperty(tempDir);

        // when
        String dirPath = generateBackUpDirPath();

        // then
        assertEquals(tempDir, dirPath);
        assertTrue(new File(tempDir).exists());
        new File(tempDir).deleteOnExit();
    }

    @Test
    public void test_checkPathExistsAndIsDir_existingDirectory() {
        File tempDir = new File("temp1_dir");
        tempDir.deleteOnExit();
        assertTrue(tempDir.mkdir());
        assertTrue(checkPathExistsAndIsDir(tempDir.getAbsolutePath()));
    }

    @Test
    public void test_checkPathExistsAndIsDir_nonexistentDirectory() {
        assertFalse(checkPathExistsAndIsDir("nonexistent_dir"));
    }

    @Test
    public void test_checkPathExistsAndIsDir_existingFile() throws IOException {
        File tempFile = new File("temp1.txt");
        tempFile.deleteOnExit();
        assertTrue(tempFile.createNewFile());
        assertFalse(checkPathExistsAndIsDir(tempFile.getAbsolutePath()));
    }

    @Test
    public void test_checkPathExistsAndIsDir_nullPath() {
        assertFalse(checkPathExistsAndIsDir(null));
    }

    @Test
    public void test_checkPathExistsAndIsDir_emptyPath() {
        assertFalse(checkPathExistsAndIsDir(""));
    }


    @Test
    public void test_checkPathExistsAndIsFile_nullPath() {
        assertFalse(checkPathExistsAndIsFile(null));
    }

    @Test
    public void test_checkPathExistsAndIsFile_emptyPath() {
        assertFalse(checkPathExistsAndIsFile(""));
    }

    @Test
    public void test_checkPathExistsAndIsFile_slashPath() {
        assertFalse(checkPathExistsAndIsFile("/"));
    }

    @Test
    public void test_checkPathExistsAndIsFile_existingFile() throws IOException {
        File tempFile = new File("temp2.txt");
        tempFile.deleteOnExit();
        assertTrue(tempFile.createNewFile());
        assertTrue(checkPathExistsAndIsFile(tempFile.getAbsolutePath()));
    }

    @Test
    public void test_checkPathExistsAndIsFile_nonexistentFile() {
        assertFalse(checkPathExistsAndIsFile("nonexistent_file.txt"));
    }

    @Test
    public void test_checkPathExistsAndIsFile_existingDirectory() {
        File tempDir = new File("temp2_dir");
        tempDir.deleteOnExit();
        assertTrue(tempDir.mkdir());
        assertFalse(checkPathExistsAndIsFile(tempDir.getAbsolutePath()));
    }

    @Test
    public void test_requestIsEmpty_NullRequestName() {
        assertTrue(requestIsEmpty(null));
    }

    @Test
    public void test_requestIsEmpty_EmptyRequestName() {
        assertTrue(requestIsEmpty(""));
        assertTrue(requestIsEmpty(" "));
        assertTrue(requestIsEmpty("   "));
    }

    @Test
    public void test_requestIsEmpty_SlashRequestName() {
        assertTrue(requestIsEmpty("/"));
        assertTrue(requestIsEmpty(" / "));
    }

    @Test
    public void test_requestIsEmpty_NonEmptyRequestName() {
        assertFalse(requestIsEmpty("test"));
        assertFalse(requestIsEmpty("test/"));
        assertFalse(requestIsEmpty("/test"));
    }

    @Test
    public void test_processResponse_successfulResponse() throws IOException {
        // given
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonResponse = mapper.createObjectNode();
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
    public void test_processResponse_IOException() throws IOException {
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonResponse = mapper.createObjectNode();
        jsonResponse.put("key", "value");

        when(response.getOutputStream()).thenReturn(outputStream);
        doThrow(new IOException("error")).when(outputStream).print(anyString());

        processResponse(response, jsonResponse);

        verify(response).setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
    }

    @Test
    public void test_deleteDirectoryRecursively_deleteEmptyDirectory() throws IOException {
        // given
        Path tempDir = Files.createTempDirectory("test_empty_dir_1");
        File directory = new File(tempDir.toString());
        // when
        deleteDirectoryRecursively(directory);
        // then
        assertFalse(directory.exists());
    }

    @Test
    public void test_deleteDirectoryRecursively_deleteNonEmptyDirectory() throws IOException {
        // given
        Path tempDir = Files.createTempDirectory("test_empty_dir_2");
        File directory = tempDir.toFile();

        File file1 = new File(directory, "file1.txt");
        assertTrue(file1.createNewFile());

        File file2 = new File(directory, "file2.txt");
        assertTrue(file2.createNewFile());

        // when
        deleteDirectoryRecursively(directory);

        // then
        assertFalse(directory.exists());
        assertFalse(file1.exists());
        assertFalse(file2.exists());
    }

    @Test
    public void test_deleteDirectoryRecursively_deleteFile() throws IOException {
        // given
        Path tempDir = Files.createTempDirectory("test_empty_dir_3");
        File file = tempDir.resolve("temp.txt").toFile();
        assertTrue(file.createNewFile());

        // when
        deleteDirectoryRecursively(file);

        // then
        assertFalse(file.exists());
    }

    @Test
    public void test_deleteDirectoryRecursively_deleteNullDirectory() {
        deleteDirectoryRecursively(null);
    }

    @Test
    public void test_deleteDirectoryRecursively_nonExistentDirectory() {
        deleteDirectoryRecursively(new File("/does/not/exist"));
        // No specific assertion needed, as the method should handle null gracefully
    }

    @Test
    public void test_getHighestExistingDirectoryNumber_cannot_mkdir() {
        // given
        String cannotCreatePath = "/this/will/not/work";
        // when
        int actual = getHighestExistingDirectoryNumber(cannotCreatePath);
        // then
        assertEquals(-1, actual);
    }

    @Test
    public void test_getHighestExistingDirectoryNumber_null() {
        // given
        // when
        int actual = getHighestExistingDirectoryNumber(null);
        // then
        assertEquals(-1, actual);
    }

    @Test
    public void test_getNextDirectoryNumber_null() {
        // given
        // when
        int actual = getNextDirectoryNumber(null);
        // then
        assertEquals(-1, actual);
    }

    @Test
    public void test_getNextDirectoryNumberAndCreate_null() {
        // given
        // when
        int actual = getNextDirectoryNumberAndCreate(null);
        // then
        assertEquals(-1, actual);
    }

    @Test
    public void test_populateNodeFromDir_null() {
        // given
        // when
        // then
        populateNodeFromDir(null, null);
    }

    @Test
    public void test_populateNodeFromDir_nonExistentDirectory() {
        // given
        // when
        // then
        populateNodeFromDir(new File("/Folder/does/not/exist"), null);
    }

    @Test
    public void test_populateNodeFromDir_notDirectory() throws IOException {
        // given
        Path tempDir = Files.createTempDirectory("test_pop_dir");
        tempDir.toFile().deleteOnExit();
        File file = tempDir.resolve("temp.txt").toFile();
        assertTrue(file.createNewFile());
        file.deleteOnExit();
        // when
        // then
        populateNodeFromDir(file, null);
    }

    @Test
    public void test_getSubdirectoryNames_nonExistentDirectory() {
        // given
        // when
        List<String> results  = getSubdirectoryNames("/Folder/does/not/exist");
        // then
        assertTrue(results.isEmpty());
    }

    @Test
    public void test_getSubdirectoryNames_notDirectory() throws IOException {
        // given
        Path tempDir = Files.createTempDirectory("test_getsub_dir");
        tempDir.toFile().deleteOnExit();
        File file = tempDir.resolve("temp.txt").toFile();
        assertTrue(file.createNewFile());
        file.deleteOnExit();
        // when
        List<String> results  = getSubdirectoryNames(file.getAbsolutePath());
        // then
        assertTrue(results.isEmpty());
    }


    @Test
    public void test_getSubdirectoryNames_emptyDirectory() throws IOException {
        // given
        Path tempDir = Files.createTempDirectory("test_getsub_dir_1");
        File directory = new File(tempDir.toString());
        // when
        List<String> results  = getSubdirectoryNames(directory.getAbsolutePath());
        // then
        assertTrue(results.isEmpty());
    }


    @Test
    public void test_getSubdirectoryNames_populatedDirectory() throws IOException {
        // given
        Path tempDir = Files.createTempDirectory("test_getsub_dir_2");
        File directory = new File(tempDir.toString());
        tempDir.toFile().deleteOnExit();

        File file = tempDir.resolve("temp.txt").toFile();
        assertTrue(file.createNewFile());
        file.deleteOnExit();

        File subDir = new File(directory, "sub");
        assertTrue(subDir.mkdir());
        subDir.deleteOnExit();

        // when
        List<String> results  = getSubdirectoryNames(directory.getAbsolutePath());
        // then
        assertFalse(results.isEmpty());
    }


}
