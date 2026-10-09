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

import java.util.Optional;

/**
 * Helper for finding the storage maintenance capabilities, i.e. backup/restore and compaction, of the security labels
 * store that belongs to a dataset
 * <p>
 * The labels store remains owned by the dataset, callers <strong>MUST NOT</strong> close it.
 * </p>
 */
public final class LabelsMaintenance {

    private LabelsMaintenance() {
    }

    /**
     * Gets the backup/restore capability of the labels store of the given dataset
     *
     * @param plugin Data security plugin
     * @param dsg    Dataset, may be {@code null}
     * @return Capability, or empty if the dataset has no labels store, or its store does not support backup/restore
     */
    public static Optional<BackupRestoreCapable> backupRestore(DataSecurityPlugin plugin, DatasetGraph dsg) {
        return labelsStore(plugin, dsg).filter(BackupRestoreCapable.class::isInstance)
                                       .map(BackupRestoreCapable.class::cast);
    }

    /**
     * Gets the compaction capability of the labels store of the given dataset
     *
     * @param plugin Data security plugin
     * @param dsg    Dataset, may be {@code null}
     * @return Capability, or empty if the dataset has no labels store, or its store does not support compaction
     */
    public static Optional<CompactCapable> compact(DataSecurityPlugin plugin, DatasetGraph dsg) {
        return labelsStore(plugin, dsg).filter(CompactCapable.class::isInstance).map(CompactCapable.class::cast);
    }

    private static Optional<LabelsStore> labelsStore(DataSecurityPlugin plugin, DatasetGraph dsg) {
        return plugin.prepareLabelledDataset(dsg).flatMap(DatasetGraphLabelled::labelsStore);
    }
}
