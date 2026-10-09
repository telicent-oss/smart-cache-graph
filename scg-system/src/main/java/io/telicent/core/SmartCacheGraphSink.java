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

import io.telicent.smart.cache.distribution.lifecycle.DistributionLifecycleState;
import io.telicent.smart.cache.payloads.RdfPayload;
import io.telicent.smart.cache.security.data.distribution.DistributionLifecycleStateFile;
import io.telicent.smart.cache.security.data.labels.DatasetGraphLabelled;
import io.telicent.smart.cache.security.data.labels.SecurityLabels;
import io.telicent.smart.cache.sources.Event;
import io.telicent.smart.cache.sources.TelicentHeaders;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.kafka.common.FusekiSink;
import org.apache.jena.sparql.core.Quad;
import org.apache.kafka.common.utils.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An event sink that handles incoming events from Fuseki Kafka connector applying them, and their security labels, to a
 * {@link DatasetGraphLabelled} instance
 */
@SuppressWarnings("java:S3776")
public class SmartCacheGraphSink extends FusekiSink<DatasetGraphLabelled> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmartCacheGraphSink.class);

    private final boolean routeToNamedGraphs;
    private final DistributionLifecycleStateFile lifecycleStateFile;
    // Tracks distributions whose first event we have already rejected (DLQ'd). The first event for a deleted
    // distribution throws so it is dead-lettered with a clear reason; subsequent events for the same distribution are
    // silently dropped. NB - this is in-memory only, so after a restart the first new event for a still-deleted
    // distribution will be DLQ'd again rather than dropped.
    private final Set<String> deletedDistributionsAlreadyRejected = ConcurrentHashMap.newKeySet();

    public SmartCacheGraphSink(DatasetGraphLabelled dataset, boolean routeToNamedGraphs) {
        this(dataset,routeToNamedGraphs,null);
    }

    public SmartCacheGraphSink(DatasetGraphLabelled dataset, boolean routeToNamedGraphs, DistributionLifecycleStateFile lifecycleStateFile){
        super(dataset);
        this.routeToNamedGraphs = routeToNamedGraphs;
        this.lifecycleStateFile = lifecycleStateFile;
    }

    @Override
    protected void applyRdfPatchEvent(Event<Bytes, RdfPayload> event) {
        // NB - A RDF Patch might have transaction boundaries in it, so we use our derived applicator class as
        //      that handles those sensibly.  Transaction boundaries can still lead to failures if the
        //      transaction boundaries in the patch are not valid.
        //      The implementation used here also ensures that labels are applied to the labels store as appropriate
        final String distributionId = getDistributionId(event);
        if(ignoreEvent(distributionId)){
            return;
        }
        final RDFChangesApplyWithLabels apply =
                new RDFChangesApplyWithLabels(this.dataset, getEventSecurityLabel(event), distributionId);
        event.value().getPatch().apply(apply);
        apply.applyPendingSecurityLabels();
    }

    @Override
    @SuppressWarnings("resources")
    protected void applyDatasetEvent(Event<Bytes, RdfPayload> event) {
        final String distributionId = getDistributionId(event);
        if (ignoreEvent(distributionId)) {
            return;
        }
        // Find the Security-Label for this event, if any
        final SecurityLabels<?> eventSecurityLabel = getEventSecurityLabel(event);
        final Node targetGraph = this.routeToNamedGraphs ? NodeFactory.createURI(distributionId) : null;
        final Set<Quad> pendingSecurityLabels = new LinkedHashSet<>();

        // Copy across quads, updating the labels store as needed
        event.value().getDataset().stream().forEach(q -> {
            if (q.getGraph().equals(this.dataset.labelsGraphName())) {
                // Ignore, labels graph is only metadata and not written to target dataset
                return;
            }
            if (routeToNamedGraphs) {
                final Quad rerouted = Quad.create(targetGraph, q.getSubject(), q.getPredicate(), q.getObject());
                this.dataset.add(rerouted);
                if (eventSecurityLabel != null) {
                    pendingSecurityLabels.add(rerouted);
                }
            }
            else {
                this.dataset.add(q);
                if (eventSecurityLabel != null) {
                    // Specific label for this event
                    pendingSecurityLabels.add(q);
                }
            }
            // NB - If no specific label for this event, dataset default will apply at read time, no need to set
            //      anything in the labels store
        });

        if (eventSecurityLabel != null && !pendingSecurityLabels.isEmpty()) {
            this.dataset.addLabels(pendingSecurityLabels, eventSecurityLabel);
        }

        // Apply fine-grained labels graph (if any) to the labels store
        final Graph labelsGraph = event.value().getDataset().getGraph(this.dataset.labelsGraphName());
        if (labelsGraph != null && !labelsGraph.isEmpty()) {
            this.dataset.addLabelsGraph(labelsGraph);
        }
    }

    private SecurityLabels<?> getEventSecurityLabel(Event<Bytes, RdfPayload> event) {
        final String rawLabel = event.lastHeader(TelicentHeaders.SECURITY_LABEL);
        // NB - A malformed label is rejected here, before anything is written
        return StringUtils.isNotBlank(rawLabel) ?
               this.dataset.labelsParser().parseSecurityLabels(rawLabel.getBytes(StandardCharsets.UTF_8)) : null;
    }

    /*
     * To avoid accidentally routing set null
     */
    private String getDistributionId(Event<Bytes, RdfPayload> event){
        return this.routeToNamedGraphs ? event.lastHeader(TelicentHeaders.DISTRIBUTION_ID) : null;
    }

    /*
     * Decides whether an event should be skipped before any writes. Returns false when the event should be ingested,
     * true when it should be silently dropped, and throws to reject (DLQ) the event - for a missing distribution id,
     * unavailable lifecycle state, or the first event of a deleted distribution.
     */
    private boolean ignoreEvent(String distributionId) {
        if (!this.routeToNamedGraphs) {
            return false;
        }

        if (StringUtils.isEmpty(distributionId)) {
            throw new IllegalArgumentException("No distribution id specified when in routing mode");
        }
        if (this.lifecycleStateFile == null) {
            return false;
        }

        final DistributionLifecycleStateFile.DistributionStateResult stateResult =
                this.lifecycleStateFile.distributionStateResult(distributionId);
        if (!stateResult.available()) {
            throw new IllegalStateException(
                    "Rejecting ingest because distribution lifecycle state is unavailable for distribution "
                            + distributionId);
        }

        final String state = stateResult.state();
        if(viableState(state)){
            this.deletedDistributionsAlreadyRejected.remove(distributionId);
            return false;
        }
        if (this.deletedDistributionsAlreadyRejected.add(distributionId)) {
            throw new IllegalStateException(
                    "Rejecting ingest for " + state + " distribution " + distributionId);
        }

        LOGGER.info("Dropping ingest for previously rejected distribution {}", distributionId);
        return true;
    }

    private static boolean viableState(String state) {
        return !DistributionLifecycleState.Deleted.name().equals(state)
                && !DistributionLifecycleState.Unregistered.name().equals(state);
    }

    @Override
    public String toString() {
        return "SmartCacheGraphSink()";
    }

}
