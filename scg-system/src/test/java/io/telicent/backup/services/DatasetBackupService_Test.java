package io.telicent.backup.services;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.core.LabelsMaintenanceTestSupport;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPlugin;
import io.telicent.smart.cache.storage.BackupConfig;
import io.telicent.smart.cache.storage.BackupStatus;
import io.telicent.smart.cache.storage.RestoreConfig;
import io.telicent.smart.cache.storage.RestoreStatus;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.sparql.core.DatasetGraph;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Class that extends DatasetBackupService purely for testing purposes
 */
public class DatasetBackupService_Test extends DatasetBackupService {
    public static final String BACKUP_TDB = "executeBackupTDB";
    public static final String BACKUP_LABELS = "executeBackupLabelStore";
    public static final String RESTORE_TDB = "executeRestoreTDB";
    public static final String RESTORE_LABELS = "executeRestoreLabelStore";
    public static final String DELETE_BACKUP_DIR = "executeDeleteBackup";

    public static Map<String, Integer> callCounts = new HashMap<>();
    public static Map<String, String> throwExceptions = new HashMap<>();

    private static final DataSecurityPlugin MOCK_DATA_SECURITY_PLUGIN = mock(DataSecurityPlugin.class);

    static {
        final var capability = mock(LabelsMaintenanceTestSupport.MaintainableLabelsStore.class);
        LabelsMaintenanceTestSupport.withLabelsStore(MOCK_DATA_SECURITY_PLUGIN, null, capability);
        when(capability.backup(any(BackupConfig.class))).thenAnswer(invocation -> {
            incrementMethodCall(BACKUP_LABELS);
            throwExceptionIfNeeded(BACKUP_LABELS);
            return BackupStatus.builder().success(true).build();
        });
        when(capability.restore(any(RestoreConfig.class))).thenAnswer(invocation -> {
            incrementMethodCall(RESTORE_LABELS);
            throwExceptionIfNeeded(RESTORE_LABELS);
            return RestoreStatus.builder().success(true).build();
        });
    }

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
        super(dapRegistry, MOCK_DATA_SECURITY_PLUGIN);
    }

    public static void clear() {
        callCounts.clear();
        throwExceptions.clear();
    }

    @Override
    void executeBackupTDB(DatasetGraph dsg, String backupFile, ObjectNode node) {
        // NO-OP
        incrementMethodCall(BACKUP_TDB);
        throwExceptionIfNeeded(BACKUP_TDB);
        node.put("success", true);
    }

    @Override
    void executeRestoreTDB(DatasetGraph dsg, String tdbRestoreFile) {
        // NO-OP
        incrementMethodCall(RESTORE_TDB);
        throwExceptionIfNeeded(RESTORE_TDB);
    }

    @Override
    void executeDeleteBackup(String deletePath) {
        // NO-OP
        incrementMethodCall(DELETE_BACKUP_DIR);
        throwExceptionIfNeeded(DELETE_BACKUP_DIR);
    }

}