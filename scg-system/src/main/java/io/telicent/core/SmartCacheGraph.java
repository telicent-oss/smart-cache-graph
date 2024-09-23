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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SmartCacheGraph {
    /**
     * Software version taken from the jar file.
     */
    public static final String VERSION = version();

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
