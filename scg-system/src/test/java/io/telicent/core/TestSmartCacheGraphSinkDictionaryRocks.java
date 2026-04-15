package io.telicent.core;

import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.jena.abac.labels.StoreFmtByHash;
import io.telicent.jena.abac.labels.hashing.HasherUtil;
import io.telicent.jena.abac.labels.store.rocksdb.modern.DictionaryLabelStoreRocksDB;
import org.rocksdb.RocksDBException;

import java.io.IOException;

public class TestSmartCacheGraphSinkDictionaryRocks extends AbstractSmartCacheGraphSinkRocksTests {

    @Override
    protected LabelsStore createLabelsStore() {
        try {
            return new DictionaryLabelStoreRocksDB(this.dbDir, new StoreFmtByHash(HasherUtil.createXX128Hasher()));
        } catch (RocksDBException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean supportsLabellingQuads() {
        return true;
    }
}
