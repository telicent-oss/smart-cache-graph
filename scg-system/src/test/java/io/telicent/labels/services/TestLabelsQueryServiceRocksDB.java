package io.telicent.labels.services;

import io.telicent.jena.abac.labels.*;
import io.telicent.labels.TripleLabels;
import org.apache.commons.io.FileUtils;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class TestLabelsQueryServiceRocksDB {

    private static final String DATASET_NAME = "test";

    private File dbDir;


    @BeforeEach
    public void setUp() throws IOException {
        dbDir = Files.createTempDirectory("tmpDir").toFile();
    }

    @Test
    public void testLabelQuery() throws Exception {
        final Triple triple = Triple.create(
                NodeFactory.createURI("http://example.org/subject"),
                NodeFactory.createURI("http://example.org/predicate"),
                NodeFactory.createURI("http://example.org/object")
        );
        final LabelsStore rocksDbLabelsStore = Labels.createLabelsStoreRocksDB(
                dbDir, LabelsStoreRocksDB.LabelMode.Overwrite, null, new StoreFmtByString());
        rocksDbLabelsStore.add(triple, Label.fromText("example"));
        final DatasetGraph emptyDsg = DatasetGraphFactory.create();
        final LabelsQueryService queryService = new LabelsQueryService(rocksDbLabelsStore, emptyDsg, DATASET_NAME);
        final List<TripleLabels> labels = queryService.queryOnlyLabelStore(triple);
        Assertions.assertEquals(1, labels.size());
        Assertions.assertEquals(1, labels.getFirst().labels.size());
    }

    @Test
    public void testLabelQueryLiteral() throws Exception
    {
        final Triple triple = Triple.create(
                NodeFactory.createURI("http://example.org/subject"),
                NodeFactory.createURI("http://example.org/predicate"),
                NodeFactory.createLiteralByValue("test")
        );
        final LabelsStore rocksDbLabelsStore = Labels.createLabelsStoreRocksDB(
                dbDir, LabelsStoreRocksDB.LabelMode.Overwrite, null, new StoreFmtByString());
        rocksDbLabelsStore.add(triple, Label.fromText("example"));
        final DatasetGraph emptyDsg = DatasetGraphFactory.create();
        final LabelsQueryService queryService = new LabelsQueryService(rocksDbLabelsStore, emptyDsg, DATASET_NAME);
        final List<TripleLabels> labels = queryService.queryOnlyLabelStore(triple);
        Assertions.assertEquals(1, labels.size());
        Assertions.assertEquals(1, labels.getFirst().labels.size());
    }

    @AfterEach
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(dbDir);
    }
}
