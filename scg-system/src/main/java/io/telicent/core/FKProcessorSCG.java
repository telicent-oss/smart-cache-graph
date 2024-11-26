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

import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.util.List;
import java.util.Objects;

import io.telicent.jena.abac.AE;
import io.telicent.jena.abac.SysABAC;
import io.telicent.jena.abac.attributes.AttributeExpr;
import io.telicent.jena.abac.core.AuthzException;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.core.StreamSplitter;
import io.telicent.jena.abac.core.VocabAuthz;
import io.telicent.jena.abac.labels.LabelsStore;
import org.apache.jena.atlas.RuntimeIOException;
import org.apache.jena.atlas.io.IO;
import org.apache.jena.atlas.lib.Timer;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.kafka.FKBatchProcessor;
import org.apache.jena.fuseki.kafka.FKProcessorBaseAction;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.servlets.ServletOps;
import org.apache.jena.fuseki.system.UploadDetails;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.kafka.FusekiKafka;
import org.apache.jena.kafka.RequestFK;
import org.apache.jena.kafka.ResponseFK;
import org.apache.jena.rdfpatch.PatchException;
import org.apache.jena.rdfpatch.RDFChanges;
import org.apache.jena.rdfpatch.changes.*;
import org.apache.jena.rdfpatch.text.RDFPatchReaderText;
import org.apache.jena.riot.*;
import org.apache.jena.riot.lang.StreamRDFCounting;
import org.apache.jena.riot.system.*;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.core.Transactional;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.system.Txn;
import org.slf4j.Logger;

/**
 * Execute RequestFK request by action.
 * Each action is performed directly.
 * <p>
 * The transactional in {@link FKBatchProcessor} is used if setup
 * by {@link FMod_FusekiKafkaSCG#makeFKBatchProcessor}.
 */
public class FKProcessorSCG extends FKProcessorBaseAction /*implements FKProcessor*/ {

    private static Logger LOG = FusekiKafka.LOG;

    private final DatasetGraph dsg;
    private final DatasetGraphABAC dsgz;

    public FKProcessorSCG(DatasetGraph dsg, String requestURI, FusekiServer server) {
        this.dsg = Objects.requireNonNull(dsg);
        this.dsgz = (dsg instanceof DatasetGraphABAC)
                ? (DatasetGraphABAC)dsg
                : null;
        // Not currently.
//        this.requestURI = requestURI;
//        this.serverContext = server.getServletContext();
    }

    // This does not have to be protected from multithreading because
    // (1) it is only used within a batch.
    // (2) receipt from Kafka is (currently) a single threaded polling loop.
    // So batches do not overlap (and there isn't much point for data loading because
    // order matters in the general case.

    private boolean inBatch = false;

    @Override
    public void startBatch(int batchSize, long startOffset) {
        //Transaction is best controlled by the batch processor.
        inBatch = true;
    }

    @Override
    public void finishBatch(int processedCount, long finishOffset, long startOffset) {
        inBatch = false;
    }

    @Override
    public ResponseFK process(RequestFK request) {
        if ( ! inBatch )
            FmtLog.warn(LOG, "[%s] Not in a batch of requests", request.getTopic());
        return super.process(request);
    }

    /**
     * Execute code for a single {@link RequestFK}, adding a transaction if necessary.
     */
    private void executeWrite(String id, RequestFK request, Transactional transactional, Runnable action) {
        Txn.executeWrite(transactional, ()->{
            try {
                action.run();
            } catch (AuthzException ex) {
                FmtLog.error(LOG, "[%s] AuthzException: %s", id, ex.getMessage());
                return;
            } catch (RuntimeException ex) {
                super.actionFailed(id, request, ex);
                FmtLog.error(LOG, "[%s] Failed: %s", id, ex.getMessage());
            }
        });
   }

    // ---- SPARQL Update request on the Kafka topic.

    @Override
    protected void actionSparqlUpdate(String id, RequestFK request, InputStream data) {
        executeWrite(id, request, dsg, ()->
            FmtLog.error(LOG, "[%s] SPARQL Update - ignored", id)
            );
    }

    // ---- RDF Patch request on the Kafka topic.

    @Override
    protected void actionRDFPatch(String id, RequestFK request, InputStream data) {
        executeWrite(id, request, dsg, ()->{
            if ( dsgz != null )
                actionRDFPatchLabel(id, request, data);
            else
                actionRDFPatchNoLabel(id, request, data);
        });
    }

    private void actionRDFPatchLabel(String id, RequestFK request, InputStream data) {
        String headerSecurityLabel = request.getHeaders().get(SysABAC.hSecurityLabel);
        RDFChanges changes = new RDFChangesApplyWithLabels(id, dsgz, headerSecurityLabel);
        execRDFPatchNoLabel(id, request, data, changes);
    }

