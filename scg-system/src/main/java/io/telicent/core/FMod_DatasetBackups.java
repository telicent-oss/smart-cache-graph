package io.telicent.core;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.atlas.io.IOX;
import org.apache.jena.atlas.lib.DateTimeUtils;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.FusekiException;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiAutoModule;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Transactional;
import org.apache.jena.sparql.core.TransactionalNull;
import org.apache.jena.system.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

public class FMod_DatasetBackups implements FusekiAutoModule {

    public static final Logger LOG = LoggerFactory.getLogger("io.telicent.core.FMod_DatasetBackups");
    final Set<String> datasets = new HashSet<>();
    static final boolean DELETE_OLD = true;
    public static final String DISABLE_INITIAL_COMPACTION = "DISABLE_INITIAL_COMPACTION";
    private static final String VERSION = Version.versionForClass(FMod_DatasetBackups.class).orElse("<development>");
    static final Map<String, Long> sizes = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "Dataset Backups";
    }

    @Override
    public void prepare(FusekiServer.Builder builder, Set<String> names, Model configModel) {
        FmtLog.info(Fuseki.configLog, "%s Fuseki Module (%s)", name(), VERSION);
        this.datasets.addAll(names);
    }

    @Override
    public void configured(FusekiServer.Builder serverBuilder, DataAccessPointRegistry dapRegistry, Model configModel) {
        // Create a new dataset endpoint for backing up all datasets
        HttpServlet datasetBackupServlet = new HttpServlet() {
            @Override
            public void doPost(HttpServletRequest req, HttpServletResponse res) {
                try {
                    // Iterate over all registered datasets
                    for (DataAccessPoint dataAccessPoint : dapRegistry.accessPoints()) {
                        DataService dataService = dataAccessPoint.getDataService();
                        backupDatasetGraphDatabase(dataService.getDataset(), dataAccessPoint.getName());
                    }
                } catch (Exception e) {
                    FmtLog.error(Fuseki.configLog, "Error while backing up data points", e);
                }
            }

        };
        serverBuilder.addServlet("/$/backups/*", datasetBackupServlet);
    }

//    @Override
//    public void serverAfterStarting(FusekiServer server) {
//        // Run after starting
//        compactDatabases(server);
//    }
//
//    /**
//     * Compacts the database
//     *
//     * @param server Server
//     */
//    private void compactDatabases(FusekiServer server) {
//        for (String name : datasets) {
//            Optional<DatasetGraph> optionalDatasetGraph = FKS.findDataset(server, name);
//            if (optionalDatasetGraph.isPresent()) {
//                compactDatasetGraphDatabase(optionalDatasetGraph.get(), name);
//            } else {
//                FmtLog.debug(LOG, "Compaction not required for %s as no graph", name);
//            }
//        }
//    }
//
    /**
     * Carries out the actual compaction, provided the size of the TDB has changed since last time
     * @param datasetGraph Dataset Graph
     * @param name Name of dataset
     */
    private static void backupDatasetGraphDatabase(DatasetGraph datasetGraph, String name) {

        String filename = chooseFileName(name);
        backup(/*new TransactionalNull(),*/ datasetGraph, filename);
    }
//
//    /**
//     * Finds the database size on disk (assuming it's a TDB 2 on-disk database)
//     *
//     * @param dsg Graph
//     * @return Size on disk, of {@code -1} if not calculable
//     */
//    public static long findDatabaseSize(DatasetGraph dsg) {
//        if (dsg instanceof DatasetGraphSwitchable switchable) {
//            File dbDir = switchable.getContainerPath().toFile();
//            if (dbDir.exists()) {
//                return FileUtils.sizeOfDirectory(dbDir);
//            }
//        }
//        return -1;
//    }
//
//    /**
//     * Formats a database size (if known) as a human-readable size
//     *
//     * @param size Size, may be less than zero if unknown
//     * @return Human-readable size
//     */
//    public static String humanReadableSize(long size) {
//        if (size < 0) {
//            return "Unknown";
//        } else {
//            return FileUtils.byteCountToDisplaySize(size);
//        }
//    }
//
//    /**
//     * Check the given Graph and, if possible, return the underlying TDB2 instance
//     *
//     * @param dsg Graph
//     * @return TDB2 compatible DSG or null
//     */
//    public static DatasetGraph getTDB2(DatasetGraph dsg) {
//        for (; ; ) {
//            if (IS_TDB_2.test(dsg)) {
//                return dsg;
//            }
//            if (!(dsg instanceof DatasetGraphWrapper dsgw)) {
//                return null;
//            }
//            dsg = dsgw.getWrapped();
//        }
//    }
//
//    private static final Predicate<DatasetGraph> IS_TDB_2 = TDBInternal::isTDB2;

    private static String chooseFileName(String dsName) {
        // Without the "/" - i.e. a relative name.
        String ds = dsName;
        if ( ds.startsWith("/") )
            ds = ds.substring(1);
        if ( ds.contains("/") ) {
            Fuseki.adminLog.warn("Dataset name: weird format: "+dsName);
            // Some kind of fixup
            ds = ds.replace("/",  "_");
        }

        String timestamp = DateTimeUtils.nowAsString("yyyy-MM-dd_HH-mm-ss");
        String filename = ds + "_" + timestamp; Path backupDir = Paths.get("backups");
        filename = backupDir.resolve(filename).toString(); //FusekiWebapp.dirBackups.resolve(filename).toString();
        return filename;
    }

    // Record of all backups so we don't attempt to backup the
    // same dataset multiple times at the same time.
    private static Set<DatasetGraph> activeBackups = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Perform a backup.
     * <p>
     * A backup is a dump of the dataset in compressed N-Quads, done inside a transaction.
     */
    public static void backup(Transactional transactional, DatasetGraph dsg, String backupfile) {
        if ( transactional == null )
            transactional = new TransactionalNull();
        Txn.executeRead(transactional, ()->backup(dsg, backupfile));
    }

    // This seems to achieve about the same as "gzip -6"
    // It's not too expensive in elapsed time but it's not
    // zero cost. GZip, large buffer.
    private static final boolean USE_GZIP = true;

    /**
     * Perform a backup.
     *
     * @see #backup(Transactional, DatasetGraph, String)
     */

    private static void backup(DatasetGraph dsg, String backupfile) {
        if (dsg == null) {
            throw new FusekiException("No dataset provided to backup");
        }

        // Per backup source lock.
        synchronized(activeBackups) {
            // Atomically check-and-set
            if ( activeBackups.contains(dsg) )
                FmtLog.warn(Fuseki.serverLog, "Backup already in progress");
            activeBackups.add(dsg);
        }

        if ( !backupfile.endsWith(".nq") )
            backupfile = backupfile + ".nq";

        if ( USE_GZIP )
            backupfile = backupfile + ".gz";

        try {
            IOX.safeWrite(Path.of(backupfile), outfile -> {
                OutputStream out = outfile;
                if ( USE_GZIP )
                    out = new GZIPOutputStream(outfile, 8 * 1024);
                try (OutputStream out2 = new BufferedOutputStream(out)) {
                    RDFDataMgr.write(out2, dsg, Lang.NQUADS);
                }
            });
        } finally {
            // Remove lock.
            synchronized(activeBackups) {
                activeBackups.remove(dsg);
            }
        }
    }
}
