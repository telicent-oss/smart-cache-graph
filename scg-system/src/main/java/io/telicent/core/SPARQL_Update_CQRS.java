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

import static org.apache.jena.fuseki.server.CounterName.UpdateExecErrors;
import static org.apache.jena.fuseki.servlets.ActionExecLib.incCounter;
import static org.apache.jena.fuseki.servlets.SPARQLProtocol.messageForException;
import static org.apache.jena.fuseki.servlets.SPARQLProtocol.messageForParseException;
import static org.apache.jena.riot.web.HttpNames.paramUsingGraphURI;
import static org.apache.jena.riot.web.HttpNames.paramUsingNamedGraphURI;

import java.io.InputStream;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import io.telicent.jena.abac.ABAC;
import io.telicent.jena.abac.fuseki.ABAC_Processor;
import io.telicent.jena.abac.fuseki.ABAC_Request;
import io.telicent.jena.graphql.schemas.telicent.graph.TelicentGraphSchema;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.servlets.*;
import org.apache.jena.irix.IRIxResolver;
import org.apache.jena.query.QueryBuildException;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.query.Syntax;
import org.apache.jena.shared.OperationDeniedException;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.engine.http.QueryExceptionHTTP;
import org.apache.jena.sparql.modify.UsingList;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateException;
import org.apache.kafka.clients.producer.Producer;

/**
 * Intended to extend SPARQL_Update Currently copies SPARQL_Update.exec Pure CQRS.
 */
public class SPARQL_Update_CQRS extends SPARQL_Update implements ABAC_Processor {

    private static final String UpdateParseBase = Fuseki.BaseParserSPARQL;
    private static final IRIxResolver resolver = IRIxResolver.create()
                                                             .base(UpdateParseBase)
                                                             .resolve(true)
                                                             .allowRelative(false)
                                                             .build();

    private final Function<HttpAction, String> getUser;
    private final String topic;
    private final Producer<String, byte[]> producer;
    private final Consumer<HttpAction> onBegin;
    private final Consumer<HttpAction> onCommit;
    private final Consumer<HttpAction> onAbort;

    public SPARQL_Update_CQRS(Function<HttpAction, String> getUser,
                              String topic,
                              Producer<String, byte[]> producer,
                              Consumer<HttpAction> onBegin,
                              Consumer<HttpAction> onCommit,
                              Consumer<HttpAction> onAbort) {
        super();
        this.getUser = Objects.requireNonNull(getUser, "getUser function cannot be null");
        this.topic = topic;
        this.producer = producer;
        this.onBegin = onBegin;
        this.onCommit = onCommit;
        this.onAbort = onAbort;
    }

    // This can be a read-end action because the base dataset is only ever read.
    // In CQRS.setup, there is a buffering dataset that stores the mutations.
    private static final boolean executeAsWrite = true;

    /**
     * Gets the configured Kafka producer for this endpoint
     *
     * @return Kafka producer
     */
    Producer<String, byte[]> getProducer() {
        return this.producer;
    }

    @Override
    protected void execute(HttpAction action, InputStream input) {
        UsingList usingList = processProtocol(action.getRequest());
        if (executeAsWrite) {
            action.beginWrite();
        } else
        // Need BufferingDatasetGraph to report "write txn" even if base DSG is read.
        {
            action.beginRead();
        }
        try {
            // Get the ABAC Dataset to use
            DatasetGraph dsgRequest;
            if (ABAC.isDatasetABAC(action.getActiveDSG())) {
                dsgRequest = ABAC_Request.decideDataset(action, action.getActiveDSG(), getUser);
            } else {
                dsgRequest = action.getActiveDSG();
            }

            CQRS.UpdateCQRS updateCtl =
                    CQRS.startOperation(topic, producer, action, dsgRequest, onBegin, onCommit, onAbort);
            UpdateAction.parseExecute(usingList, updateCtl.dataset(), input, UpdateParseBase, Syntax.syntaxARQ);
            // Don't make the changes until read back from Kafka.
            CQRS.finishOperation(action, updateCtl);
            if (executeAsWrite) {
                action.abort();
            }
            // Finished with this - it might have a large buffering dataset so clearly release it.
            updateCtl = null;
            /* ---- */
        } catch (UpdateException ex) {
            ActionLib.consumeBody(action);
            abortSilent(action);
            incCounter(action.getEndpoint().getCounters(), UpdateExecErrors);
            ServletOps.errorOccurred(ex.getMessage());
        } catch (QueryParseException ex) {
            ActionLib.consumeBody(action);
            abortSilent(action);
            String msg = messageForParseException(ex);
            action.log.warn("[{}] Parse error: {}", action.id, msg);
            ServletOps.errorBadRequest(messageForException(ex));
        } catch (QueryBuildException | QueryExceptionHTTP ex) {
            ActionLib.consumeBody(action);
            abortSilent(action);
            // Counter inc'ed further out.
            String msg = messageForException(ex);
            action.log.warn("[{}] Bad request: {}", action.id, msg);
            ServletOps.errorBadRequest(messageForException(ex));
        } catch (OperationDeniedException ex) {
            ActionLib.consumeBody(action);
            abortSilent(action);
            throw ex;
        } catch (Throwable ex) {
            ActionLib.consumeBody(action);
            if (!(ex instanceof ActionErrorException)) {
                abortSilent(action);
                ServletOps.errorOccurred(ex.getMessage(), ex);
            }
        } finally {
            action.end();
        }
    }

    //Necessary copies due to private.

    /* [It is an error to supply the using-graph-uri or using-named-graph-uri parameters
     * when using this protocol to convey a SPARQL 1.1 Update request that contains an
     * operation that uses the USING, USING NAMED, or WITH clause.]
     *
     * We will simply capture any using parameters here and pass them to the parser, which will be
     * responsible for throwing an UpdateException if the query violates the above requirement,
     * and will also be responsible for adding the using parameters to update queries that can
     * accept them.
     */
    private UsingList processProtocol(HttpServletRequest request) {
        String[] usingArgs = request.getParameterValues(paramUsingGraphURI);
        String[] usingNamedArgs = request.getParameterValues(paramUsingNamedGraphURI);
        if (usingArgs == null && usingNamedArgs == null) {
            return null;
        }
        ServletOps.errorBadRequest("Not allowed: using-graph-uri or using-named-graph-uri parameters");
        return null;
    }

    private static void abortSilent(HttpAction action) {
        action.abortSilent();
    }
}
