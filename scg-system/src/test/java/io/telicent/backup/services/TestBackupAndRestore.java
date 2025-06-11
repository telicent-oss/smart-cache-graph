package io.telicent.backup.services;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.jena.abac.ABAC;
import io.telicent.jena.abac.SysABAC;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.LabelsStoreMem;
import org.apache.jena.atlas.lib.FileOps;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.Quad;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.*;

public class TestBackupAndRestore {

    private static final String TEST_BACKUP_DIR = "target/backup/";

    private static final String RESTORE_FILES = "src/test/files/Data/restore/";

    private static final Node DEFAULT_GRAPH = Quad.defaultGraphIRI;

    private static DatasetBackupService datasetBackupService;

    private static DatasetGraphABAC dsgABAC;


    @BeforeAll
    public static void beforeAll() {
        FileOps.ensureDir(TEST_BACKUP_DIR);
        datasetBackupService = new DatasetBackupService(null);
        dsgABAC = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                null,
                LabelsStoreMem.create(),
                SysABAC.allowLabel,
                null);
    }

    @AfterAll
    public static void afterAll() {
        FileOps.clearAll(TEST_BACKUP_DIR);
    }

    @AfterEach
    public void clearTest() {
        dsgABAC.clear();
    }

    @Test
    public void test_executeRestoreTDB_defaultGraph() throws IOException {
        // given
        String tdbRestoreFile = RESTORE_FILES + "test_sample_default_graph.nq.gz";
        // when
        datasetBackupService.executeRestoreTDB(dsgABAC, tdbRestoreFile);
        //then
        assertTrue(dsgABAC.contains(DEFAULT_GRAPH, NodeFactory.createURI("http://example.org/subject1"), NodeFactory.createURI("http://example.org/predicate1"), NodeFactory.createLiteralString("object1")));
        assertTrue(dsgABAC.contains(DEFAULT_GRAPH, NodeFactory.createURI("http://example.org/subject2"), NodeFactory.createURI("http://example.org/predicate2"), NodeFactory.createURI("http://example.org/object2")));
        assertTrue(dsgABAC.contains(DEFAULT_GRAPH, NodeFactory.createBlankNode("blankNode1"), NodeFactory.createURI("http://example.org/predicate3"), NodeFactory.createLiteralString("object3")));
        assertTrue(dsgABAC.contains(DEFAULT_GRAPH, NodeFactory.createURI("http://example.org/subject4"), NodeFactory.createURI("http://example.org/predicate4"), NodeFactory.createBlankNode("blankNode2")));
        assertTrue(dsgABAC.contains(DEFAULT_GRAPH, NodeFactory.createURI("http://example.org/subject5"), NodeFactory.createURI("http://example.org/predicate5"), NodeFactory.createLiteralString("object5")));
    }

    @Test
    public void test_executeRestoreTDB_namedGraphs() throws IOException {
        // given
        String tdbRestoreFile = RESTORE_FILES + "test_sample_named_graphs.nq.gz";
        // when
        datasetBackupService.executeRestoreTDB(dsgABAC, tdbRestoreFile);
        //then
        assertTrue(dsgABAC.contains(NodeFactory.createURI("http://example.org/graph1"), NodeFactory.createURI("http://example.org/subject1"), NodeFactory.createURI("http://example.org/predicate1"), NodeFactory.createLiteralString("object1")));
        assertTrue(dsgABAC.contains(NodeFactory.createURI("http://example.org/graph2"), NodeFactory.createURI("http://example.org/subject2"), NodeFactory.createURI("http://example.org/predicate2"), NodeFactory.createURI("http://example.org/object2")));
        assertTrue(dsgABAC.contains(NodeFactory.createURI("http://example.org/graph3"), NodeFactory.createBlankNode("blankNode1"), NodeFactory.createURI("http://example.org/predicate3"), NodeFactory.createLiteralString("object3")));
        assertTrue(dsgABAC.contains(NodeFactory.createURI("http://example.org/graph4"), NodeFactory.createURI("http://example.org/subject4"), NodeFactory.createURI("http://example.org/predicate4"), NodeFactory.createBlankNode("blankNode2")));
        assertTrue(dsgABAC.contains(DEFAULT_GRAPH, NodeFactory.createURI("http://example.org/subject5"), NodeFactory.createURI("http://example.org/predicate5"), NodeFactory.createLiteralString("object5")));
    }

    @Test
    public void test_backupEntries_AndThen_Restore() throws IOException {
        // given
        dsgABAC.add(Quad.create(NodeFactory.createURI("http://example.org/graph1"), NodeFactory.createURI("http://example.org/subject1"), NodeFactory.createURI("http://example.org/predicate1"), NodeFactory.createURI("http://example.org/object1")));
        dsgABAC.add(Quad.create(NodeFactory.createURI("http://example.org/graph2"), NodeFactory.createURI("http://example.org/subject2"), NodeFactory.createURI("http://example.org/predicate2"), NodeFactory.createLiteralString("object2")));
        dsgABAC.add(Quad.create(NodeFactory.createURI("http://example.org/graph3"), NodeFactory.createBlankNode("subject3"), NodeFactory.createURI("http://example.org/predicate3"), NodeFactory.createURI("http://example.org/object3")));
        dsgABAC.add(Quad.create(NodeFactory.createURI("http://example.org/graph4"), NodeFactory.createURI("http://example.org/subject4"), NodeFactory.createURI("http://example.org/predicate4"), NodeFactory.createBlankNode("object4")));
        dsgABAC.add(Quad.create(DEFAULT_GRAPH, NodeFactory.createURI("http://example.org/subject5"), NodeFactory.createURI("http://example.org/predicate5"), NodeFactory.createURI("http://example.org/object5")));

        String backupFilename = TEST_BACKUP_DIR + "test_file.nq";
        String backupCompressedFilename = backupFilename+".gz";
        ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
        // when
        datasetBackupService.executeBackupTDB(dsgABAC, backupFilename, resultNode);
        // then
        assertNotNull(resultNode);
        assertTrue(resultNode.has("success"));
        assertTrue(resultNode.get("success").asBoolean(false));
        assertTrue(FileOps.exists(backupCompressedFilename));

        // when
        assertTrue(dsgABAC.contains(Quad.create(NodeFactory.createURI("http://example.org/graph1"), NodeFactory.createURI("http://example.org/subject1"), NodeFactory.createURI("http://example.org/predicate1"), NodeFactory.createURI("http://example.org/object1"))));
        assertTrue(dsgABAC.contains((Quad.create(NodeFactory.createURI("http://example.org/graph2"), NodeFactory.createURI("http://example.org/subject2"), NodeFactory.createURI("http://example.org/predicate2"), NodeFactory.createLiteralString("object2")))));
        assertTrue(dsgABAC.contains(Quad.create(NodeFactory.createURI("http://example.org/graph3"), NodeFactory.createBlankNode("subject3"), NodeFactory.createURI("http://example.org/predicate3"), NodeFactory.createURI("http://example.org/object3"))));
        assertTrue(dsgABAC.contains(Quad.create(NodeFactory.createURI("http://example.org/graph4"), NodeFactory.createURI("http://example.org/subject4"), NodeFactory.createURI("http://example.org/predicate4"), NodeFactory.createBlankNode("object4"))));
        assertTrue(dsgABAC.contains(Quad.create(DEFAULT_GRAPH, NodeFactory.createURI("http://example.org/subject5"), NodeFactory.createURI("http://example.org/predicate5"), NodeFactory.createURI("http://example.org/object5"))));
        dsgABAC.clear();
        assertFalse(dsgABAC.contains(Quad.create(NodeFactory.createURI("http://example.org/graph1"), NodeFactory.createURI("http://example.org/subject1"), NodeFactory.createURI("http://example.org/predicate1"), NodeFactory.createURI("http://example.org/object1"))));
        assertFalse(dsgABAC.contains((Quad.create(NodeFactory.createURI("http://example.org/graph2"), NodeFactory.createURI("http://example.org/subject2"), NodeFactory.createURI("http://example.org/predicate2"), NodeFactory.createLiteralString("object2")))));
        assertFalse(dsgABAC.contains(Quad.create(NodeFactory.createURI("http://example.org/graph3"), NodeFactory.createBlankNode("subject3"), NodeFactory.createURI("http://example.org/predicate3"), NodeFactory.createURI("http://example.org/object3"))));
        assertFalse(dsgABAC.contains(Quad.create(NodeFactory.createURI("http://example.org/graph4"), NodeFactory.createURI("http://example.org/subject4"), NodeFactory.createURI("http://example.org/predicate4"), NodeFactory.createBlankNode("object4"))));
        assertFalse(dsgABAC.contains(Quad.create(DEFAULT_GRAPH, NodeFactory.createURI("http://example.org/subject5"), NodeFactory.createURI("http://example.org/predicate5"), NodeFactory.createURI("http://example.org/object5"))));

        datasetBackupService.executeRestoreTDB(dsgABAC, backupCompressedFilename);
        // then
        assertTrue(dsgABAC.contains(Quad.create(NodeFactory.createURI("http://example.org/graph1"), NodeFactory.createURI("http://example.org/subject1"), NodeFactory.createURI("http://example.org/predicate1"), NodeFactory.createURI("http://example.org/object1"))));
        assertTrue(dsgABAC.contains((Quad.create(NodeFactory.createURI("http://example.org/graph2"), NodeFactory.createURI("http://example.org/subject2"), NodeFactory.createURI("http://example.org/predicate2"), NodeFactory.createLiteralString("object2")))));
        assertTrue(dsgABAC.contains(Quad.create(NodeFactory.createURI("http://example.org/graph3"), NodeFactory.createBlankNode("subject3"), NodeFactory.createURI("http://example.org/predicate3"), NodeFactory.createURI("http://example.org/object3"))));
        assertTrue(dsgABAC.contains(Quad.create(NodeFactory.createURI("http://example.org/graph4"), NodeFactory.createURI("http://example.org/subject4"), NodeFactory.createURI("http://example.org/predicate4"), NodeFactory.createBlankNode("object4"))));
        assertTrue(dsgABAC.contains(Quad.create(DEFAULT_GRAPH, NodeFactory.createURI("http://example.org/subject5"), NodeFactory.createURI("http://example.org/predicate5"), NodeFactory.createURI("http://example.org/object5"))));
    }

    @Test
    public void test_executeDeleteBackup() throws IOException {
        // given
        Path tempDir = Path.of(TEST_BACKUP_DIR);
        Path file1 = tempDir.resolve("test_file1.txt");
        Path file2 = tempDir.resolve("subdir/test_file2.txt");
        Files.createDirectories(file2.getParent());
        Files.writeString(file1, "Test content");
        Files.writeString(file2, "More test content");
        assertTrue(file1.toFile().exists());
        assertTrue(file2.toFile().exists());
        // when
        datasetBackupService.executeDeleteBackup(TEST_BACKUP_DIR);

        // then
        assertFalse(file1.toFile().exists());
        assertFalse(file2.toFile().exists());
        assertFalse(file2.getParent().toFile().exists());
    }
}
