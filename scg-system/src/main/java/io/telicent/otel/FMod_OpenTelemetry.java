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
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.jena.Jena;
import org.apache.jena.atlas.lib.Lib;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.server.*;
import org.apache.jena.rdf.model.Model;

@SuppressWarnings("deprecation")
public class FMod_OpenTelemetry implements FusekiModule {

    public static final AttributeKey<String> FUSEKI_ENDPOINT = AttributeKey.stringKey("fuseki.endpoint");
    public static final AttributeKey<String> FUSEKI_OPERATION = AttributeKey.stringKey("fuseki.operation");

    public static String ENV_OPENTELEMETRY = "FUSEKI_FMOD_OTEL";
    public static String SYS_OPENTELEMETRY = "fuseki:fmod:OpenTelemetry";

    @Override
    public String name() {
        return "FMod_OpenTelemetry";
    }

    private static final String VERSION = Version.versionForClass(FMod_OpenTelemetry.class).orElse("<development>");

    /**
     * Enable / disable FMod_OpenTelemetry : this is necessary because there are
     * background operations (threads) associated with OpenTelemetry. If running with
     * OTel on the class/module path, and setting {@code ENABLED = false} means
     * FMod_OpenTelemetry will not start OpenTelemetry. This must be called before
     * Fuseki module loading, which is usually happening during JenaSystem.init();
     */
    public static boolean ENABLED = Lib.isPropertyOrEnvVarSetToTrue(SYS_OPENTELEMETRY, ENV_OPENTELEMETRY);

    private static AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    /** One time initialization */
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
        if ( ! ENABLED )
            return;
        init();
        FmtLog.info(Fuseki.configLog, "OpenTelemetry Fuseki Module (%s)", VERSION);
    }

    @Override
    public void configured(FusekiServer.Builder serverBuilder, DataAccessPointRegistry dapRegistry, Model configModel) {
        if ( ! ENABLED )
            return;
        dapRegistry.accessPoints().forEach(accessPoint -> buildMetrics(accessPoint));
    }

    public static void buildMetrics(DataAccessPoint dap) {
        Meter meter = JenaMetrics.getMeter("Jena", Jena.VERSION);
        DataService dataService = dap.getDataService();

        for ( Operation operation : dataService.getOperations() ) {
            List<Endpoint> endpoints = dataService.getEndpoints(operation);
            for ( Endpoint endpoint : endpoints ) {
                CounterSet counters = endpoint.getCounters();
                for ( CounterName counterName : counters.counters() ) {
                    Counter counter = counters.get(counterName);

                    // Single shared gauge for each Fuseki counter, unique attribute sets for each endpoint that has
                    // that counter
                    // smartcache.graph.[counter name]
                    // Moving to io.opentelemetry.semconv:opentelemetry-semconv
                    // and the class name looks like it becomes IncubatingSemanticAttributes
                    Attributes metricAttributes = Attributes.of(SemanticAttributes.DB_SYSTEM, "Apache Jena Fuseki",
                                                                SemanticAttributes.DB_NAME, dap.getName(),
                                                                SemanticAttributes.DB_OPERATION,
                                                                endpoint.getOperation().getName(),
                                                                FUSEKI_ENDPOINT, endpoint.getName(),
                                                                FUSEKI_OPERATION, operation.getDescription());

                    String counterTxt = fixupName(counterName.getFullName());
                    String gaugeName = "smartcache.graph." + counterTxt;
                    meter.gaugeBuilder(gaugeName)
                         .ofLongs()
                         .buildWithCallback(measure -> measure.record(counter.value(), metricAttributes));
                }
            }
        }
    }

    /**
     * Take a filename string and replace any troublesome characters
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