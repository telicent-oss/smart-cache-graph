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

import java.util.List;

import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.sys.JenaSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainSmartCacheGraph {

    public final static Logger LOG = LoggerFactory.getLogger(MainSmartCacheGraph.class);

    /*
    To test with Open Telemetry metrics you'll need to adjust the run configuration for this class to attach the Java
    Agent to the launched JVM by adding the following argument:

    -javaagent:$MODULE_DIR$/target/agents/opentelemetry-javaagent.jar

    Note that the pom.xml for this module ensures that the Java Agent is copied to the above path during builds.

    You will also want to set all the following environment variables:

    FUSEKI_FMOD_OTEL to true to enable the OTel module
    OTEL_SERVICE_NAME to give the application a human-readable name e.g. Smart Cache Graph
    OTEL_METRICS_EXPORTER to otlp to export to a local OTel Collector on the default port, or none to disable traces
    OTEL_TRACES_EXPORTER to otlp to export to a local OTel Collector on the default port, or none to disable traces
    OTEL_METRICS_INTERVAL to the desired export interval in milliseconds
     */

    public static void main(String... args) {
        buildAndRun(args);

        }

    public static FusekiServer buildAndRun(String... args) {
        FusekiServer server = build(args);
        server.start();
        return server;
    }

    public static FusekiServer build(String... args) {
        JenaSystem.init();
        FusekiLogging.markInitialized(true);
        LOG.info("Smart Cache Graph: Args: {}", List.of(args));
        LOG.info("Smart Cache Graph ({})", SmartCacheGraph.VERSION);
        LOG.info("Apache Jena Fuseki ({})", Fuseki.VERSION);
        String userAttributeStore = urlUserAttributeStore();
        if ( userAttributeStore == null )
            LOG.warn("No ENV_USER_ATTRIBUTES_URL setting");
        else
            LOG.info("User attribute store: {}", userAttributeStore);

        // SmartCacheGraph.construct does the work of building a configured server.
        FusekiServer server = SmartCacheGraph.construct(args);
        return server;
    }

    private static String ENV_USER_ATTRIBUTES_URL = "USER_ATTRIBUTES_URL";
    private static String PROPERTY_USER_ATTRIBUTES_URL = "USER_ATTRIBUTES_URL";
    //private static String PROPERTY_USER_ATTRIBUTES_URL = "io.telicent.userAttributeStoreURL";

    private static String urlUserAttributeStore() {
        String s1 = System.getenv().get(ENV_USER_ATTRIBUTES_URL);
        if ( s1 != null )
            return s1;
        String s2 = System.getProperty(PROPERTY_USER_ATTRIBUTES_URL);
        return s2;
    }
}
