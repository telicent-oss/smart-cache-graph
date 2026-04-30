package io.telicent.core;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static io.telicent.utils.ServletUtils.processResponse;

public class FMod_InitialCompaction implements FusekiAutoModule {

    public static final Logger LOG = LoggerFactory.getLogger(FMod_InitialCompaction.class);
    private static final String ALL_DATASETS_SCOPE = "__ALL_DATASETS__";
    private final CompactionJobManager jobManager = new CompactionJobManager();
    final Set<String> datasets = new HashSet<>();
    static final boolean DELETE_OLD = true;
    public static final String DISABLE_INITIAL_COMPACTION = "DISABLE_INITIAL_COMPACTION";
    private static final String VERSION = Version.versionForClass(FMod_InitialCompaction.class).orElse("<development>");
    /**
     * Intentionally shared between instances of this module so that they can determine whether a compaction is actually
     * needed or not
     */
    static final Map<String, Long> SIZES = new ConcurrentHashMap<>();

    enum CompactionStatus {
        COMPACTED,
        SKIPPED_ALREADY_COMPACTED,
        SKIPPED_PREVIOUSLY_COMPACTED,
        SKIPPED_LOCK_CONTENTION,
        SKIPPED_NOT_TDB2
    }

    enum CompactionIndicatorState {
        IN_PROGRESS,
        SUCCEEDED,
        FAILED
    }

   record CompactionIndicator(CompactionIndicatorState state, String datasetName, String startedAt,
                                      String updatedAt, long sizeBefore, long sizeAfter, String error) {
    }

    record CompactionOperationResponse(int statusCode, ObjectNode body) {
    }

    private static final class CompactionJobManager {

        private static final String STATUS_PENDING = "PENDING";
        private static final String STATUS_RUNNING = "RUNNING";
        private static final String STATUS_SUCCEEDED = "SUCCEEDED";
        private static final String STATUS_FAILED = "FAILED";

