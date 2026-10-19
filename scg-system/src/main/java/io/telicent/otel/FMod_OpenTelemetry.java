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

package io.telicent.otel;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.semconv.DbAttributes;
import io.telicent.core.FMod_InitialCompaction;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.LabelsStore;
import org.apache.commons.io.FileUtils;
import org.apache.jena.Jena;
import org.apache.jena.atlas.lib.Lib;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.server.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphWrapper;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;

@SuppressWarnings("deprecation")
public class FMod_OpenTelemetry implements FusekiModule {

    public static final AttributeKey<String> DB_SYSTEM = AttributeKey.stringKey("db.system");
    public static final AttributeKey<String> DB_NAME = AttributeKey.stringKey("db.name");
    public static final AttributeKey<String> DB_OPERATION = AttributeKey.stringKey("db.operation");
    public static final AttributeKey<String> FUSEKI_ENDPOINT = AttributeKey.stringKey("fuseki.endpoint");
    public static final AttributeKey<String> FUSEKI_OPERATION = AttributeKey.stringKey("fuseki.operation");
    protected static final String METRIC_PREFIX = "smartcache.graph.";
    /**
     * Metric that records TDB2 disk space usage
     */
    public static final String TDB_DISK_USAGE = METRIC_PREFIX + "tdb2.disk.usage";
    public static final String LABEL_ADD_ATTEMPTS = METRIC_PREFIX + "labels.add.attempts";
    public static final String LABEL_CACHE_NO_OPS = METRIC_PREFIX + "labels.cache.noops";
    public static final String LABEL_WRITES = METRIC_PREFIX + "labels.writes";
    /**
     * Container (cgroup) metrics: disk I/O, page cache and memory, see {@link ContainerResources}
     */
    public static final String CONTAINER_IO_READ = METRIC_PREFIX + "container.io.read";
    public static final String CONTAINER_IO_WRITE = METRIC_PREFIX + "container.io.write";
    public static final String CONTAINER_PAGE_CACHE = METRIC_PREFIX + "container.memory.page_cache";
    public static final String CONTAINER_DIRTY = METRIC_PREFIX + "container.memory.dirty";
    public static final String CONTAINER_WRITEBACK = METRIC_PREFIX + "container.memory.writeback";
    public static final String CONTAINER_ANON = METRIC_PREFIX + "container.memory.anon";
    public static final String CONTAINER_LIMIT = METRIC_PREFIX + "container.memory.limit";
    public static final String CONTAINER_USAGE = METRIC_PREFIX + "container.memory.usage";
    public static final String CONTAINER_MAJOR_FAULTS = METRIC_PREFIX + "container.memory.major_faults";
    public static final String CONTAINER_REFAULTS = METRIC_PREFIX + "container.memory.page_cache_refaults";
    private static final AtomicBoolean CONTAINER_METRICS_REGISTERED = new AtomicBoolean(false);

    public static String ENV_OPENTELEMETRY = "FUSEKI_FMOD_OTEL";
    public static String SYS_OPENTELEMETRY = "fuseki:fmod:OpenTelemetry";

    @Override
    public String name() {
        return "FMod_OpenTelemetry";
    }

    private static final String VERSION = Version.versionForClass(FMod_OpenTelemetry.class).orElse("<development>");

    /**
     * Enable / disable FMod_OpenTelemetry : this is necessary because there are background operations (threads)
     * associated with OpenTelemetry. If running with OTel on the class/module path, and setting {@code ENABLED = false}
     * means FMod_OpenTelemetry will not start OpenTelemetry. This must be called before Fuseki module loading, which is
     * usually happening during JenaSystem.init();
     */
    public static boolean ENABLED = Lib.isPropertyOrEnvVarSetToTrue(SYS_OPENTELEMETRY, ENV_OPENTELEMETRY);

    private static AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    /**
     * One time initialization
     */
    private static void init() {
        // Run once.
        INITIALIZED.getAndSet(true);
        // NB - This module now relies on the OpenTelemetry SDK being injected at runtime via the OTel Java Agent which
        //      will automatically capture a variety of JVM metrics without any explicit configuration on our part.
        //      This also means that if the user choose not to attach the Java Agent at runtime the OTel pieces all
        //      become no-ops as the API dependency alone simply provides a no-op implementation.
    }

    @Override
    public void prepare(FusekiServer.Builder serverBuilder, Set<String> datasetNames, Model configModel) {
        if (!ENABLED) {
            return;
        }
        init();
        FmtLog.info(Fuseki.configLog, "OpenTelemetry Fuseki Module (%s)", VERSION);
    }

