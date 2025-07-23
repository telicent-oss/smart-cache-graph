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
import io.telicent.backup.utils.EncryptionUtils;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.jena.abac.labels.LabelsStoreRocksDB;
import io.telicent.jena.abac.labels.node.LabelToNodeGenerator;
import io.telicent.model.KeyPair;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.apache.jena.atlas.lib.DateTimeUtils;
import org.apache.jena.fuseki.mgt.Backup;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.StreamRDFLib;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.system.Txn;
import org.bouncycastle.openpgp.PGPException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static io.telicent.backup.utils.BackupConstants.*;
import static io.telicent.backup.utils.BackupUtils.*;
import static io.telicent.backup.utils.BackupUtils.checkPathExistsAndIsDir;
import static io.telicent.backup.utils.CompressionUtils.*;
import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static io.telicent.backup.utils.JsonFileUtils.writeObjectNodeToFile;
import static org.apache.jena.riot.Lang.NQUADS;

public class DatasetBackupService {

    public static final Logger LOG = LoggerFactory.getLogger("DatasetBackupService");

    private final static String BACKUP_SUFFIX = "_backup";

    private final ReentrantLock lock;

    private final DataAccessPointRegistry dapRegistry;

    private final EncryptionUtils encryptionUtils;

    private final KeyPair keyPair;

    final static ConcurrentHashMap<String, TriConsumer<DataAccessPoint, String, ObjectNode>> backupConsumerMap = new ConcurrentHashMap<>();
    final static ConcurrentHashMap<String, TriConsumer<DataAccessPoint, String, ObjectNode>> restoreConsumerMap = new ConcurrentHashMap<>();

    public DatasetBackupService(DataAccessPointRegistry dapRegistry, KeyPair keyPair) throws URISyntaxException, IOException, PGPException {
        LOG.info("Backup encryption is enabled.");
        this.keyPair = keyPair;
        this.encryptionUtils = new EncryptionUtils(keyPair.privateKeyUrl().openStream(), keyPair.passphrase());
        this.dapRegistry = dapRegistry;
        registerMethods("tdb", this::backupTDB, this::restoreTDB);
        registerMethods("labels", this::backupLabelStore, this::restoreLabelStore);
        lock = new ReentrantLock();
    }

    public DatasetBackupService(DataAccessPointRegistry dapRegistry) {
        LOG.warn("Backup encryption is not enabled.");
        this.keyPair = null;
        this.encryptionUtils = null;
        this.dapRegistry = dapRegistry;
        registerMethods("tdb", this::backupTDB, this::restoreTDB);
        registerMethods("labels", this::backupLabelStore, this::restoreLabelStore);
        lock = new ReentrantLock();
    }

