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
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.semconv.DbAttributes;
import io.telicent.core.FMod_InitialCompaction;
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
import org.apache.jena.sparql.util.DatasetUtils;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.apache.jena.tdb2.store.DatasetGraphTDB;

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
        name = name.replaceAll("\\)", "");
        return name;
    }
}