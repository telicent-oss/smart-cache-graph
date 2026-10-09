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

import io.telicent.smart.cache.security.data.labels.DatasetGraphLabelled;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPlugin;
import io.telicent.smart.cache.storage.BackupRestoreCapable;
import io.telicent.smart.cache.storage.CompactCapable;
import io.telicent.smart.cache.storage.labels.LabelsStore;
import org.apache.jena.sparql.core.DatasetGraph;
import org.mockito.ArgumentMatchers;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test support for tests of labels store maintenance (backup, restore, compaction)
 */
public final class LabelsMaintenanceTestSupport {

    private LabelsMaintenanceTestSupport() {
    }

    /**
     * A labels store that supports all the maintenance capabilities
     */
    public interface MaintainableLabelsStore extends LabelsStore, BackupRestoreCapable, CompactCapable {
    }

    /**
     * Configures the plugin mock so that for the given dataset (or any dataset, including {@code null}, if
     * {@code dataset} is {@code null}) it provides a labelled dataset whose labels store is the given store
     *
     * @param plugin  Plugin mock
     * @param dataset Dataset to match, or {@code null} to match anything
     * @param store   Labels store to expose, may be {@code null} for a labelled dataset without a store
     */
    public static void withLabelsStore(DataSecurityPlugin plugin, DatasetGraph dataset, LabelsStore store) {
        DatasetGraphLabelled labelled = mock(DatasetGraphLabelled.class);
        when(labelled.labelsStore()).thenReturn(Optional.ofNullable(store));
        if (dataset != null) {
            when(plugin.prepareLabelledDataset(dataset)).thenReturn(Optional.of(labelled));
        } else {
            when(plugin.prepareLabelledDataset(ArgumentMatchers.nullable(DatasetGraph.class))).thenReturn(
                    Optional.of(labelled));
        }
    }
}
