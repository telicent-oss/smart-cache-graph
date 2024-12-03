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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.jena.abac.labels.LabelsStoreRocksDB;
import io.telicent.jena.graphql.utils.ExcludeFromJacocoGeneratedReport;
import org.apache.jena.atlas.lib.DateTimeUtils;
import org.apache.jena.fuseki.mgt.Backup;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.system.Txn;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static io.telicent.backup.utils.BackupUtils.*;
import static org.apache.jena.riot.Lang.NQUADS;

public class DatasetBackupService {

    private final DataAccessPointRegistry dapRegistry;

    public DatasetBackupService(DataAccessPointRegistry dapRegistry) {
        this.dapRegistry = dapRegistry;
    }

    /**
     * Perform a backup of all datasets or a specific dataset if a name is provided.
     */
    public ObjectNode backupDataset(String datasetName) {
        ObjectNode response = MAPPER.createObjectNode();
        String backupPath = getBackUpDir();
        int backupID = getNextDirectoryNumberAndCreate(backupPath);
        String backupIDPath = backupPath + "/" + backupID;
        response.put("backup-id", backupID);
        response.put("date", DateTimeUtils.nowAsString("yyyy-MM-dd_HH-mm-ss"));

        ArrayNode datasetNodes = MAPPER.createArrayNode();
        for (DataAccessPoint dataAccessPoint : dapRegistry.accessPoints()) {
            String dataAccessPointName = dataAccessPoint.getName();
            if (requestIsEmpty(datasetName) || dataAccessPointName.equals(datasetName)) {
                DatasetGraph datasetGraph = dataAccessPoint.getDataService().getDataset();
                String datasetBackupPath = backupIDPath + dataAccessPointName;

                ObjectNode datasetJSON = MAPPER.createObjectNode();
                datasetJSON.put("dataset-id", dataAccessPointName);
                datasetJSON.set("tdb", backupTDB(datasetGraph, datasetBackupPath, dataAccessPointName));
                datasetJSON.set("labels", backupLabelStore(datasetGraph, datasetBackupPath));
                datasetNodes.add(datasetJSON);
            }
        }
        response.set("datasets", datasetNodes);
        return response;
    }

    ObjectNode backupTDB(DatasetGraph dsg, String backupPath, String datasetName) {
        ObjectNode node = MAPPER.createObjectNode();
        String tdbBackupPath = backupPath + "/TDB/";
        if (!createPathIfNotExists(tdbBackupPath)) {
            node.put("reason", "Cannot create backup directory: " + tdbBackupPath);
            node.put("success", false);
            return node;
        }
        try {
            executeBackupTDB(dsg, tdbBackupPath + datasetName+ "_backup"); // .nq.gz will be added automatically.
            node.put("folder", tdbBackupPath);
            node.set("files", populateNodeFromDir(tdbBackupPath));
            node.put("success", true);
        } catch (RuntimeException e) {
            node.put("reason", e.getMessage());
            node.put("success", false);
        }
        return node;
    }

    /**
     * Wrapping actual back-up call to aid with testings sake.
     * @param dsg Dataset to backup
     * @param backupFile the file to back up to
     */
    @ExcludeFromJacocoGeneratedReport
    void executeBackupTDB(DatasetGraph dsg, String backupFile) {
        Backup.backup(dsg, dsg, backupFile);
    }

    ObjectNode backupLabelStore(DatasetGraph dsg, String backupPath) {
        ObjectNode node = MAPPER.createObjectNode();
        if (dsg instanceof DatasetGraphABAC abac) {
            LabelsStore labelsStore = abac.labelsStore();
            if (labelsStore instanceof LabelsStoreRocksDB rocksDB) {
                String labelBackupPath = backupPath + "/labels/";
                if (!createPathIfNotExists(labelBackupPath)) {
                    node.put("reason", "Cannot create label backup directory: " + labelBackupPath);
                    node.put("success", false);
                } else {
                    try {
                        executeBackupLabelStore(rocksDB, labelBackupPath);
                        node.put("folder", labelBackupPath);
                        node.set("files", populateNodeFromDir(labelBackupPath));
                        node.put("success", true);
                    } catch (RuntimeException e) {
                        node.put("reason", e.getMessage());
                        node.put("success", false);
                    }
                }
            } else {
                node.put("reason", "No Label Store to back up (not RocksDB)");
                node.put("success", false);
            }
        } else {
            node.put("reason", "No Label Store to back up (not ABAC)");
            node.put("success", false);
        }
        return node;
    }

    @ExcludeFromJacocoGeneratedReport
    void executeBackupLabelStore(LabelsStoreRocksDB rocksDB, String labelBackupPath) {
        // Disabling for the time-being
//        rocksDB.backup(labelBackupPath);
    }

    public ObjectNode listBackups() {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("date", DateTimeUtils.nowAsString("yyyy-MM-dd_HH-mm-ss"));
        response.set("backups", populateNodeFromDirNumerically(getBackUpDir()));
        return response;
    }