    private void actionRDFPatchNoLabel(String id, RequestFK request, InputStream data) {
        String headerSecurityLabel = request.getHeaders().get(SysABAC.hSecurityLabel);
        if ( headerSecurityLabel != null ) {
            String msg = format("[%s] RDF Patch. Header %s encountered. This dataset does not support ABAC security labelling.", id, SysABAC.hSecurityLabel);
            throw new AuthzException(msg);
        }
        RDFChanges changes = new RDFChangesApply(dsg);
        execRDFPatchNoLabel(id, request, data, changes);
    }

    // Execute an RDF patch.
    private void execRDFPatchNoLabel(String id, RequestFK request, InputStream data, RDFChanges changes) {
        RDFPatchReaderText pr = new RDFPatchReaderText(data);
        // External transaction. Suppress patch recorded TX and TC.
        changes = new RDFChangesExternalTxn(changes) {
            @Override
            public void txnAbort() {
                //throw new PatchTxnAbortException();
            }
        };
        RDFChangesCounter counter = new RDFChangesCounter();
        RDFChanges dest = RDFChangesN.multi(changes, counter);
        try {
            pr.apply(dest);
        } catch (PatchException ex) {
            FmtLog.error(LOG, "[%s] Bad RDF Patch: %s", id, ex.getMessage());
            return;
        }

        PatchSummary summary = counter.summary();
        if ( summary.countAddPrefix > 0 || summary.countDeletePrefix > 0 ) {
            FmtLog.debug(LOG, "[%s] RDF Patch: A=%d, D=%d, PA=%d, PD=%d", id,
                                summary.countAddData, summary.countDeleteData,
                                summary.countAddPrefix, summary.countDeletePrefix);
        } else {
            FmtLog.debug(LOG, "[%s] RDF Patch: A=%d, D=%d", id, summary.countAddData, summary.countDeleteData);
        }
    }

    private class RDFChangesApplyWithLabels extends RDFChangesApply {

        private final String securityLabel;
        private final String id;
        private boolean quadWarningLog = false;

        public RDFChangesApplyWithLabels(String id, DatasetGraphABAC dsgz, String securitylabel) {
            super(dsgz);
            this.securityLabel = securitylabel;
            this.id = id;
        }

        @Override
        public void add(Node g, Node s, Node p, Node o) {
            super.add(g, s, p, o);
            if ( g != null && ! Quad.isDefaultGraph(g) ) {
                if ( VocabAuthz.graphForLabels.equals(g) ) {
                    String msg = format("[%s] Attempt to patch the labels graph", id);
                    FmtLog.warn(LOG, msg);
                    throw new AuthzException(msg);
                }
                if ( ! quadWarningLog ) {
                    String msg = format("[%s] Attempt to patch a named graph - ignored", id);
                    FmtLog.warn(LOG, msg);
                    quadWarningLog = true;
                }
                return;
            }
            // Triple.
            if ( securityLabel != null ) {
                Triple triple = Triple.create(s, p, o);
                dsgz.labelsStore().add(triple, securityLabel);
            }
        }

        @Override
        public void delete(Node g, Node s, Node p, Node o) {
            if ( g != null && ! Quad.isDefaultGraph(g) ) {
                if ( VocabAuthz.graphForLabels.equals(g) ) {
                    String msg = format("[%s] Attempt to delete from the labels graph", id);
                    FmtLog.warn(LOG, msg);
                    throw new AuthzException(msg);
                }
                if ( ! quadWarningLog ) {
                    String msg = format("[%s] Attempt to patch a named graph - ignored", id);
                    FmtLog.warn(LOG, msg);
                    quadWarningLog = true;
                }
                return;
            }
            // Currently, no safe delete label functionality
//            if ( securityLabel != null ) {
//                Triple triple = Triple.create(s, p, o);
//                dsgz.labelsStore().delete(triple, securityLabel);
//            }
            super.delete(g, s, p, o);
        }
    }

    // ---- Data request on the Kafka topic.

    // See also LabelledDataLoader.execute (which is currently tied to HttpAction).
    @Override
    protected void actionData(String id, RequestFK request, Lang lang, InputStream data) {
        executeWrite(id, request, dsg, ()->{
            if ( dsg instanceof DatasetGraphABAC ) {
                actionDataLabelled(id, request, lang, data);
            } else {
                actionDataNoLabels(id, request, lang, data);
            }
        });
    }

