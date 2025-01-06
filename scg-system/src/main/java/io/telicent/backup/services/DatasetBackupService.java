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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPInputStream;

import static io.telicent.backup.utils.BackupUtils.*;
import static org.apache.jena.riot.Lang.NQUADS;

public class DatasetBackupService {

    private final ReentrantLock lock;

    private final DataAccessPointRegistry dapRegistry;

    final static ConcurrentHashMap<String, TriConsumer<DataAccessPoint, String, ObjectNode>> backupConsumerMap = new ConcurrentHashMap<>();
    final static ConcurrentHashMap<String, TriConsumer<DataAccessPoint, String, ObjectNode>> restoreConsumerMap = new ConcurrentHashMap<>();

    public DatasetBackupService(DataAccessPointRegistry dapRegistry) {
        this.dapRegistry = dapRegistry;
        registerMethods("tdb", this::backupTDB, this::restoreTDB);
        registerMethods("labels", this::backupLabelStore, this::restoreLabelStore);
        lock = new ReentrantLock();
    }

    /**
     * Ensures that only one operation is processed at a time
     * @param request incoming request
     * @param response outgoing response
     * @param backup flag indicating backup or restore
     */
    public void process(HttpServletRequest request, HttpServletResponse response, boolean backup) {
        // Try to acquire the lock without blocking
        ObjectNode resultNode = MAPPER.createObjectNode();
        if (!lock.tryLock()) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            resultNode.put("error", "Another conflicting operation is already in progress. Please try again later.");
            processResponse(response, resultNode);
        } else {
            try {
                String id = request.getPathInfo();
                resultNode.put("id", id);
                resultNode.put("date", DateTimeUtils.nowAsString("yyyy-MM-dd_HH-mm-ss"));
                resultNode.put("user", request.getRemoteUser());
                String name = request.getParameter("description");
                if (name != null) {
                    resultNode.put("description", name);
                }
                if (backup) {
                    resultNode.set("backup", backupDataset(id));
                } else {
                    resultNode.set("restore", restoreDatasets(id));
                }
                processResponse(response, resultNode);
            } catch (Exception exception) {
                handleError(response, resultNode, exception);
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Perform a backup of all datasets or a specific dataset if a name is provided.
     * @param datasetName the name of the dataset to back up
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
                ObjectNode datasetJSON = MAPPER.createObjectNode();
                datasetJSON.put("dataset-id", dataAccessPointName);
                applyBackUpMethods(datasetJSON, dataAccessPoint, backupIDPath + dataAccessPointName);
                datasetNodes.add(datasetJSON);
            }
        }
        response.set("datasets", datasetNodes);
        return response;
    }

    /**
     * For all registered backup Consumers, apply the backup operation
     * @param moduleJSON JSON to update with details
     * @param dataAccessPoint the details to  which to apply
     * @param backupPath the path to backup to
     */
    public void applyBackUpMethods(ObjectNode moduleJSON, DataAccessPoint dataAccessPoint, String backupPath) {
        for (Map.Entry<String,TriConsumer<DataAccessPoint, String, ObjectNode>> entry : backupConsumerMap.entrySet()){
            ObjectNode node = MAPPER.createObjectNode();
            String modBackupPath = backupPath + "/" + entry.getKey() + "/";
            node.put("folder", modBackupPath);
            if (!createPathIfNotExists(modBackupPath)) {
                node.put("reason", "Cannot create backup directory: " + modBackupPath);
                node.put("success", false);
            } else {
                try {
                    entry.getValue().accept(dataAccessPoint, modBackupPath, node);
                    node.set("files", populateNodeFromDir(modBackupPath));
                } catch (RuntimeException e) {
                    node.put("reason", e.getMessage());
                    node.put("success", false);
                }
            }
            moduleJSON.set(entry.getKey(), node);
        }
    }

    /**
     * Wrapping actual back-up call to aid with testings sake.
     * @param dataAccessPoint Dataset to backup
     * @param backupPath the folder to back up to
     * @param node JSON Node to store results
     */
    void backupTDB(DataAccessPoint dataAccessPoint, String backupPath, ObjectNode node) {
        String backupFile = backupPath + dataAccessPoint.getName() + "_backup";
        DatasetGraph dsg = dataAccessPoint.getDataService().getDataset();
        executeBackupTDB(dsg, backupFile, node);
    }

    /**
     * Wrapping actual back-up call to aid with testings sake.
     * @param dsg DatasetGraph to back up
     * @param backupFile the file to back up to
     * @param node JSON Node to store results
     */
    //public void backupKafka(String dataset, String path, ObjectNode resultNode);
    @ExcludeFromJacocoGeneratedReport
    void executeBackupTDB(DatasetGraph dsg, String backupFile, ObjectNode node) {
        Backup.backup(dsg, dsg, backupFile);
        node.put("success", true);
    }

    /**
     * Back up the label store for the given data access point (DSG)
     * @param dataAccessPoint the data access point
     * @param backupPath the path to back up to
     * @param node the object node to write the results of the operation too
     */
    void backupLabelStore(DataAccessPoint dataAccessPoint, String backupPath, ObjectNode node) {
        DatasetGraph dsg = dataAccessPoint.getDataService().getDataset();
        if (dsg instanceof DatasetGraphABAC abac) {
            LabelsStore labelsStore = abac.labelsStore();
            if (labelsStore instanceof LabelsStoreRocksDB rocksDB) {
                try {
                    executeBackupLabelStore(rocksDB, backupPath, node);
                } catch (RuntimeException e) {
                    node.put("reason", e.getMessage());
                    node.put("success", false);
                }
            } else {
                node.put("reason", "No Label Store to back up (not RocksDB)");
                node.put("success", false);
            }
        } else {
            node.put("reason", "No Label Store to back up (not ABAC)");
            node.put("success", false);
        }
    }

    /**
     * Call Rocks DB to back up itself.
     * @param rocksDB instance to call
     * @param labelBackupPath path to use
     * @param node to collect the results
     */
    void executeBackupLabelStore(LabelsStoreRocksDB rocksDB, String labelBackupPath, ObjectNode node) {
        rocksDB.backup(labelBackupPath);
        node.put("success", true);
    }

    /**
     * List all the available back-up files
     * @return Object Node of the results
     */
    public ObjectNode listBackups() {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("date", DateTimeUtils.nowAsString("yyyy-MM-dd_HH-mm-ss"));
        response.set("backups", populateNodeFromDirNumerically(getBackUpDir()));
        return response;
    }

    /**
     * Restore the system with the files located by the ID
     * @param restoreId the subdirectory to use
     * @return a node of the results
     */
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

    /**
     * Restore the data set with the given files location.
     * @param restorePath the location of the back-up files
     * @param datasetName the dataset to apply the changes too.
     * @return a node with the results of the operation
     */
    ObjectNode restoreDataset(String restorePath, String datasetName) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("dataset-id", datasetName);
        DataAccessPoint dataAccessPoint = dapRegistry.get("/" + datasetName);
        if (dataAccessPoint == null) {
            response.put("reason", datasetName + " does not exist");
            response.put("success", false);
            return response;
        }
        applyRestoreMethods(response, dataAccessPoint, restorePath + "/" + datasetName);
        return response;
    }

    /**
     * Restore the Triple store for a dataset with the given files.
     * @param dataAccessPoint access to the dataset to recover
     * @param restorePath the location of the recovery files
     * @param node the results of the operation.
     */
    void restoreTDB(DataAccessPoint dataAccessPoint, String restorePath, ObjectNode node) {
        String tdbRestoreFile = restorePath + "/" + dataAccessPoint.getName() + "_backup.nq.gz";
        node.put("restorePath", tdbRestoreFile);
        if (!checkPathExistsAndIsFile(tdbRestoreFile)) {
            node.put("reason", "Restore file not found: " + tdbRestoreFile);
            node.put("success", false);
        } else {
            try {
                DatasetGraph dsg = dataAccessPoint.getDataService().getDataset();
                executeRestoreTDB(dsg, tdbRestoreFile);
                node.put("success", true);
            } catch (Exception e) {
                node.put("reason", e.getMessage());
                node.put("success", false);
            }
        }
    }

    /**
     * For all registered restore Consumers, apply the restore operation
     * @param moduleJSON JSON to update with details
     * @param dataAccessPoint access to the dataset to which to apply
     * @param restorePath the path to restore from
     */
    public void applyRestoreMethods(ObjectNode moduleJSON, DataAccessPoint dataAccessPoint, String restorePath) {
        for (Map.Entry<String,TriConsumer<DataAccessPoint, String, ObjectNode>> entry : restoreConsumerMap.entrySet()){
            ObjectNode node = MAPPER.createObjectNode();
            String modRestorePath = restorePath + "/" + entry.getKey() + "/";
            node.put("folder", modRestorePath);
            if (!checkPathExistsAndIsDir(modRestorePath)) {
                node.put("reason", "Restore path not found: " + modRestorePath);
                node.put("success", false);
            } else {
                try {
                    entry.getValue().accept(dataAccessPoint, modRestorePath, node);
                    node.set("files", populateNodeFromDir(modRestorePath));
                } catch (RuntimeException e) {
                    node.put("reason", e.getMessage());
                    node.put("success", false);
                }
            }
            moduleJSON.set(entry.getKey(), node);
        }
    }

    /**
     * Restore the triple store
     * @param dsg dataset to recover
     * @param tdbRestoreFile the zipped file with te recovery data
     * @throws IOException if there's an issue.
     */
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

    /**
     * Restore the underlying label store of the dataset
     * @param dataAccessPoint access to the dataset
     * @param restorePath the location of the recovery files
     * @param node the results of the operation
     */
    void restoreLabelStore(DataAccessPoint dataAccessPoint, String restorePath, ObjectNode node) {
        DatasetGraph dsg = dataAccessPoint.getDataService().getDataset();
        if (dsg instanceof DatasetGraphABAC abac) {
            LabelsStore labelsStore = abac.labelsStore();
            if (labelsStore instanceof LabelsStoreRocksDB rocksDB) {
                if (!checkPathExistsAndIsDir(restorePath)) {
                    node.put("reason", "Restore directory not found: " + restorePath);
                    node.put("success", false);
                } else {
                    try {
                        executeRestoreLabelStore(rocksDB, restorePath, node);
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
    }

    /**
     * Calls a RocksDB to restore itself
     * @param rocksDB the rocks db label store
     * @param labelRestorePath the location of the recovery files
     * @param node the results of the operation
     */
    void executeRestoreLabelStore(LabelsStoreRocksDB rocksDB, String labelRestorePath, ObjectNode node) {
        rocksDB.restore(labelRestorePath);
        node.put("success", true);
    }

    /**
     * For the given ID, delete the recovery files
     * @param deleteID the subdirectory containing the recovery files
     * @return an Object Node with the results
     */
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

    /**
     * Delete everything within the give directory
     * @param deletePath path to delete
     */
    @ExcludeFromJacocoGeneratedReport
    void executeDeleteBackup(String deletePath) {
        deleteDirectoryRecursively(new File(deletePath));
    }

    /**
     * Register both backup/restore methods to apply for the given key.
     * @param key the name of the module being backed up or restored.
     * @param backupConsumer method that backs up the modules data
     * @param restoreConsumer method that recovers the module
     */
    public static void registerMethods(String key, TriConsumer<DataAccessPoint, String, ObjectNode> backupConsumer, TriConsumer<DataAccessPoint, String, ObjectNode> restoreConsumer) {
        registerMethod(backupConsumerMap, key, backupConsumer);
        registerMethod(restoreConsumerMap, key, restoreConsumer);
    }

    /**
     * Store a given method in the map by the given key
     * @param map the mapping of methods to store
     * @param key the item for which the method is to be applied for
     * @param consumer the method itself
     */
    private static void registerMethod(Map<String, TriConsumer<DataAccessPoint, String, ObjectNode>> map, String key, TriConsumer<DataAccessPoint, String, ObjectNode> consumer) {
        map.put(key, consumer);
    }

}
