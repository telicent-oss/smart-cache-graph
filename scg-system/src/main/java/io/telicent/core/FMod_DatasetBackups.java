package io.telicent.core;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletOutputStream;
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
import org.apache.jena.riot.WebContent;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Transactional;
import org.apache.jena.sparql.core.TransactionalNull;
import org.apache.jena.system.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

public class FMod_DatasetBackups implements FusekiAutoModule {

    public static final Logger LOG = LoggerFactory.getLogger("FMod_DatasetBackups");

    final Set<String> datasets = new HashSet<>();

    private static final String VERSION = Version.versionForClass(FMod_DatasetBackups.class).orElse("<development>");

    private static Path dirBackups;

    private static final boolean USE_GZIP = true;

    private static final Set<DatasetGraph> activeBackups = Collections.newSetFromMap(new ConcurrentHashMap<>());

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

        //if (dirBackups == null)
        dirBackups = getDirBackups();

        // Create a new dataset endpoint for backing up datasets
        HttpServlet backupsServlet = new HttpServlet() {
            @Override
            public void doPost(HttpServletRequest req, HttpServletResponse res) {

                try {

                    backupDatasetGraphDatabase(dapRegistry, req, res);
                } catch (Exception e) {

                    FmtLog.error(Fuseki.configLog, "Error while backing up data points", e);
                }

            }

        };
        serverBuilder.addServlet("/$/backups/*", backupsServlet);
    }

    private  void backupDatasetGraphDatabase(DataAccessPointRegistry dapRegistry, HttpServletRequest req, HttpServletResponse res ) {

        ArrayList<String> backedUpFiles = new ArrayList<>();

        dirBackups.toFile().mkdir();

        for (DataAccessPoint dataAccessPoint : dapRegistry.accessPoints()) {
            DataService dataService = dataAccessPoint.getDataService();

            String dataAccessPointName = dataAccessPoint.getName();
            String requestDatasetName = req.getPathInfo();

            if (dataAccessPointName.equals(requestDatasetName)) {
                String fFilename = chooseBaseFileName(dataAccessPoint.getName());
                backedUpFiles.add(fFilename);
                fFilename = dirBackups.resolve(fFilename).toString();
                backup(new TransactionalNull(), dataService.getDataset(), fFilename);
            }
        }

        processResponse(res, backedUpFiles);

    }

    private static void processResponse(HttpServletResponse res, ArrayList<String> backedUpFiles) {
        ObjectMapper mapper = new ObjectMapper();
        String jsonOutput = "[]";
        try (ServletOutputStream out = res.getOutputStream()){

            jsonOutput = mapper.writeValueAsString(backedUpFiles);

            res.setContentLength(jsonOutput.length());
            res.setContentType(WebContent.contentTypeJSON);
            res.setCharacterEncoding(WebContent.charsetUTF8);

            out.print(jsonOutput);

        } catch (JsonProcessingException e) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);

        } catch (IOException ex) {
            res.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
        }

    }

    private Path getDirBackups() {
        String dirBackupStr = System.getenv("ENV_BACKUPS_DIR");

        if (dirBackupStr == null) {
            dirBackupStr = System.getenv("PWD") + "/backups";
            File dir = new File(dirBackupStr);
            dir.mkdir();
            FmtLog.info(Fuseki.serverLog, "ENV_BACKUPS_DIR not set!!. Backups folder set to [default] : /backups");
            return Path.of(dirBackupStr);
        }

        File dir = new File(dirBackupStr);
        if(!dir.exists()) {
            if (dir.mkdir()) {

                FmtLog.info(Fuseki.serverLog, "ENV_BACKUPS_DIR : /%s", dirBackupStr);
                return Path.of(dirBackupStr);
            } else {

                FmtLog.info(Fuseki.serverLog, "ENV_BACKUPS_DIR invalid!!. Backups folder set to [default] : /backups");
                dirBackupStr = System.getenv("PWD") + "/backups";
                return Path.of(dirBackupStr);
            }
        }

        FmtLog.info(Fuseki.serverLog, "ENV_BACKUPS_DIR already exists. Backups folder set to /%s", dirBackupStr);
        return Path.of(dirBackupStr);
    }

    private String chooseBaseFileName(String dsName) {
        // Without the "/" - i.e. a relative name.
        String ds = dsName;
        if ( ds.startsWith("/") )
            ds = ds.substring(1);
        if ( ds.contains("/") ) {
            Fuseki.configLog.warn("Dataset name: weird format: "+dsName);
            // Some kind of fixup
            ds = ds.replace("/",  "_");
        }

        String timestamp = DateTimeUtils.nowAsString("yyyy-MM-dd_HH-mm-ss");
        String filename = ds + "_" + timestamp;
        //filename = dirBackups.resolve(filename).toString();
        return filename;
    }

    /**
     * Perform a backup.
     * <p>
     * A backup is a dump of the dataset in compressed N-Quads, done inside a transaction.
     */
    public void backup(Transactional transactional, DatasetGraph dsg, String backupFile) {
        if ( transactional == null )
            transactional = new TransactionalNull();
        Txn.executeRead(transactional, ()->backup(dsg, backupFile));
    }

    private  void backup(DatasetGraph dsg, String backupfile) {
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
