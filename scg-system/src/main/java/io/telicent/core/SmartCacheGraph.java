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

import io.telicent.core.cmds.FusekiMain;
import io.telicent.graphql.FMod_TelicentGraphQL;
import io.telicent.jena.abac.fuseki.FMod_ABAC;
import io.telicent.otel.FMod_OpenTelemetry;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.caches.configuration.auth.AuthConstants;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.cmd.ArgModule;
import org.apache.jena.cmd.ArgModuleGeneral;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
//import org.apache.jena.fuseki.main.cmds.FusekiMain;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import records.ConfigStruct;
import records.RDFConfigGenerator;
import records.YAMLConfigParser;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static records.ConfigConstants.log;

public class SmartCacheGraph {
    /**
     * Software version taken from the jar file.
     */
    public static final String VERSION = version();

    public final static Logger log = LoggerFactory.getLogger("fuseki-yaml-config");

    private static String version() {
        return Version.versionForClass(SmartCacheGraph.class).orElse("<development>");
    }

    /**
     * FusekiServer.Builder for a Fuseki server configured for SmartCacheGraph
     */
    public static FusekiServer.Builder smartCacheGraphBuilder() {
        return FusekiServer.create().fusekiModules(modules());
    }

    /**
     * Builder for a Fuseki server configured for SmartCacheGraph
     */
    public static FusekiServer construct(String... args) {

        //import your local yaml-parser library
        //either take it out here (and take out the yaml file) and put a model in the arguments,
        //or do it in the builder parseConfig or parseConfigFile? (You'll have to add the ArgModule in FusekiMain then as well)
        // tests in testMainSCG, but add your own too
       //Configurator.setAllLevels(log.getName(), Level.getLevel("info"));

        boolean isConfigYaml = Arrays.asList(args).contains("--yaml-config");
        args = Arrays.stream(args)
                .filter(arg -> !arg.equals("yaml-config"))
                .toArray(String[]::new);
        if (isConfigYaml) {
            YAMLConfigParser ycp = new YAMLConfigParser();
            RDFConfigGenerator rcg = new RDFConfigGenerator();
            String configPath = "";
            for (int i = 0; i < args.length; i++) {
                if (args[i].equalsIgnoreCase("--config") && i + 1 < args.length) {
                    // Return the next argument as the value of the current argument
                    configPath = args[i + 1];
                    //System.out.println("YAML config path: " + configPath);
                    //log.warn("YAML config path: " + configPath);
                    //FmtLog.warn(Fuseki.configLog, "YAML config path: " + configPath);
                    try {
                        ConfigStruct configStruct = ycp.runYAMLParser(configPath);
                        Model configModel = rcg.createRDFModel(configStruct);
                        try (FileOutputStream out = new FileOutputStream("target/config.ttl")) {
                            configModel.write(out, "TTL");
                        } catch (IOException e) {
                            log.error(e.getMessage());
                        }
                    } catch (RuntimeException ex) {
                        log.error(ex.getMessage());
                    }
                    args[i+1] = "target/config.ttl";
                }
            }
            /*if (configPath.isEmpty())
                throw new RuntimeException("YAML config missing a file path.");*/
        }

        FusekiModules fmods = modules();
        FusekiServer server = FusekiMain
                .builder(args)
                .fusekiModules(fmods)
                .enablePing(true)
                .build();
        return server;
    }

    /**
     * Set up a builder for a Fuseki/SmartCacheGraph (used when embedding and testing SmartCacheGraph)
     */
    public static FusekiServer.Builder serverBuilder() {
        FusekiModules fmods = modules();
        return FusekiServer.create().fusekiModules(fmods).enablePing(true);
    }

    /**
     * The fixed set of Fuseki Modules that are explicitly configured for the FusekiServer instance built for SCG
     * <p>
     * Only modules in this list are loaded. Use of {@link java.util.ServiceLoader} and
     * {@link org.apache.jena.fuseki.main.sys.FusekiAutoModule} for loading additional modules is not supported.
     * </p>
     *
     * @return FusekiModules
     */
    public static FusekiModules modules() {
        List<FusekiModule> mods = new ArrayList<>();
        mods.add(new FMod_CQRS());
        // Only add the ABAC mode when authentication is enabled
        if (isAuthEnabled()) {
            mods.add(new FMod_ABAC());
        }

        // Initial compaction gets applied twice, once before Kafka module and once after, this is because we want to
        // clean up any bloat from previous instances of the service before we read new data from Kafka.  Then we want
        // to clean up any further bloat created by catching up with Kafka.
        // The same instance of the module is used in both places so it can use an instance variable to track the
        // database sizes and see if further compactions are needed
        FMod_InitialCompaction compaction = new FMod_InitialCompaction();
        if (isInitialCompactionEnabled()) {
            mods.add(compaction);
        }

        mods.addAll(List.of(
                new FMod_FusekiKafkaSCG()
                , new FMod_JwtServletAuth()
                , new FMod_OpenTelemetry()
                , new FMod_TelicentGraphQL()
                , new FMod_RequestIDFilter()
        ));

        // Initial compaction gets added again per the earlier comments
        if (isInitialCompactionEnabled()) {
            mods.add(compaction);
        }
        return FusekiModules.create(mods);
    }

    private static boolean isAuthEnabled() {
        String jwksUrl = Configurator.get(AuthConstants.ENV_JWKS_URL);
        return !Objects.equals(jwksUrl, AuthConstants.AUTH_DISABLED);
    }

    private static boolean isInitialCompactionEnabled() {
        return !Configurator.get(FMod_InitialCompaction.DISABLE_INITIAL_COMPACTION, Boolean::parseBoolean, false);
    }
}
