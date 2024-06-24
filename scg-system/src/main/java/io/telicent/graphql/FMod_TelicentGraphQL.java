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

package io.telicent.graphql;

import java.util.Set;

import io.telicent.jena.abac.fuseki.ServerABAC;
import io.telicent.jena.graphql.execution.GraphQLOverDatasetExecutor;
import io.telicent.jena.graphql.fuseki.FMod_GraphQL;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.servlets.ActionProcessor;
import org.apache.jena.rdf.model.Model;

/**
 * A Fuseki module that provides a new Telicent GraphQL operation
 */
public class FMod_TelicentGraphQL extends FMod_GraphQL {

    private static final String VERSION = Version.versionForClass(FMod_GraphQL.class).orElse("<unset>");

    /**
     * Creates the Telicent GraphQL module
     */
    public FMod_TelicentGraphQL() {
        super();
    }

    @Override
    public String name() {
        return "Telicent GraphQL Schema";
    }

    @Override
    public void prepare(FusekiServer.Builder serverBuilder, Set<String> datasetNames, Model configModel) {
        FmtLog.info(Fuseki.configLog, "Telicent GraphQL Fuseki Module (%s)", VERSION);
    }

    @Override
    protected ActionProcessor createActionProcessor(GraphQLOverDatasetExecutor executor) {
        return new ActionTelicentGraphQL(executor, ServerABAC.userForRequest());
    }
}
