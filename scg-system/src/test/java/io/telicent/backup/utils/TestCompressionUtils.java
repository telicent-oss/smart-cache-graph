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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class TestCompressionUtils {

    @TempDir
    Path tempDir;

    private Path sourceDir;
    private Path destZipDir;
    private Path destUnzipDir;

    @BeforeEach
    void setUp() throws IOException {
        sourceDir = tempDir.resolve("source");
        Files.createDirectory(sourceDir);
        destZipDir = tempDir.resolve("output_zip.zip"); // Ensure it's a file path with .zip extension
        destUnzipDir = tempDir.resolve("unzipped_output");
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    @DisplayName("Should zip a simple directory with a file")
    void testZipDirectorySimple() throws IOException {
        Path file1 = sourceDir.resolve("file1.txt");
        Files.write(file1, "Hello, world!".getBytes());

        CompressionUtils.zipDirectory(sourceDir, destZipDir, false);

        assertTrue(Files.exists(destZipDir), "ZIP file should be created");
        assertTrue(Files.size(destZipDir) > 0, "ZIP file should not be empty");

        try (ZipFile zipFile = new ZipFile(destZipDir.toFile())) {
            assertNotNull(zipFile.getEntry("file1.txt"), "ZIP should contain file1.txt");
            assertEquals(1, zipFile.size(), "ZIP should contain only the file entry");
            assertNull(zipFile.getEntry(sourceDir.getFileName().toString() + "/"), "ZIP should not contain the root directory");
        }
    }

    @Test
    @DisplayName("Should zip a directory with subdirectories and multiple files")
    void testZipDirectoryComplex() throws IOException {
        Path subDir1 = sourceDir.resolve("sub1");
        Files.createDirectory(subDir1);
        Path subDir2 = subDir1.resolve("sub2");
        Files.createDirectory(subDir2);

        Files.write(sourceDir.resolve("root_file.txt"), "Root content".getBytes());
        Files.write(subDir1.resolve("sub1_file.txt"), "Sub1 content".getBytes());
        Files.write(subDir2.resolve("sub2_file.txt"), "Sub2 content".getBytes());

        CompressionUtils.zipDirectory(sourceDir, destZipDir, false);

        assertTrue(Files.exists(destZipDir));
        try (ZipFile zipFile = new ZipFile(destZipDir.toFile())) {
            assertNotNull(zipFile.getEntry("root_file.txt"), "root_file.txt should be present");
            assertNotNull(zipFile.getEntry("sub1/"), "sub1 directory entry should be present");
            assertNotNull(zipFile.getEntry("sub1/sub1_file.txt"), "sub1_file.txt should be present");
            assertNotNull(zipFile.getEntry("sub1/sub2/"), "sub2 directory entry should be present");
            assertNotNull(zipFile.getEntry("sub1/sub2/sub2_file.txt"), "sub2_file.txt should be present");
            assertEquals(5, zipFile.size(), "ZIP should contain 3 files + 2 directories"); // 3 files + 3 directories
        }
    }

    @Test
    @DisplayName("Should zip and then delete the source directory")
    void testZipDirectoryAndDeleteSource() throws IOException {
        Path file1 = sourceDir.resolve("file_to_delete.txt");
        Files.write(file1, "Content".getBytes());

        CompressionUtils.zipDirectory(sourceDir, destZipDir, true);

        assertTrue(Files.exists(destZipDir), "ZIP file should be created");
        assertFalse(Files.exists(sourceDir), "Source directory should be deleted");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException if source is not a directory")
    void testZipDirectoryInvalidSource() {
        Path nonDirFile = tempDir.resolve("not_a_directory.txt");
        assertDoesNotThrow(() -> Files.createFile(nonDirFile));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> CompressionUtils.zipDirectory(nonDirFile, destZipDir));
        assertTrue(thrown.getMessage().contains("Source path must be a directory"));
        assertFalse(Files.exists(destZipDir), "ZIP file should not be created on error");
    }

    @Test
    @DisplayName("Should handle IOException during zipping")
    void testZipDirectoryIOException() {
        Path invalidZipPath = tempDir.resolve("invalid_zip_dir");
        assertDoesNotThrow(() -> Files.createDirectory(invalidZipPath));

        Path file1 = sourceDir.resolve("test.txt");
        assertDoesNotThrow(() -> Files.write(file1, "content".getBytes()));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> CompressionUtils.zipDirectory(sourceDir, invalidZipPath));

        assertNotNull(thrown.getCause());
        assertInstanceOf(IOException.class, thrown.getCause());
    }

    @Test
    @DisplayName("Should throw NullPointerException for null source directory path (String)")
    void testZipDirectoryNullSourceString() {
        assertThrows(NullPointerException.class, () -> CompressionUtils.zipDirectory((String) null, destZipDir.toString()));
    }

    @Test
    @DisplayName("Should throw NullPointerException for null source directory path (Path)")
    void testZipDirectoryNullSourcePath() {
        assertThrows(NullPointerException.class, () -> CompressionUtils.zipDirectory((Path) null, destZipDir, false));
    }

    @Test
    @DisplayName("Should throw NullPointerException for null zip file path (String)")
    void testZipDirectoryNullDestString() {
        assertThrows(NullPointerException.class, () -> CompressionUtils.zipDirectory(sourceDir.toString(), (String) null));
    }

    @Test
    @DisplayName("Should throw NullPointerException for null zip file path (Path)")
    void testZipDirectoryNullDestPath() {
        assertThrows(NullPointerException.class, () -> CompressionUtils.zipDirectory(sourceDir, (Path) null, false));
    }

    @Test
    @DisplayName("Should use String instead of Path for zipDirectory")
    void testZipDirectoryStringOverloads() throws IOException {
        Path file1 = sourceDir.resolve("file_string.txt");
        Files.write(file1, "Content string".getBytes());

        Path zipFile1 = tempDir.resolve("test_string_zip1.zip");
        CompressionUtils.zipDirectory(sourceDir.toString(), zipFile1.toString());
        assertTrue(Files.exists(zipFile1));
        try (ZipFile zipFile = new ZipFile(zipFile1.toFile())) {
            assertNotNull(zipFile.getEntry("file_string.txt"));
        }
        Files.delete(zipFile1);

        Path zipFile2 = tempDir.resolve("test_string_zip2.zip");
        Path sourceDirForDelete = tempDir.resolve("source_for_delete_string");
        Files.createDirectory(sourceDirForDelete);
        Path file2 = sourceDirForDelete.resolve("file_to_delete_string.txt");
        Files.write(file2, "Content to delete".getBytes());

        CompressionUtils.zipDirectory(sourceDirForDelete.toString(), zipFile2.toString(), true);
        assertTrue(Files.exists(zipFile2));
        assertFalse(Files.exists(sourceDirForDelete), "Source directory should be deleted after zipping via string overload");
    }

    @Test
    @DisplayName("Should unzip a simple ZIP file")
    void testUnzipDirectorySimple() throws IOException {
        Path file1 = sourceDir.resolve("zipped_file1.txt");
        Files.write(file1, "Unzip test content.".getBytes());
        CompressionUtils.zipDirectory(sourceDir, destZipDir, false); // Create a zip file

        CompressionUtils.unzipDirectory(destZipDir, destUnzipDir);

        assertTrue(Files.exists(destUnzipDir), "Unzipped directory should exist");
        Path unzippedFile1 = destUnzipDir.resolve("zipped_file1.txt");
        assertTrue(Files.exists(unzippedFile1), "Unzipped file should exist");
        assertEquals("Unzip test content.", Files.readString(unzippedFile1));
    }

    @Test
    @DisplayName("Should unzip a ZIP file with subdirectories and multiple files")
    void testUnzipDirectoryComplex() throws IOException {
        Path subDir1 = sourceDir.resolve("z_sub1");
        Files.createDirectory(subDir1);
        Path subDir2 = subDir1.resolve("z_sub2");
        Files.createDirectory(subDir2);

        Files.write(sourceDir.resolve("z_root_file.txt"), "Zipped Root content".getBytes());
        Files.write(subDir1.resolve("z_sub1_file.txt"), "Zipped Sub1 content".getBytes());
        Files.write(subDir2.resolve("z_sub2_file.txt"), "Zipped Sub2 content".getBytes());

        CompressionUtils.zipDirectory(sourceDir, destZipDir, false); // Create a zip file

        CompressionUtils.unzipDirectory(destZipDir.toString(), destUnzipDir.toString()); // Use the string based method for coverage

        assertTrue(Files.exists(destUnzipDir));
        assertTrue(Files.isDirectory(destUnzipDir.resolve("z_sub1")));
        assertTrue(Files.isDirectory(destUnzipDir.resolve("z_sub1/z_sub2")));

        assertTrue(Files.exists(destUnzipDir.resolve("z_root_file.txt")));
        assertTrue(Files.exists(destUnzipDir.resolve("z_sub1/z_sub1_file.txt")));
        assertTrue(Files.exists(destUnzipDir.resolve("z_sub1/z_sub2/z_sub2_file.txt")));

        assertEquals("Zipped Root content", Files.readString(destUnzipDir.resolve("z_root_file.txt")));
        assertEquals("Zipped Sub1 content", Files.readString(destUnzipDir.resolve("z_sub1/z_sub1_file.txt")));
        assertEquals("Zipped Sub2 content", Files.readString(destUnzipDir.resolve("z_sub1/z_sub2/z_sub2_file.txt")));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException if ZIP file does not exist")
    void testUnzipDirectoryNonExistentZip() {
        Path nonExistentZip = tempDir.resolve("non_existent.zip");
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> CompressionUtils.unzipDirectory(nonExistentZip, destUnzipDir));
        assertTrue(thrown.getMessage().contains("ZIP file does not exist or is not a regular file"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException if ZIP file is a directory")
    void testUnzipDirectoryZipIsDirectory() throws IOException {
        Files.createDirectory(destZipDir); // Make destZipDir a directory to simulate error
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> CompressionUtils.unzipDirectory(destZipDir, destUnzipDir));
        assertTrue(thrown.getMessage().contains("ZIP file does not exist or is not a regular file"));
    }

    @Test
    @DisplayName("Should throw IOException on path traversal attempt during unzip")
    void testUnzipDirectoryPathTraversal() throws IOException {
        Path maliciousZip = tempDir.resolve("malicious.zip");
        try (FileOutputStream fos = new FileOutputStream(maliciousZip.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zos.putNextEntry(new ZipEntry("../../../malicious_file.txt"));
            zos.write("I am a bad file!".getBytes());
            zos.closeEntry();
        }

        IOException thrown = assertThrows(IOException.class, () -> CompressionUtils.unzipDirectory(maliciousZip, destUnzipDir));
        assertTrue(thrown.getMessage().contains("Attempted path traversal attack"));
        assertFalse(Files.exists(tempDir.getParent().resolve("malicious_file.txt")));
    }

    @Test
    @DisplayName("Should throw NullPointerException for null zip file path (String)")
    void testUnzipDirectoryNullSourceString() {
        assertThrows(NullPointerException.class, () -> CompressionUtils.unzipDirectory((String) null, destUnzipDir.toString()));
    }

    @Test
    @DisplayName("Should throw NullPointerException for null zip file path (Path)")
    void testUnzipDirectoryNullSourcePath() {
        assertThrows(NullPointerException.class, () -> CompressionUtils.unzipDirectory((Path) null, destUnzipDir));
    }

    @Test
    @DisplayName("Should throw NullPointerException for null destination directory path (String)")
    void testUnzipDirectoryNullDestString() {
        assertThrows(NullPointerException.class, () -> CompressionUtils.unzipDirectory(destZipDir.toString(), (String) null));
    }

    @Test
    @DisplayName("Should throw NullPointerException for null destination directory path (Path)")
    void testUnzipDirectoryNullDestPath() {
        assertThrows(NullPointerException.class, () -> CompressionUtils.unzipDirectory(destZipDir, (Path) null));
    }
}