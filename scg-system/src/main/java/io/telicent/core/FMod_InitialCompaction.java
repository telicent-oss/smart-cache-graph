package io.telicent.core;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.smart.cache.security.data.DataSecurityException;
import io.telicent.smart.cache.storage.CompactCapable;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPlugin;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPluginLoader;
import io.telicent.smart.cache.configuration.Configurator;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.apache.jena.atlas.io.IO;
import org.apache.jena.atlas.lib.Timer;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.dboe.transaction.txn.TransactionCoordinator;
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
import org.apache.jena.base.Sys;
import org.apache.jena.tdb2.DatabaseMgr;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.apache.jena.tdb2.store.DatasetGraphTDB;
import org.apache.jena.tdb2.sys.DatabaseOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static io.telicent.utils.ServletUtils.processResponse;

@SuppressWarnings("java:S3398")
public class FMod_InitialCompaction implements FusekiAutoModule {

    // JSON response keys
    private static final String ERROR = "error";
    private static final String STATUS = "status";

    public static final Logger LOG = LoggerFactory.getLogger(FMod_InitialCompaction.class);
    private static final String ALL_DATASETS_SCOPE = "__ALL_DATASETS__";
    private final CompactionJobManager jobManager = new CompactionJobManager();
    final Set<String> datasets = new HashSet<>();
    static final boolean DELETE_OLD = true;
    public static final String DISABLE_INITIAL_COMPACTION = "DISABLE_INITIAL_COMPACTION";
    /**
     * Whether compactions requested via the compaction endpoints use RO Compaction (default {@code true}): Kafka ingest into
     * the dataset is paused, writes are rejected and reads are served while the copy is built. During switching,
     * new dataset requests are rejected until accepted requests drain and the switch finishes. When
     * {@code false} a requested compaction needs exclusive access to the dataset (no active transactions at all) and the
     * dataset is unavailable while it runs, which in practice means it only succeeds when the server is idle.
     */
    public static final String COMPACTION_RO = "COMPACTION_RO";
    /**
     * How long an RO Compaction waits for Kafka ingest to reach a safe pause point (ISO-8601 duration, default
     * {@code PT2M})
     */
    public static final String COMPACTION_INGEST_PAUSE_TIMEOUT = "COMPACTION_INGEST_PAUSE_TIMEOUT";
    /**
     * How long an RO Compaction waits for in-progress write transactions to finish once ingest is paused (ISO-8601
     * duration, default {@code PT1M})
     */
    public static final String COMPACTION_WRITER_DRAIN_TIMEOUT = "COMPACTION_WRITER_DRAIN_TIMEOUT";
    /**
     * How long an RO Compaction waits, once the compacted copy is ready, for in-flight queries to finish so it can
     * switch over to the copy (ISO-8601 duration, default {@code PT1M}). Accepted requests drain first; new dataset
     * requests receive HTTP 503 during this phase. If the time passes the copy is discarded and the database left unchanged.
     */
    public static final String COMPACTION_SWITCH_TIMEOUT = "COMPACTION_SWITCH_TIMEOUT";
    /**
     * How often a running compaction logs its progress (ISO-8601 duration, default {@code PT1M})
     */
    public static final String COMPACTION_PROGRESS_LOG_INTERVAL = "COMPACTION_PROGRESS_LOG_INTERVAL";
    /**
     * Whether to check for enough free disk space before compacting (default {@code true})
     */
    public static final String COMPACTION_DISK_SPACE_CHECK = "COMPACTION_DISK_SPACE_CHECK";
    /**
     * TDB2's node data file, which is append-only so grows with the live data rather than with copy-on-write garbage
     */
    static final String NODE_DATA_FILE = "nodes-data.obj";
    /**
     * Least node data a compacted store must have had for its size to be scaled up when estimating the next compacted
     * size; below this the fixed size of an empty TDB2 store (about 200 MB) makes the ratio meaningless
     */
    static final long MIN_NODE_DATA_FOR_ESTIMATE = 64L * 1024 * 1024;
    /**
     * When there's no usable previous compaction to scale from, the compacted size is estimated as the size of an empty
     * TDB2 store plus this many bytes per byte of node data ({@value #NODE_DATA_FILE}), default 20. A compacted store of
     * default graph data is around 10 times its node data; quads in named graphs use more index space per tuple, so the
     * default allows twice that.
     */
    public static final String COMPACTION_NODE_DATA_RATIO = "COMPACTION_NODE_DATA_RATIO";
    static final double DEFAULT_NODE_DATA_RATIO = 20;
    /**
     * Size on disk of an empty TDB2 store, which is mostly pre-allocated index files
     */
    static final long EMPTY_TDB2_SIZE = 200L * 1024 * 1024;

