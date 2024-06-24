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

import java.util.Optional;

import org.apache.jena.atlas.lib.Pair;
import org.apache.jena.fuseki.kafka.FKBatchProcessor;
import org.apache.jena.fuseki.kafka.FKProcessor;
import org.apache.jena.fuseki.kafka.FKS;
import org.apache.jena.fuseki.kafka.FMod_FusekiKafka;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.servlets.ActionProcessor;
import org.apache.jena.kafka.KConnectorDesc;
import org.apache.jena.kafka.SysJenaKafka;
import org.apache.jena.sparql.core.DatasetGraph;

/**
 * Extension of {@link FMod_FusekiKafka} that directly applies a Kafka message batch.
 */
public class FMod_FusekiKafkaSCG extends FMod_FusekiKafka {

    public FMod_FusekiKafkaSCG() { super(); }

    private static void init() {}

    @Override
    protected String logMessage() {
        return String.format("Fuseki-Kafka Connector Module SCG (%s)", SysJenaKafka.VERSION);
    }

    @Override
    protected FKBatchProcessor makeFKBatchProcessor(KConnectorDesc conn, FusekiServer server) {
        if ( false )
            // Use jena-fuseki-kafka default batch processor.
            return super.makeFKBatchProcessor(conn, server);
        String dispatchPath = conn.getLocalDispatchPath();
        DatasetGraph dsg = determineDataset(server, dispatchPath);
        FKProcessor requestProcessor = new FKProcessorSCG(dsg, dispatchPath, server);
        // Pass dsg as the transactional. Each batch will executed by
        // requestProcessor inside a single transaction.
        // See FKBatchProcessor.batchProcess.
        FKBatchProcessor batchProcessor = new FKBatchProcessor(/*Transactional*/dsg, requestProcessor);
        return batchProcessor;
    }

    /** Find the dataset for direct operation, not via an endpoint */
    private static DatasetGraph determineDataset(FusekiServer server, String dispatchPath) {
        Optional<DatasetGraph> optDSG = FKS.findDataset(server, dispatchPath);
        if ( optDSG.isPresent() )
            return optDSG.get();

        // Not dataset directly.
        Pair<ActionProcessor, DatasetGraph> deliveryPoint = FKS.findActionProcessorDataset(server, dispatchPath);
        // Use the HTTP operation.
        // (Note: 2023-07) Reinstate code when the configurations are all changed.
//        if ( deliveryPoint.car() == null )
//            return super.makeFKBatchProcessor(conn, server);

        // Legacy - used a HTTP endpoint for the destination of Kafka messages.
        // We find the dataset itself, ignore the ActionProcessor, and
        // do directly with FKProcessorSCG, with a batch of operations
        // wrapped in a single transaction by FKBatchProcessor.
        return deliveryPoint.cdr();
    }
}