    @Override
    public void configured(FusekiServer.Builder serverBuilder, DataAccessPointRegistry dapRegistry, Model configModel) {
        if (!ENABLED) {
            return;
        }
        dapRegistry.accessPoints().forEach(FMod_OpenTelemetry::buildMetrics);
        // Container metrics are per process, not per dataset, so only register them once
        if (CONTAINER_METRICS_REGISTERED.compareAndSet(false, true)) {
            ContainerResources.detect().ifPresentOrElse(resources -> {
                buildContainerMetrics(JenaMetrics.getMeter("Jena", Jena.VERSION), resources);
                FmtLog.info(Fuseki.configLog, "Container memory and disk I/O metrics enabled (cgroup %s)",
                            resources.version());
            }, () -> FmtLog.info(Fuseki.configLog,
                                 "No cgroup memory accounting found, container memory and disk I/O metrics disabled"));
        }
    }

    /**
     * Registers container disk I/O, page cache and memory metrics, read from the cgroup
     * <p>
     * All the instruments share one reading, refreshed at most once a second, so a metrics collection reads the cgroup
     * files once. Figures that aren't available (e.g. no memory limit set) aren't recorded.
     * </p>
     *
     * @param meter     Meter
     * @param resources Container resources to read
     */
    static void buildContainerMetrics(Meter meter, ContainerResources resources) {
        Supplier<ContainerResources.Snapshot> snapshot = cachedSnapshot(resources, 1_000);
        Attributes attributes = Attributes.empty();
        containerCounter(meter, CONTAINER_IO_READ, "Bytes read from block devices by the container", "By",
                         () -> snapshot.get().readBytes(), attributes);
        containerCounter(meter, CONTAINER_IO_WRITE, "Bytes written to block devices by the container", "By",
                         () -> snapshot.get().writeBytes(), attributes);
        containerGauge(meter, CONTAINER_PAGE_CACHE,
                       "Page cache charged to the container, including TDB2's memory-mapped files",
                       () -> snapshot.get().pageCache(), attributes);
        containerGauge(meter, CONTAINER_DIRTY, "Page cache waiting to be written to disk",
                       () -> snapshot.get().dirty(), attributes);
        containerGauge(meter, CONTAINER_WRITEBACK, "Page cache currently being written to disk",
                       () -> snapshot.get().writeback(), attributes);
        containerGauge(meter, CONTAINER_ANON, "Anonymous memory, essentially the JVM",
                       () -> snapshot.get().anon(), attributes);
        containerGauge(meter, CONTAINER_LIMIT, "Container memory limit", () -> snapshot.get().limit(), attributes);
        containerGauge(meter, CONTAINER_USAGE, "Memory charged to the container, including page cache",
                       () -> snapshot.get().usage(), attributes);
        containerCounter(meter, CONTAINER_MAJOR_FAULTS, "Page faults that needed a disk read", "{fault}",
                         () -> snapshot.get().majorFaults(), attributes);
        containerCounter(meter, CONTAINER_REFAULTS, "Evicted page cache pages that were needed again", "{page}",
                         () -> snapshot.get().refaults(), attributes);
    }

    private static Supplier<ContainerResources.Snapshot> cachedSnapshot(ContainerResources resources, long maxAgeMs) {
        return new Supplier<>() {
            private ContainerResources.Snapshot last;
            private long readAt;

            @Override
            public synchronized ContainerResources.Snapshot get() {
                long now = System.nanoTime();
                if (last == null || now - readAt >= maxAgeMs * 1_000_000L) {
                    last = resources.read();
                    readAt = now;
                }
                return last;
            }
        };
    }

    private static void containerGauge(Meter meter, String name, String description, LongSupplier value,
                                       Attributes attributes) {
        meter.gaugeBuilder(name).setDescription(description).setUnit("bytes").ofLongs().buildWithCallback(m -> {
            long v = value.getAsLong();
            if (v >= 0) {
                m.record(v, attributes);
            }
        });
    }

    private static void containerCounter(Meter meter, String name, String description, String unit,
                                         LongSupplier value, Attributes attributes) {
        meter.counterBuilder(name).setDescription(description).setUnit(unit).buildWithCallback(m -> {
            long v = value.getAsLong();
            if (v >= 0) {
                m.record(v, attributes);
            }
        });
    }

