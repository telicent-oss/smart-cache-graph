package io.telicent.core;

import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.jena.abac.labels.LabelsStoreRocksDB;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.apache.jena.atlas.io.IO;
import org.apache.jena.atlas.lib.Timer;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.kafka.FKS;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiAutoModule;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.servlets.ServletOps;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphWrapper;
import org.apache.jena.tdb2.DatabaseMgr;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.apache.jena.tdb2.sys.DatabaseOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FMod_InitialCompaction implements FusekiAutoModule {

    public static final Logger LOG = LoggerFactory.getLogger("io.telicent.core.FMod_InitialCompaction");
    final Set<String> datasets = new HashSet<>();
    static final boolean DELETE_OLD = true;
    public static final String DISABLE_INITIAL_COMPACTION = "DISABLE_INITIAL_COMPACTION";
    private static final String VERSION = Version.versionForClass(FMod_InitialCompaction.class).orElse("<development>");
    /**
     * Intentionally shared between instances of this module so that they can determine whether a compaction is actually
     * needed or not
     */
    static final Map<String, Long> SIZES = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "Initial Compaction";
    }

    @Override
    public void prepare(FusekiServer.Builder builder, Set<String> names, Model configModel) {
        FmtLog.info(Fuseki.configLog, "%s Fuseki Module (%s)", name(), VERSION);
        this.datasets.addAll(names);
    }

    @Override
    public void configured(FusekiServer.Builder serverBuilder, DataAccessPointRegistry dapRegistry, Model configModel) {
        // Create a new dataset endpoint for compacting all datasets
        HttpServlet compactAllServlet = new HttpServlet() {
            @Override
            public void doPost(HttpServletRequest req, HttpServletResponse res) {
                try {
                    // Iterate over all registered datasets
                    for (DataAccessPoint dataAccessPoint : dapRegistry.accessPoints()) {
                        DataService dataService = dataAccessPoint.getDataService();
                        compactDatasetGraphDatabase(dataService.getDataset(), dataAccessPoint.getName());
                    }
                } catch (Exception e) {
                    FmtLog.error(Fuseki.configLog, "Error while compacting data points", e);
                    ServletOps.errorOccurred(e.getMessage());
                }
            }

        };
        serverBuilder.addServlet("/$/compactall", compactAllServlet);
    }

    @Override
    public void serverAfterStarting(FusekiServer server) {
        // Run after starting
        compactDatabases(server);
    }

    /**
     * Compacts the database
     *
     * @param server Server
     */
    private void compactDatabases(FusekiServer server) {
        for (String name : datasets) {
            Optional<DatasetGraph> optionalDatasetGraph = FKS.findDataset(server, name);
            if (optionalDatasetGraph.isPresent()) {
                compactDatasetGraphDatabase(optionalDatasetGraph.get(), name);
            } else {
                FmtLog.debug(LOG, "Compaction not required for %s as no graph", name);
            }
        }
    }

    /**
     * Carries out the actual compaction, provided the size of the TDB has changed since last time
     *
     * @param datasetGraph Dataset Graph
     * @param name         Name of dataset
     */
    private static void compactDatasetGraphDatabase(DatasetGraph datasetGraph, String name) {
        DatasetGraphSwitchable dsg = getTDB2(datasetGraph);
        if (dsg != null) {
            // See how big the database is, and whether it's size has changed
            // NB - Due to how the module is registered twice (see SmartCacheGraph) we'll get called twice, once
            //      before Kafka connectors are started, and again after they are started, this gives us two
            //      opportunities to compact stuff
            long sizeBefore = findDatabaseSize(dsg);
            if (SIZES.containsKey(name)) {
                if (sizeBefore <= SIZES.get(name)) {
                    FmtLog.info(LOG,
                                "[Compaction] Additional compaction not required for %s as it is already maximally compacted at %s (%d)",
                                name, humanReadableSize(sizeBefore), sizeBefore);
                    return;
                }
            }

            // To avoid redundant work when we complete a compaction we record the compacted size in a file on the
            // filesystem, that way we can check whether the currently observed size is different from the previously
            // completed compaction size and if not skip compaction
            // If the size is different then we know the database has new data written to it and can potentially benefit
            // from further compaction
            long previousCompactionSize = findPreviousCompactionSize(dsg);
            if (previousCompactionSize == sizeBefore) {
                FmtLog.info(LOG,
                            "[Compaction] Additional compaction not required for %s as it was already maximally compacted by a prior compaction at %s (%d)",
                            name, humanReadableSize(sizeBefore), sizeBefore);
                return;
            }

            try {
                // Due to known issue - obtain a write lock prior to compaction.
                // If it fails, stop processing to avoid deadlock.

                if (!dsg.tryExclusiveMode(false)) {
                    FmtLog.info(LOG,
                                "[Compaction] Ignoring for %s due to potential deadlock operation", name);
                    return;
                }
                FmtLog.info(LOG, "[Compaction] >>>> Start compact %s, current size is %s (%d)", name,
                            humanReadableSize(sizeBefore), sizeBefore);
                Timer timer = new Timer();
                timer.startTimer();
                DatabaseMgr.compact(dsg, DELETE_OLD);
                long sizeAfter = findDatabaseSize(dsg);
                FmtLog.info(LOG, "[Compaction] <<<< Finish compact %s. Took %s seconds.  Compacted size is %s (%d)",
                            name, Timer.timeStr(timer.endTimer()), humanReadableSize(sizeAfter), sizeAfter);
                SIZES.put(name, sizeAfter);
                updateCompactionResultsFile(dsg, sizeAfter);
                compactLabels(datasetGraph);
            } finally {
                dsg.finishExclusiveMode();
            }
        } else {
            FmtLog.debug(LOG, "Compaction not required for %s as not TDB2", name);
        }
    }

    /**
     * Updates the compaction results file for the database (if supported)
     *
     * @param dsg  Dataset Graph
     * @param size Compacted size to record
     */
    private static void updateCompactionResultsFile(DatasetGraphSwitchable dsg, long size) {
        try (FileOutputStream output = new FileOutputStream(getPreviousCompactionResultFile(dsg))) {
            output.write(Long.toString(size).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            FmtLog.warn(LOG,
                        "[Compaction] Unable to write compaction results file - {} - unnecessary compactions may occur in a future as a result",
                        e.getMessage());
        }
    }

    /**
     * Finds the database size on disk (assuming it's a TDB 2 on-disk database)
     *
     * @param dsg Graph
     * @return Current size on disk, or {@code -1} if does not exist or otherwise not calculable
     */
    public static long findDatabaseSize(DatasetGraph dsg) {
        if (dsg instanceof DatasetGraphSwitchable switchable) {
            // Find the current Data-NNNN directory as this represents the current database size
            Path currentDataDir = DatabaseOps.findStorageLocation(switchable.getContainerPath());
            if (currentDataDir == null) {
                return -1;
            }
            if (currentDataDir.toFile().exists()) {
                return FileUtils.sizeOfDirectory(currentDataDir.toFile());
            }
        }
        return -1;
    }

    /**
     * Finds the previously recorded compacted database size (if any)
     *
     * @param dsg Dataset Graph
     * @return Previous compaction size, {@code -1} if unknown or not a database type that is supported
     */
    public static long findPreviousCompactionSize(DatasetGraph dsg) {
        if (dsg instanceof DatasetGraphSwitchable switchable) {
            File prevCompactionFile = getPreviousCompactionResultFile(switchable);
            if (prevCompactionFile.exists() && prevCompactionFile.isFile()) {
                try (InputStream input = new FileInputStream(prevCompactionFile)) {
                    return Long.parseLong(IO.readWholeFileAsUTF8(input).strip());
                } catch (IOException | NumberFormatException e) {
                    // Ignore and treat as if we don't know the previously compacted size
                    return -1;
                }
            }
        }
        return -1;
    }

    /**
     * Gets the compaction results file used to store previously compacted size
     *
     * @param switchable Dataset Graph
     * @return Results file, no guarantee that this file exists or is valid
     */
    public static File getPreviousCompactionResultFile(DatasetGraphSwitchable switchable) {
        return new File(switchable.getContainerPath().toFile(), ".last-compaction");
    }

    /**
     * Formats a database size (if known) as a human-readable size
     *
     * @param size Size, may be less than zero if unknown
     * @return Human-readable size
     */
    public static String humanReadableSize(long size) {
        if (size < 0) {
            return "Unknown";
        } else {
            return FileUtils.byteCountToDisplaySize(size);
        }
    }

    /**
     * Check the given Graph and, if possible, return the underlying TDB2 instance
     *
     * @param dsg Graph
     * @return TDB2 compatible DSG or null
     */
    public static DatasetGraphSwitchable getTDB2(DatasetGraph dsg) {
        for (; ; ) {
            if (dsg instanceof DatasetGraphSwitchable datasetGraphSwitchable) {
                return datasetGraphSwitchable;
            }
            if (!(dsg instanceof DatasetGraphWrapper dsgw)) {
                return null;
            }
            dsg = dsgw.getWrapped();
        }
    }

    public static void compactLabels(DatasetGraph dsg) {
        if (dsg instanceof DatasetGraphABAC abac) {
            LabelsStore labelsStore = abac.labelsStore();
            if (labelsStore instanceof LabelsStoreRocksDB rocksDB) {
                Timer timer = new Timer();
                timer.startTimer();
                FmtLog.info(LOG, "[Compaction] <<<< Start label store compaction.");
                rocksDB.compact();
                FmtLog.info(LOG, "[Compaction] <<<< Finish label store compaction. Took %s seconds.",
                            Timer.timeStr(timer.endTimer()));
                return;
            }
        }
        FmtLog.info(LOG, "[Compaction] <<<< Label store compaction not needed.");
    }
}
