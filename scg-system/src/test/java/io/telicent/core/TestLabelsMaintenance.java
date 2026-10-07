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
package io.telicent.core;

import io.telicent.smart.cache.security.data.plugins.DataSecurityPlugin;
import io.telicent.smart.cache.storage.labels.LabelsStore;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TestLabelsMaintenance {

    private final DatasetGraph dataset = DatasetGraphFactory.createTxnMem();
    private final DataSecurityPlugin plugin = mock(DataSecurityPlugin.class);

    @Test
    void givenPluginThatCannotLabelDataset_whenFindingCapabilities_thenEmpty() {
        when(plugin.prepareLabelledDataset(dataset)).thenReturn(Optional.empty());

        assertTrue(LabelsMaintenance.backupRestore(plugin, dataset).isEmpty());
        assertTrue(LabelsMaintenance.compact(plugin, dataset).isEmpty());
    }

    @Test
    void givenLabelledDatasetWithoutStore_whenFindingCapabilities_thenEmpty() {
        LabelsMaintenanceTestSupport.withLabelsStore(plugin, dataset, null);

        assertTrue(LabelsMaintenance.backupRestore(plugin, dataset).isEmpty());
        assertTrue(LabelsMaintenance.compact(plugin, dataset).isEmpty());
    }

    @Test
    void givenStoreWithoutMaintenanceCapabilities_whenFindingCapabilities_thenEmpty() {
        LabelsStore store = mock(LabelsStore.class);
        LabelsMaintenanceTestSupport.withLabelsStore(plugin, dataset, store);

        assertTrue(LabelsMaintenance.backupRestore(plugin, dataset).isEmpty());
        assertTrue(LabelsMaintenance.compact(plugin, dataset).isEmpty());
        verifyNoInteractions(store);
    }

    @Test
    void givenMaintainableStore_whenFindingCapabilities_thenStoreItselfReturnedAndNotClosed() {
        LabelsMaintenanceTestSupport.MaintainableLabelsStore store =
                mock(LabelsMaintenanceTestSupport.MaintainableLabelsStore.class);
        LabelsMaintenanceTestSupport.withLabelsStore(plugin, dataset, store);

        assertSame(store, LabelsMaintenance.backupRestore(plugin, dataset).orElseThrow());
        assertSame(store, LabelsMaintenance.compact(plugin, dataset).orElseThrow());
        verifyNoInteractions(store);
    }
}
