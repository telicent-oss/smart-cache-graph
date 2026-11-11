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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;

/**
 * A durable record that a restore has been applied to a dataset, and how far it got
 * <p>
 * A restore replaces each of the dataset's stores in turn, and they are not replaced by the same mechanism: the triple
 * store is restored inside a transaction that is rolled back if it never commits, while the security labels store is
 * closed, replaced on disk and reopened, which no transaction can undo. Any abnormal termination part way through
 * therefore leaves the dataset with some stores restored and others not, with the labels potentially describing data
 * that is no longer there. Without a record on disk there is no way, on the next startup, to tell such a dataset from
 * a cleanly restored one.
 * </p>
 * <p>
 * This record is what is written to disk. See {@link RestoreMarkerStore} for where it is kept and how its lifecycle
 * maps onto a restore.
 * </p>
 *
 * @param state            How the restore this marker describes ended, if it has
 * @param datasetName      Name of the dataset being restored, as registered, e.g. {@code /knowledge}
 * @param backupId         Identifier of the backup being restored from, may be empty if it could not be determined
 * @param startedAt        When the restore started, as an ISO-8601 instant
 * @param updatedAt        When this marker was last written, as an ISO-8601 instant
 * @param startedModules   Names of the restore modules that had begun changing the dataset's own store when this
 *                         marker was last written. A module that is skipped never appears here, so this says what might
 *                         have been changed.
 * @param completedModules Names of the restore modules, e.g. {@code tdb} and {@code labels}, that had been applied
 *                         when this marker was last written
 * @param reason           Why the restore did not complete, or {@code null} while it is still running
 */
public record RestoreMarker(State state,
                            String datasetName,
                            String backupId,
                            String startedAt,
                            String updatedAt,
                            List<String> startedModules,
                            List<String> completedModules,
                            String reason) {

    /**
     * The state of the restore a marker describes
     */
    public enum State {
        /**
         * A restore is running right now. A marker left in this state is one whose restore never got to say how it
         * ended, i.e. the service died while it was running, and is treated as {@link #INCOMPLETE} when found at
         * startup.
         */
        IN_PROGRESS,
        /**
         * The restore did not leave the dataset in a consistent state: either it was interrupted, or it failed after
         * it had already replaced one of the dataset's stores.  A dataset with a marker in this state is not served
         * until the restore is repeated successfully.
         */
        INCOMPLETE,
        /**
         * The restore failed without having begun to change any of the dataset's stores, so there is nothing
         * inconsistent about it. The failure was reported to whoever asked for the restore, so this is a record
         * rather than a reason to stop serving the dataset.
         */
        FAILED
    }

    public RestoreMarker {
        startedModules = startedModules == null ? List.of() : List.copyOf(startedModules);
        completedModules = completedModules == null ? List.of() : List.copyOf(completedModules);
    }

    /**
     * @return Whether a dataset carrying this marker should be withheld from service
     */
    public boolean quarantines() {
        return state == State.INCOMPLETE;
    }

    /**
     * Modules that began changing the dataset's own store and never reported that they had finished
     * <p>
     * Such a module has left the store in a state nobody can describe: a labels store, for instance, is restored by
     * closing it, replacing its files and reopening it, so a failure part way through leaves some of the old files and
     * some of the new. That is the same inconsistency an interrupted restore produces, and it withholds the dataset
     * for the same reason, even though no module ever reported success.
     * </p>
     *
     * @return Names of the modules that started and did not complete, which is empty if there are none
     */
    public List<String> unfinishedModules() {
        return startedModules.stream().filter(module -> !completedModules.contains(module)).toList();
    }

    /**
     * @return Whether this restore is known not to have changed any of the dataset's stores
     */
    public boolean changedNothing() {
        return completedModules.isEmpty() && startedModules.isEmpty();
    }

    /**
     * Creates a marker for a restore that is about to start
     *
     * @param state       Initial state, which is {@link State#INCOMPLETE} if the dataset was already quarantined
     * @param datasetName Dataset name
     * @param backupId    Backup being restored from
     * @return Marker
     */
    public static RestoreMarker starting(State state, String datasetName, String backupId) {
        final String now = Instant.now().toString();
        return new RestoreMarker(state, datasetName, backupId, now, now, List.of(), List.of(), null);
    }

    /**
     * Records that a module is about to change the dataset's own store, in a way that abandoning the restore's
     * transaction will not undo
     *
     * @param module Name of the restore module
     * @return A copy of this marker that records the module as started, timestamped now
     */
    public RestoreMarker withModuleStarted(String module) {
        if (startedModules.contains(module)) {
            return this;
        }
        final List<String> modules = Stream.concat(startedModules.stream(), Stream.of(module)).toList();
        return new RestoreMarker(state, datasetName, backupId, startedAt, Instant.now().toString(), modules,
                                 completedModules, reason);
    }

    /**
     * @param module Name of a restore module that has just been applied
     * @return A copy of this marker that records the module as applied, timestamped now
     */
    public RestoreMarker withModule(String module) {
        if (completedModules.contains(module)) {
            return this;
        }
        final List<String> modules = Stream.concat(completedModules.stream(), Stream.of(module)).toList();
        return new RestoreMarker(state, datasetName, backupId, startedAt, Instant.now().toString(), startedModules,
                                 modules, reason);
    }

    /**
     * @param newState New state
     * @param why      Why the restore ended in that state
     * @return A copy of this marker in the given state, timestamped now
     */
    public RestoreMarker ended(State newState, String why) {
        return new RestoreMarker(newState, datasetName, backupId, startedAt, Instant.now().toString(), startedModules,
                                 completedModules, why);
    }

    /**
     * @return This marker as JSON, for reporting through the backup/restore status API
     */
    public ObjectNode toJson() {
        final ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("dataset-name", datasetName);
        node.put("state", state.name());
        node.put("serving", !quarantines());
        if (backupId != null && !backupId.isEmpty()) {
            node.put("backup-id", backupId);
        }
        node.put("started-at", startedAt);
        node.put("updated-at", updatedAt);
        final ArrayNode modules = node.putArray("completed-modules");
        completedModules.forEach(modules::add);
        final List<String> unfinished = unfinishedModules();
        if (!unfinished.isEmpty()) {
            // Only reported when there are any: these are the stores nobody can describe the state of, which is what
            // an operator needs to know before deciding to put the dataset back into service
            final ArrayNode unfinishedModules = node.putArray("unfinished-modules");
            unfinished.forEach(unfinishedModules::add);
        }
        if (reason != null && !reason.isEmpty()) {
            node.put("reason", reason);
        }
        return node;
    }

}