    public static void buildMetrics(DataAccessPoint dap) {
        Meter meter = JenaMetrics.getMeter("Jena", Jena.VERSION);
        DataService dataService = dap.getDataService();

        // Add gauge for disk space usage if using TDB2
        DatasetGraphSwitchable tdb = FMod_InitialCompaction.getTDB2(dataService.getDataset());
        if (tdb != null) {
            Attributes diskUsageAttributes = Attributes.builder()
                                                       .put(DB_SYSTEM, "Apache Jena TDB2")
                                                       .put(DbAttributes.DB_SYSTEM_NAME, "Apache Jena TDB2")
                                                       .put(DB_NAME, dap.getName())
                                                       .put(DbAttributes.DB_NAMESPACE,
                                                            "file://" + tdb.getContainerPath()
                                                                           .toFile()
                                                                           .getAbsolutePath())
                                                       .build();
            meter.gaugeBuilder(TDB_DISK_USAGE)
                 .setUnit("bytes")
                 .ofLongs()
                 .buildWithCallback(m -> m.record(FileUtils.sizeOfDirectory(tdb.getContainerPath().toFile()),
                                                  diskUsageAttributes));
        }

        DatasetGraphABAC datasetABAC = getDatasetGraphABAC(dataService.getDataset());
        if (datasetABAC != null) {
            buildLabelStoreMetrics(meter, dap.getName(), datasetABAC.labelsStore());
        }

        // Add gauges for each Fuseki counter
        for (Operation operation : dataService.getOperations()) {
            List<Endpoint> endpoints = dataService.getEndpoints(operation);
            for (Endpoint endpoint : endpoints) {
                CounterSet counters = endpoint.getCounters();
                for (CounterName counterName : counters.counters()) {
                    Counter counter = counters.get(counterName);

                    // Single shared gauge for each Fuseki counter, unique attribute sets for each endpoint that has
                    // that counter
                    // smartcache.graph.[counter name]
                    Attributes metricAttributes = Attributes.builder()
                                                            .put(DB_SYSTEM, "Apache Jena Fuseki")
                                                            .put(DbAttributes.DB_SYSTEM_NAME, "Apache Jena Fuseki")
                                                            .put(DB_NAME, dap.getName())
                                                            .put(DB_OPERATION, endpoint.getOperation().getName())
                                                            .put(DbAttributes.DB_OPERATION_NAME,
                                                                 endpoint.getOperation().getName())
                                                            .put(FUSEKI_ENDPOINT, endpoint.getName())
                                                            .put(FUSEKI_OPERATION, operation.getDescription())
                                                            .build();

                    String counterTxt = fixupName(counterName.getFullName());
                    String gaugeName = METRIC_PREFIX + counterTxt;
                    meter.gaugeBuilder(gaugeName)
                         .ofLongs()
                         .buildWithCallback(measure -> measure.record(counter.value(), metricAttributes));
                }
            }
        }
    }

    static void buildLabelStoreMetrics(Meter meter, String datasetName, LabelsStore labelsStore) {
        Map<String, Long> availableMetrics = labelsStore.getMetrics();
        if (availableMetrics.isEmpty()) {
            return;
        }

        Attributes attributes = Attributes.builder()
                                          .put(DB_SYSTEM, "RDF ABAC label store")
                                          .put(DbAttributes.DB_SYSTEM_NAME, "RDF ABAC label store")
                                          .put(DB_NAME, datasetName)
                                          .build();
        buildLabelStoreMetric(meter, LABEL_ADD_ATTEMPTS, "Label assignments attempted",
                              LabelsStore.METRIC_LABEL_ADD_ATTEMPTS, labelsStore, attributes);
        buildLabelStoreMetric(meter, LABEL_CACHE_NO_OPS, "Label writes avoided by duplicate detection",
                              LabelsStore.METRIC_LABEL_CACHE_NO_OPS, labelsStore, attributes);
        buildLabelStoreMetric(meter, LABEL_WRITES, "Label assignments written to storage",
                              LabelsStore.METRIC_LABEL_WRITES, labelsStore, attributes);
    }

    private static void buildLabelStoreMetric(Meter meter, String metricName, String description, String propertyName,
                                              LabelsStore labelsStore, Attributes attributes) {
        if (!labelsStore.getMetrics().containsKey(propertyName)) {
            return;
        }
        meter.counterBuilder(metricName)
             .setDescription(description)
             .setUnit("{assignment}")
             .buildWithCallback(measurement -> {
                 Long value = labelsStore.getMetrics().get(propertyName);
                 if (value != null) {
                     measurement.record(value, attributes);
                 }
             });
    }

    static DatasetGraphABAC getDatasetGraphABAC(DatasetGraph dataset) {
        while (dataset != null) {
            if (dataset instanceof DatasetGraphABAC datasetABAC) {
                return datasetABAC;
            }
            if (!(dataset instanceof DatasetGraphWrapper wrapper)) {
                return null;
            }
            dataset = wrapper.getWrapped();
        }
        return null;
    }

    /**
     * Take a filename string and replace any troublesome characters
     *
     * @param name to be cleaned
     * @return a cleaned filename
     */
    public static String fixupName(String name) {
        name = name.replace(' ', '_');
        name = name.replace('/', '_');
        name = name.replace('(', '-');
        name = name.replace(")", "");
        return name;
    }
}