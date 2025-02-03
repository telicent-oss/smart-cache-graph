package io.telicent.labels.services;

import io.telicent.jena.abac.labels.Labels;
import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.jena.abac.labels.LabelsStoreRocksDB;
import io.telicent.jena.abac.labels.StoreFmtByString;
import io.telicent.labels.TripleLabels;
import org.apache.commons.io.FileUtils;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.junit.jupiter.api.*;
import org.rocksdb.RocksDBException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ITLabelsQueryService {

    private static File dbDir;

    @BeforeEach
    public void setUp() throws IOException {
        dbDir = Files.createTempDirectory("tmpDir").toFile();
    }

    @Test
    public void testLabelQuery() throws IOException, RocksDBException {
        final Triple triple = Triple.create(
                NodeFactory.createURI("http://example.org/subject"),
                NodeFactory.createURI("http://example.org/predicate"),
                NodeFactory.createURI("http://example.org/object")
        );
        final LabelsStore rocksDbLabelsStore = Labels.createLabelsStoreRocksDB(
                dbDir, LabelsStoreRocksDB.LabelMode.Overwrite, null, new StoreFmtByString());
        rocksDbLabelsStore.add(triple, "example");
        final LabelsQueryService queryService = new LabelsQueryService(rocksDbLabelsStore);
        final TripleLabels labels = queryService.queryLabelStore(triple);
        Assertions.assertEquals(1, labels.labels.size());
    }

    @Test
    public void testLabelQueryLiteral() throws IOException, RocksDBException {
        final Triple triple = Triple.create(
                NodeFactory.createURI("http://example.org/subject"),
                NodeFactory.createURI("http://example.org/predicate"),
                NodeFactory.createLiteralByValue("test")
        );
        final LabelsStore rocksDbLabelsStore = Labels.createLabelsStoreRocksDB(
                dbDir, LabelsStoreRocksDB.LabelMode.Overwrite, null, new StoreFmtByString());
        rocksDbLabelsStore.add(triple, "example");
        final LabelsQueryService queryService = new LabelsQueryService(rocksDbLabelsStore);
        final TripleLabels labels = queryService.queryLabelStore(triple);
        Assertions.assertEquals(1, labels.labels.size());
    }

    @AfterEach
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(dbDir);
    }
}
