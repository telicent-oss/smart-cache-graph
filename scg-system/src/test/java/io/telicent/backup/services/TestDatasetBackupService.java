/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.telicent.backup.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.backup.utils.BackupUtils;
import io.telicent.jena.abac.ABAC;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.LabelsStoreMem;
import io.telicent.jena.abac.labels.LabelsStoreRocksDB;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.telicent.backup.services.DatasetBackupService_Test.*;
import static io.telicent.backup.utils.BackupUtils.MAPPER;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class TestDatasetBackupService {

    public final DataAccessPointRegistry mockRegistry = mock(DataAccessPointRegistry.class);


    private final DatasetBackupService cut = new DatasetBackupService_Test(mockRegistry);

    private final ObjectNode RESULT_NODE = MAPPER.createObjectNode();
    private Path baseDir;

    @BeforeEach
    public void setup() throws Exception {
        DatasetBackupService_Test.clear();

        baseDir = Files.createTempDirectory("test-temp-folder");
        baseDir.toFile().deleteOnExit();

        BackupUtils.dirBackups = baseDir.toString();
        RESULT_NODE.removeAll();
    }

    /*
     * DELETE TESTS
     */
    @Test
    public void test_delete_when_ID_does_not_exist() {
        // given
        String missingID = "not-there";

        // when
        ObjectNode result = cut.deleteBackup(missingID);

        // then
        assertEquals(0, DatasetBackupService_Test.getCallCount(DELETE_BACKUP_DIR));
        assertTrue(result.has("success"));
        assertFalse(result.get("success").asBoolean());
        assertTrue(result.has("reason"));
        assertTrue(result.get("reason").asText().contains("Backup path unsuitable"));
    }

    @Test
    public void test_delete_when_ID_does_exist() {
        // given
        Path parent = baseDir.getParent();
        BackupUtils.dirBackups = parent.toAbsolutePath().toString();
        String existingID = baseDir.getFileName().toString();

        // when
        ObjectNode result = cut.deleteBackup(existingID);

        // then
        assertEquals(1, DatasetBackupService_Test.getCallCount(DELETE_BACKUP_DIR));
        assertTrue(result.has("success"));
        assertTrue(result.get("success").asBoolean());
    }

    /*
     * LIST BACKUP TESTS
     */

    @Test
    public void test_listBackups_emptyDir() {
        // given
        // when
        ObjectNode result = cut.listBackups();

        // then
        assertTrue(result.has("backups"));
        assertEquals("{}", result.get("backups").toString());
    }

    @Test
    public void test_listBackups_contents() {
        // given
        Path parent = baseDir.getParent();
        BackupUtils.dirBackups = parent.toAbsolutePath().toString();

        // when
        ObjectNode result = cut.listBackups();

        // then
        assertTrue(result.has("backups"));
        assertNotEquals("{}", result.get("backups").toString());
    }

    /*
     * BACK UP TESTS
     */

    @Test
    public void test_backup_missing_DatasetName() {
        // given
        String datasetName = "datasetName";
        DataAccessPoint dap = new DataAccessPoint("different-name", DataService.newBuilder().build());
        when(mockRegistry.accessPoints()).thenReturn(List.of(dap));

        // when
        ObjectNode result = cut.backupDataset(datasetName);

        // then
        assertTrue(result.has("backup-id"));
        assertEquals(1, result.get("backup-id").asInt());
        assertTrue(result.has("datasets"));
        assertTrue(result.get("datasets").isArray());
        assertEquals(0, result.get("datasets").size());
        assertEquals(0, DatasetBackupService_Test.getCallCount(BACKUP_TDB));
        assertEquals(0, DatasetBackupService_Test.getCallCount(BACKUP_LABELS));
    }

    @Test
    public void test_backup_emptyRegistry() {
        // given
        String datasetName = "datasetName";
        when(mockRegistry.accessPoints()).thenReturn(emptyList());

        // when
        ObjectNode result = cut.backupDataset(datasetName);

        // then
        assertTrue(result.has("backup-id"));
        assertEquals(1, result.get("backup-id").asInt());
        assertTrue(result.has("datasets"));
        assertTrue(result.get("datasets").isArray());
        assertEquals(0, result.get("datasets").size());
        assertEquals(0, DatasetBackupService_Test.getCallCount(BACKUP_TDB));
        assertEquals(0, DatasetBackupService_Test.getCallCount(BACKUP_LABELS));
    }


    @Test
    public void test_backup_happyPath_abac_rocksDB_labels() {
        // given
        String datasetName = "/dataset-name";
        DatasetGraphABAC dsgABAC = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                null,
                mock(LabelsStoreRocksDB.class),
                null,
                null);
        DataAccessPoint dap = new DataAccessPoint("dataset-name", DataService.newBuilder().dataset(dsgABAC).build());
        DataAccessPoint dap2 = new DataAccessPoint("dataset-ignore", DataService.newBuilder().dataset(dsgABAC).build());
        when(mockRegistry.accessPoints()).thenReturn(List.of(dap, dap2));

        // when
        ObjectNode result = cut.backupDataset(datasetName);

        // then
        assertTrue(result.has("backup-id"));
        assertEquals(1, result.get("backup-id").asInt());
        assertTrue(result.has("datasets"));
        assertTrue(result.get("datasets").isArray());
        assertEquals(1, result.get("datasets").size());
        ArrayNode datasets = (ArrayNode) result.get("datasets");
        JsonNode dataset = datasets.get(0);
        assertTrue(dataset.has("dataset-id"));
        assertTrue(dataset.get("dataset-id").isTextual());
        assertEquals(datasetName, dataset.get("dataset-id").asText());

        assertTrue(dataset.has("tdb"));
        JsonNode tdbNode = dataset.get("tdb");
        assertTrue(tdbNode.has("success"));
        assertTrue(tdbNode.get("success").asBoolean());

        assertTrue(dataset.has("labels"));
        JsonNode labelsNode = dataset.get("labels");
        assertTrue(labelsNode.has("success"));
        assertTrue(labelsNode.get("success").asBoolean());

        assertEquals(1, DatasetBackupService_Test.getCallCount(BACKUP_TDB));
        assertEquals(1, DatasetBackupService_Test.getCallCount(BACKUP_LABELS));
    }

    @Test
    public void test_backup_happyPath_abac_rocksDB_labels_noID() {
        // given
        String datasetName = "/dataset-name";
        String datasetName2 = "/dataset-include";
        DatasetGraphABAC dsgABAC = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                null,
                mock(LabelsStoreRocksDB.class),
                null,
                null);
        DataAccessPoint dap = new DataAccessPoint("dataset-name", DataService.newBuilder().dataset(dsgABAC).build());
        DataAccessPoint dap2 = new DataAccessPoint("dataset-include", DataService.newBuilder().dataset(dsgABAC).build());
        when(mockRegistry.accessPoints()).thenReturn(List.of(dap, dap2));

        // when
        ObjectNode result = cut.backupDataset(null);

        // then
        assertTrue(result.has("backup-id"));
        assertEquals(1, result.get("backup-id").asInt());
        assertTrue(result.has("datasets"));
        assertTrue(result.get("datasets").isArray());
        assertEquals(2, result.get("datasets").size());
        ArrayNode datasets = (ArrayNode) result.get("datasets");

        JsonNode dataset = datasets.get(0);
        assertTrue(dataset.has("dataset-id"));
        assertTrue(dataset.get("dataset-id").isTextual());
        assertEquals(datasetName, dataset.get("dataset-id").asText());

        assertTrue(dataset.has("tdb"));
        JsonNode tdbNode = dataset.get("tdb");
        assertTrue(tdbNode.has("success"));
        assertTrue(tdbNode.get("success").asBoolean());

        assertTrue(dataset.has("labels"));
        JsonNode labelsNode = dataset.get("labels");
        assertTrue(labelsNode.has("success"));
        assertTrue(labelsNode.get("success").asBoolean());

        dataset = datasets.get(1);
        assertTrue(dataset.has("dataset-id"));
        assertTrue(dataset.get("dataset-id").isTextual());
        assertEquals(datasetName2, dataset.get("dataset-id").asText());

        assertTrue(dataset.has("tdb"));
        tdbNode = dataset.get("tdb");
        assertTrue(tdbNode.has("success"));
        assertTrue(tdbNode.get("success").asBoolean());

        assertTrue(dataset.has("labels"));
        labelsNode = dataset.get("labels");
        assertTrue(labelsNode.has("success"));
        assertTrue(labelsNode.get("success").asBoolean());

        assertEquals(2, DatasetBackupService_Test.getCallCount(BACKUP_TDB));
        assertEquals(2, DatasetBackupService_Test.getCallCount(BACKUP_LABELS));
    }

    @Test
    public void test_backup_happyPath_tdb_only() {
        // given
        String datasetName = "/dataset-name";
        DataAccessPoint dap = new DataAccessPoint("dataset-name", DataService.newBuilder().build());
        when(mockRegistry.accessPoints()).thenReturn(List.of(dap));

        // when
        ObjectNode result = cut.backupDataset(datasetName);

        // then
        assertTrue(result.has("backup-id"));
        assertEquals(1, result.get("backup-id").asInt());
        assertTrue(result.has("datasets"));
        assertTrue(result.get("datasets").isArray());
        assertEquals(1, result.get("datasets").size());
        ArrayNode datasets = (ArrayNode) result.get("datasets");
        JsonNode dataset = datasets.get(0);
        assertTrue(dataset.has("dataset-id"));
        assertTrue(dataset.get("dataset-id").isTextual());
        assertEquals(datasetName, dataset.get("dataset-id").asText());

        assertTrue(dataset.has("tdb"));
        JsonNode tdbNode = dataset.get("tdb");
        assertTrue(tdbNode.has("success"));
        assertTrue(tdbNode.get("success").asBoolean());

        assertTrue(dataset.has("labels"));
        JsonNode labelsNode = dataset.get("labels");
        assertTrue(labelsNode.has("success"));
        assertFalse(labelsNode.get("success").asBoolean());

        assertEquals(1, DatasetBackupService_Test.getCallCount(BACKUP_TDB));
        assertEquals(0, DatasetBackupService_Test.getCallCount(BACKUP_LABELS));
    }

    @Test
    public void test_backup_happyPath_abac_not_rocksDB() {
        // given
        String datasetName = "/dataset-name";
        DatasetGraphABAC dsgABAC = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                null,
                null,
                null,
                null);
        DataAccessPoint dap = new DataAccessPoint("dataset-name", DataService.newBuilder().dataset(dsgABAC).build());
        when(mockRegistry.accessPoints()).thenReturn(List.of(dap));

        // when
        ObjectNode result = cut.backupDataset(datasetName);

        // then
        assertTrue(result.has("backup-id"));
        assertEquals(1, result.get("backup-id").asInt());
        assertTrue(result.has("datasets"));
        assertTrue(result.get("datasets").isArray());
        assertEquals(1, result.get("datasets").size());
        ArrayNode datasets = (ArrayNode) result.get("datasets");
        JsonNode dataset = datasets.get(0);
        assertTrue(dataset.has("dataset-id"));
        assertTrue(dataset.get("dataset-id").isTextual());
        assertEquals(datasetName, dataset.get("dataset-id").asText());
        assertTrue(dataset.get("dataset-id").isTextual());

        assertTrue(dataset.has("tdb"));
        JsonNode tdbNode = dataset.get("tdb");
        assertTrue(tdbNode.has("success"));
        assertTrue(tdbNode.get("success").asBoolean());

        assertTrue(dataset.has("labels"));
        JsonNode labelsNode = dataset.get("labels");
        assertTrue(labelsNode.has("success"));
        assertFalse(labelsNode.get("success").asBoolean());

        assertEquals(1, DatasetBackupService_Test.getCallCount(BACKUP_TDB));
        assertEquals(0, DatasetBackupService_Test.getCallCount(BACKUP_LABELS));
    }

    @Test
    public void test_backup_happyPath_tdb_exception() {
        // given
        String datasetName = "/dataset-name";
        DataAccessPoint dap = new DataAccessPoint("dataset-name", DataService.newBuilder().build());
        when(mockRegistry.accessPoints()).thenReturn(List.of(dap));

        DatasetBackupService_Test.setupExceptionForMethod(BACKUP_TDB, "Test Jena Failure");

        // when
        ObjectNode result = cut.backupDataset(datasetName);

        // then
        assertTrue(result.has("backup-id"));
        assertEquals(1, result.get("backup-id").asInt());
        assertTrue(result.has("datasets"));
        assertTrue(result.get("datasets").isArray());
        assertEquals(1, result.get("datasets").size());
        ArrayNode datasets = (ArrayNode) result.get("datasets");
        JsonNode dataset = datasets.get(0);
        assertTrue(dataset.has("dataset-id"));
        assertTrue(dataset.get("dataset-id").isTextual());
        assertEquals(datasetName, dataset.get("dataset-id").asText());

        assertTrue(dataset.has("tdb"));
        JsonNode tdbNode = dataset.get("tdb");
        assertTrue(tdbNode.has("success"));
        assertFalse(tdbNode.get("success").asBoolean());

        assertTrue(dataset.has("labels"));
        JsonNode labelsNode = dataset.get("labels");
        assertTrue(labelsNode.has("success"));
        assertFalse(labelsNode.get("success").asBoolean());

        assertEquals(1, DatasetBackupService_Test.getCallCount(BACKUP_TDB));
        assertEquals(0, DatasetBackupService_Test.getCallCount(BACKUP_LABELS));
    }


    @Test
    public void test_backup_abac_rocksDB_exceptions() {
        // given
        String datasetName = "/dataset-name";
        DatasetGraphABAC dsgABAC = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                null,
                mock(LabelsStoreRocksDB.class),
                null,
                null);
        DataAccessPoint dap = new DataAccessPoint("dataset-name", DataService.newBuilder().dataset(dsgABAC).build());
        when(mockRegistry.accessPoints()).thenReturn(List.of(dap));

        DatasetBackupService_Test.setupExceptionForMethod(BACKUP_TDB, "Test Jena Failure");
        DatasetBackupService_Test.setupExceptionForMethod(BACKUP_LABELS, "Test Rocks DB Failure");

        // when
        ObjectNode result = cut.backupDataset(datasetName);

        // then
        assertTrue(result.has("backup-id"));
        assertEquals(1, result.get("backup-id").asInt());
        assertTrue(result.has("datasets"));
        assertTrue(result.get("datasets").isArray());
        assertEquals(1, result.get("datasets").size());
        ArrayNode datasets = (ArrayNode) result.get("datasets");
        JsonNode dataset = datasets.get(0);
        assertTrue(dataset.has("dataset-id"));
        assertTrue(dataset.get("dataset-id").isTextual());
        assertEquals(datasetName, dataset.get("dataset-id").asText());

        assertTrue(dataset.has("tdb"));
        JsonNode tdbNode = dataset.get("tdb");
        assertTrue(tdbNode.has("success"));
        assertFalse(tdbNode.get("success").asBoolean());

        assertTrue(dataset.has("labels"));
        JsonNode labelsNode = dataset.get("labels");
        assertTrue(labelsNode.has("success"));
        assertFalse(labelsNode.get("success").asBoolean());

        assertEquals(1, DatasetBackupService_Test.getCallCount(BACKUP_TDB));
        assertEquals(1, DatasetBackupService_Test.getCallCount(BACKUP_LABELS));
    }

    @Test
    public void test_restoreTDB_wrongDir() {
        // given
        String missingPath = "/does_not_exist";
        DataAccessPoint mockDataAccessPoint = mock(DataAccessPoint.class);
        when(mockDataAccessPoint.getName()).thenReturn("test");

        // when
        cut.restoreTDB(mockDataAccessPoint, missingPath, RESULT_NODE);

        // then
        assertTrue(RESULT_NODE.has("success"));
        assertFalse(RESULT_NODE.get("success").asBoolean());
        assertTrue(RESULT_NODE.has("reason"));
        assertTrue(RESULT_NODE.get("reason").isTextual());
        assertTrue(RESULT_NODE.get("reason").asText().startsWith("Restore file not found:"));
    }

    @Test
    public void test_executeBackupLabelStore_happyPath() {
        // given
        LabelsStoreRocksDB mockRocksDB = mock(LabelsStoreRocksDB.class);
        DatasetBackupService datasetBackupService = new DatasetBackupService(null);
        // when
        datasetBackupService.executeBackupLabelStore(mockRocksDB, "doesn't matter", RESULT_NODE);
        // then
        assertTrue(RESULT_NODE.has("success"));
        assertTrue(RESULT_NODE.get("success").asBoolean());
    }

    @Test
    public void test_executeRestoreLabelStore_happyPath() {
        // given
        LabelsStoreRocksDB mockRocksDB = mock(LabelsStoreRocksDB.class);
        DatasetBackupService datasetBackupService = new DatasetBackupService(null);
        // when
        datasetBackupService.executeRestoreLabelStore(mockRocksDB, "doesn't matter", RESULT_NODE);
        // then
        assertTrue(RESULT_NODE.has("success"));
        assertTrue(RESULT_NODE.get("success").asBoolean());
    }

    /*
     * RESTORE TESTS
     */

    @Test
    public void test_restoreDatasets_wrongDir() {
        // given
        String missingID = "does_not_exist";
        // when
        ObjectNode result = cut.restoreDatasets(missingID);
        // then
        assertTrue(result.has("restorePath"));
        assertTrue(result.has("success"));
        assertFalse(result.get("success").asBoolean());
        assertTrue(result.has("reason"));
        assertTrue(result.get("reason").isTextual());
        assertTrue(result.get("reason").asText().contains("Restore path unsuitable"));
        assertEquals(0, DatasetBackupService_Test.getCallCount(RESTORE_TDB));
        assertEquals(0, DatasetBackupService_Test.getCallCount(RESTORE_LABELS));
    }

    @Test
    public void test_restoreDatasets_NoBackups() {
        // given
        String emptyID = "empty_dir";
        File newDir = new File(baseDir.toString() + "/" + emptyID);
        assertTrue(newDir.mkdir());
        newDir.deleteOnExit();

        // when
        ObjectNode result = cut.restoreDatasets(emptyID);
        // then
        assertTrue(result.has("restorePath"));
        assertTrue(result.has("success"));
        assertFalse(result.get("success").asBoolean());
        assertTrue(result.has("reason"));
        assertTrue(result.get("reason").isTextual());
        assertTrue(result.get("reason").asText().contains("Restore path unsuitable"));
        assertEquals(0, DatasetBackupService_Test.getCallCount(RESTORE_TDB));
        assertEquals(0, DatasetBackupService_Test.getCallCount(RESTORE_LABELS));
    }


    @Test
    public void test_restoreDatasets_NoMatch() {
        // given
        String restoreID = "restore";
        File newDir = new File(baseDir.toString() + "/" + restoreID);
        assertTrue(newDir.mkdir());
        newDir.deleteOnExit();

        String datasetName = "dataset";
        File newDataset = new File(newDir.toString() + "/" + datasetName);
        assertTrue(newDataset.mkdir());
        newDataset.deleteOnExit();

        when(mockRegistry.get(anyString())).thenReturn(null);

        // when
        ObjectNode result = cut.restoreDatasets(restoreID);
        // then
        assertTrue(result.has("restorePath"));
        assertFalse(result.has("success"));
        assertTrue(result.has(datasetName));
        JsonNode dataset = result.get("dataset");
        assertTrue(dataset.has("success"));
        assertFalse(dataset.get("success").asBoolean());
        assertTrue(dataset.get("reason").asText().contains("does not exist"));
        assertEquals(0, DatasetBackupService_Test.getCallCount(RESTORE_TDB));
        assertEquals(0, DatasetBackupService_Test.getCallCount(RESTORE_LABELS));
    }

    @Test
    public void test_restoreDatasets_abac_rocksDB() throws IOException {
        // given
        String restoreID = "restore";
        File newDir = new File(baseDir.toString() + "/" + restoreID);
        assertTrue(newDir.mkdir());
        newDir.deleteOnExit();

        String datasetName = "dataset-name";
        File newDataset = new File(newDir + "/" + datasetName);
        assertTrue(newDataset.mkdir());
        newDataset.deleteOnExit();

        File tdbDir = new File(newDataset + "/tdb/");
        assertTrue(tdbDir.mkdir());
        tdbDir.deleteOnExit();

        File tdbFile = new File(newDataset + "/tdb/" + datasetName + "_backup.nq.gz");
        assertTrue(tdbFile.createNewFile());
        tdbFile.deleteOnExit();

        File labelsDir = new File(newDataset + "/labels/");
        assertTrue(labelsDir.mkdir());
        labelsDir.deleteOnExit();

        LabelsStoreRocksDB mockRocksDbLabelStore = mock(LabelsStoreRocksDB.class);
        when(mockRocksDbLabelStore.getTransactional()).thenReturn(DatasetGraphFactory.createTxnMem());


        DatasetGraphABAC dsgABAC = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                null,
                mockRocksDbLabelStore,
                null,
                null);
        DataAccessPoint dap = new DataAccessPoint("dataset-name", DataService.newBuilder().dataset(dsgABAC).build());

        when(mockRegistry.get("/dataset-name")).thenReturn(dap);

        // when
        ObjectNode result = cut.restoreDatasets(restoreID);

        // then
        assertTrue(result.has("restorePath"));
        assertFalse(result.has("success"));
        assertTrue(result.has(datasetName));
        JsonNode dataset = result.get(datasetName);
        assertTrue(dataset.has("dataset-id"));
        assertEquals(dataset.get("dataset-id").asText(), datasetName);

        assertTrue(dataset.has("tdb"));
        JsonNode tdb = dataset.get("tdb");
        assertTrue(tdb.has("success"));
        assertTrue(tdb.get("success").asBoolean());

        assertTrue(dataset.has("labels"));
        JsonNode labels = dataset.get("labels");
        assertTrue(labels.has("success"));
        assertTrue(labels.get("success").asBoolean());

        assertEquals(1, DatasetBackupService_Test.getCallCount(RESTORE_TDB));
        assertEquals(1, DatasetBackupService_Test.getCallCount(RESTORE_LABELS));
    }

    @Test
    public void test_restoreDatasets_not_abac_dsg() throws IOException {
        // given
        String restoreID = "restore";
        File newDir = new File(baseDir.toString() + "/" + restoreID);
        assertTrue(newDir.mkdir());
        newDir.deleteOnExit();

        String datasetName = "dataset-name";
        File newDataset = new File(newDir + "/" + datasetName);
        assertTrue(newDataset.mkdir());
        newDataset.deleteOnExit();

        File tdbDir = new File(newDataset + "/tdb/");
        assertTrue(tdbDir.mkdir());
        tdbDir.deleteOnExit();

        File tdbFile = new File(newDataset + "/tdb/" + datasetName + "_backup.nq.gz");
        assertTrue(tdbFile.createNewFile());
        tdbFile.deleteOnExit();

        DataAccessPoint dap = new DataAccessPoint("dataset-name", DataService.newBuilder().dataset(DatasetGraphFactory.createTxnMem()).build());

        when(mockRegistry.get("/dataset-name")).thenReturn(dap);

        // when
        ObjectNode result = cut.restoreDatasets(restoreID);

        // then
        assertTrue(result.has("restorePath"));
        assertFalse(result.has("success"));
        assertTrue(result.has(datasetName));
        JsonNode dataset = result.get(datasetName);
        assertTrue(dataset.has("dataset-id"));
        assertEquals(dataset.get("dataset-id").asText(), datasetName);

        assertTrue(dataset.has("tdb"));
        JsonNode tdb = dataset.get("tdb");
        assertTrue(tdb.has("success"));
        assertTrue(tdb.get("success").asBoolean());

        assertTrue(dataset.has("labels"));
        JsonNode labels = dataset.get("labels");
        assertTrue(labels.has("success"));
        assertFalse(labels.get("success").asBoolean());
        assertTrue(labels.has("reason"));
        assertTrue(labels.get("reason").isTextual());
        assertTrue(labels.get("reason").asText().startsWith("Restore path not found:"));

        assertEquals(1, DatasetBackupService_Test.getCallCount(RESTORE_TDB));
        assertEquals(0, DatasetBackupService_Test.getCallCount(RESTORE_LABELS));
    }


    @Test
    public void test_restoreDatasets_exception() throws IOException {
        // given
        String restoreID = "restore";
        File newDir = new File(baseDir.toString() + "/" + restoreID);
        assertTrue(newDir.mkdir());
        newDir.deleteOnExit();

        String datasetName = "dataset-name";
        File newDataset = new File(newDir + "/" + datasetName);
        assertTrue(newDataset.mkdir());
        newDataset.deleteOnExit();

        File tdbDir = new File(newDataset + "/tdb/");
        assertTrue(tdbDir.mkdir());
        tdbDir.deleteOnExit();

        File tdbFile = new File(newDataset + "/tdb/" + datasetName + "_backup.nq.gz");
        assertTrue(tdbFile.createNewFile());
        tdbFile.deleteOnExit();

        File labelsDir = new File(newDataset + "/labels/");
        assertTrue(labelsDir.mkdir());
        labelsDir.deleteOnExit();

        LabelsStoreRocksDB mockRocksDbLabelStore = mock(LabelsStoreRocksDB.class);
        when(mockRocksDbLabelStore.getTransactional()).thenReturn(DatasetGraphFactory.createTxnMem());

        DatasetGraphABAC dsgABAC = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                null,
                mockRocksDbLabelStore,
                null,
                null);
        DataAccessPoint dap = new DataAccessPoint("dataset-name", DataService.newBuilder().build());

        when(mockRegistry.get("/dataset-name")).thenReturn(dap);

        DatasetBackupService_Test.setupExceptionForMethod(RESTORE_TDB, "Failure");
        DatasetBackupService_Test.setupExceptionForMethod(RESTORE_LABELS, "Failure");

        // when
        ObjectNode result = cut.restoreDatasets(restoreID);

        // then
        assertTrue(result.has("restorePath"));
        assertFalse(result.has("success"));
        assertTrue(result.has(datasetName));
        JsonNode dataset = result.get(datasetName);
        assertTrue(dataset.has("dataset-id"));
        assertEquals(dataset.get("dataset-id").asText(), datasetName);

        assertEquals(0, DatasetBackupService_Test.getCallCount(RESTORE_TDB));
        assertEquals(0, DatasetBackupService_Test.getCallCount(RESTORE_LABELS));
    }

    @Test
    public void test_restoreDatasets_invalidDataAccessPoint() throws IOException {
        // given
        String restoreID = "restore";
        File newDir = new File(baseDir.toString() + "/" + restoreID);
        assertTrue(newDir.mkdir());
        newDir.deleteOnExit();

        String datasetName = "dataset-name";
        File newDataset = new File(newDir + "/" + datasetName);
        assertTrue(newDataset.mkdir());
        newDataset.deleteOnExit();

        File tdbDir = new File(newDataset + "/tdb/");
        assertTrue(tdbDir.mkdir());
        tdbDir.deleteOnExit();

        File tdbFile = new File(newDataset + "/tdb/" + datasetName + "_backup.nq.gz");
        assertTrue(tdbFile.createNewFile());
        tdbFile.deleteOnExit();

        File labelsDir = new File(newDataset + "/labels/");
        assertTrue(labelsDir.mkdir());
        labelsDir.deleteOnExit();

        LabelsStoreRocksDB mockRocksDbLabelStore = mock(LabelsStoreRocksDB.class);
        when(mockRocksDbLabelStore.getTransactional()).thenReturn(DatasetGraphFactory.createTxnMem());

        DataAccessPoint mockDAP = mock(DataAccessPoint.class);

        when(mockRegistry.get("/dataset-name")).thenReturn(mockDAP);

        DatasetBackupService_Test.setupExceptionForMethod(RESTORE_TDB, "Failure");
        DatasetBackupService_Test.setupExceptionForMethod(RESTORE_LABELS, "Failure");

        // when
        ObjectNode result = cut.restoreDatasets(restoreID);

        // then
        assertTrue(result.has("restorePath"));
        assertFalse(result.has("success"));
        assertTrue(result.has(datasetName));
        JsonNode dataset = result.get(datasetName);
        assertTrue(dataset.has("dataset-id"));
        assertEquals(dataset.get("dataset-id").asText(), datasetName);

        assertEquals(0, DatasetBackupService_Test.getCallCount(RESTORE_TDB));
        assertEquals(0, DatasetBackupService_Test.getCallCount(RESTORE_LABELS));
    }

    @Test
    public void test_restoreDatasets_abac_dsg_not_rocksdb() throws IOException {
        // given
        String restoreID = "restore";
        File newDir = new File(baseDir.toString() + "/" + restoreID);
        assertTrue(newDir.mkdir());
        newDir.deleteOnExit();

        String datasetName = "dataset-name";
        File newDataset = new File(newDir + "/" + datasetName);
        assertTrue(newDataset.mkdir());
        newDataset.deleteOnExit();

        File tdbDir = new File(newDataset + "/tdb/");
        assertTrue(tdbDir.mkdir());
        tdbDir.deleteOnExit();

        File tdbFile = new File(newDataset + "/tdb/" + datasetName + "_backup.nq.gz");
        assertTrue(tdbFile.createNewFile());
        tdbFile.deleteOnExit();

        DatasetGraphABAC dsgABAC = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                null,
                LabelsStoreMem.create(),
                null,
                null);
        DataAccessPoint dap = new DataAccessPoint("dataset-name", DataService.newBuilder().dataset(dsgABAC).build());

        when(mockRegistry.get("/dataset-name")).thenReturn(dap);

        // when
        ObjectNode result = cut.restoreDatasets(restoreID);

        // then
        assertTrue(result.has("restorePath"));
        assertFalse(result.has("success"));
        assertTrue(result.has(datasetName));
        JsonNode dataset = result.get(datasetName);
        assertTrue(dataset.has("dataset-id"));
        assertEquals(dataset.get("dataset-id").asText(), datasetName);

        assertTrue(dataset.has("tdb"));
        JsonNode tdb = dataset.get("tdb");
        assertTrue(tdb.has("success"));
        assertTrue(tdb.get("success").asBoolean());

        assertTrue(dataset.has("labels"));
        JsonNode labels = dataset.get("labels");
        assertTrue(labels.has("success"));
        assertFalse(labels.get("success").asBoolean());
        assertTrue(labels.has("reason"));
        assertTrue(labels.get("reason").isTextual());
        assertTrue(labels.get("reason").asText().startsWith("Restore path not found:"));

        assertEquals(1, DatasetBackupService_Test.getCallCount(RESTORE_TDB));
        assertEquals(0, DatasetBackupService_Test.getCallCount(RESTORE_LABELS));
    }

    @Test
    public void test_restoreDatasets_abac_rocksDB_exceptions() throws IOException {
        // given
        String restoreID = "restore";
        File newDir = new File(baseDir.toString() + "/" + restoreID);
        assertTrue(newDir.mkdir());
        newDir.deleteOnExit();

        String datasetName = "dataset-name";
        File newDataset = new File(newDir + "/" + datasetName);
        assertTrue(newDataset.mkdir());
        newDataset.deleteOnExit();

        File tdbDir = new File(newDataset + "/tdb/");
        assertTrue(tdbDir.mkdir());
        tdbDir.deleteOnExit();

        File tdbFile = new File(newDataset + "/tdb/" + datasetName + "_backup.nq.gz");
        assertTrue(tdbFile.createNewFile());
        tdbFile.deleteOnExit();

        File labelsDir = new File(newDataset + "/labels/");
        assertTrue(labelsDir.mkdir());
        labelsDir.deleteOnExit();

        LabelsStoreRocksDB mockRocksDbLabelStore = mock(LabelsStoreRocksDB.class);
        when(mockRocksDbLabelStore.getTransactional()).thenReturn(DatasetGraphFactory.createTxnMem());

        DatasetGraphABAC dsgABAC = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                null,
                mockRocksDbLabelStore,
                null,
                null);
        DataAccessPoint dap = new DataAccessPoint("dataset-name", DataService.newBuilder().dataset(dsgABAC).build());

        when(mockRegistry.get("/dataset-name")).thenReturn(dap);

        DatasetBackupService_Test.setupExceptionForMethod(RESTORE_TDB, "Failure");
        DatasetBackupService_Test.setupExceptionForMethod(RESTORE_LABELS, "Failure");

        // when
        ObjectNode result = cut.restoreDatasets(restoreID);

        // then
        assertTrue(result.has("restorePath"));
        assertFalse(result.has("success"));
        assertTrue(result.has(datasetName));
        JsonNode dataset = result.get(datasetName);
        assertTrue(dataset.has("dataset-id"));
        assertEquals(dataset.get("dataset-id").asText(), datasetName);

        assertTrue(dataset.has("tdb"));
        JsonNode tdb = dataset.get("tdb");
        assertTrue(tdb.has("success"));
        assertFalse(tdb.get("success").asBoolean());

        assertTrue(dataset.has("labels"));
        JsonNode labels = dataset.get("labels");
        assertTrue(labels.has("success"));
        assertFalse(labels.get("success").asBoolean());

        assertEquals(1, DatasetBackupService_Test.getCallCount(RESTORE_TDB));
        assertEquals(1, DatasetBackupService_Test.getCallCount(RESTORE_LABELS));
    }

    @Test
    public void test_restoreDatasets_abac_rocksDB_noFiles() {
        // given
        String restoreID = "restore";
        File newDir = new File(baseDir.toString() + "/" + restoreID);
        assertTrue(newDir.mkdir());
        newDir.deleteOnExit();

        String datasetName = "dataset-name";
        File newDataset = new File(newDir.toString() + "/" + datasetName);
        assertTrue(newDataset.mkdir());
        newDataset.deleteOnExit();

        LabelsStoreRocksDB mockRocksDbLabelStore = mock(LabelsStoreRocksDB.class);
        when(mockRocksDbLabelStore.getTransactional()).thenReturn(DatasetGraphFactory.createTxnMem());

        DatasetGraphABAC dsgABAC = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                null,
                mockRocksDbLabelStore,
                null,
                null);
        DataAccessPoint dap = new DataAccessPoint("dataset-name", DataService.newBuilder().dataset(dsgABAC).build());

        when(mockRegistry.get("/dataset-name")).thenReturn(dap);

        // when
        ObjectNode result = cut.restoreDatasets(restoreID);

        // then
        assertTrue(result.has("restorePath"));
        assertFalse(result.has("success"));
        assertTrue(result.has(datasetName));
        JsonNode dataset = result.get(datasetName);
        assertTrue(dataset.has("dataset-id"));
        assertEquals(dataset.get("dataset-id").asText(), datasetName);

        assertTrue(dataset.has("tdb"));
        JsonNode tdb = dataset.get("tdb");
        assertTrue(tdb.has("success"));
        assertFalse(tdb.get("success").asBoolean());
        assertTrue(tdb.has("reason"));
        assertTrue(tdb.get("reason").asText().startsWith("Restore path not found:"));

        assertTrue(dataset.has("labels"));
        JsonNode labels = dataset.get("labels");
        assertTrue(labels.has("success"));
        assertFalse(labels.get("success").asBoolean());
        assertTrue(labels.has("reason"));
        assertTrue(labels.get("reason").asText().startsWith("Restore path not found:"));

        assertEquals(0, DatasetBackupService_Test.getCallCount(RESTORE_TDB));
        assertEquals(0, DatasetBackupService_Test.getCallCount(RESTORE_LABELS));
    }

    @Test
    public void testValidateBackup_success_one_dataset() throws Exception {
        final String datasetName = "dataset-name";
        final String[] validateParams = {"validate", datasetName};
        final File newDir = createTestDirectory(validateParams[0]);

        final File newDataset = createTestDirectory(newDir, datasetName);

        final File tdbDir = createTestDirectory(newDataset, "tdb");

        final URL backupUrl = getClass().getClassLoader().getResource("backup.nq.gz");
        assert backupUrl != null;
        final Path copyPath = Path.of(newDataset.getPath(), tdbDir.getName(), datasetName + "_backup.nq.gz");
        Files.copy(Paths.get(backupUrl.toURI()), copyPath);
        copyPath.toFile().deleteOnExit();

        try (final InputStream inputStream = getShapeInputStream()) {
            final ObjectNode result = cut.validateBackup(validateParams, inputStream);
            assertTrue(result.has("validatePath"));
            assertTrue(result.has("success"));
            assertTrue(result.get("success").asBoolean());
            assertTrue(result.has(datasetName));
            JsonNode dataset = result.get(datasetName);
            assertTrue(dataset.has("success"));
            assertTrue(dataset.get("success").asBoolean());
        }
    }

    @Test
    public void testValidateBackup_success_two_datasets() throws Exception {
        final String[] validateParams = {"validate"};
        final File newDir = createTestDirectory(validateParams[0]);

        final String dataset1Name = "dataset-1-name";
        final File newDataset1 = createTestDirectory(newDir, dataset1Name);

        final String dataset2Name = "dataset-2-name";
        final File newDataset2 = createTestDirectory(newDir, dataset2Name);

        final File tdbDir1 = createTestDirectory(newDataset1, "tdb");
        final File tdbDir2 = createTestDirectory(newDataset2, "tdb");

        final URL backupUrl = getClass().getClassLoader().getResource("backup.nq.gz");
        assert backupUrl != null;
        final Path copyPath1 = Path.of(newDataset1.getPath(), tdbDir1.getName(), dataset1Name + "_backup.nq.gz");
        final Path copyPath2 = Path.of(newDataset2.getPath(), tdbDir2.getName(), dataset2Name + "_backup.nq.gz");

        Files.copy(Paths.get(backupUrl.toURI()), copyPath1);
        copyPath1.toFile().deleteOnExit();

        Files.copy(Paths.get(backupUrl.toURI()), copyPath2);
        copyPath2.toFile().deleteOnExit();

        try (final InputStream inputStream = getShapeInputStream()) {
            final ObjectNode result = cut.validateBackup(validateParams, inputStream);
            assertTrue(result.has("validatePath"));
            assertTrue(result.has("success"));
            assertTrue(result.get("success").asBoolean());
            assertTrue(result.has(dataset1Name));
            JsonNode dataset1 = result.get(dataset1Name);
            assertTrue(dataset1.has("success"));
            assertTrue(dataset1.get("success").asBoolean());
            assertTrue(result.has(dataset2Name));
            JsonNode dataset2 = result.get(dataset2Name);
            assertTrue(dataset2.has("success"));
            assertTrue(dataset2.get("success").asBoolean());
        }
    }

    @Test
    public void testValidateBackup_fail_invalid_id() throws Exception {
        try (final InputStream inputStream = getShapeInputStream()) {
            final ObjectNode result = cut.validateBackup(new String[]{"invalidID"}, inputStream);
            assertTrue(result.has("validatePath"));
            assertTrue(result.has("success"));
            assertFalse(result.get("success").asBoolean());
            assertTrue(result.has("reason"));
            JsonNode node = result.get("reason");
            assertTrue(node.asText().contains("Validation path unsuitable"));
        }
    }

    @Test
    public void testValidateBackup_fail_unzip() throws Exception {
        final String[] validateParams = {"validate"};
        final File newDir = createTestDirectory(validateParams[0]);

        final String datasetName = "dataset-name";
        final File newDataset = createTestDirectory(newDir, datasetName);

        final File tdbDir = createTestDirectory(newDataset, "tdb");

        final File backupFile = new File(newDataset, tdbDir.getName() + "/" + datasetName + "_backup.nq.gz");
        assertTrue(backupFile.createNewFile());
        backupFile.deleteOnExit();

        try (InputStream inputStream = getShapeInputStream()) {
            final ObjectNode result = cut.validateBackup(validateParams, inputStream);
            assertTrue(result.has("validatePath"));
            assertTrue(result.has("success"));
            assertTrue(result.get("success").asBoolean());
            assertTrue(result.has(datasetName));
            JsonNode dataset = result.get(datasetName);
            assertTrue(dataset.has("success"));
            assertFalse(dataset.get("success").asBoolean());
            assertTrue(dataset.has("reason"));
            JsonNode node = dataset.get("reason");
            assertTrue(node.asText().contains("Not found:"));
        }
    }

    @Test
    public void testValidateBackup_partial_fail_unzip() throws Exception {
        final String[] validateParams = {"validate"};
        final File newDir = createTestDirectory(validateParams[0]);

        final String dataset1Name = "dataset-1-name";
        final File newDataset1 = createTestDirectory(newDir, dataset1Name);

        final String dataset2Name = "dataset-2-name";
        final File newDataset2 = createTestDirectory(newDir, dataset2Name);

        final File tdbDir1 = createTestDirectory(newDataset1, "tdb");
        final File tdbDir2 = createTestDirectory(newDataset2, "tdb");

        final File backupFile = new File(newDataset1, tdbDir1.getName() + "/" + dataset1Name + "_backup.nq.gz");
        assertTrue(backupFile.createNewFile());
        backupFile.deleteOnExit();

        final URL backupUrl = getClass().getClassLoader().getResource("backup.nq.gz");
        assert backupUrl != null;
        final Path copyPath = Path.of(newDataset2.getPath(), tdbDir2.getName(), dataset2Name + "_backup.nq.gz");

        Files.copy(Paths.get(backupUrl.toURI()), copyPath);
        copyPath.toFile().deleteOnExit();

        try (InputStream inputStream = getShapeInputStream()) {
            final ObjectNode result = cut.validateBackup(validateParams, inputStream);
            assertTrue(result.has("validatePath"));
            assertTrue(result.has("success"));
            assertTrue(result.get("success").asBoolean());
            assertTrue(result.has(dataset1Name));
            JsonNode dataset1 = result.get(dataset1Name);
            assertTrue(dataset1.has("success"));
            assertFalse(dataset1.get("success").asBoolean());
            assertTrue(dataset1.has("reason"));
            JsonNode node1 = dataset1.get("reason");
            assertTrue(node1.asText().contains("Not found:"));
            JsonNode dataset2 = result.get(dataset2Name);
            assertTrue(dataset2.has("success"));
            assertTrue(dataset2.get("success").asBoolean());
        }
    }

    @Test
    public void applyBackUpMethods_noMethodsNoOp() {
        // given
        backupConsumerMap.clear();
        // when
        cut.applyBackUpMethods(RESULT_NODE, null, null);
        // then
        assertTrue(RESULT_NODE.isEmpty());
    }


    @Test
    public void applyBackUpMethods_missingDir() {
        // given
        String missingPath = "/does_not_exist";
        registerMethods("test", this::doNothing, this::doNothing);
        // when
        cut.applyBackUpMethods(RESULT_NODE, null, missingPath);
        // then
        assertFalse(RESULT_NODE.isEmpty());
        assertTrue(RESULT_NODE.has("test"));
        JsonNode testNode = RESULT_NODE.get("test");
        assertTrue(testNode.has("success"));
        assertFalse(testNode.get("success").asBoolean());
    }

    @Test
    public void applyBackupMethods_exception() {
        // given
        File newDir = new File(baseDir.toString() + "/test");
        assertTrue(newDir.mkdir());
        newDir.deleteOnExit();
        registerMethods("test", this::throwException, this::doNothing);
        // when
        cut.applyBackUpMethods(RESULT_NODE, null, baseDir.toString());
        // then
        assertFalse(RESULT_NODE.isEmpty());
        assertTrue(RESULT_NODE.has("test"));
        JsonNode testNode = RESULT_NODE.get("test");
        assertTrue(testNode.has("success"));
        assertFalse(testNode.get("success").asBoolean());
    }

    @Test
    public void applyBackUpMethods_happyPath() {
        // given
        File newDir = new File(baseDir.toString() + "/test");
        assertTrue(newDir.mkdir());
        newDir.deleteOnExit();
        registerMethods("test", this::doNothing, this::doNothing);
        // when
        cut.applyBackUpMethods(RESULT_NODE, null, baseDir.toString());
        // then
        assertFalse(RESULT_NODE.isEmpty());
        assertTrue(RESULT_NODE.has("test"));
        JsonNode testNode = RESULT_NODE.get("test");
        assertTrue(testNode.has("success"));
        assertTrue(testNode.get("success").asBoolean());

    }

    @Test
    public void applyRestoreMethods_noMethodsNoOp() {
        // given
        restoreConsumerMap.clear();
        ObjectNode node = MAPPER.createObjectNode();
        // when
        cut.applyRestoreMethods(node, null, "ignored");
        // then
        assertTrue(node.isEmpty());
    }

    @Test
    public void applyRestoreMethods_missingDir() {
        // given
        String missingPath = "/does_not_exist";
        registerMethods("test", this::doNothing, this::doNothing);
        // when
        cut.applyRestoreMethods(RESULT_NODE, null, missingPath);
        // then
        assertFalse(RESULT_NODE.isEmpty());
        assertTrue(RESULT_NODE.has("test"));
        JsonNode testNode = RESULT_NODE.get("test");
        assertTrue(testNode.has("success"));
        assertFalse(testNode.get("success").asBoolean());
    }

    @Test
    public void applyRestoreMethods_exception() {
        // given
        File newDir = new File(baseDir.toString() + "/test");
        assertTrue(newDir.mkdir());
        newDir.deleteOnExit();
        registerMethods("test", this::doNothing, this::throwException);
        // when
        cut.applyRestoreMethods(RESULT_NODE, null, baseDir.toString());
        // then
        assertFalse(RESULT_NODE.isEmpty());
        assertTrue(RESULT_NODE.has("test"));
        JsonNode testNode = RESULT_NODE.get("test");
        assertTrue(testNode.has("success"));
        assertFalse(testNode.get("success").asBoolean());
    }

    @Test
    public void applyRestoreMethods_happyPath() {
        // given
        File newDir = new File(baseDir.toString() + "/test");
        assertTrue(newDir.mkdir());
        newDir.deleteOnExit();
        registerMethods("test", this::doNothing, this::doNothing);

        // when
        cut.applyRestoreMethods(RESULT_NODE, null, baseDir.toString());
        // then
        assertFalse(RESULT_NODE.isEmpty());
        assertTrue(RESULT_NODE.has("test"));
        JsonNode testNode = RESULT_NODE.get("test");
        assertTrue(testNode.has("success"));
        assertTrue(testNode.get("success").asBoolean());

    }

    @Test
    public void test_restoreLabelStore_nonABAC() {
        // given
        DatasetGraph dsgNonABAC = DatasetGraphFactory.createTxnMem();

        DataAccessPoint dap = new DataAccessPoint("dataset-name", DataService.newBuilder().dataset(dsgNonABAC).build());
        when(mockRegistry.accessPoints()).thenReturn(List.of(dap));

        // when
        cut.restoreLabelStore(dap, "ignorefornow", RESULT_NODE);

        // then
        assertTrue(RESULT_NODE.has("success"));
        assertFalse(RESULT_NODE.get("success").asBoolean());
        assertTrue(RESULT_NODE.has("reason"));
        assertTrue(RESULT_NODE.get("reason").asText().equals("No Label Store to restore (not ABAC)"));
    }


    @Test
    public void test_restoreLabelStore_nonRocksDB() {
        // given
        DatasetGraphABAC dsgABAC = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                null,
                null,
                null,
                null);
        DataAccessPoint dap = new DataAccessPoint("dataset-name", DataService.newBuilder().dataset(dsgABAC).build());
        when(mockRegistry.accessPoints()).thenReturn(List.of(dap));

        // when
        cut.restoreLabelStore(dap, "ignorefornow", RESULT_NODE);

        // then
        assertTrue(RESULT_NODE.has("success"));
        assertFalse(RESULT_NODE.get("success").asBoolean());
        assertTrue(RESULT_NODE.has("reason"));
        assertTrue(RESULT_NODE.get("reason").asText().equals("No Label Store to restore (not RocksDB)"));
    }

    @Test
    public void test_restoreLabelStore_wrongPath() {
        // given
        String datasetName = "/dataset-name";

        DatasetGraphABAC dsgABAC = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                null,
                mock(LabelsStoreRocksDB.class),
                null,
                null);
        DataAccessPoint dap = new DataAccessPoint("dataset-name", DataService.newBuilder().dataset(dsgABAC).build());
        when(mockRegistry.accessPoints()).thenReturn(List.of(dap));

        // when
        cut.restoreLabelStore(dap, "/doesnotexist", RESULT_NODE);

        // then
        assertTrue(RESULT_NODE.has("success"));
        assertFalse(RESULT_NODE.get("success").asBoolean());
        assertTrue(RESULT_NODE.has("reason"));
        assertTrue(RESULT_NODE.get("reason").asText().startsWith("Restore directory not found: "));
    }

    @Test
    public void test_process_failure() throws IOException {
        // given
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(outputStream);
        when(request.getPathInfo()).thenThrow(new RuntimeException("Some error"));

        // when
        cut.process(request, response, true);
        // then
        verify(response, times(1)).setStatus(500);
    }

    @Test
    public void test_process_multipleCalls() throws InterruptedException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteUser()).thenReturn("testUser");
        when(request.getParameter("description")).thenReturn("description of backup");
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(outputStream);
        doAnswer(invocation -> {
            Thread.sleep(50); // Simulate  operation
            return "dataset";
        }).when(request).getPathInfo();

        // Use ExecutorService to run multiple threads
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {

            // Submit 5 concurrent tasks simulating individual requests
            for (int i = 0; i < 2; i++) {
                final boolean flag = (i == 0);
                executorService.submit(() -> {
                    cut.process(request, response, flag);
                });
            }
            // Wait for threads to complete
            executorService.shutdown();
            executorService.awaitTermination(1, TimeUnit.SECONDS);
        }
        verify(response, times(1)).setStatus(409);
    }

    @Test
    public void test_process_exception() throws IOException {
        // given
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(outputStream);
        doThrow(new RuntimeException("Test")).when(request).getPathInfo();

        // when
        cut.process(request, response, true);

        // then
        verify(response, times(1)).setStatus(500);
    }

    public void doNothing(DataAccessPoint dataAccessPoint, String path, ObjectNode resultNode) {
        resultNode.put("success", true);
    }

    public void throwException(DataAccessPoint dataAccessPoint, String path, ObjectNode resultNode) {
        throw new RuntimeException("TEST");
    }


    private InputStream getShapeInputStream() throws Exception {
        final URL shapeUrl = getClass().getClassLoader().getResource("CountryShape.ttl");
        assert shapeUrl != null;
        return shapeUrl.openStream();
    }

    private File createTestDirectory(final String testDirName) {
        final File testDir = new File(baseDir.toString() + "/" + testDirName);
        assertTrue(testDir.mkdir());
        testDir.deleteOnExit();
        return testDir;
    }

    private File createTestDirectory(final File rootDir, final String testDirName) {
        final File newDir = new File(rootDir + "/" + testDirName);
        assertTrue(newDir.mkdir());
        newDir.deleteOnExit();
        return newDir;
    }
}