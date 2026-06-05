package io.telicent.core;

import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.tdb2.TDB2Factory;

public class TestSmartCacheGraphDictionaryRocksTdb2 extends TestSmartCacheGraphSinkDictionaryRocks {

    @Override
    protected DatasetGraph createBaseDataset() {
        return TDB2Factory.createDataset().asDatasetGraph();
    }
}