    /** The destination is an DatasetGraphABAC. Labelled data - either in the header or as a TriG file */
    private void actionDataLabelled(String id, RequestFK request, Lang lang, InputStream data) {
        Timer timer = new Timer();
        timer.startTimer();
        String headerSecurityLabel = request.getHeaders().get(SysABAC.hSecurityLabel);
        List<String> dataDftLabels = parseAttributeList(headerSecurityLabel);
        if ( dataDftLabels != null )
            FmtLog.debug(LOG, "[%s] Security-Label %s", id, dataDftLabels);
        else
            // Dataset default will apply at use time.
            FmtLog.debug(LOG, "[%s] Dataset default label: %s", id, dsgz.getDefaultLabel());

        // Stream to database
        StreamRDF rdfData = StreamRDFLib.dataset(dsgz.getBase());

        if ( canStream(lang, dataDftLabels) ) {
            StreamRDF destination = rdfData;
            if ( dataDftLabels == null || dataDftLabels.isEmpty() ) {
                // Go straight to the database.
                // The dataset default label will apply at access time.
            } else {
                LabelsStore labelStore = dsgz.labelsStore();
                // Stream to labels store.
                StreamRDF storeLabels = new StreamRDFWrapper(rdfData) {
                    @Override
                    public void triple(Triple triple) {
                        labelStore.add(triple, dataDftLabels);
                        other.triple(triple);
                    }
                };
                destination = storeLabels;
            }
            long count = incomingData(id, request, lang, data, destination);
        } else {
            // Can't stream.
            // Do it by building a labels graph.
            // Get all the labels - as they may come first, we need to collect them
            // together, then process them before the txn commit.
            Graph labelsGraph = GraphFactory.createDefaultGraph();

            StreamRDF destination = new StreamSplitter(rdfData, labelsGraph, dataDftLabels);
            long count = incomingData(id, request, lang, data, destination);
            applyLabels(dsgz, labelsGraph);
        }
    }

    /** Destination is not a DatsetGhraphABAC/. Accept plain, unlabelled data.  */
    private void actionDataNoLabels(String id, RequestFK request, Lang lang, InputStream data) {
        String headerSecurityLabel = request.getHeaders().get(SysABAC.hSecurityLabel);
        if ( headerSecurityLabel != null ) {
            String msg = format("[%s] Data received. Header %s encountered. This dataset does not support ABAC security labelling.", id, SysABAC.hSecurityLabel);
            throw new AuthzException(msg);
        }

        StreamRDF rdfData = StreamRDFLib.dataset(dsg);

        if ( ! RDFLanguages.isTriples(lang) )
            // Detect any labels.
            rdfData = new StreamRDF_NoLabels(rdfData, id);

        // Plain load.
        incomingData(id, request, lang, request.getInputStream(), rdfData);
        return;
    }

    /** Reject qdata if the a quad is for the labels graph */
    private static class StreamRDF_NoLabels extends StreamRDFWrapper {
        private final String id;

        public StreamRDF_NoLabels(StreamRDF other, String id) {
            super(other);
            this.id = id;
        }

        @Override
        public void quad(Quad quad) {
            if ( isLabelsQuad(quad) ) {
                String msg = format("[%s] This dataset does not support ABAC security labelling.", id);
                throw new AuthzException(msg);
            }
            else {
                other.quad(quad);
            }
        }
    }

    /** Looks like a labels quad. */
    private static boolean isLabelsQuad(Quad quad) {
        Node gn = quad.getGraph();
        return VocabAuthz.graphForLabels.equals(gn);
    }

    /**
     * Determine if the data is plain RDF (a triple format) and the ABAC labels are
     * only going to come from the header only.
     */
    private boolean canStream(Lang lang, List<String> defaultLabels) {
        return RDFLanguages.isTriples(lang);
    }

    private long incomingData(String id, RequestFK request, Lang lang, InputStream data, StreamRDF dest) {
        StreamRDFCounting countingDest = StreamRDFLib.count(dest);
        String base = "kafka://"+request.getTopic()+"/";
        try {
            parse(id, countingDest, data, lang, base);
            String details = UploadDetails.detailsStr(countingDest.count(), countingDest.countTriples(), countingDest.countQuads());
            if ( LOG.isDebugEnabled() )
                LOG.debug(format("[%s] Body: Content-Length=%d, Content-Type=%s => %s : %s",
                                id, request.getByteCount(), request.getContentType(), lang.getName(),
                                details));
            return countingDest.count();
        } catch (RiotException ex) {
            LOG.warn(format("[%s] Failed attempt to load: Content-Length=%d, Content-Type=%s => %s",
                            id, request.getByteCount(), request.getContentType(), ex.getMessage()));
            // Exhaust input.
            IO.skipToEnd(data);
            //throw ex;
            return -1;
        }
    }

    private static void applyLabels(DatasetGraphABAC dsgz, Graph labelsGraph) {
        if ( labelsGraph == null || labelsGraph.isEmpty() )
            return;
        dsgz.labelsStore().addGraph(labelsGraph);
    }

    private static void parse(String id, StreamRDF dest, InputStream input, Lang lang, String base) {
        try {
            if ( ! RDFParserRegistry.isRegistered(lang) )
                ServletOps.errorBadRequest("No parser for language '"+lang.getName()+"'");
            ErrorHandler errorHandler = ErrorHandlerFactory.errorHandlerStd(LOG);
            RDFParser.create()
                .errorHandler(errorHandler)
                .source(input)
                .lang(lang)
                .base(base)
                .parse(dest);
        } catch (RuntimeIOException ex) {
            if ( ex.getCause() instanceof CharacterCodingException )
                throw new RiotException("Character Coding Error: "+ex.getMessage());
            throw ex;
        }
    }

    private List<String> parseAttributeList(String securityLabelsList) {
        if ( securityLabelsList == null )
            return null;
        List<AttributeExpr> x = AE.parseExprList(securityLabelsList);
        return AE.asStrings(x);
    }
}
