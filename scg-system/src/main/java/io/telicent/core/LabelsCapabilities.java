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
import org.apache.jena.sparql.core.DatasetGraph;

import java.util.Optional;

/**
 * Finds the storage capabilities (backup and restore, compaction) of a dataset's security labels store
 */
public final class LabelsCapabilities {

    private LabelsCapabilities() {
    }

    /**
     * Gets a dataset's security labels store as a given storage capability, via the data security plugin's labelled
     * view of the dataset
     *
     * @param plugin  Data security plugin
     * @param dataset Dataset
     * @param type    Capability required, e.g. {@code BackupRestoreCapable} or {@code CompactCapable}
     * @param <T>     Capability type
     * @return The labels store as that capability, or empty if the dataset has no labels store or it lacks the
     * capability
     */
    public static <T> Optional<T> labelsStoreAs(DataSecurityPlugin plugin, DatasetGraph dataset, Class<T> type) {
        return plugin.prepareLabelledDataset(dataset)
                     .flatMap(DatasetGraphLabelled::labelsStore)
                     .filter(type::isInstance)
                     .map(type::cast);
    }
}
