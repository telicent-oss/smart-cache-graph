package io.telicent.core;

import io.telicent.distribution.DistributionLifecycleStateFile;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.core.VocabAuthz;
import io.telicent.jena.abac.labels.Label;
import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.smart.cache.distribution.lifecycle.DistributionLifecycleState;
import io.telicent.smart.cache.payloads.RdfPayload;
import io.telicent.smart.cache.sources.Event;
import io.telicent.smart.cache.sources.TelicentHeaders;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.kafka.common.FusekiSink;
import org.apache.jena.rdfpatch.RDFChanges;
import org.apache.jena.sparql.core.Quad;
import org.apache.kafka.common.utils.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An event sink that handles incoming events from Fuseki Kafka connector applying them, and their security labels, to a
 * {@link DatasetGraphABAC} instance
 */
public class SmartCacheGraphSink extends FusekiSink<DatasetGraphABAC> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmartCacheGraphSink.class);

    private final boolean routeToNamedGraphs;
    private final DistributionLifecycleStateFile lifecycleState;
    // Tracks distributions whose first event we have already rejected (DLQ'd). The first event for a deleted/unregistered
    // distribution throws so it is dead-lettered with a clear reason; subsequent events for the same distribution are
    // silently dropped. NB - this is in-memory only, so after a restart the first new event for a still-deleted
    // distribution will be DLQ'd again rather than dropped.
    private final Set<String> distributionsAlreadyRejected = ConcurrentHashMap.newKeySet();

    public SmartCacheGraphSink(DatasetGraphABAC dataset, boolean routeToNamedGraphs) {
        this(dataset, routeToNamedGraphs, null);
    }

    public SmartCacheGraphSink(DatasetGraphABAC dataset, boolean routeToNamedGraphs,
                               DistributionLifecycleStateFile lifecycleState) {
        super(dataset);
        this.routeToNamedGraphs = routeToNamedGraphs;
        this.lifecycleState = lifecycleState;
    }

    @Override
    protected void applyRdfPatchEvent(Event<Bytes, RdfPayload> event) {
        // NB - A RDF Patch might have transaction boundaries in it, so we use our derived applicator class as
        //      that handles those sensibly.  Transaction boundaries can still lead to failures if the
        //      transaction boundaries in the patch are not valid.
        //      The implementation used here also ensures that labels are applied to the labels store as appropriate
        String distributionId = getDistributionId(event);
        if (ignoreEvent(distributionId)) {
            return;
        }
        RDFChanges apply =
                new RDFChangesApplyWithLabels(this.dataset, getEventSecurityLabel(event), distributionId);
        event.value().getPatch().apply(apply);
    }

    @Override
    @SuppressWarnings("resources")
    protected void applyDatasetEvent(Event<Bytes, RdfPayload> event) {
        String distributionId = getDistributionId(event);
        if (ignoreEvent(distributionId)) {
            return;
        }
        // Find the Security-Label for this event, if any
        Label eventSecurityLabel = getEventSecurityLabel(event);
        LabelsStore labelsStore = this.dataset.labelsStore();
        Node targetGraph = this.routeToNamedGraphs ? NodeFactory.createURI(distributionId) : null;

        // Copy across quads, updating the labels store as needed
        event.value().getDataset().stream().forEach(q -> {
            if (q.getGraph().equals(VocabAuthz.graphForLabels)) {
                // Ignore, labels graph is only metadata and not written to target dataset
                return;
            }
            if (routeToNamedGraphs) {
                Quad rerouted = Quad.create(targetGraph, q.getSubject(), q.getPredicate(), q.getObject());
                this.dataset.add(rerouted);
                if (eventSecurityLabel != null) {
                    labelsStore.add(rerouted, eventSecurityLabel);
                }
            }
            else {
                this.dataset.add(q);
                if (eventSecurityLabel != null) {
                    // Specific label for this event
                    labelsStore.add(q, eventSecurityLabel);
                }
            }
            // NB - If no specific label for this event, dataset default will apply at read time, no need to set
            //      anything in the labels store
        });

        // Apply fine-grained labels graph (if any) to the labels store
        Graph labelsGraph = event.value().getDataset().getGraph(VocabAuthz.graphForLabels);
        if (labelsGraph != null && !labelsGraph.isEmpty()) {
            labelsStore.addGraph(labelsGraph);
        }
    }

    private static Label getEventSecurityLabel(Event<Bytes, RdfPayload> event) {
        return StringUtils.isNotBlank(event.lastHeader(TelicentHeaders.SECURITY_LABEL)) ?
               Label.fromText(event.lastHeader(TelicentHeaders.SECURITY_LABEL)) : null;
    }

    /*
     * To avoid accidentally routing set null
     */
    private String getDistributionId(Event<Bytes, RdfPayload> event) {
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
        if (this.lifecycleState == null) {
            return false;
        }

        DistributionLifecycleStateFile.DistributionStateResult stateResult =
                this.lifecycleState.distributionStateResult(distributionId);
        if (!stateResult.available()) {
            throw new IllegalStateException(
                    "Rejecting ingest because distribution lifecycle state is unavailable for distribution "
                    + distributionId);
        }

        String state = stateResult.state();
        if (viableState(state)) {
            this.distributionsAlreadyRejected.remove(distributionId);
            return false;
        }
        if (this.distributionsAlreadyRejected.add(distributionId)) {
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
