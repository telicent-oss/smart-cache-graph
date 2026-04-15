package io.telicent;

import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.jena.abac.labels.StoreFmtByHash;
import io.telicent.jena.abac.labels.hashing.HasherUtil;
import io.telicent.jena.abac.labels.store.rocksdb.modern.DictionaryLabelStoreRocksDB;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.rocksdb.RocksDBException;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class TestSmartCacheGraphSinkDictionaryRocks extends TestSmartCacheGraphSink {

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
    protected LabelsStore createLabelsStore() {
        try {
            return new DictionaryLabelStoreRocksDB(this.dbDir, new StoreFmtByHash(HasherUtil.createXX128Hasher()));
        } catch (RocksDBException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