    /**
     * Used to pause Kafka ingest for RO Compactions, replaceable for testing
     */
    static DatasetIngestPause ingestPause = new DatasetIngestPause();
    /**
     * Usable space on the file system holding a database directory, replaceable for testing
     */
    static java.util.function.ToLongFunction<File> usableSpace = File::getUsableSpace;
    private static final java.util.concurrent.ScheduledExecutorService PROGRESS_LOGGER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                final Thread thread = new Thread(r, "scg-compaction-progress");
                thread.setDaemon(true);
                return thread;
            });
    private static final String VERSION = Version.versionForClass(FMod_InitialCompaction.class).orElse("<development>");
    /**
     * Intentionally shared between instances of this module so that they can determine whether a compaction is actually
     * needed or not
     */
    static final Map<String, Long> SIZES = new ConcurrentHashMap<>();
    static final Map<DatasetGraphSwitchable, CompactionIndicator> CURRENT_COMPACTIONS = new ConcurrentHashMap<>();

    enum CompactionStatus {
        COMPACTED,
        SKIPPED_ALREADY_COMPACTED,
        SKIPPED_PREVIOUSLY_COMPACTED,
        SKIPPED_LOCK_CONTENTION,
        SKIPPED_MAINTENANCE_IN_PROGRESS,
        SKIPPED_INSUFFICIENT_DISK_SPACE,
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
            LOG.info("[Compaction] Queued async job {} ({})", job.jobId, operation);
            this.executor.submit(() -> {
                try {
                    job.markRunning();
                    LOG.info("[Compaction] >>>> Async job {} ({}) started", job.jobId, operation);
                    final CompactionOperationResponse response = task.call();
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        job.markSucceeded(response);
                        LOG.info("[Compaction] <<<< Async job {} ({}) completed successfully (status={})",
                                 job.jobId, operation, response.statusCode());
                    } else {
                        job.markFailed(response);
                        LOG.error("[Compaction] <<<< Async job {} ({}) FAILED (status={}): {}",
                                  job.jobId, operation, response.statusCode(), job.message);
                    }
                } catch (Exception e) {
                    LOG.error("[Compaction] <<<< Async job {} ({}) FAILED with an unhandled error", job.jobId, operation, e);
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
            // S3077: Store a fresh deepCopy and return a deepCopy to readers; never mutate the published result.
            @SuppressWarnings("java:S3077")
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
                this.message = response.body().has(ERROR) ? response.body().get(ERROR).asText() : "Job failed.";
            }

            private ObjectNode submission() {
                final ObjectNode node = OBJECT_MAPPER.createObjectNode();
                node.put("job-id", this.jobId);
                node.put("operation", this.operation);
                node.put(STATUS, this.status);
                node.put("status-path", this.statusPath);
                node.put("message", "Job accepted for asynchronous execution.");
                return node;
            }

            private ObjectNode status() {
                final ObjectNode node = OBJECT_MAPPER.createObjectNode();
                node.put("job-id", this.jobId);
                node.put("operation", this.operation);
                node.put(STATUS, this.status);
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
    // S1181: Last-resort isolation records failure and completes cleanup at this background or lifecycle
    // boundary.
    @SuppressWarnings("java:S1181")
    private void compactDatabases(FusekiServer server) {
        for (String name : datasets) {
            Optional<DatasetGraph> optionalDatasetGraph = FKS.findDataset(server, name);
            if (optionalDatasetGraph.isPresent()) {
                try {
                    compactDatasetGraphDatabase(optionalDatasetGraph.get(), name, false);
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
     * Compacts the database, provided its size has changed since it was last compacted
     *
     * @param datasetGraph Dataset Graph
     * @param name         Name of dataset
     * @param requested    Whether this compaction was requested via the compaction endpoints (rather than run at
     *                     startup); requested compactions use RO Compaction unless {@value #COMPACTION_RO} is
     *                     {@code false}
     */
    private static CompactionStatus compactDatasetGraphDatabase(DatasetGraph datasetGraph, String name,
                                                                boolean requested) {
        DatasetGraphSwitchable dsg = getTDB2(datasetGraph);
        if (dsg == null) {
            LOG.debug("Compaction not required for {} as not TDB2", name);
            return CompactionStatus.SKIPPED_NOT_TDB2;
        }
        // Check this before anything touches the database directory: while a compaction is running, the
        // directory holds an in-progress Data-NNNN-tmp directory, which the size calculation can't parse.
        Optional<DatasetMaintenanceRegistry.MaintenanceIndicator> current =
                DatasetMaintenanceRegistry.findCurrentMaintenance(dsg);
        if (current.isPresent()) {
            LOG.info("[Compaction] Ignoring for {} as a {} is already in progress (started {})", name,
                     current.get().operation().displayName(), current.get().startedAt());
            return CompactionStatus.SKIPPED_MAINTENANCE_IN_PROGRESS;
        }
        logPreviousCompactionIndicator(dsg, name);

        // See how big the database is, and whether it's size has changed
        // NB - Due to how the module is registered twice (see SmartCacheGraph) we'll get called twice, once
        //      before Kafka connectors are started, and again after they are started, this gives us two
        //      opportunities to compact stuff
        long sizeBefore = findDatabaseSize(dsg);
        Long previousSize = SIZES.get(name);
        if (previousSize != null && sizeBefore <= previousSize) {
            LOG.info("[Compaction] Additional compaction not required for {} as it is already maximally compacted at {} ({})",
                     name, humanReadableSize(sizeBefore), sizeBefore);
            return CompactionStatus.SKIPPED_ALREADY_COMPACTED;
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

        if (!hasEnoughDiskSpace(dsg, name, sizeBefore, previousCompactionSize,
                                findPreviousCompactionNodeDataSize(dsg), findNodeDataSize(dsg))) {
            return CompactionStatus.SKIPPED_INSUFFICIENT_DISK_SPACE;
        }

        if (requested && isROCompactionEnabled()) {
            return compactRO(datasetGraph, dsg, name, sizeBefore);
        }
        return compactExclusive(datasetGraph, dsg, name, sizeBefore);
    }

    /**
     * Compacts while serving reads during copying and rejecting dataset requests during switching.
     * <p>
     * Before compacting this registers the maintenance operation (from which point the availability filter rejects
     * writes but lets reads through), pauses Kafka ingest into the dataset (which commits any batch in progress), and
     * waits a bounded time for any other in-progress write transactions to finish. If ingest can't be paused or writers
     * don't drain in time the compaction is skipped, never forced.
     * </p>
     * <p>
     * The compaction itself is {@link ROCompaction}, not Jena's {@code DatabaseMgr.compact}: Jena's switches to the
     * compacted copy while queries are running, and a query that spans the switch leaves it waiting forever
     * (apache/jena#3488). {@link ROCompaction} rejects new dataset requests while it drains accepted requests and
     * switches, and abandons the compaction (leaving the database unchanged) if in-flight transactions don't finish within
     * {@value #COMPACTION_SWITCH_TIMEOUT}.
     * </p>
     */
    private static CompactionStatus compactRO(DatasetGraph datasetGraph, DatasetGraphSwitchable dsg, String name,
                                                  long sizeBefore) {
        Optional<DatasetMaintenanceRegistry.MaintenanceHandle> maintenance =
                DatasetMaintenanceRegistry.begin(datasetGraph, name,
                                                 DatasetMaintenanceRegistry.MaintenanceOperation.RO_COMPACTION);
        if (maintenance.isEmpty()) {
            LOG.info("[Compaction] Ignoring for {} due to another maintenance operation already being in progress", name);
            return CompactionStatus.SKIPPED_MAINTENANCE_IN_PROGRESS;
        }
        try (DatasetIngestPause.Handle ingest = ingestPause.pause(name, configuredDuration(
                COMPACTION_INGEST_PAUSE_TIMEOUT, Duration.ofMinutes(2)))) {
            if (!ingest.isIngestStopped()) {
                LOG.warn("[Compaction] Ignoring for {} as Kafka ingest could not be paused", name);
                return CompactionStatus.SKIPPED_LOCK_CONTENTION;
            }
            Duration drainTimeout = configuredDuration(COMPACTION_WRITER_DRAIN_TIMEOUT, Duration.ofMinutes(1));
            if (!waitForNoActiveWriters(dsg, drainTimeout)) {
                LOG.warn("[Compaction] Ignoring for {} as write transactions were still active after {}", name,
                         drainTimeout);
                return CompactionStatus.SKIPPED_LOCK_CONTENTION;
            }
            LOG.info("[Compaction] >>>> Start RO Compaction {}, current size is {} ({}); reads continue during copying, writes are rejected",
                     name, humanReadableSize(sizeBefore), sizeBefore);
            Duration switchTimeout = configuredDuration(COMPACTION_SWITCH_TIMEOUT, Duration.ofMinutes(1));
            return runCompaction(datasetGraph, dsg, name, sizeBefore,
                                 () -> ROCompaction.compact(dsg, DELETE_OLD, switchTimeout));
        } catch (ROCompaction.SwitchAbandonedException e) {
            LOG.warn("[Compaction] Abandoned RO Compaction of {}: {}", name, e.getMessage());
            return CompactionStatus.SKIPPED_LOCK_CONTENTION;
        } finally {
            DatasetMaintenanceRegistry.end(maintenance.get());
        }
    }

    /**
     * Compacts with exclusive access to the dataset; used at startup and when {@value #COMPACTION_RO} is
     * {@code false}
     */
    private static CompactionStatus compactExclusive(DatasetGraph datasetGraph, DatasetGraphSwitchable dsg, String name,
                                                     long sizeBefore) {
        // Due to known issue - obtain a write lock prior to compaction.
        // If it fails, stop processing to avoid deadlock.
        if (!dsg.tryExclusiveMode(false)) {
            LOG.info("[Compaction] Ignoring for {} due to potential deadlock operation", name);
            return CompactionStatus.SKIPPED_LOCK_CONTENTION;
        }
        Optional<DatasetMaintenanceRegistry.MaintenanceHandle> maintenance =
                DatasetMaintenanceRegistry.begin(datasetGraph, name,
                                                 DatasetMaintenanceRegistry.MaintenanceOperation.COMPACTION);
        if (maintenance.isEmpty()) {
            dsg.finishExclusiveMode();
            LOG.info("[Compaction] Ignoring for {} due to another maintenance operation already being in progress", name);
            return CompactionStatus.SKIPPED_MAINTENANCE_IN_PROGRESS;
        }
        try {
            LOG.info("[Compaction] >>>> Start compact {}, current size is {} ({})", name,
                     humanReadableSize(sizeBefore), sizeBefore);
            return runCompaction(datasetGraph, dsg, name, sizeBefore, () -> DatabaseMgr.compact(dsg, DELETE_OLD));
        } finally {
            dsg.finishExclusiveMode();
            DatasetMaintenanceRegistry.end(maintenance.get());
        }
    }

    /**
     * Runs the compaction itself, recording its progress and outcome
     */
    // S1181: Last-resort isolation records failure and completes cleanup at this background or lifecycle
    // boundary.
    @SuppressWarnings("java:S1181")
    private static CompactionStatus runCompaction(DatasetGraph datasetGraph, DatasetGraphSwitchable dsg, String name,
                                                  long sizeBefore, Runnable compactor) {
        Instant startedAt = Instant.now();
        updateCompactionIndicator(dsg, new CompactionIndicator(CompactionIndicatorState.IN_PROGRESS, name,
                                                               startedAt.toString(), startedAt.toString(),
                                                               sizeBefore, -1, null));
        Timer timer = new Timer();
        timer.startTimer();
        CompactionIndicator successIndicator = null;
        java.util.concurrent.ScheduledFuture<?> progress = startProgressLogging(dsg, name, startedAt);
        try {
            compactor.run();
            long sizeAfter = findDatabaseSize(dsg);
            LOG.info("[Compaction] <<<< Finish compact {}. Took {} seconds.  Compacted size is {} ({})",
                     name, Timer.timeStr(timer.endTimer()), humanReadableSize(sizeAfter), sizeAfter);
            SIZES.put(name, sizeAfter);
            updateCompactionResultsFile(dsg, sizeAfter);
            try {
                compactLabels(datasetGraph);
            } catch (DataSecurityException e) {
                LOG.warn("[Compaction] Label compaction failed for {} — data compaction still succeeded", name, e);
            }
            successIndicator = new CompactionIndicator(CompactionIndicatorState.SUCCEEDED, name,
                                                       startedAt.toString(), Instant.now().toString(),
                                                       sizeBefore, sizeAfter, null);
            return CompactionStatus.COMPACTED;
        } catch (Throwable t) {
            updateCompactionIndicator(dsg,
                                      new CompactionIndicator(CompactionIndicatorState.FAILED, name,
                                                              startedAt.toString(), Instant.now().toString(),
                                                              sizeBefore, -1, summarizeThrowable(t)));
            throw t;
        } finally {
            progress.cancel(false);
            if (successIndicator != null) {
                updateCompactionIndicator(dsg, successIndicator);
            }
        }
    }

    static boolean isROCompactionEnabled() {
        // The RO Compaction moves the compacted copy into place while the old database is open, which MS Windows
        // doesn't support
        return !Sys.isWindows && Configurator.get(COMPACTION_RO, Boolean::parseBoolean, true);
    }

    private static Duration configuredDuration(String key, Duration defaultValue) {
        return Configurator.get(key, Duration::parse, defaultValue);
    }

    /**
     * Waits for any in-progress write transactions on the dataset to finish
     *
     * @return {@code true} if there are no active writers, {@code false} if some were still active at the timeout
     */
    static boolean waitForNoActiveWriters(DatasetGraphSwitchable dsg, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (activeWriters(dsg) > 0) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    private static TransactionCoordinator transactionCoordinator(DatasetGraphSwitchable dsg) {
        return dsg.get() instanceof DatasetGraphTDB tdb ? tdb.getTxnSystem().getTxnMgr() : null;
    }

    static long activeWriters(DatasetGraphSwitchable dsg) {
        TransactionCoordinator coordinator = transactionCoordinator(dsg);
        return coordinator != null ? coordinator.countActiveWriter() : 0;
    }

    /**
     * Checks there is likely enough free space for the compacted copy, which is written alongside the current one.
     * <p>
     * The compacted size isn't known in advance, see {@link #estimateCompactedSize(long, long, long, long)}. 10% is
     * added to the estimate for ingest that continues during the compaction.
     * </p>
     *
     * @param sizeBefore             Current size of the database
     * @param previousCompactionSize Size after the last compaction, or {@code -1} if not known
     * @param previousNodeDataSize   Node data size after the last compaction, or {@code -1} if not known
     * @param nodeDataSize           Current node data size, or {@code -1} if not known
     */
    static boolean hasEnoughDiskSpace(DatasetGraphSwitchable dsg, String name, long sizeBefore,
                                      long previousCompactionSize, long previousNodeDataSize, long nodeDataSize) {
        if (sizeBefore <= 0 || !Boolean.TRUE.equals(Configurator.get(COMPACTION_DISK_SPACE_CHECK, Boolean::parseBoolean, true))) {
            return true;
        }
        CompactedSizeEstimate estimate =
                estimate(sizeBefore, previousCompactionSize, previousNodeDataSize, nodeDataSize);
        long required = estimate.bytes() + estimate.bytes() / 10;
        long usable = usableSpace.applyAsLong(dsg.getContainerPath().toFile());
        if (usable >= required) {
            LOG.info("[Compaction] Free space check for {}: needs about {} for the compacted copy (estimated from {}), {} available",
                     name, humanReadableSize(required), estimate.basis(), humanReadableSize(usable));
            return true;
        }
        LOG.warn("[Compaction] Ignoring for {} as it needs about {} free for the compacted copy (estimated from {}) but only {} is available",
                 name, humanReadableSize(required), estimate.basis(), humanReadableSize(usable));
        return false;
    }

    /**
     * An estimate of a compacted store's size, and what it was based on (for logging)
     */
    record CompactedSizeEstimate(long bytes, String basis) {
    }

    /**
     * Estimates the size of the compacted copy, never more than the current size.
     * <p>
     * Most of a bloated TDB2 store is old copies of index blocks, but the node data file ({@value #NODE_DATA_FILE}) is
     * append-only, so it grows with the live data and not with the garbage. So, in order of preference:
     * </p>
     * <ol>
     *     <li>The last compacted size, scaled by how much the node data has grown since. This needs a last compaction
     *     of a store with enough data for the ratio to mean something (at least {@value #MIN_NODE_DATA_FOR_ESTIMATE}
     *     bytes of node data): the last compaction is often the one at startup, of a store that was empty at the time.
     *     </li>
     *     <li>Otherwise, the size of an empty store plus {@value #COMPACTION_NODE_DATA_RATIO} (default 20) bytes per byte
     *     of node data.</li>
     *     <li>If the node data size isn't known, the current size, which is the most the copy can need.</li>
     * </ol>
     */
    static CompactedSizeEstimate estimate(long sizeBefore, long previousCompactionSize, long previousNodeDataSize,
                                          long nodeDataSize) {
        if (nodeDataSize <= 0) {
            return new CompactedSizeEstimate(sizeBefore, "the current size, as the node data size is unknown");
        }
        if (previousCompactionSize > 0 && previousNodeDataSize >= MIN_NODE_DATA_FOR_ESTIMATE) {
            double scaled = Math.ceil((double) previousCompactionSize * nodeDataSize / previousNodeDataSize);
            return capped(sizeBefore, scaled, "the last compaction scaled by node data growth");
        }
        double ratio = nodeDataRatio();
        double fromNodeData = Math.ceil(EMPTY_TDB2_SIZE + ratio * nodeDataSize);
        return capped(sizeBefore, fromNodeData, String.format("%s of node data at %s bytes per byte",
                                                               humanReadableSize(nodeDataSize), ratio));
    }

    private static CompactedSizeEstimate capped(long sizeBefore, double estimate, String basis) {
        return estimate >= sizeBefore ? new CompactedSizeEstimate(sizeBefore, "the current size")
                                      : new CompactedSizeEstimate((long) estimate, basis);
    }

    /**
     * @return Estimated compacted size, see {@link #estimate(long, long, long, long)}
     */
    static long estimateCompactedSize(long sizeBefore, long previousCompactionSize, long previousNodeDataSize,
                                      long nodeDataSize) {
        return estimate(sizeBefore, previousCompactionSize, previousNodeDataSize, nodeDataSize).bytes();
    }

    private static double nodeDataRatio() {
        double ratio = Configurator.get(COMPACTION_NODE_DATA_RATIO, Double::parseDouble, DEFAULT_NODE_DATA_RATIO);
        return ratio > 0 ? ratio : DEFAULT_NODE_DATA_RATIO;
    }

    /**
     * @return Size of the current database's node data file, or {@code -1} if it can't be found
     */
    static long findNodeDataSize(DatasetGraphSwitchable dsg) {
        try {
            Path current = DatabaseOps.findStorageLocation(dsg.getContainerPath());
            if (current == null) {
                return -1;
            }
            File nodeData = current.resolve(NODE_DATA_FILE).toFile();
            return nodeData.isFile() ? nodeData.length() : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /**
     * @return Node data size recorded after the last compaction, or {@code -1} if not known
     */
    static long findPreviousCompactionNodeDataSize(DatasetGraphSwitchable dsg) {
        File file = getPreviousCompactionNodeDataFile(dsg);
        if (!file.isFile()) {
            return -1;
        }
        try (InputStream input = new FileInputStream(file)) {
            return Long.parseLong(IO.readWholeFileAsUTF8(input).strip());
        } catch (IOException | NumberFormatException e) {
            return -1;
        }
    }

    static File getPreviousCompactionNodeDataFile(DatasetGraphSwitchable switchable) {
        return new File(switchable.getContainerPath().toFile(), ".last-compaction-nodes");
    }

    /**
     * Periodically logs a running compaction's progress so a stalled one is visible: INFO while the new copy is
     * growing, WARN if it has not grown since the last check
     */
    // java:S1452 - the wildcard comes from ScheduledExecutorService.scheduleAtFixedRate(), the caller only cancels it
    @SuppressWarnings("java:S1452")
    static java.util.concurrent.ScheduledFuture<?> startProgressLogging(DatasetGraphSwitchable dsg, String name,
                                                                               Instant startedAt) {
        Duration interval = configuredDuration(COMPACTION_PROGRESS_LOG_INTERVAL, Duration.ofMinutes(1));
        long[] lastCopySize = { -1 };
        Runnable report = () -> {
            long copySize = compactionCopySize(dsg);
            TransactionCoordinator coordinator = transactionCoordinator(dsg);
            String activity = coordinator == null ? "" :
                              String.format(", active readers %d, active writers %d", coordinator.countActiveReaders(),
                                            coordinator.countActiveWriter());
            Duration elapsed = Duration.between(startedAt, Instant.now()).withNanos(0);
            if (copySize >= 0 && copySize == lastCopySize[0]) {
                LOG.warn("[Compaction] Still compacting {} after {}; the new copy has not grown since the last check ({}){}",
                         name, elapsed, humanReadableSize(copySize), activity);
            } else {
                LOG.info("[Compaction] Still compacting {} after {}; new copy is {}{}", name, elapsed,
                         humanReadableSize(copySize), activity);
            }
            lastCopySize[0] = copySize;
        };
        return PROGRESS_LOGGER.scheduleAtFixedRate(report, interval.toMillis(), interval.toMillis(),
                                                   java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * @return Size of the in-progress compacted copy ({@code Data-NNNN-tmp}), or {@code -1} if there isn't one yet
     */
    private static long compactionCopySize(DatasetGraphSwitchable dsg) {
        File[] tmp = dsg.getContainerPath().toFile().listFiles(f -> f.isDirectory() && f.getName().endsWith("-tmp"));
        if (tmp == null || tmp.length == 0) {
            return -1;
        }
        long total = 0;
        for (File f : tmp) {
            total += FileUtils.sizeOfDirectory(f);
        }
        return total;
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
        // Kept separate from the results file, whose format older versions rely on
        try (FileOutputStream output = new FileOutputStream(getPreviousCompactionNodeDataFile(dsg))) {
            output.write(Long.toString(findNodeDataSize(dsg)).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.warn("[Compaction] Unable to record the compacted node data size - the next free space check will be more conservative",
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
            Path currentDataDir;
            try {
                currentDataDir = DatabaseOps.findStorageLocation(switchable.getContainerPath());
            } catch (RuntimeException e) {
                // e.g. a Data-NNNN-tmp directory left by a compaction that is running or was interrupted
                LOG.warn("[Compaction] Unable to determine current storage location for {}: {}",
                         switchable.getContainerPath(), e.getMessage());
                return -1;
            }
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
                                                               properties.getProperty(ERROR)));
                } catch (IOException | IllegalArgumentException e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    static Optional<CompactionIndicator> findCurrentCompactionIndicator(DatasetGraphSwitchable dsg) {
        return Optional.ofNullable(CURRENT_COMPACTIONS.get(dsg));
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

    public static void compactLabels(DatasetGraph dsg) throws DataSecurityException {
        final DataSecurityPlugin plugin = DataSecurityPluginLoader.load();
        final Optional<CompactCapable> capability = LabelsMaintenance.compact(plugin, dsg);
        if (capability.isEmpty()) {
            return;
        }
        final Timer timer = new Timer();
        timer.startTimer();
        LOG.info("[Compaction] >>>> Start label store compaction.");
        try {
            capability.get().compact();
        } catch (Exception e) {
            throw new DataSecurityException(e.getMessage(), e);
        } finally {
            LOG.info("[Compaction] <<<< Finish label store compaction. Took {} seconds.",
                     Timer.timeStr(timer.endTimer()));
        }
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
        CURRENT_COMPACTIONS.put(dsg, indicator);

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
            properties.setProperty(ERROR, indicator.error());
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
            moveCompactionIndicator(tempFile.toPath(), indicatorFile.toPath());
        } catch (IOException e) {
            LOG.warn("[Compaction] Unable to move compaction indicator file into place {}", indicatorFile, e);
            try {
                Files.delete(tempFile.toPath());
            } catch (IOException cleanupError) {
                LOG.warn("[Compaction] Unable to delete temporary indicator file {}", tempFile, cleanupError);
            }
        }
    }

    private static void moveCompactionIndicator(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
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

    // S1948: Embedded Jetty does not serialise these servlets or replicate their injected service state.
    @SuppressWarnings("java:S1948")
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

        private static CompactionOperationResponse executeCompactOne(final DatasetGraph dsg, final String datasetName) {
            try {
                final CompactionStatus outcome = compactDatasetGraphDatabase(dsg, datasetName, true);
                return new CompactionOperationResponse(HttpServletResponse.SC_OK,
                                                       toCompactionSummaryJson(Map.of(datasetName, outcome)));
            } catch (Exception e) {
                FmtLog.error(Fuseki.configLog, "Error while compacting dataset " + datasetName, e);
                return compactionFailureResponse(compactionFailureDetails(datasetName, e));
            }
        }

      private static String compactionFailureDetails(String datasetName, Throwable t) {
            return "Compaction failed for dataset " + datasetName + ": "
                    + (t.getMessage() != null ? t.getMessage() : t.getClass().getName());
        }
    }

    // S1948: Embedded Jetty does not serialise these servlets or replicate their injected service state.
    @SuppressWarnings("java:S1948")
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
                    final CompactionStatus outcome = compactDatasetGraphDatabase(dataService.getDataset(), datasetName, true);
                    outcomes.put(datasetName, outcome);
                } catch (Exception e) {
                    failures.put(datasetName, e.getMessage() != null ? e.getMessage() : e.getClass().getName());
                    FmtLog.error(Fuseki.configLog, "Error while compacting dataset " + datasetName, e);
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
    }

    // S1948: Embedded Jetty does not serialise these servlets or replicate their injected service state.
    @SuppressWarnings("java:S1948")
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
                resultNode.put(ERROR, "Unknown compaction job id: " + jobId);
                processResponse(res, resultNode);
            });
        }
    }

    private static CompactionOperationResponse compactionFailureResponse(final String details) {
        final ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.put(ERROR, details != null ? details : "Compaction failed.");
        return new CompactionOperationResponse(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, body);
    }

    private static void writeSyncResponse(final HttpServletResponse res, final CompactionOperationResponse response) {
        if (response.statusCode() >= 400) {
            ServletOps.errorOccurred(response.body().path(ERROR).asText("Compaction failed."));
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
        body.put(STATUS, "ok");
        final ObjectNode datasetsNode = body.putObject("datasets");
        outcomes.forEach((datasetName, outcome) -> datasetsNode.put(datasetName, outcome.name()));
        return body;
    }
}
