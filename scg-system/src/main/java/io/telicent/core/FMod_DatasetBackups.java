package io.telicent.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.telicent.smart.cache.configuration.Configurator;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.atlas.lib.DateTimeUtils;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiAutoModule;
import org.apache.jena.fuseki.mgt.Backup;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.servlets.ServletOps;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.WebContent;
import org.apache.jena.sparql.core.DatasetGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * A Fuseki Module for managing the back-up of the TDB.
 */
public class FMod_DatasetBackups implements FusekiAutoModule {

    public static final Logger LOG = LoggerFactory.getLogger("FMod_DatasetBackups");

    final Set<String> datasets = new HashSet<>();

    private static final String VERSION = Version.versionForClass(FMod_DatasetBackups.class).orElse("<development>");

    static Path dirBackups;

    public static final String ENV_BACKUPS_DIR = "ENV_BACKUPS_DIR";
    public static final String DISABLE_BACKUP = "DISABLE_BACKUP";

    static ObjectMapper MAPPER = new ObjectMapper();

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
        // Create a new dataset endpoint for backing up datasets
        HttpServlet backupsServlet = new HttpServlet() {
            @Override
            public void doPost(HttpServletRequest req, HttpServletResponse res) {
                try {
                    backupDatasetGraphDatabase(dapRegistry, req, res);
                } catch (Exception e) {
                    FmtLog.error(Fuseki.configLog, "Error while backing up data points", e);
                    ServletOps.errorOccurred(e.getMessage());
                }
            }

        };
        serverBuilder.addServlet("/$/backups/*", backupsServlet);
    }

    private void backupDatasetGraphDatabase(DataAccessPointRegistry dapRegistry, HttpServletRequest req, HttpServletResponse res) {
        ArrayList<String> backedUpFiles = new ArrayList<>();
        String requestDatasetName = req.getPathInfo();
        for (DataAccessPoint dataAccessPoint : dapRegistry.accessPoints()) {
            DataService dataService = dataAccessPoint.getDataService();
            String dataAccessPointName = dataAccessPoint.getName();
            if (datasetNotRequested(requestDatasetName) || dataAccessPointName.equals(requestDatasetName)) {
                String fFilename = chooseFileName(dataAccessPoint.getName());
                backedUpFiles.add(fFilename);
                backup(dataService.getDataset(), fFilename);
            }
        }
        processResponse(res, backedUpFiles);
    }

    /**
     * Generate a JSON response from call
     *
     * @param res           Response to populate
     * @param backedUpFiles the list of files created.
     */
    public static void processResponse(HttpServletResponse res, ArrayList<String> backedUpFiles) {
        String jsonOutput;
        try (ServletOutputStream out = res.getOutputStream()) {

            jsonOutput = MAPPER.writeValueAsString(backedUpFiles);

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

    private static String getBackUpDirProperty() {
        return Configurator.get(ENV_BACKUPS_DIR);
    }

    private static Path dirBackups() {
        if (dirBackups == null) {
            dirBackups = getDirBackups();
        }
        return dirBackups;
    }

    /**
     * Generate the back-up dir.
     *
     * @return the Path of the back-up directory location.
     */
    public static Path getDirBackups() {
        String dirBackupStr = getBackUpDirProperty();

        if (dirBackupStr == null) {
            dirBackupStr = System.getenv("PWD") + "/backups";
            File dir = new File(dirBackupStr);
            dir.mkdir();
            FmtLog.info(LOG, "ENV_BACKUPS_DIR not set!!. Backups folder set to [default] : /backups");
            return Path.of(dirBackupStr);
        }

        File dir = new File(dirBackupStr);
        if (!dir.exists()) {
            if (dir.mkdir()) {
                FmtLog.info(LOG, "ENV_BACKUPS_DIR : /%s", dirBackupStr);
                return Path.of(dirBackupStr);
            } else {

                FmtLog.info(LOG, "ENV_BACKUPS_DIR invalid!!. Backups folder set to [default] : /backups");
                dirBackupStr = System.getenv("PWD") + "/backups";
                return Path.of(dirBackupStr);
            }
        }

        FmtLog.info(Fuseki.serverLog, "ENV_BACKUPS_DIR already exists. Backups folder set to /%s", dirBackupStr);
        return Path.of(dirBackupStr);
    }

    /**
     * Perform a backup.
     * <p>
     * A backup is a dump of the dataset in compressed N-Quads, done inside a transaction.
     */
    public void backup(DatasetGraph dsg, String backupFile) {
        Backup.backup(dsg, dsg, backupFile);
    }

    /**
     * This is a like-for-like copy of Jena's Backup.chooseFilename due to
     * dependencies on Fuseki Webapp
     *
     * @param dsName the data set name
     * @return a filename composed of the back-up dir, the data set and a timestamp.
     */
    public static String chooseFileName(String dsName) {
        String ds = dsName;
        if (ds.startsWith("/")) {
            ds = ds.substring(1);
        }

        if (ds.contains("/")) {
            FmtLog.warn(LOG, "Dataset name: incorrect format: %s. Modifying", dsName);
            ds = ds.replace("/", "_");
        }

        String timestamp = DateTimeUtils.nowAsString("yyyy-MM-dd_HH-mm-ss");
        String filename = ds + "_" + timestamp;
        filename = dirBackups().resolve(filename).toString();
        return filename;
    }

    /**
     * Checks to see if the requested dataset is empty or just a '/'
     *
     * @param requestName the requested dataset (if provided)
     * @return true if empty, false if set
     */
    public static boolean datasetNotRequested(String requestName) {
        if (requestName == null) {
            return true;
        } else if (requestName.trim().isEmpty()) {
            return true;
        } else return requestName.trim().equals("/");
    }
}
