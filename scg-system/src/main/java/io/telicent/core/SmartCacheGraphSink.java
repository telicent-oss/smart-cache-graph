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

import io.telicent.smart.cache.payloads.RdfPayload;
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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * An event sink that handles incoming events from Fuseki Kafka connector applying them, and their security labels, to a
 * {@link DatasetGraphLabelled} instance
 */
@SuppressWarnings("java:S3776")
public class SmartCacheGraphSink extends FusekiSink<DatasetGraphLabelled> {

    private final boolean routeToNamedGraphs;

    public SmartCacheGraphSink(DatasetGraphLabelled dataset, boolean routeToNamedGraphs) {
        super(dataset);
        this.routeToNamedGraphs = routeToNamedGraphs;
    }

    @Override
    protected void applyRdfPatchEvent(Event<Bytes, RdfPayload> event) {
        // NB - A RDF Patch might have transaction boundaries in it, so we use our derived applicator class as
        //      that handles those sensibly.  Transaction boundaries can still lead to failures if the
        //      transaction boundaries in the patch are not valid.
        //      The implementation used here also ensures that labels are applied to the labels store as appropriate
        final String distributionId = getDistributionId(event);
        final var apply = new RDFChangesApplyWithLabels(this.dataset, getEventSecurityLabel(event), distributionId);
        event.value().getPatch().apply(apply);
        apply.applyPendingSecurityLabels();
    }

    @Override
    @SuppressWarnings("resources")
    protected void applyDatasetEvent(Event<Bytes, RdfPayload> event) {
        final String distributionId = getDistributionId(event);
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
                final var rerouted = Quad.create(targetGraph, q.getSubject(), q.getPredicate(), q.getObject());
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

    private String getDistributionId(Event<Bytes, RdfPayload> event) {
        if (!this.routeToNamedGraphs) {
            return null;
        }
        String distributionId = event.lastHeader(TelicentHeaders.DISTRIBUTION_ID);
        if (StringUtils.isBlank(distributionId)) {
            throw new IllegalArgumentException("No distribution id specified when in routing mode");
        }
        return distributionId;
    }

    @Override
    public String toString() {
        return "SmartCacheGraphSink()";
    }

}
