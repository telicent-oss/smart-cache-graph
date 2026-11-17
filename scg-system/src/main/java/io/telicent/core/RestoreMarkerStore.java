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

import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Keeps a durable record, next to a dataset's own files, of a restore that has been applied to it
 * <p>
 * The marker is written before the first of the dataset's stores is touched and removed only when the restore has
 * succeeded, so a marker that is still there on the next startup means the restore never finished.  Such a dataset may
 * have had some of its stores replaced and not others - in particular its security labels may no longer describe its
 * data - so it is <em>quarantined</em>: {@link FMod_DatasetAvailabilityFilter} refuses to serve it, rather than
 * bringing it up as though nothing had happened.  See {@link RestoreMarker} for why the stores can end up out of step.
 * </p>
 * <p>
 * The lifecycle of a marker is:
 * </p>
 * <ul>
 *     <li>{@link #begin(DatasetGraphSwitchable, String, String)} before any store is touched, writing it as
 *     {@link RestoreMarker.State#IN_PROGRESS}, or keeping it {@link RestoreMarker.State#INCOMPLETE} if the dataset was
 *     already quarantined,</li>
 *     <li>{@link #moduleStarted(DatasetGraphSwitchable, String)} as a module begins changing a store in a way that
 *     abandoning the restore's transaction will not undo, and
 *     {@link #moduleRestored(DatasetGraphSwitchable, String)} as each module is applied, so the record says how far
 *     the restore got,</li>
 *     <li>{@link #complete(DatasetGraphSwitchable, boolean, String)} when the restore returns: the marker is removed
 *     if it succeeded, and otherwise left as {@link RestoreMarker.State#INCOMPLETE} if any store had been replaced or
 *     left part way through, or {@link RestoreMarker.State#FAILED} if the restore is known to have changed
 *     nothing.</li>
 *     <li>{@link #recover(DatasetGraphSwitchable, String)} at startup, which turns a marker left
 *     {@link RestoreMarker.State#IN_PROGRESS} by an abnormal termination into
 *     {@link RestoreMarker.State#INCOMPLETE}.</li>
 * </ul>
 * <p>
 * So a dataset is quarantined whenever a restore has begun changing one of its stores without finishing, whether the
 * process survived to say so or not.  A restore that failed without changing anything leaves a record but nothing to
 * protect anyone from, and is not quarantined. This matters, because a restore legitimately reports a failure for a
 * module that has nothing to do, such as the labels of a dataset that has no labels store.
 * </p>
 * <p>
 * A quarantine is lifted by restoring the dataset successfully, and by nothing else. The marker could be manually
 * removed but this is not recommended as this potentially leaves the data in an inconsistent state. For example with
 * security labels that are out of date or data hidden completely due to a lack of security labels.
 * </p>
 * <p>
 * Markers live in the dataset's own TDB2 container directory, alongside the compaction markers written by
 * {@link FMod_InitialCompaction}, so they travel with the data they describe rather than with the backups.
 * </p>
 */
public final class RestoreMarkerStore {

    private static final Logger LOG = LoggerFactory.getLogger(RestoreMarkerStore.class);

    /**
     * Name of the marker file, written in the dataset's TDB2 container directory
     */
    static final String MARKER_FILE = ".restore-status";

    private static final String STATE = "state";
    private static final String DATASET = "dataset";
    private static final String BACKUP_ID = "backupId";
    private static final String STARTED_AT = "startedAt";
    private static final String UPDATED_AT = "updatedAt";
    private static final String STARTED_MODULES = "startedModules";
    private static final String COMPLETED_MODULES = "completedModules";
    private static final String REASON = "reason";

    /**
     * The markers of the datasets this server knows about, mirroring what is on disk. Keyed by dataset identity, as
     * {@link DatasetMaintenanceRegistry} is, so that two servers in the same JVM serving the same dataset name do not
     * see each other's state.
     */
    static final ConcurrentMap<DatasetGraphSwitchable, RestoreMarker> MARKERS = new ConcurrentHashMap<>();

    private RestoreMarkerStore() {
    }

    /**
     * Records that a restore is about to be applied to a dataset
     * <p>
     * This <strong>must</strong> be called before the first of the dataset's stores is touched, since its whole purpose
     * is to be on disk if the service dies part way through.
     * </p>
     *
     * @param dsg         Dataset being restored, may be {@code null} if it is not managed as TDB2
     * @param datasetName Dataset name, as registered
     * @param backupId    Identifier of the backup being restored from
     */
    public static void begin(DatasetGraphSwitchable dsg, String datasetName, String backupId) {
        if (dsg == null) {
            return;
        }
        MARKERS.compute(dsg, (key, current) -> {
            // A dataset that is already quarantined stays quarantined unless this restore puts it right
            final RestoreMarker.State state =
                    current != null && current.quarantines() ? RestoreMarker.State.INCOMPLETE
                                                             : RestoreMarker.State.IN_PROGRESS;
            final RestoreMarker marker = RestoreMarker.starting(state, datasetName, backupId);
            write(key, marker);
            return marker;
        });
    }

    /**
     * Records that one of a restore's modules is about to change the dataset's own store
     * <p>
     * This is for the changes that abandoning the restore's transaction does <em>not</em> undo - replacing the files
     * of the security labels store, in particular.  Once such a module has started, a failure that stops it part way
     * through leaves the store in a state nobody can describe, which withholds the dataset even though the module
     * never reported success.  Modules whose work is transactional, and modules that are skipped because there is
     * nothing for them to do, must not call this: doing so would withhold datasets that are perfectly consistent.
     * </p>
     *
     * @param dsg    Dataset being restored, may be {@code null}
     * @param module Module name, e.g. {@code labels}
     */
    public static void moduleStarted(DatasetGraphSwitchable dsg, String module) {
        if (dsg == null) {
            return;
        }
        MARKERS.computeIfPresent(dsg, (key, current) -> {
            final RestoreMarker marker = current.withModuleStarted(module);
            if (marker != current) {
                write(key, marker);
            }
            return marker;
        });
    }

    /**
     * Records that one of a restore's modules has been applied, so that an interrupted restore says how far it got
     *
     * @param dsg    Dataset being restored, may be {@code null}
     * @param module Module name, e.g. {@code tdb} or {@code labels}
     */
    public static void moduleRestored(DatasetGraphSwitchable dsg, String module) {
        if (dsg == null) {
            return;
        }
        MARKERS.computeIfPresent(dsg, (key, current) -> {
            final RestoreMarker marker = current.withModule(module);
            if (marker != current) {
                write(key, marker);
            }
            return marker;
        });
    }

    /**
     * Records that a restore has returned, one way or the other
     *
     * @param dsg     Dataset that was being restored, may be {@code null}
     * @param success Whether the restore succeeded, which is the only thing that removes a marker
     * @param reason  Why it did not succeed, if it did not
     */
    public static void complete(DatasetGraphSwitchable dsg, boolean success, String reason) {
        if (dsg == null) {
            return;
        }
        MARKERS.compute(dsg, (key, current) -> {
            if (current == null) {
                return null;
            }
            if (success) {
                if (current.quarantines()) {
                    LOG.info("[RESTORE] Dataset {} has been restored successfully and is no longer withheld from "
                                     + "service", current.datasetName());
                }
                delete(key);
                return null;
            }
            // Data is withheld if it has been quarantined or only partially restored. Only a restore that is known to
            // have changed nothing is merely a record.
            final boolean inconsistent = current.quarantines() || !current.changedNothing();
            final RestoreMarker.State state =
                    inconsistent ? RestoreMarker.State.INCOMPLETE : RestoreMarker.State.FAILED;
            final RestoreMarker marker = current.ended(state, reason);
            write(key, marker);
            return marker;
        });
    }

    /**
     * Reads any marker left on disk for a dataset and, if a restore was interrupted, quarantines the dataset
     * <p>
     * Called once per dataset as the server is configured, before it starts serving.
     * </p>
     *
     * @param dsg         Dataset, may be {@code null} if it is not managed as TDB2
     * @param datasetName Dataset name, as registered
     */
    public static void recover(DatasetGraphSwitchable dsg, String datasetName) {
        if (dsg == null) {
            return;
        }
        final Optional<RestoreMarker> found = read(dsg);
        if (found.isEmpty()) {
            MARKERS.remove(dsg);
            return;
        }
        RestoreMarker marker = found.get();
        if (marker.state() == RestoreMarker.State.IN_PROGRESS) {
            marker = marker.ended(RestoreMarker.State.INCOMPLETE,
                                  "The service stopped while this restore was running, so the dataset may have had "
                                          + "some of its stores restored and not others");
            write(dsg, marker);
        }
        MARKERS.put(dsg, marker);
        if (marker.quarantines()) {
            LOG.error("[RESTORE] Dataset {} will not be served: a restore from backup {} started at {} did not "
                              + "complete (modules applied: {}; left part way through: {}). Its data and its security "
                              + "labels may be inconsistent. Restore it again to return it to service.",
                      datasetName, marker.backupId(), marker.startedAt(),
                      marker.completedModules().isEmpty() ? "none" : String.join(", ", marker.completedModules()),
                      marker.unfinishedModules().isEmpty() ? "none" : String.join(", ", marker.unfinishedModules()));
        } else {
            LOG.warn("[RESTORE] Dataset {} has a record of a restore from backup {} that failed at {}: {}",
                     datasetName, marker.backupId(), marker.updatedAt(), marker.reason());
        }
    }

    /**
     * @param dsg Dataset, may be {@code null}
     * @return The marker withholding this dataset from service, if there is one
     */
    public static Optional<RestoreMarker> quarantine(DatasetGraphSwitchable dsg) {
        return marker(dsg).filter(RestoreMarker::quarantines);
    }

    /**
     * @param dsg Dataset, may be {@code null}
     * @return This dataset's marker, whether or not it withholds it from service
     */
    public static Optional<RestoreMarker> marker(DatasetGraphSwitchable dsg) {
        return dsg == null ? Optional.empty() : Optional.ofNullable(MARKERS.get(dsg));
    }

    /**
     * @return Every known marker, for reporting through the backup/restore status API
     */
    public static Collection<RestoreMarker> markers() {
        return List.copyOf(MARKERS.values());
    }

    /**
     * Gets the file a dataset's restore marker is kept in, which sits in the dataset's own TDB2 container directory
     *
     * @param dsg Dataset, may be {@code null}
     * @return The dataset's marker file, which is not guaranteed to exist, or {@code null} if the dataset keeps no
     * files on disk, i.e. it is held in memory, in which case nothing about it outlives the process anyway
     */
    public static File markerFile(DatasetGraphSwitchable dsg) {
        if (dsg == null) {
            return null;
        }
        final Path container = dsg.getContainerPath();
        return container == null ? null : new File(container.toFile(), MARKER_FILE);
    }

    private static Optional<RestoreMarker> read(DatasetGraphSwitchable dsg) {
        final File file = markerFile(dsg);
        if (file == null || !file.isFile()) {
            return Optional.empty();
        }
        final Properties properties = new Properties();
        try (InputStream input = new FileInputStream(file)) {
            properties.load(input);
        } catch (IOException e) {
            LOG.error("[RESTORE] Failed to read the restore marker {}", file, e);
            return Optional.empty();
        }
        final String state = properties.getProperty(STATE);
        final String dataset = properties.getProperty(DATASET);
        if (state == null || dataset == null) {
            LOG.error("[RESTORE] Ignoring the malformed restore marker {}", file);
            return Optional.empty();
        }
        final RestoreMarker.State parsed;
        try {
            parsed = RestoreMarker.State.valueOf(state);
        } catch (IllegalArgumentException e) {
            LOG.error("[RESTORE] Ignoring the restore marker {}, which has an unknown state {}", file, state);
            return Optional.empty();
        }
        return Optional.of(new RestoreMarker(parsed, dataset, properties.getProperty(BACKUP_ID, ""),
                                             properties.getProperty(STARTED_AT, ""),
                                             properties.getProperty(UPDATED_AT, ""),
                                             modules(properties.getProperty(STARTED_MODULES, "")),
                                             modules(properties.getProperty(COMPLETED_MODULES, "")),
                                             properties.getProperty(REASON)));
    }

    private static List<String> modules(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(module -> !module.isEmpty()).toList();
    }

    /**
     * Writes a marker, forcing it to disk before returning
     * <p>
     * The point of a marker is to survive the service dying, including the machine under it, so there is no value in
     * one that is only in the page cache when the first store is replaced.
     * </p>
     *
     * @param dsg    Dataset
     * @param marker Marker to write
     */
    private static void write(DatasetGraphSwitchable dsg, RestoreMarker marker) {
        final File file = markerFile(dsg);
        if (file == null) {
            // An in-memory dataset has nowhere to write to, and nothing about it survives the process in any case
            return;
        }
        final Properties properties = new Properties();
        properties.setProperty(STATE, marker.state().name());
        properties.setProperty(DATASET, marker.datasetName() == null ? "" : marker.datasetName());
        properties.setProperty(BACKUP_ID, marker.backupId() == null ? "" : marker.backupId());
        properties.setProperty(STARTED_AT, marker.startedAt());
        properties.setProperty(UPDATED_AT, marker.updatedAt());
        properties.setProperty(STARTED_MODULES, String.join(",", marker.startedModules()));
        properties.setProperty(COMPLETED_MODULES, String.join(",", marker.completedModules()));
        if (marker.reason() != null) {
            properties.setProperty(REASON, marker.reason());
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            properties.store(output, "Smart Cache Graph restore status - a dataset with this file may be mid-restore");
            output.flush();
            output.getFD().sync();
        } catch (IOException e) {
            LOG.error("[RESTORE] Failed to write the restore marker {}; an interrupted restore of {} will not be "
                              + "detected on the next startup", file, marker.datasetName(), e);
            return;
        }
        syncDirectory(file.getParentFile());
    }

    private static void delete(DatasetGraphSwitchable dsg) {
        final File file = markerFile(dsg);
        if (file == null) {
            return;
        }
        try {
            if (Files.deleteIfExists(file.toPath())) {
                syncDirectory(file.getParentFile());
            }
        } catch (IOException e) {
            LOG.error("[RESTORE] Failed to remove the restore marker {}; the dataset may be withheld from service "
                              + "after a restart even though its restore succeeded", file, e);
        }
    }

    /**
     * Forces a directory entry to disk, so that a marker that has just been created or removed is still there, or
     * still gone, after a power failure
     *
     * @param directory Directory
     */
    private static void syncDirectory(File directory) {
        if (directory == null) {
            return;
        }
        try (FileChannel channel = FileChannel.open(directory.toPath(), StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException e) {
            // Not every platform allows a directory to be opened for this, and the marker itself is already written
            LOG.debug("Could not sync the directory {}", directory, e);
        }
    }

}
