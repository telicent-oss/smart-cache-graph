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

import static java.lang.String.format;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import io.telicent.jena.abac.SysABAC;
import org.apache.jena.atlas.lib.Bytes;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.atlas.logging.Log;
import org.apache.jena.fuseki.server.Operation;
import org.apache.jena.fuseki.servlets.ActionService;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.kafka.JenaKafkaException;
import org.apache.jena.rdfpatch.RDFChanges;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.rdfpatch.changes.RDFChangesExternalTxn;
import org.apache.jena.rdfpatch.text.RDFChangesWriterText;
import org.apache.jena.rdfpatch.text.TokenWriter;
import org.apache.jena.rdfpatch.text.TokenWriterText;
import org.apache.jena.riot.WebContent;
import org.apache.jena.riot.web.HttpNames;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.util.Symbol;
import org.apache.jena.system.buffering.BufferingDatasetGraph;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CQRS {

    /** Log for CQRS related messages */
    public static Logger LOG = LoggerFactory.getLogger("io.telicent.CQRS");
    /** Context symbol for Kafka topic */
    public static Symbol symKafkaTopic = Symbol.create("kafka:topic");

    public static class Vocab {
        public static String NS = "http://telicent.io/cqrs#";
        public static String getURI() { return NS; }
        public static final Operation operationUpdateCQRS = Operation.alloc(CQRS.Vocab.getURI()+"update",
                                                                            "cqrs:update",
                                                                            "Update CQRS");
    }

    /**
     * Return an {@link ActionService} suitable for registering for {@link Operation#Update}.
     */
    public static ActionService updateAction(String topic, Properties producerProperties) {
        Producer<String, byte[]> producer = (producerProperties == null)
                ? null
                : new KafkaProducer<>(producerProperties, new StringSerializer(), new ByteArraySerializer());
        return new SPARQL_Update_CQRS(topic, producer, onBegin, onCommit, onAbort);
    }

    static ActionService updateActionWithProducer(String topic, Producer<String, byte[]> producer) {
        return new SPARQL_Update_CQRS(topic, producer, onBegin, onCommit, onAbort);
    }

    /**
     * Setup to capture changes within the update lifecycle.
     * Return the dataset for the update execution.
     */
    static UpdateCQRS startOperation(String topic,
                                     Producer<String, byte[]> producer,
                                     HttpAction action,
                                     Consumer<HttpAction> onBegin,
                                     Consumer<HttpAction> onCommit,
                                     Consumer<HttpAction> onAbort) {
        // Base dataset is only ever read.
        DatasetGraph dsgBase = action.getActiveDSG();
        String hSecurityLabel = action.getRequestHeader(SysABAC.hSecurityLabel);

        BufferingDatasetGraph dsgBuffering = new BufferingDatasetGraph(dsgBase);
        // Writing the patch log.
        ByteArrayOutputStream bout = new ByteArrayOutputStream(8*1024);
        TokenWriter tok = TokenWriterText.create(bout);
        RDFChangesWriterText changesWriter = new RDFChangesWriterText(tok);

        // Manage transaction change record here so we can terminate the patch
        // (i.e. write "TX") before and separately from the dataset commit.
        RDFChanges changes = new RDFChangesExternalTxn(changesWriter) {
            @Override public void txnBegin()  { if ( onBegin != null )  onBegin.accept(action) ; }
            @Override public void txnCommit() { if ( onCommit != null ) onCommit.accept(action) ; }
            @Override public void txnAbort()  { if ( onAbort != null )  onAbort.accept(action) ; }
        };
        DatasetGraph dsgOperation = RDFPatchOps.changes(dsgBuffering, changes);

        // Record the details of the setup.
        UpdateCQRS updateCtl = new UpdateCQRS(topic, dsgBase,
                                              bout, tok, changesWriter, changes,
                                              dsgBuffering, dsgOperation, hSecurityLabel,
                                              producer);
        // XXX RemoveMe and pass UpdateCQRS to on*
        // Add to the action context so it is carried through the update.
        action.getContext().set(symbol, updateCtl);
        updateCtl.changes.start();
        updateCtl.changes.txnBegin();

        return updateCtl;
    }

    static void finishOperation(HttpAction action, UpdateCQRS updateCtl) {
        onCommit.accept(action);
        updateCtl.changes.finish();
    }

    public record UpdateCQRS(String topic, DatasetGraph dsgBase,
                             ByteArrayOutputStream bout, TokenWriter tok, RDFChangesWriterText changesWriter, RDFChanges changes,
                             BufferingDatasetGraph datasetBuffering,
                             DatasetGraph dataset, // Operate on this DataestGraph
                             String securityLabelHeader,
                             Producer<String, byte[]> producer) {}

    /** Used to pass the addition information through the HttpActionLifecycle. */
    private static Symbol symbol = Symbol.create("cqrs:update");

    private static DatasetGraph getOperationDataset(HttpAction action) {
        UpdateCQRS updateCtl = action.getContext().get(symbol);
        return updateCtl.dataset;
    }

    // Call just after dsg.begin.
    private static Consumer<HttpAction> onBegin = CQRS::onBegin;

    private static void onBegin(HttpAction action) {
        UpdateCQRS updateCtl = action.getContext().get(symbol);
        updateCtl.changesWriter.txnBegin();
    }

    // Call just before dsg.commit
    private static Consumer<HttpAction> onCommit = CQRS::onCommit;

    private static void onCommit(HttpAction action) {
        UpdateCQRS changesCtl = action.getContext().get(symbol);
        if ( changesCtl == null ) {
            FmtLog.error(LOG, "[%d] onCommit: No UpdateCQRS record", action.id);
            return;
        }

        changesCtl.changesWriter.txnCommit();
        changesCtl.changes.finish();
        changesCtl.tok.flush();

        boolean isConnected = (changesCtl.producer != null);
        byte[] kBody = changesCtl.bout.toByteArray();
        int addCount = changesCtl.datasetBuffering.getAddedTriples().size();
        int delCount = changesCtl.datasetBuffering.getDeletedTriples().size();
        Log.info(action.log, format("[%d] CQRS Patch: Add=%,d : Del=%,d", action.id, addCount, delCount));

        if ( isConnected ) {
            List<Header> sendHeaders;
            Header headerContentType = kafkaHeader(HttpNames.hContentType, WebContent.contentTypePatch);
            if ( changesCtl.securityLabelHeader != null ) {
                Header headerSecurityLabel = kafkaHeader(SysABAC.hSecurityLabel, changesCtl.securityLabelHeader);
                sendHeaders = List.of(headerContentType, headerSecurityLabel);
            } else {
                sendHeaders = List.of(headerContentType);
            }
            sendToKafka(changesCtl.producer, changesCtl.topic, sendHeaders, kBody);
        } else {
            System.out.print(Bytes.bytes2string(kBody));
            FmtLog.info(LOG, "Send to Kafka: topic=%s bytes=%d", changesCtl.topic, kBody.length);
        }
        action.getContext().remove(symbol);
    }

    // Call just before dsg.commit
    private static Consumer<HttpAction> onAbort = CQRS::onAbort;

    private static void onAbort(HttpAction action) {
        UpdateCQRS changesCtl = action.getContext().get(symbol);
        if ( changesCtl == null ) {
            // May be an abort before or after UpdateCQRS exists (unlikely!)
            FmtLog.warn(LOG, "[%d] onAbort: No UpdateCQRS record", action.id);
            return;
        }
        action.getContext().remove(symbol);
    }

    /**
     * Send to the Kafka topic.
     */
    protected static <K,V> long sendToKafka(Producer<K,V> producer, String topic, List<Header> sendHeaders, V content) {
        RecordMetadata res = sendToKafka(producer, null, topic, sendHeaders, content);
        FmtLog.info(LOG, "[%s] Send: Offset = %d", topic, res.offset());
        return res.offset();
    }

    // Worker to actually send to Kafka.
    private static <K,V> RecordMetadata sendToKafka(Producer<K, V> producer, Integer partition, String topic, List<Header> headers, V body) {
        try {
            ProducerRecord<K, V> pRec = new ProducerRecord<>(topic, partition, null, null, body, headers);
            Future<RecordMetadata> f = producer.send(pRec);
            RecordMetadata res = f.get();
            return res;
        } catch (InterruptedException | ExecutionException e) {
            throw new JenaKafkaException("Failed to send Kafka message", e);
        }
    }

//    private static Header kafkaHeader(String key_value) {
//        String[] a = key_value.split(":",2);
//        if ( a.length != 2 )
//            throw new CmdException("Bad header (format is \"name: value\"): "+key_value);
//        String key = a[0].trim();
//        String value = a[1].trim();
//        return kafkaHeader(key, value);
//    }

    static Header kafkaHeader(String key, String value) {
        return new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8));
    }
}
