package io.telicent.backup.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPlugin;
import io.telicent.smart.cache.storage.*;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestLabelsMaintenance {
    private final DatasetGraph dataset = DatasetGraphFactory.createTxnMem();
    private final DataAccessPoint dap = new DataAccessPoint("/test", DataService.newBuilder().dataset(dataset).build());
    private final DataSecurityPlugin plugin = mock(DataSecurityPlugin.class);
    private final BackupRestoreCapable capability = mock(BackupRestoreCapable.class);
    private final DatasetBackupService service = new DatasetBackupService(null, plugin);
    private final ObjectNode result = new ObjectMapper().createObjectNode();

    @Test
    void genericBackupUsesDatasetCapabilityAndReportsStatus() {
        when(plugin.prepareLabelsBackup(dataset)).thenReturn(java.util.Optional.of(capability));
        when(capability.backup(any())).thenReturn(BackupStatus.builder().success(true).build());
        service.backupLabelStore(dap, "backup", result);
        assertTrue(result.path("success").asBoolean());
        verify(capability).backup(argThat(config -> config.getBackupLocation().equals("backup")));
    }

    @Test
    void backupStatusFailureIsReported() {
        when(plugin.prepareLabelsBackup(dataset)).thenReturn(java.util.Optional.of(capability));
        when(capability.backup(any())).thenReturn(BackupStatus.builder().success(false).errorMessage("disk full").build());
        service.backupLabelStore(dap, "backup", result);
        assertFalse(result.path("success").asBoolean());
        assertEquals("disk full", result.path("reason").asText());
    }

    @Test
    void genericRestoreReportsFailureAndException() {
        when(plugin.prepareLabelsRestore(dataset)).thenReturn(java.util.Optional.of(capability));
        when(capability.restore(any())).thenReturn(RestoreStatus.builder().success(false).errorMessage("bad backup").build());
        service.restoreLabelStore(dap, "backup", result);
        assertFalse(result.path("success").asBoolean());
        assertEquals("bad backup", result.path("reason").asText());
        verify(capability).restore(argThat(config -> config.getBackupLocation().equals("backup")));
        when(capability.restore(any())).thenThrow(new IllegalStateException("restore failed"));
        service.restoreLabelStore(dap, "backup", result);
        assertFalse(result.path("success").asBoolean());
        assertEquals("restore failed", result.path("reason").asText());
    }

    @Test
    void absentCapabilitiesAreReportedWithoutInvokingStorage() {
        service.backupLabelStore(dap, "backup", result);
        assertFalse(result.path("success").asBoolean());
        assertTrue(result.path("reason").asText().contains("backup"));
        service.restoreLabelStore(dap, "backup", result);
        assertFalse(result.path("success").asBoolean());
        assertTrue(result.path("reason").asText().contains("restore"));
        verifyNoInteractions(capability);
    }
}
