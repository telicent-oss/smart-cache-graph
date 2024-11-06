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

import io.telicent.graphql.FMod_TelicentGraphQL;
import io.telicent.jena.abac.fuseki.FMod_ABAC;
import io.telicent.otel.FMod_OpenTelemetry;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.caches.configuration.auth.AuthConstants;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.cmds.FusekiMain;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.rdf.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yamlconfig.ConfigStruct;
import yamlconfig.RDFConfigGenerator;
import yamlconfig.YAMLConfigParser;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
        /*YAMLConfigParser ycp = new YAMLConfigParser();
        RDFConfigGenerator rcg = new RDFConfigGenerator();
        String configPath;
        String pattern = ".*\\.(yaml|yml)$";
        Pattern regex = Pattern.compile(pattern);
        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--conf") && i + 1 < args.length ) {
                configPath = args[i + 1];
                Matcher matcher = regex.matcher(configPath);
                if (matcher.matches()) {
                    try {
                        ConfigStruct configStruct = ycp.runYAMLParser(configPath);
                        Model configModel = rcg.createRDFModel(configStruct);

                        File rdfConfigPath = File.createTempFile("RDF.state", ".ttl");
                        rdfConfigPath.deleteOnExit();

                        try (FileOutputStream out = new FileOutputStream(rdfConfigPath)) {
                            configModel.write(out, "TTL", new File(configPath).getAbsoluteFile().getParentFile().toURI().toString());
                            log.info(new File(configPath).getAbsoluteFile().getParentFile().toURI().toString());
                            configModel.write(System.out, "TTL", new File(configPath).getAbsoluteFile().getParentFile().toURI().toString());
                            args[i + 1] = rdfConfigPath.getPath();
                        } catch (IOException e) {
                            log.error(e.getMessage());
                            throw new RuntimeException(e.getMessage());
                        }
                    } catch (RuntimeException ex) {
                        throw new RuntimeException("Failure parsing the YAML config file: " + ex.getMessage());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }*/
        convertYamlConfigToRDF(args);
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

    private static String convertToAbsolutePathURI(String fileUri) {
        // Remove the "file:" prefix to get the relative path
        String relativePath = fileUri.substring(5);

        // Convert the relative path to an absolute path
        File file = new File(relativePath);
        String absolutePath = file.getAbsolutePath();

        // Return the absolute path as a file URI
        return "file:/" + absolutePath;
    }

    private static void convertYamlConfigToRDF(String... args) {
        YAMLConfigParser ycp = new YAMLConfigParser();
        RDFConfigGenerator rcg = new RDFConfigGenerator();
        String configPath;
        String pattern = ".*\\.(yaml|yml)$";
        Pattern regex = Pattern.compile(pattern);
        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--conf") && i + 1 < args.length ) {
                configPath = args[i + 1];
                Matcher matcher = regex.matcher(configPath);
                if (matcher.matches()) {
                    try {
                        ConfigStruct configStruct = ycp.runYAMLParser(configPath);
                        Model configModel = rcg.createRDFModel(configStruct);

                        File rdfConfigPath = File.createTempFile("RDF.state", ".ttl");
                        rdfConfigPath.deleteOnExit();

                        try (FileOutputStream out = new FileOutputStream(rdfConfigPath)) {
                            configModel.write(out, "TTL", new File(configPath).getAbsoluteFile().getParentFile().toURI().toString());
                            log.info(new File(configPath).getAbsoluteFile().getParentFile().toURI().toString());
                            configModel.write(System.out, "TTL", new File(configPath).getAbsoluteFile().getParentFile().toURI().toString());
                            args[i + 1] = rdfConfigPath.getPath();
                        } catch (IOException e) {
                            log.error(e.getMessage());
                            throw new RuntimeException(e.getMessage());
                        }
                    } catch (RuntimeException ex) {
                        throw new RuntimeException("Failure parsing the YAML config file: " + ex.getMessage());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }
}
