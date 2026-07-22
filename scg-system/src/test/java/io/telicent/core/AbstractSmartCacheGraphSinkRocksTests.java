package io.telicent.core;

import io.telicent.jena.abac.labels.Labels;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public abstract class AbstractSmartCacheGraphSinkRocksTests extends AbstractSmartCacheGraphSinkTests {
    protected File dbDir;

    @BeforeEach
    public void setupRocksDir() throws IOException {
        this.dbDir = Files.createTempDirectory("rocks").toFile();
    }

    @AfterEach
    public void teardownRocksDir() throws IOException {
        FileUtils.deleteDirectory(this.dbDir);
    }

    @AfterAll
    public static void teardownRocksCache() {
        // We Have to close any open labels stores otherwise we leave the RocksDB native handles open, holding the OS
        // lock, and interfere with following tests that use the same configuration file
        Labels.rocks.forEach((f, labels) -> {
            try {
                labels.close();
            } catch (Exception e) {
                // Ignore
            }
        });
        Labels.rocks.clear();
    }
}