    public ObjectNode restoreDatasets(String restoreId) {
        ObjectNode response = MAPPER.createObjectNode();
        String restorePath = getBackUpDir() + "/" + restoreId;
        response.put("restorePath", restorePath);
        if (!checkPathExistsAndIsDir(restorePath)) {
            response.put("reason", "Restore path unsuitable: " + restorePath);
            response.put("success", false);
        } else {
            List<String> datasets = getSubdirectoryNames(restorePath);
            if (datasets.isEmpty()) {
                response.put("reason", "Restore path unsuitable: " + restorePath);
                response.put("success", false);
            } else {
                for (String datasetName : datasets) {
                    response.set(datasetName, restoreDataset(restorePath, datasetName));
                }
            }
        }
        return response;
    }

    ObjectNode restoreDataset(String restorePath, String datasetName) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("dataset-id", datasetName);
        DataAccessPoint dataAccessPoint = dapRegistry.get("/" + datasetName);
        if (dataAccessPoint == null) {
            response.put("reason", datasetName + " does not exist");
            response.put("success", false);
            return response;
        }
        DatasetGraph dsg = dataAccessPoint.getDataService().getDataset();
        response.set("tdb", restoreTDB(datasetName, dsg, restorePath));
        response.set("labels", restoreLabelStore(datasetName, dsg, restorePath));
        return response;
    }

    ObjectNode restoreTDB(String datasetName, DatasetGraph dsg, String restorePath) {
        ObjectNode node = MAPPER.createObjectNode();
        String tdbRestoreFile = restorePath + "/" + datasetName + "/TDB/" + datasetName + "_backup.nq.gz";
        node.put("restorePath", tdbRestoreFile);
        if (!checkPathExistsAndIsFile(tdbRestoreFile)) {
            node.put("reason", "Restore file not found: " + tdbRestoreFile);
            node.put("success", false);
            return node;
        }
        try {
            executeRestoreTDB(dsg, tdbRestoreFile);
            node.put("success", true);
        } catch (Exception e) {
            node.put("reason", e.getMessage());
            node.put("success", false);
        }
        return node;
    }

    @ExcludeFromJacocoGeneratedReport
    void executeRestoreTDB(DatasetGraph dsg, String tdbRestoreFile) throws IOException {
        try (InputStream fis = new FileInputStream(tdbRestoreFile);
             InputStream gis = new GZIPInputStream(fis)) {
            Txn.executeWrite(dsg, () -> {
                dsg.clear();
                RDFDataMgr.read(dsg, gis, NQUADS);
            });
        }
    }

    ObjectNode restoreLabelStore(String datasetName, DatasetGraph dsg, String restorePath) {
        ObjectNode node = MAPPER.createObjectNode();
        String labelRestorePath = restorePath + "/" + datasetName + "/labels/";
        node.put("restorePath", labelRestorePath);
        if (dsg instanceof DatasetGraphABAC abac) {
            LabelsStore labelsStore = abac.labelsStore();
            if (labelsStore instanceof LabelsStoreRocksDB rocksDB) {
                if (!checkPathExistsAndIsDir(labelRestorePath)) {
                    node.put("reason", "Restore directory not found: " + labelRestorePath);
                    node.put("success", false);
                } else {
                    try {
                        executeRestoreLabelStore(rocksDB, labelRestorePath);
                        node.put("success", true);
                    } catch (RuntimeException e) {
                        node.put("reason", e.getMessage());
                        node.put("success", false);
                    }
                }
            } else {
                node.put("reason", "No Label Store to restore (not RocksDB)");
                node.put("success", false);
            }
        } else {
            node.put("reason", "No Label Store to restore (not ABAC)");
            node.put("success", false);
        }
        return node;
    }

    @ExcludeFromJacocoGeneratedReport
    void executeRestoreLabelStore(LabelsStoreRocksDB rocksDB, String labelRestorePath) {
        // Disabling for the time-being
//        rocksDB.restore(labelRestorePath);
    }

    public ObjectNode deleteBackup(String deleteID) {
        String deletePath = getBackUpDir() + "/" + deleteID;
        ObjectNode response = MAPPER.createObjectNode();
        response.put("delete-id", deleteID);
        response.put("date", DateTimeUtils.nowAsString("yyyy-MM-dd_HH-mm-ss"));
        response.put("deletePath", deletePath);
        if (!checkPathExistsAndIsDir(deletePath)) {
            response.put("reason", "Backup path unsuitable: " + deletePath);
            response.put("success", false);
        } else {
            executeDeleteBackup(deletePath);
            response.put("success", true);
        }
        return response;
    }

    @ExcludeFromJacocoGeneratedReport
    void executeDeleteBackup(String deletePath) {
        deleteDirectoryRecursively(new File(deletePath));
    }
}
