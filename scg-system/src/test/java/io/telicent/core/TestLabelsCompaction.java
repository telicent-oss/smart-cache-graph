package io.telicent.core;

import io.telicent.smart.cache.security.data.DataSecurityException;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPlugin;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPluginLoader;
import io.telicent.smart.cache.storage.CompactException;
import io.telicent.smart.cache.storage.CompactStatus;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TestLabelsCompaction {

    @Test
    void genericCompactionUsesStatusAndPropagatesFailure() throws Exception {
        final DatasetGraph dataset = DatasetGraphFactory.createTxnMem();
        final var plugin = mock(DataSecurityPlugin.class);
        final var capability = mock(LabelsMaintenanceTestSupport.MaintainableLabelsStore.class);
        LabelsMaintenanceTestSupport.withLabelsStore(plugin, dataset, capability);
        try (var loader = mockStatic(DataSecurityPluginLoader.class)) {
            loader.when(DataSecurityPluginLoader::load).thenReturn(plugin);
            when(capability.compact()).thenReturn(new CompactStatus(10, 5, java.time.Instant.now(), java.time.Instant.now()));
            FMod_InitialCompaction.compactLabels(dataset);
            verify(capability).compact();
            when(capability.compact()).thenThrow(new CompactException("compaction failed"));
            final DataSecurityException error = assertThrows(DataSecurityException.class,
                    () -> FMod_InitialCompaction.compactLabels(dataset));
            assertEquals("compaction failed", error.getMessage());
        }
    }

}
