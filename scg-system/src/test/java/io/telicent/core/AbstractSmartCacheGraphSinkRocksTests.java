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
        Labels.rocks.clear();
    }
}
