package io.telicent.core;

import io.telicent.jena.abac.labels.Labels;
import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.jena.abac.labels.StoreFmtByString;
import org.rocksdb.RocksDBException;

public class TestSmartCacheGraphSinkLegacyRocks extends AbstractSmartCacheGraphSinkRocksTests {

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
