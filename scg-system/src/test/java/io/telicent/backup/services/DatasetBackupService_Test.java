package io.telicent.backup.services;

import io.telicent.jena.abac.labels.LabelsStoreRocksDB;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.sparql.core.DatasetGraph;

import java.util.HashMap;
import java.util.Map;

/**
 * Class that extends DatasetBackupService purely for testing purposes
 */
public class DatasetBackupService_Test extends DatasetBackupService {
    public final static String BACKUP_TDB = "executeBackupTDB";
    public final static String BACKUP_LABELS = "executeBackupLabelStore";
    public final static String RESTORE_TDB = "executeRestoreTDB";
    public final static String RESTORE_LABELS = "executeRestoreLabelStore";
    public final static String DELETE_BACKUP_DIR = "executeDeleteBackup";

    public static Map<String, Integer> callCounts = new HashMap<>();
    public static Map<String, String> throwExceptions = new HashMap<>();

    public static void setupExceptionForMethod(String method, String message) {
        throwExceptions.put(method, message);
    }

    public static void throwExceptionIfNeeded(String method) {
        if (throwExceptions.containsKey(method)) {
            throw new RuntimeException(throwExceptions.get(method));
        }
    }

    public static void incrementMethodCall(String methodName) {
        callCounts.compute(methodName, (key, value) -> value == null ? 1 : value + 1);
    }

    public static int getCallCount(String methodName) {
        return callCounts.getOrDefault(methodName, 0);
    }

    // Review and replace with a mock
    public DatasetBackupService_Test(DataAccessPointRegistry dapRegistry) {
        super(dapRegistry);
    }

    public static void clear() {
        callCounts.clear();
        throwExceptions.clear();
    }

    @Override
    void executeBackupTDB(DatasetGraph dsg, String backupFile) {
        // NO-OP
        incrementMethodCall(BACKUP_TDB);
        throwExceptionIfNeeded(BACKUP_TDB);
    }

    @Override
    void executeBackupLabelStore(LabelsStoreRocksDB rocksDB, String labelBackupPath) {
        // NO-OP
        incrementMethodCall(BACKUP_LABELS);
        throwExceptionIfNeeded(BACKUP_LABELS);
    }

    @Override
    void executeRestoreTDB(DatasetGraph dsg, String tdbRestoreFile) {
        // NO-OP
        incrementMethodCall(RESTORE_TDB);
        throwExceptionIfNeeded(RESTORE_TDB);
    }

    @Override
    void executeRestoreLabelStore(LabelsStoreRocksDB rocksDB, String labelRestorePath) {
        // NO-OP
        incrementMethodCall(RESTORE_LABELS);
        throwExceptionIfNeeded(RESTORE_LABELS);
    }

    @Override
    void executeDeleteBackup(String deletePath) {
        // NO-OP
        incrementMethodCall(DELETE_BACKUP_DIR);
        throwExceptionIfNeeded(DELETE_BACKUP_DIR);
    }
}