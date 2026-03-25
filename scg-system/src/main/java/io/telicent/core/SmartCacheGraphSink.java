package io.telicent.core;

import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.core.VocabAuthz;
import io.telicent.jena.abac.labels.Label;
import io.telicent.jena.abac.labels.LabelsStore;
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

/**
 * An event sink that handles incoming events from Fuseki Kafka connector applying them, and their security labels, to a
 * {@link DatasetGraphABAC} instance
 */
public class SmartCacheGraphSink extends FusekiSink<DatasetGraphABAC> {

    private final boolean routeToNamedGraphs;

    public SmartCacheGraphSink(DatasetGraphABAC dataset, boolean routeToNamedGraphs) {
        super(dataset);
        this.routeToNamedGraphs = routeToNamedGraphs;
    }

    @Override
    protected void applyRdfPatchEvent(Event<Bytes, RdfPayload> event) {
        // NB - A RDF Patch might have transaction boundaries in it, so we use our derived applicator class as
        //      that handles those sensibly.  Transaction boundaries can still lead to failures if the
        //      transaction boundaries in the patch are not valid.
        //      The implementation used here also ensures that labels are applied to the labels store as appropriate
        String distributionId = null;
        if (routeToNamedGraphs) {
            distributionId = event.lastHeader("Distribution-Id");
            if (StringUtils.isEmpty(distributionId)) {
                throw new IllegalArgumentException("No distribution id specified when in routing mode");
            }
            RDFChanges apply = new RDFChangesApplyWithLabels(this.dataset, getEventSecurityLabel(event), distributionId);
            event.value().getPatch().apply(apply);
        }
        else {
            RDFChanges apply = new RDFChangesApplyWithLabels(this.dataset, getEventSecurityLabel(event));
            event.value().getPatch().apply(apply);
        }
    }

    @Override
    @SuppressWarnings("resources")
    protected void applyDatasetEvent(Event<Bytes, RdfPayload> event) {
        // Find the Security-Label for this event, if any
        Label eventSecurityLabel = getEventSecurityLabel(event);
        LabelsStore labelsStore = this.dataset.labelsStore();

        // Copy across quads, updating the labels store as needed
        event.value().getDataset().stream().forEach(q -> {
            System.out.println("Processing quad: " + q);
            System.out.println("Graph: " + q.getGraph());
            System.out.println("routeToNamedGraphs: " + routeToNamedGraphs);
            if (q.getGraph().equals(VocabAuthz.graphForLabels)) {
                // Ignore, labels graph is only metadata and not written to target dataset
                return;
            }

            if (routeToNamedGraphs) {
                //TODO
                // add TelicentHeaders.DISTRIBUTION_ID const
                String distributionId = event.lastHeader("Distribution-Id");
                if (StringUtils.isEmpty(distributionId)) {
                    //throw new IllegalArgumentException("No distribution id specified when in routing mode");
                    IllegalArgumentException ex = new IllegalArgumentException("No distribution id specified when in routing mode");
                    ex.printStackTrace();
                    throw ex;
                }
                else {
                    Node targetGraph = NodeFactory.createURI(distributionId);
                    Quad rerouted = new Quad(targetGraph, q.getSubject(), q.getPredicate(), q.getObject());
                    this.dataset.add(rerouted);
                    if (eventSecurityLabel != null) {
                        // Specific label for this event
                        //TODO
                        // write a comment about changing it once the label graph name filtering is on
                        // needs updating once
                        labelsStore.add(rerouted.asTriple(), eventSecurityLabel);
                    }
                }
            }
            else {
                this.dataset.add(q);
                if (eventSecurityLabel != null) {
                    // Specific label for this event
                    labelsStore.add(q.asTriple(), eventSecurityLabel);
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

    @Override
    public String toString() {
        return "SmartCacheGraphSink()";
    }
}
