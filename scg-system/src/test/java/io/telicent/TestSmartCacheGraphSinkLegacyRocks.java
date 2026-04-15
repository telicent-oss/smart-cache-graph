package io.telicent;

import io.telicent.jena.abac.labels.Labels;
import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.jena.abac.labels.StoreFmtByString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.rocksdb.RocksDBException;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class TestSmartCacheGraphSinkLegacyRocks extends TestSmartCacheGraphSink {

    private File dbDir;

    @BeforeEach
    public void setupRocksDir() throws IOException {
        this.dbDir = Files.createTempDirectory("rocks").toFile();
    }

    @AfterEach
    public void teardownRocksDir() throws IOException {
        FileUtils.deleteDirectory(this.dbDir);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected LabelsStore createLabelsStore() {
        try {
            return Labels.createLabelsStoreRocksDB(this.dbDir, null, new StoreFmtByString());
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean supportsLabellingQuads() {
        return false;
    }
}
