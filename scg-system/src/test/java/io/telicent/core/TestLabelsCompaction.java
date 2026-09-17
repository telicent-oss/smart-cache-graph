package io.telicent.core;

import io.telicent.smart.cache.security.data.DataSecurityException;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPlugin;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPluginLoader;
import io.telicent.smart.cache.storage.CompactCapable;
import io.telicent.smart.cache.storage.CompactStatus;
import io.telicent.smart.cache.storage.CompactException;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestLabelsCompaction {
    @Test
    void genericCompactionUsesStatusAndPropagatesFailure() throws Exception {
        DatasetGraph dataset = DatasetGraphFactory.createTxnMem();
        DataSecurityPlugin plugin = mock(DataSecurityPlugin.class);
        CompactCapable capability = mock(CompactCapable.class);
        when(plugin.prepareLabelsCompact(dataset)).thenReturn(java.util.Optional.of(capability));
        try (var loader = mockStatic(DataSecurityPluginLoader.class)) {
            loader.when(DataSecurityPluginLoader::load).thenReturn(plugin);
            when(capability.compact()).thenReturn(new CompactStatus(10, 5, java.time.Instant.now(), java.time.Instant.now()));
            FMod_InitialCompaction.compactLabels(dataset);
            verify(capability).compact();
            when(capability.compact()).thenThrow(new CompactException("compaction failed"));
            DataSecurityException error = assertThrows(DataSecurityException.class,
                    () -> FMod_InitialCompaction.compactLabels(dataset));
            assertEquals("compaction failed", error.getMessage());
        }
    }
}
