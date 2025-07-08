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

import static io.telicent.core.CQRS.symKafkaTopic;

import java.util.List;
import java.util.Set;

import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.FusekiConfigException;
import org.apache.jena.fuseki.kafka.FKRegistry;
import org.apache.jena.fuseki.kafka.FKS;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.server.*;
import org.apache.jena.fuseki.servlets.ActionService;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.kafka.KConnectorDesc;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.WebContent;
import org.apache.jena.sparql.util.Context;
import org.slf4j.Logger;

/** Add CQRS update (writes patches to Kafka). */
public class FMod_CQRS implements FusekiModule {

    public static Logger LOG = CQRS.LOG;

    /** Software version taken from the jar file. */
    private static final String VERSION = Version.versionForClass(FMod_CQRS.class).orElse("<development>");

    private static ActionService placeholder = new  ActionService() {
        @Override
        public void validate(HttpAction action) {}
        @Override
        public void execute(HttpAction action) {
            FmtLog.info(CQRS.LOG, "CQRS execute called but ActionService not configured");
        }
    };

    public FMod_CQRS() {
        // Register a placeholder ActionService
        // This is replaced by the specific ActionService during prepare()
        OperationRegistry.get()
            .register(CQRS.Vocab.operationUpdateCQRS, WebContent.contentTypeSPARQLUpdate, placeholder);
    }

    @Override
    public String name() {
        return "CQRS";
    }

    @Override
    public void prepare(FusekiServer.Builder builder, Set<String> names, Model configModel) {
        FmtLog.info(Fuseki.configLog, "CQRS Fuseki Module (%s)", VERSION);
//        // Register "http://telicent.io/cqrs#update".
        // The configured ActionService is set during configDataAccessPoint.
        builder.registerOperation(CQRS.Vocab.operationUpdateCQRS,
                                  WebContent.contentTypeSPARQLUpdate,
                                  placeholder);
    }

    @Override
    public void configured(FusekiServer.Builder serverBuilder, DataAccessPointRegistry dapRegistry, Model configModel) {
        FusekiModule.super.configured(serverBuilder, dapRegistry, configModel);
    }

    @Override
    public void configDataAccessPoint(DataAccessPoint dap, Model configModel) {
        dap.getDataService().forEachEndpoint(endpoint->{
            Operation op = endpoint.getOperation();
            // Upgrade all update operations ...
//            if ( Operation.Update.equals(op) ) {
//                if ( topicName != null )
//                    op = CQRS.Vocab.operationUpdateCQRS;
//            }

            // Bind a processor to any CQRS Update operation.
            if ( CQRS.Vocab.operationUpdateCQRS.equals(op) ) {
                String topicName = getTopicFromContext(endpoint.getContext());
                if ( topicName == null ) {
                    List<String> topics = FKS.findTopics(dap.getName());
                    if ( topics.isEmpty()) {
                        FmtLog.error(LOG, "No topic name in context nor a registered connector for dataset %s.");
                        throw new FusekiConfigException("No topic name found");
                    }
                    if ( topics.size() > 1 ) {
                        FmtLog.error(LOG, "Multiple registered connectors for dataset %s. Set topic name in context to select one.");
                        throw new FusekiConfigException("Mutliple topic names found");
                    }
                    topicName = topics.getFirst();
                }

                FmtLog.info(LOG, "Endpoint %s (operation %s) to topic %s", endpointName(dap, endpoint), op.getName(), topicName);
                if ( topicName.isEmpty() )
                    throw new FusekiConfigException("Empty string for topic name for "+symKafkaTopic+" on CQRS update operation");

                KConnectorDesc conn = FKRegistry.get().getConnectorDescriptor(topicName);
                // NB - It's safe to just pass the same set of Consumer Properties to the Producer, it will simply
                //      ignore the properties that are consumer specific
                //      If we don't pass these in as-in any extra configuration a user has provided, e.g. for Kafka
                //      AuthN, won't be propagated and the producer will be stuck in a failure loop
                ActionService cqrsUpdate = CQRS.updateAction(topicName, conn.getKafkaConsumerProps());
                endpoint.setProcessor(cqrsUpdate);
            }
        });
    }

    private String endpointName(DataAccessPoint dap, Endpoint endpoint) {
        if ( endpoint.isUnnamed() )
            return dap.getName();
        return dap.getName()+"/"+endpoint.getName();
    }

    private static String getTopicFromContext(Context context) {
        if ( context == null )
            return null;
        return context.get(symKafkaTopic);
    }
}