        private final Map<String, CompactionJob> jobs = new ConcurrentHashMap<>();
        private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            final Thread thread = new Thread(r, "scg-compaction-jobs");
            thread.setDaemon(true);
            return thread;
        });

        ObjectNode submit(final String scope,
                          final String operation,
                          final String statusPathPrefix,
                          final Callable<CompactionOperationResponse> task) {
            final CompactionJob job = new CompactionJob(scope, UUID.randomUUID().toString(), operation, statusPathPrefix);
            this.jobs.put(job.jobId, job);
            this.executor.submit(() -> {
                try {
                    job.markRunning();
                    final CompactionOperationResponse response = task.call();
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        job.markSucceeded(response);
                    } else {
                        job.markFailed(response);
                    }
                } catch (Exception e) {
                    LOG.error("Unhandled compaction job failure for {}", operation, e);
                    job.markFailed(compactionFailureResponse(e.getMessage() != null ? e.getMessage() : e.getClass().getName()));
                }
            });
            return job.submission();
        }

        ArrayNode listJobs(final String scope) {
            final ArrayNode jobsArray = OBJECT_MAPPER.createArrayNode();
            this.jobs.values()
                     .stream()
                     .filter(job -> job.scope.equals(scope))
                     .sorted(Comparator.comparing((CompactionJob job) -> job.submittedAt).reversed())
                     .map(CompactionJob::status)
                     .forEach(jobsArray::add);
            return jobsArray;
        }

        Optional<ObjectNode> getJob(final String scope, final String jobId) {
            return Optional.ofNullable(this.jobs.get(jobId))
                           .filter(job -> job.scope.equals(scope))
                           .map(CompactionJob::status);
        }

        private static final class CompactionJob {
            private final String scope;
            private final String jobId;
            private final String operation;
            private final String statusPath;
            private final Instant submittedAt = Instant.now();
            private volatile String status = STATUS_PENDING;
            private volatile String message = "Waiting to run.";
            private volatile Instant startedAt;
            private volatile Instant completedAt;
            private volatile Integer httpStatus;
            private volatile ObjectNode result;

            private CompactionJob(final String scope,
                                  final String jobId,
                                  final String operation,
                                  final String statusPathPrefix) {
                this.scope = scope;
                this.jobId = jobId;
                this.operation = operation;
                this.statusPath = statusPathPrefix + jobId;
            }

            private void markRunning() {
                this.status = STATUS_RUNNING;
                this.message = "Job is running.";
                this.startedAt = Instant.now();
            }

            private void markSucceeded(final CompactionOperationResponse response) {
                this.status = STATUS_SUCCEEDED;
                this.completedAt = Instant.now();
                this.httpStatus = response.statusCode();
                this.result = response.body().deepCopy();
                this.message = "Job completed successfully.";
            }

            private void markFailed(final CompactionOperationResponse response) {
                this.status = STATUS_FAILED;
                this.completedAt = Instant.now();
                this.httpStatus = response.statusCode();
                this.result = response.body().deepCopy();
                this.message = response.body().has("error") ? response.body().get("error").asText() : "Job failed.";
            }

            private ObjectNode submission() {
                final ObjectNode node = OBJECT_MAPPER.createObjectNode();
                node.put("job-id", this.jobId);
                node.put("operation", this.operation);
                node.put("status", this.status);
                node.put("status-path", this.statusPath);
                node.put("message", "Job accepted for asynchronous execution.");
                return node;
            }

            private ObjectNode status() {
                final ObjectNode node = OBJECT_MAPPER.createObjectNode();
                node.put("job-id", this.jobId);
                node.put("operation", this.operation);
                node.put("status", this.status);
                node.put("status-path", this.statusPath);
                node.put("message", this.message);
                node.put("submitted-at", this.submittedAt.toString());
                if (this.startedAt != null) {
                    node.put("started-at", this.startedAt.toString());
                }
                if (this.completedAt != null) {
                    node.put("completed-at", this.completedAt.toString());
                }
                if (this.httpStatus != null) {
                    node.put("http-status", this.httpStatus);
                }
                if (this.result != null) {
                    node.set("result", this.result.deepCopy());
                }
                return node;
            }
        }
    }

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
        serverBuilder.addServlet("/$/compactall", new CompactAllServlet(dapRegistry, this.jobManager));
        serverBuilder.addServlet("/$/compaction/jobs/all/*", new CompactionJobsServlet(this.jobManager, ALL_DATASETS_SCOPE));

        if (dapRegistry != null) {
            // Create a new endpoint for compacting a single dataset
            for (DataAccessPoint dataAccessPoint : dapRegistry.accessPoints()) {
                DataService dataService = dataAccessPoint.getDataService();

                serverBuilder.addServlet("/$/compact" + dataAccessPoint.getName(),
                                         new CompactOneServlet(dataService.getDataset(), dataAccessPoint.getName(), this.jobManager));
                serverBuilder.addServlet("/$/compaction/jobs" + dataAccessPoint.getName() + "/*",
                                         new CompactionJobsServlet(this.jobManager, dataAccessPoint.getName()));
            }
        }
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
                try {
                    compactDatasetGraphDatabase(optionalDatasetGraph.get(), name);
                } catch (Throwable t) {
                    // Compaction is a best-effort maintenance task and must not take the server down.
                    LOG.error("[Compaction] Startup compaction failed for {}. Leaving dataset available without compaction.",
                              name, t);
                }
            } else {
                LOG.debug("Compaction not required for {} as no graph", name);
            }
        }
    }

    /**
     * Carries out the actual compaction, provided the size of the TDB has changed since last time
     *
     * @param datasetGraph Dataset Graph
     * @param name         Name of dataset
     */
    private static CompactionStatus compactDatasetGraphDatabase(DatasetGraph datasetGraph, String name) {
        DatasetGraphSwitchable dsg = getTDB2(datasetGraph);
        if (dsg != null) {
            logPreviousCompactionIndicator(dsg, name);

            // See how big the database is, and whether it's size has changed
            // NB - Due to how the module is registered twice (see SmartCacheGraph) we'll get called twice, once
            //      before Kafka connectors are started, and again after they are started, this gives us two
            //      opportunities to compact stuff
            long sizeBefore = findDatabaseSize(dsg);
            if (SIZES.containsKey(name)) {
                if (sizeBefore <= SIZES.get(name)) {
                    LOG.info("[Compaction] Additional compaction not required for {} as it is already maximally compacted at {} ({})",
                             name, humanReadableSize(sizeBefore), sizeBefore);
                    return CompactionStatus.SKIPPED_ALREADY_COMPACTED;
                }
            }

            // To avoid redundant work when we complete a compaction we record the compacted size in a file on the
            // filesystem, that way we can check whether the currently observed size is different from the previously
            // completed compaction size and if not skip compaction
            // If the size is different then we know the database has new data written to it and can potentially benefit
            // from further compaction
            long previousCompactionSize = findPreviousCompactionSize(dsg);
            if (previousCompactionSize == sizeBefore) {
                LOG.info("[Compaction] Additional compaction not required for {} as it was already maximally compacted by a prior compaction at {} ({})",
                         name, humanReadableSize(sizeBefore), sizeBefore);
                return CompactionStatus.SKIPPED_PREVIOUSLY_COMPACTED;
            }

            // Due to known issue - obtain a write lock prior to compaction.
            // If it fails, stop processing to avoid deadlock.
            if (!dsg.tryExclusiveMode(false)) {
                LOG.info("[Compaction] Ignoring for {} due to potential deadlock operation", name);
                return CompactionStatus.SKIPPED_LOCK_CONTENTION;
            }
            try {
                LOG.info("[Compaction] >>>> Start compact {}, current size is {} ({})", name,
                         humanReadableSize(sizeBefore), sizeBefore);
                Instant startedAt = Instant.now();
                updateCompactionIndicator(dsg, new CompactionIndicator(CompactionIndicatorState.IN_PROGRESS, name,
                                                                       startedAt.toString(), startedAt.toString(),
                                                                       sizeBefore, -1, null));
                Timer timer = new Timer();
                timer.startTimer();
                try {
                    DatabaseMgr.compact(dsg, DELETE_OLD);
                } catch (Throwable t) {
                    updateCompactionIndicator(dsg,
                                              new CompactionIndicator(CompactionIndicatorState.FAILED, name,
                                                                      startedAt.toString(), Instant.now().toString(),
                                                                      sizeBefore, -1, summarizeThrowable(t)));
                    throw t;
                }
                long sizeAfter = findDatabaseSize(dsg);
                updateCompactionIndicator(dsg, new CompactionIndicator(CompactionIndicatorState.SUCCEEDED, name,
                                                                       startedAt.toString(), Instant.now().toString(),
                                                                       sizeBefore, sizeAfter, null));
                LOG.info("[Compaction] <<<< Finish compact {}. Took {} seconds.  Compacted size is {} ({})",
                         name, Timer.timeStr(timer.endTimer()), humanReadableSize(sizeAfter), sizeAfter);
                SIZES.put(name, sizeAfter);
                updateCompactionResultsFile(dsg, sizeAfter);
                compactLabels(datasetGraph);
                return CompactionStatus.COMPACTED;
            } finally {
                dsg.finishExclusiveMode();
            }
        } else {
            LOG.debug("Compaction not required for {} as not TDB2", name);
            return CompactionStatus.SKIPPED_NOT_TDB2;
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
            LOG.warn("[Compaction] Unable to write compaction results file - unnecessary compactions may occur in a future as a result",
                     e);
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

    static Optional<CompactionIndicator> findPreviousCompactionIndicator(DatasetGraph dsg) {
        if (dsg instanceof DatasetGraphSwitchable switchable) {
            File indicatorFile = getCompactionIndicatorFile(switchable);
            if (indicatorFile.exists() && indicatorFile.isFile()) {
                Properties properties = new Properties();
                try (InputStream input = new FileInputStream(indicatorFile)) {
                    properties.load(input);
                    String state = properties.getProperty("state");
                    String datasetName = properties.getProperty("dataset");
                    if (state == null || datasetName == null) {
                        return Optional.empty();
                    }
                    return Optional.of(new CompactionIndicator(CompactionIndicatorState.valueOf(state), datasetName,
                                                               properties.getProperty("startedAt"),
                                                               properties.getProperty("updatedAt"),
                                                               parseLongProperty(properties.getProperty("sizeBefore")),
                                                               parseLongProperty(properties.getProperty("sizeAfter")),
                                                               properties.getProperty("error")));
                } catch (IOException | IllegalArgumentException e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    static File getCompactionIndicatorFile(DatasetGraphSwitchable switchable) {
        return new File(switchable.getContainerPath().toFile(), ".compaction-status");
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
                LOG.info("[Compaction] <<<< Start label store compaction.");
                rocksDB.compact();
                LOG.info("[Compaction] <<<< Finish label store compaction. Took {} seconds.",
                         Timer.timeStr(timer.endTimer()));
                return;
            }
        }
        LOG.info("[Compaction] <<<< Label store compaction not needed.");
    }

    private static String compactionFailureDetails(String datasetName, Throwable t) {
        return "Compaction failed for dataset " + datasetName + ": "
                + (t.getMessage() != null ? t.getMessage() : t.getClass().getName());
    }

    private static void logPreviousCompactionIndicator(DatasetGraphSwitchable dsg, String datasetName) {
        findPreviousCompactionIndicator(dsg).ifPresent(indicator -> {
            if (indicator.state() == CompactionIndicatorState.IN_PROGRESS) {
                LOG.warn("[Compaction] Previous compaction for {} appears to have been interrupted. Last recorded start={}, size before={} ({})",
                         datasetName, indicator.startedAt(), humanReadableSize(indicator.sizeBefore()),
                         indicator.sizeBefore());
            } else if (indicator.state() == CompactionIndicatorState.FAILED) {
                LOG.warn("[Compaction] Previous compaction for {} failed. Last recorded update={}, error={}",
                         datasetName, indicator.updatedAt(), indicator.error());
            }
        });
    }

    private static void updateCompactionIndicator(DatasetGraphSwitchable dsg, CompactionIndicator indicator) {
        Properties properties = new Properties();
        properties.setProperty("state", indicator.state().name());
        properties.setProperty("dataset", indicator.datasetName());
        if (indicator.startedAt() != null) {
            properties.setProperty("startedAt", indicator.startedAt());
        }
        if (indicator.updatedAt() != null) {
            properties.setProperty("updatedAt", indicator.updatedAt());
        }
        properties.setProperty("sizeBefore", Long.toString(indicator.sizeBefore()));
        properties.setProperty("sizeAfter", Long.toString(indicator.sizeAfter()));
        if (indicator.error() != null) {
            properties.setProperty("error", indicator.error());
        }

        File indicatorFile = getCompactionIndicatorFile(dsg);
        File tempFile = new File(indicatorFile.getParentFile(), indicatorFile.getName() + ".tmp");
        try (OutputStream output = new FileOutputStream(tempFile)) {
            properties.store(output, "Smart Cache Graph compaction status");
        } catch (IOException e) {
            LOG.warn("[Compaction] Unable to write compaction indicator file {}", indicatorFile, e);
            return;
        }

        try {
            try {
                Files.move(tempFile.toPath(), indicatorFile.toPath(),
                           StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile.toPath(), indicatorFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.warn("[Compaction] Unable to move compaction indicator file into place {}", indicatorFile, e);
            tempFile.delete();
        }
    }

    private static long parseLongProperty(String value) {
        if (value == null) {
            return -1;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String summarizeThrowable(Throwable t) {
        String message = t.getMessage();
        if (message == null || message.isBlank()) {
            return t.getClass().getName();
        }
        return t.getClass().getName() + ": " + message;
    }

    private static class CompactOneServlet extends HttpServlet {
        private final DatasetGraph dsg;
        private final String datasetName;
        private final CompactionJobManager jobManager;

        public CompactOneServlet(DatasetGraph dsg, String datasetName, CompactionJobManager jobManager) {
            this.dsg = dsg;
            this.datasetName = datasetName;
            this.jobManager = jobManager;
        }

        @Override
        public void doPost(HttpServletRequest req, HttpServletResponse res) {
            if (Boolean.parseBoolean(req.getParameter("async"))) {
                res.setStatus(HttpServletResponse.SC_ACCEPTED);
                processResponse(res,
                                this.jobManager.submit(this.datasetName,
                                                       "COMPACT_DATASET",
                                                       "/$/compaction/jobs" + this.datasetName + "/",
                                                       () -> executeCompactOne(this.dsg, this.datasetName)));
                return;
            }
            writeSyncResponse(res, executeCompactOne(this.dsg, this.datasetName));
        }
    }

    private static class CompactAllServlet extends HttpServlet {
        private final DataAccessPointRegistry dapRegistry;
        private final CompactionJobManager jobManager;

        public CompactAllServlet(DataAccessPointRegistry dapRegistry, CompactionJobManager jobManager) {
            this.dapRegistry = dapRegistry;
            this.jobManager = jobManager;
        }

        @Override
        public void doPost(HttpServletRequest req, HttpServletResponse res) {
            if (Boolean.parseBoolean(req.getParameter("async"))) {
                res.setStatus(HttpServletResponse.SC_ACCEPTED);
                processResponse(res,
                                this.jobManager.submit(ALL_DATASETS_SCOPE,
                                                       "COMPACT_ALL_DATASETS",
                                                       "/$/compaction/jobs/all/",
                                                       () -> executeCompactAll(this.dapRegistry)));
                return;
            }
            writeSyncResponse(res, executeCompactAll(this.dapRegistry));
        }

    }

    private static final class CompactionJobsServlet extends HttpServlet {
        private final CompactionJobManager jobManager;
        private final String scope;

        private CompactionJobsServlet(final CompactionJobManager jobManager, final String scope) {
            this.jobManager = jobManager;
            this.scope = scope;
        }

        @Override
        protected void doGet(final HttpServletRequest req, final HttpServletResponse res) {
            final ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
            final String pathInfo = req.getPathInfo();
            if (pathInfo == null || pathInfo.isBlank() || "/".equals(pathInfo)) {
                resultNode.set("jobs", this.jobManager.listJobs(this.scope));
                processResponse(res, resultNode);
                return;
            }

            final String jobId = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
            this.jobManager.getJob(this.scope, jobId).ifPresentOrElse(job -> {
                resultNode.set("job", job);
                processResponse(res, resultNode);
            }, () -> {
                res.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resultNode.put("error", "Unknown compaction job id: " + jobId);
                processResponse(res, resultNode);
            });
        }
    }

    private static CompactionOperationResponse executeCompactOne(final DatasetGraph dsg, final String datasetName) {
        try {
            final CompactionStatus outcome = compactDatasetGraphDatabase(dsg, datasetName);
            return new CompactionOperationResponse(HttpServletResponse.SC_OK,
                                                   toCompactionSummaryJson(Map.of(datasetName, outcome)));
        } catch (Throwable t) {
            FmtLog.error(Fuseki.configLog, "Error while compacting dataset " + datasetName, t);
            return compactionFailureResponse(compactionFailureDetails(datasetName, t));
        }
    }

    private static CompactionOperationResponse executeCompactAll(final DataAccessPointRegistry dapRegistry) {
        if (dapRegistry == null) {
            return compactionFailureResponse("No DataAccessPoint registry configured");
        }

        final Map<String, CompactionStatus> outcomes = new LinkedHashMap<>();
        final Map<String, String> failures = new LinkedHashMap<>();
        for (DataAccessPoint dataAccessPoint : dapRegistry.accessPoints()) {
            final DataService dataService = dataAccessPoint.getDataService();
            final String datasetName = dataAccessPoint.getName();
            try {
                final CompactionStatus outcome = compactDatasetGraphDatabase(dataService.getDataset(), datasetName);
                outcomes.put(datasetName, outcome);
            } catch (Throwable t) {
                failures.put(datasetName, t.getMessage() != null ? t.getMessage() : t.getClass().getName());
                FmtLog.error(Fuseki.configLog, "Error while compacting dataset " + datasetName, t);
            }
        }

        if (!failures.isEmpty()) {
            StringBuilder details = new StringBuilder("Compaction failed for one or more datasets: ");
            boolean first = true;
            for (Map.Entry<String, String> entry : failures.entrySet()) {
                if (!first) {
                    details.append("; ");
                }
                first = false;
                details.append(entry.getKey()).append("=").append(entry.getValue());
            }
            FmtLog.error(Fuseki.configLog, details.toString());
            return compactionFailureResponse(details.toString());
        }
        return new CompactionOperationResponse(HttpServletResponse.SC_OK, toCompactionSummaryJson(outcomes));
    }

    private static CompactionOperationResponse compactionFailureResponse(final String details) {
        final ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.put("error", details != null ? details : "Compaction failed.");
        return new CompactionOperationResponse(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, body);
    }

    private static void writeSyncResponse(final HttpServletResponse res, final CompactionOperationResponse response) {
        if (response.statusCode() >= 400) {
            ServletOps.errorOccurred(response.body().path("error").asText("Compaction failed."));
            return;
        }
        res.setStatus(response.statusCode());
        res.setContentType("application/json");
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            res.getWriter().write(OBJECT_MAPPER.writeValueAsString(response.body()));
        } catch (IOException e) {
            FmtLog.warn(Fuseki.configLog, "Error writing compaction response", e);
        }
    }

    private static ObjectNode toCompactionSummaryJson(final Map<String, CompactionStatus> outcomes) {
        final ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.put("status", "ok");
        final ObjectNode datasetsNode = body.putObject("datasets");
        outcomes.forEach((datasetName, outcome) -> datasetsNode.put(datasetName, outcome.name()));
        return body;
    }
}
