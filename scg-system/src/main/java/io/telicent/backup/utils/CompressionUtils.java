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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipInputStream;

public class CompressionUtils {

    public static final Logger LOG = LoggerFactory.getLogger(CompressionUtils.class);
    private static final int BUFFER_SIZE = 1024;

    /**
     * Zips a directory and its contents into a single ZIP file.
     * @param sourceDirPath The path to the directory to be zipped.
     * @param zipFilePath   The path where the resulting ZIP file will be created.
     */
    public static void zipDirectory(Path sourceDirPath, Path zipFilePath) {
        zipDirectory(sourceDirPath, zipFilePath, false);
    }

    /**
     * Zips a directory and its contents into a single ZIP file.
     * @param sourceDirPath The path to the directory to be zipped.
     * @param zipFilePath   The path where the resulting ZIP file will be created.
     * @return the Path of the ZIP file
     */
    public static Path zipDirectory(String sourceDirPath, String zipFilePath)  {
        return zipDirectory(Path.of(sourceDirPath), Path.of(zipFilePath), false);
    }

    /**
     * Zips a directory and its contents into a single ZIP file.
     * @param sourceDirPath The path to the directory to be zipped.
     * @param zipFilePath   The path where the resulting ZIP file will be created.
     * @return the Path of the ZIP file
     */
    public static Path zipDirectory(String sourceDirPath, String zipFilePath, boolean deleteSource)  {
        return zipDirectory(Path.of(sourceDirPath), Path.of(zipFilePath), deleteSource);
    }

    /**
     * Zips a directory and its contents into a single ZIP file.
     * @param sourceDirPath The path to the directory to be zipped.
     * @param zipFilePath   The path where the resulting ZIP file will be created.
     * @param deleteSource  If true, the source directory will be deleted upon successful zipping.
     * @return the Path of the ZIP file
     */
    public static Path zipDirectory(Path sourceDirPath, Path zipFilePath, boolean deleteSource)  {
        Objects.requireNonNull(sourceDirPath, "Source directory path cannot be null");
        Objects.requireNonNull(zipFilePath, "ZIP file path cannot be null");

        if (!Files.isDirectory(sourceDirPath)) {
            throw new IllegalArgumentException("Source path must be a directory: " + sourceDirPath);
        }

        boolean zipSuccessful = false;
        try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            addDirToZip(sourceDirPath, sourceDirPath.toString(), zos);
            zipSuccessful = true;
            return zipFilePath;

        } catch (IOException e) {
            LOG.error("Error zipping directory {}", sourceDirPath, e);
            throw new RuntimeException(e);
        } finally {
            if (deleteSource && zipSuccessful) {
                try {
                    deleteDirectoryRecursive(sourceDirPath);
                    LOG.info("Source directory deleted: {}", sourceDirPath);
                } catch (IOException e) {
                    LOG.error("Error deleting source directory {} after zipping", sourceDirPath, e);
                }
            }
        }
    }

    /**
     * Recursively adds a directory and its contents to the ZipOutputStream.
     * @param sourceDir  The current directory being processed.
     * @param parentPath The parent path (used to determine relative paths within the zip).
     * @param zos        The ZipOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    private static void addDirToZip(Path sourceDir, String parentPath, ZipOutputStream zos) throws IOException {
        File[] files = sourceDir.toFile().listFiles();
        if (files == null) {
            return;
        }

        byte[] buffer = new byte[BUFFER_SIZE];

        for (File file : files) {
            String entryName = Paths.get(parentPath).relativize(file.toPath()).toString();

            if (file.isDirectory()) {
                zos.putNextEntry(new ZipEntry(entryName + "/"));
                zos.closeEntry();
                addDirToZip(file.toPath(), parentPath, zos);
            } else {
                try (FileInputStream fis = new FileInputStream(file)) {
                    zos.putNextEntry(new ZipEntry(entryName));
                    int len;
                    while ((len = fis.read(buffer)) != -1) {
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                }
            }
        }
    }

    /**
     * Unzips a ZIP file to a specified destination directory.
     * @param zipFilePath     The path to the ZIP file.
     * @param destDirectoryPath The path to the directory where contents will be extracted.
     */
    public static void unzipDirectory(String zipFilePath, String destDirectoryPath) {
        try {
            unzipDirectory(Path.of(zipFilePath), Path.of(destDirectoryPath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Unzips a ZIP file to a specified destination directory.
     *
     * @param zipFilePath     The path to the ZIP file.
     * @param destDirectoryPath The path to the directory where contents will be extracted.
     * @throws IOException If an I/O error occurs.
     */
    public static void unzipDirectory(Path zipFilePath, Path destDirectoryPath) throws IOException {
        Objects.requireNonNull(zipFilePath, "ZIP file path cannot be null");
        Objects.requireNonNull(destDirectoryPath, "Destination directory path cannot be null");

        if (!Files.exists(zipFilePath) || !Files.isRegularFile(zipFilePath)) {
            throw new IllegalArgumentException("ZIP file does not exist or is not a regular file: " + zipFilePath);
        }

        Files.createDirectories(destDirectoryPath);

        try (FileInputStream fis = new FileInputStream(zipFilePath.toFile());
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry entry;
            byte[] buffer = new byte[BUFFER_SIZE];

            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = destDirectoryPath.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(destDirectoryPath)) {
                    throw new IOException("Attempted path traversal attack: " + entry.getName() + " is outside " + destDirectoryPath);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (FileOutputStream fos = new FileOutputStream(entryPath.toFile())) {
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Deletes a directory and all its contents recursively.
     * @param path The path to the directory to delete.
     */
    public static void cleanupDirectory(String path) {
        try {
            deleteDirectoryRecursive(Path.of(path));
        } catch (IOException e) {
            LOG.error("Unable to clean up directory: {}", path, e);
        }
    }

    /**
     * Deletes a directory and all its contents recursively.
     * @param directory The path to the directory to delete.
     * @throws IOException If an I/O error occurs during deletion.
     */
    private static void deleteDirectoryRecursive(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> walk = Files.walk(directory)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(file -> {
                            if (!file.delete()) {
                                LOG.error("Failed to delete: {}", file.getAbsolutePath());
                            }
                        });
            }
        }
    }
}