    /**
     * Ensures that only one operation is processed at a time
     *
     * @param request  incoming request
     * @param response outgoing response
     * @param backup   flag indicating backup or restore
     */
    public void process(HttpServletRequest request, HttpServletResponse response, boolean backup) {
        // Try to acquire the lock without blocking
        final ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
        if (!lock.tryLock()) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            resultNode.put("error", "Another conflicting operation is already in progress. Please try again later.");
            processResponse(response, resultNode);
        } else {
            try {
                String id = sanitiseName(request.getPathInfo());
                if (!id.isEmpty()) {
                    resultNode.put("backup-name", id);
                } else {
                    resultNode.put("backup-name", "FULL");
                }
                resultNode.put("date", DateTimeUtils.nowAsString(DATE_FORMAT));
                resultNode.put("user", request.getRemoteUser());
                String name = request.getParameter("description");
                if (name != null) {
                    resultNode.put("description", name);
                }
                if (backup) {
                    backupDataset(id, resultNode);
                } else {
                    restoreDatasets(id, resultNode);
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
     *
     * @param datasetName the name of the dataset to back up
     * @param response the node to add metadata of process to
     */
    public void backupDataset(String datasetName, ObjectNode response) {
        ZonedDateTime startTime = ZonedDateTime.now();
        String backupPath = getBackUpDir();
        int backupID = getNextDirectoryNumberAndCreate(backupPath);
        String backupIDPath = backupPath + "/" + backupID;
        response.put("backup-id", backupID);
        response.put("date", DateTimeUtils.nowAsString(DATE_FORMAT));

        ArrayNode datasetNodes = OBJECT_MAPPER.createArrayNode();
        for (DataAccessPoint dataAccessPoint : dapRegistry.accessPoints()) {
            String dataAccessPointName = dataAccessPoint.getName();
            String sanitizedDataAccessPointName = sanitiseName(dataAccessPointName);
            if (requestIsEmpty(datasetName) || sanitizedDataAccessPointName.equalsIgnoreCase(datasetName)) {
                ObjectNode datasetJSON = OBJECT_MAPPER.createObjectNode();
                datasetJSON.put("dataset-name", sanitizedDataAccessPointName);
                applyBackUpMethods(datasetJSON, dataAccessPoint, backupIDPath + "/" + sanitizedDataAccessPointName);
                datasetNodes.add(datasetJSON);
            }
        }
        response.set("datasets", datasetNodes);
        response.put("start-time", startTime.toString());
        compressAndStoreBackupMetadata(response, backupIDPath);
    }

    /**
     * For all registered backup Consumers, apply the backup operation
     *
     * @param moduleJSON      JSON to update with details
     * @param dataAccessPoint the details to  which to apply
     * @param backupPath      the path to backup to
     */
    public void applyBackUpMethods(ObjectNode moduleJSON, DataAccessPoint dataAccessPoint, String backupPath) {
        for (Map.Entry<String, TriConsumer<DataAccessPoint, String, ObjectNode>> entry : backupConsumerMap.entrySet()) {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            String modBackupPath = backupPath + "/" + entry.getKey() + "/";
            node.put("folder", modBackupPath);
            if (!createPathIfNotExists(modBackupPath)) {
                node.put("reason", "Cannot create backup directory: " + modBackupPath);
                node.put("success", false);
            } else {
                try {
                    entry.getValue().accept(dataAccessPoint, modBackupPath, node);
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
     *
     * @param dataAccessPoint Dataset to backup
     * @param backupPath      the folder to back up to
     * @param node            JSON Node to store results
     */
    void backupTDB(DataAccessPoint dataAccessPoint, String backupPath, ObjectNode node) {
        String backupFile = backupPath + sanitiseName(dataAccessPoint.getName()) + BACKUP_SUFFIX;
        DatasetGraph dsg = dataAccessPoint.getDataService().getDataset();
        executeBackupTDB(dsg, backupFile, node);
    }

    /**
     * Wrapping actual back-up call to aid with testings sake.
     *
     * @param dsg        DatasetGraph to back up
     * @param backupFile the file to back up to
     * @param node       JSON Node to store results
     */
    void executeBackupTDB(DatasetGraph dsg, String backupFile, ObjectNode node) {
        Backup.backup(dsg, dsg, backupFile);
        node.put("success", true);
    }

    /**
     * Back up the label store for the given data access point (DSG)
     *
     * @param dataAccessPoint the data access point
     * @param backupPath      the path to back up to
     * @param node            the object node to write the results of the operation too
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
     *
     * @param rocksDB         instance to call
     * @param labelBackupPath path to use
     * @param node            to collect the results
     */
    void executeBackupLabelStore(LabelsStoreRocksDB rocksDB, String labelBackupPath, ObjectNode node) {
        rocksDB.backup(labelBackupPath);
        node.put("success", true);
    }

    /**
     * List all the available back-up files
     *
     * @return Object Node of the results
     */
    public ObjectNode listBackups() {
        return getObjectNodeFromNumberedFiles(getBackUpDir(), JSON_INFO_SUFFIX);
    }

    /**
     * Restore the system with the files located by the ID
     *
     * @param restoreId the subdirectory to use
     * @param response a node of the results
     */
    public void restoreDatasets(String restoreId, ObjectNode response) throws PGPException, IOException {
        String specificDatasetIfAny = "";
        if(restoreId.contains("/")) {
            // obtain specific dataset and strip it out.
            int slashIndex = restoreId.indexOf('/');
            specificDatasetIfAny = restoreId.substring(slashIndex+1);
            restoreId = restoreId.substring(0, slashIndex);
        }

        if (restoreId.isEmpty()) {
            int highestDirNumber = getHighestDirectoryNumber(getBackUpDir());
            restoreId = String.valueOf(highestDirNumber);
        }
        String restorePath = getBackUpDir() + "/" + restoreId;
        response.put("restorePath", restorePath);

        boolean decompressDir = false;
        if (checkPathExistsAndIsFile(restorePath + ZIP_SUFFIX)) {
            unzipDirectory(restorePath + ZIP_SUFFIX, restorePath);
            decompressDir = true;
        } else if (checkPathExistsAndIsFile(restorePath + ZIP_SUFFIX + ENCRYPTION_SUFFIX)) {
            final Path encZipFilePath = Path.of(restorePath + ZIP_SUFFIX + ENCRYPTION_SUFFIX);
            final Path decryptedZipPath = encryptionUtils.decryptFile(encZipFilePath, Path.of(restorePath + ZIP_SUFFIX));
            LOG.debug("Successfully decrypted file: {} as {}", encZipFilePath, decryptedZipPath);
            unzipDirectory(decryptedZipPath, Path.of(restorePath));
            Files.delete(decryptedZipPath);
            decompressDir = true;
        }
        if (!checkPathExistsAndIsDir(restorePath)) {
            response.put("reason", "Restore path unsuitable: " + restorePath);
            response.put("success", false);
        } else {
            List<String> datasets = getSubdirectoryNames(restorePath);
            if (datasets.isEmpty()) {
                response.put("reason", "Restore path unsuitable: " + restorePath);
                response.put("success", false);
            } else {
                boolean noMatches = true;
                boolean successSoFar = true;
                for (String datasetName : datasets) {
                    if (specificDatasetIfAny.isEmpty() || specificDatasetIfAny.equalsIgnoreCase(datasetName)) {
                        noMatches = false;
                        successSoFar = successSoFar && restoreDataset(restorePath, datasetName, response);
                    }
                }
                if(noMatches) {
                    response.put("reason", "No matches for dataset.");
                    response.put("success", false);
                } else {
                    response.put("success", successSoFar);
                }
            }
        }
        if(DELETE_GENERATED_FILES && decompressDir) {
            cleanupDirectory(restorePath);
        }
    }

    /**
     * Restore the data set with the given files location.
     *
     * @param restorePath the location of the back-up files
     * @param datasetName the dataset to apply the changes too.
     * @param responseNode the results of the operation
     * @return the success of the operation
     */
    boolean restoreDataset(String restorePath, String datasetName, ObjectNode responseNode) {
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.put("dataset-name", datasetName);
        responseNode.put(datasetName, response);
        DataAccessPoint dataAccessPoint = getDataAccessPoint(datasetName);
        if (dataAccessPoint == null || dataAccessPoint.getDataService() == null) {
            response.put("reason", datasetName + " does not exist");
            response.put("success", false);
            return false;
        }
        DatasetGraph dsg = dataAccessPoint.getDataService().getDataset();
        try {
            Txn.executeWrite(dsg, () -> applyRestoreMethods(response, dataAccessPoint, restorePath + "/" + datasetName));
        } catch (RuntimeException ex) {
            response.put("reason", ex.getMessage());
            response.put("success", false);
        }
        return true;
    }

    /**
     * Restore the Triple store for a dataset with the given files.
     *
     * @param dataAccessPoint access to the dataset to recover
     * @param restorePath     the location of the recovery files
     * @param node            the results of the operation.
     */
    void restoreTDB(DataAccessPoint dataAccessPoint, String restorePath, ObjectNode node) {
        String tdbRestoreFile = restorePath + "/" + sanitiseName(dataAccessPoint.getName()) + BACKUP_SUFFIX + RDF_BACKUP_SUFFIX;
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
     *
     * @param moduleJSON      JSON to update with details
     * @param dataAccessPoint access to the dataset to which to apply
     * @param restorePath     the path to restore from
     */
    public void applyRestoreMethods(ObjectNode moduleJSON, DataAccessPoint dataAccessPoint, String restorePath) {
        for (Map.Entry<String, TriConsumer<DataAccessPoint, String, ObjectNode>> entry : restoreConsumerMap.entrySet()) {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            String modRestorePath = restorePath + "/" + entry.getKey() + "/";
            node.put("folder", modRestorePath);
            if (!checkPathExistsAndIsDir(modRestorePath)) {
                node.put("reason", "Restore path not found: " + modRestorePath);
                node.put("success", false);
            } else {
                try {
                    entry.getValue().accept(dataAccessPoint, modRestorePath, node);
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
     *
     * @param dsg            dataset to recover
     * @param tdbRestoreFile the zipped file with te recovery data
     * @throws IOException if there's an issue.
     */
    void executeRestoreTDB(DatasetGraph dsg, String tdbRestoreFile) throws IOException {
        try (InputStream fis = new FileInputStream(tdbRestoreFile);
             InputStream gis = new GZIPInputStream(fis)) {
            Txn.executeWrite(dsg, () -> {
                dsg.clear();
                RDFParser.create()
                        .source(gis)
                        .labelToNode(LabelToNodeGenerator.generate())
                        .lang(NQUADS)
                        .parse(StreamRDFLib.dataset(dsg));
            });
        }
    }

    /**
     * Restore the underlying label store of the dataset
     *
     * @param dataAccessPoint access to the dataset
     * @param restorePath     the location of the recovery files
     * @param node            the results of the operation
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
     *
     * @param rocksDB          the rocks db label store
     * @param labelRestorePath the location of the recovery files
     * @param node             the results of the operation
     */
    void executeRestoreLabelStore(LabelsStoreRocksDB rocksDB, String labelRestorePath, ObjectNode node) {
        rocksDB.restore(labelRestorePath);
        node.put("success", true);
    }

    /**
     * For the given ID, delete the recovery files
     *
     * @param deleteID the subdirectory containing the recovery files
     * @return an Object Node with the results
     */
    public ObjectNode deleteBackup(String deleteID) {
        String deletePath = getBackUpDir() + "/" + deleteID;
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.put("delete-id", deleteID);
        response.put("date", DateTimeUtils.nowAsString(DATE_FORMAT));
        response.put("deletePath", deletePath);
        if (!checkPathExistsAndIsDir(deletePath) && !checkPathExistsAndIsFile(deletePath + JSON_INFO_SUFFIX) && !checkPathExistsAndIsFile(deletePath + ZIP_SUFFIX)) {
            response.put("reason", "Backup path unsuitable: " + deletePath);
            response.put("success", false);
        } else {
            executeDeleteBackup(deletePath);
            executeDeleteBackup(deletePath + JSON_INFO_SUFFIX);
            executeDeleteBackup(deletePath + ZIP_SUFFIX);
            deleteFilesRegEx(getBackUpDir(), deleteID + WILDCARD_REPORT_SUFFIX);
            response.put("success", true);
        }
        return response;
    }

    /**
     * Validate all datasets or a specific dataset if specified
     *
     * @param validateParams   a String array of the request path parameters
     * @param shapeInputStream an Input Stream to the SHACL shape file to be used
     * @param response         the servlet response
     * @return an Object Node with the results
     */
    public ObjectNode validateBackup(final String[] validateParams, final InputStream shapeInputStream, final HttpServletResponse response) throws IOException {
        final String validatePath = getBackUpDir() + "/" + validateParams[0];
        final Model shapesModel = getShapeModel(shapeInputStream);
        final Graph shapesGraph = shapesModel.getGraph();
        final ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
        final String datasetName = (validateParams.length > 1) ? "/" + validateParams[1] : "";
        boolean decompressDir = false;
        if (checkPathExistsAndIsFile(validatePath + ZIP_SUFFIX)) {
            unzipDirectory(validatePath + ZIP_SUFFIX, validatePath);
            decompressDir = true;
        }
        if (!checkPathExistsAndIsDir(validatePath + datasetName)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resultNode.put("reason", "Validation path unsuitable: " + validatePath + datasetName);
            resultNode.put("success", false);
        } else {
            final Set<String> datasetDirs = listDirectories(validatePath, validateParams);
            resultNode.put("backup-id", validateParams[0]);
            resultNode.put("date", DateTimeUtils.nowAsString(DATE_FORMAT));
            resultNode.put("validate-path", validatePath + datasetName);
            final ObjectNode datasetResult = OBJECT_MAPPER.createObjectNode();
            for (String datasetDir : datasetDirs) {
                datasetResult.set(datasetDir, executeValidation(validatePath, datasetDir, shapesGraph));
            }
            resultNode.set("results", datasetResult);
        }
        if (decompressDir) {
            cleanupDirectory(validatePath);
        }
        return resultNode;
    }

    /**
     * Retrieve a SHACL validation report for a specific backup and dataset
     *
     * @param backupId    the back-up identifier
     * @param datasetName the dataset name
     * @return the SHACL validation report as a JSON String
     * @throws Exception If error occurs
     */
    public ObjectNode getReport(final String backupId, final String datasetName, final HttpServletResponse response) throws Exception {
        final ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
        final String reportPathString = getBackUpDir() + "/" + backupId + "-" + datasetName + REPORT_SUFFIX;

        if (checkPathExistsAndIsFile(reportPathString)) {
            final Model model = RDFDataMgr.loadModel(reportPathString);
            try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                RDFDataMgr.write(baos, model, Lang.RDFJSON);
                resultNode.put("backup-id", backupId);
                resultNode.put("dataset-name", datasetName);
                return resultNode.set("result", OBJECT_MAPPER.readValue(baos.toString(StandardCharsets.UTF_8), ObjectNode.class));
            }
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resultNode.put("error", "Invalid path or file: " + reportPathString);
            return resultNode;
        }
    }

    /**
     * Display detailed information about a backup
     *
     * @param backupId the back-up identifier
     */
    public ObjectNode getDetails(final String backupId) {
        final ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
        final String detailsPathString = getBackUpDir() + "/" + backupId;
        resultNode.put("backup-id", backupId);
        resultNode.put("details-path", detailsPathString);

        if (checkPathExistsAndIsFile(detailsPathString + ZIP_SUFFIX)) {
            try {
                // size
                long sizeInBytes = Files.size(Path.of(detailsPathString + ZIP_SUFFIX));
                resultNode.put("zip-size-in-bytes", sizeInBytes);
                resultNode.put("zip-size", FileUtils.byteCountToDisplaySize(sizeInBytes));

                // kafka state
                Optional<Integer> kafkaState = readKafkaStateOffsetZip(detailsPathString + ZIP_SUFFIX);
                kafkaState.ifPresent(integer -> resultNode.put("kafka-state", integer));
                // times
                String jsonPath = getBackUpDir() + "/" + backupId + JSON_INFO_SUFFIX;
                Optional<ZonedDateTime> startTime = readTime(jsonPath, "start-time");
                Optional<ZonedDateTime> endTime = readTime(jsonPath, "end-time");
                startTime.ifPresent(time -> resultNode.put("start-time", time.toString()));
                endTime.ifPresent(time -> resultNode.put("end-time", time.toString()));
                if (startTime.isPresent() && endTime.isPresent()) {
                    Duration duration = Duration.between(startTime.get(), endTime.get());
                    resultNode.put("backup-duration", humanReadableDuration(duration));
                    resultNode.put("backup-duration-in-ms", duration.toMillis() );
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return resultNode;
    }

    /**
     * Delete everything within the give directory
     *
     * @param deletePath path to delete
     */
    void executeDeleteBackup(String deletePath) {
        deleteDirectoryRecursively(new File(deletePath));
    }

    /**
     * Register both backup/restore methods to apply for the given key.
     *
     * @param key             the name of the module being backed up or restored.
     * @param backupConsumer  method that backs up the modules data
     * @param restoreConsumer method that recovers the module
     */
    public static void registerMethods(String key, TriConsumer<DataAccessPoint, String, ObjectNode> backupConsumer, TriConsumer<DataAccessPoint, String, ObjectNode> restoreConsumer) {
        registerMethod(backupConsumerMap, key, backupConsumer);
        registerMethod(restoreConsumerMap, key, restoreConsumer);
    }


    /**
     * Strip out any prefix forward slashes and any other dangerous characters.
     *
     * @param name the dataset name
     * @return a cleaned up version or null if null.
     */
    public static String sanitiseName(String name) {
        if (null == name) {
            return "";
        }
        if (name.startsWith("/"))
            name=name.substring(1);

        if (name.endsWith("/"))
            name = name.substring(0, name.length() - 1);

        return name;
    }

    /**
     * Store a given method in the map by the given key
     *
     * @param map      the mapping of methods to store
     * @param key      the item for which the method is to be applied for
     * @param consumer the method itself
     */
    private static void registerMethod(Map<String, TriConsumer<DataAccessPoint, String, ObjectNode>> map, String key, TriConsumer<DataAccessPoint, String, ObjectNode> consumer) {
        map.put(key, consumer);
    }

    private ObjectNode executeValidation(String validatePath, String datasetDir, Graph shapesGraph) {
        final ObjectNode response = OBJECT_MAPPER.createObjectNode();
        try {
            final Path source = Path.of(validatePath, datasetDir, "tdb", datasetDir + BACKUP_SUFFIX + RDF_BACKUP_SUFFIX);
            final List<Path> tempPaths = new ArrayList<>();

            final Graph dataGraph = RDFDataMgr.loadGraph(source.toString());
            final Shapes shapes = Shapes.parse(shapesGraph);

            final ValidationReport report = ShaclValidator.get().validate(shapes, dataGraph);
            writeValidationReport(report, datasetDir, Path.of(validatePath));
            cleanUp(tempPaths);
            response.put("success", true);
        } catch (Exception ex) {
            response.put("reason", (ex.getMessage() == null) ? ex.getClass().getName() : ex.getMessage());
            response.put("success", false);
        }
        return response;
    }

    private Set<String> listDirectories(final String validatePath, String[] validateParams) throws IOException {
        try (Stream<Path> stream = Files.list(Path.of(validatePath))) {
            if (validateParams.length > 1) {
                return stream.filter(Files::isDirectory).map(Path::getFileName).map(Path::toString).filter(n -> n.equals(validateParams[1])).collect(Collectors.toSet());
            } else {
                return stream.filter(Files::isDirectory).map(Path::getFileName).map(Path::toString).collect(Collectors.toSet());
            }
        }
    }

    private Model getShapeModel(final InputStream shapeInputStream) {
        final Model model = ModelFactory.createDefaultModel();
        return model.read(shapeInputStream, null, "TTL");
    }

    private void writeValidationReport(final ValidationReport report, String dataset, Path sourcePath) throws IOException {
        Path reportPath = Path.of(sourcePath + "-" + dataset + REPORT_SUFFIX);
        try (final FileOutputStream fos = new FileOutputStream(reportPath.toString())) {
            RDFDataMgr.write(fos, report.getModel(), Lang.TTL);
        }
    }


    /**
     * Obtain the access point from the registry.
     * We do two passes - a straight check and one
     * using the sanitised name
     *
     * @param datasetName name
     * @return the access point
     */
    private DataAccessPoint getDataAccessPoint(String datasetName) {
        DataAccessPoint accessPoint = dapRegistry.get(datasetName);
        if (null != accessPoint) {
            return accessPoint;
        }
        for (DataAccessPoint accessPointToCheck : dapRegistry.accessPoints()) {
            if (sanitiseName(accessPointToCheck.getName()).equalsIgnoreCase(datasetName)) {
                return accessPointToCheck;
            }
        }
        return null;
    }

    /**
     * Compress the files generated into a single zipped file and write a complimentary metadata file.
     * If encryption is being used then also encrypt the zipped file.
     *
     * @param response JSON object returned to client calls (and used to write metadata)
     * @param dirPath  location of files to compress
     */
    private void compressAndStoreBackupMetadata(ObjectNode response, String dirPath) {
        final Path zipFilePath = zipDirectory(dirPath, dirPath + ZIP_SUFFIX, DELETE_GENERATED_FILES);
        if (encryptionUtils != null) {
            try {
                final Path encZipFilePath = Path.of(dirPath + ZIP_SUFFIX + ENCRYPTION_SUFFIX);
                final Path encZipPath = encryptionUtils.encryptFile(zipFilePath, encZipFilePath, keyPair.publicKeyUrl());
                LOG.debug("Successfully encrypted file: {} as {}", zipFilePath, encZipPath.toString());
                Files.delete(zipFilePath);
            } catch (IOException | PGPException ex) {
                LOG.error("Failed to encrypt backup files due to {}", ex.getMessage(), ex);
            }
        }
        ZonedDateTime endTime = ZonedDateTime.now();
        response.put("end-time", endTime.toString());
        writeObjectNodeToFile(response, dirPath + JSON_INFO_SUFFIX);
    }

    /**
     * A utility method that takes the duration and represents it in
     * a more easy to grasp format.
     * @param duration the number of milliseconds in question
     * @return a string representation in hours. minutes, seconds and milliseconds
     */
    public static String humanReadableDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.toSeconds() % 60;
        long millis = duration.toMillis() % 1000;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0) sb.append(seconds).append("s ");
        if (millis > 0 || sb.isEmpty()) sb.append(millis).append("ms");

        return sb.toString().trim();
    }


}
