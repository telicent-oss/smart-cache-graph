package io.telicent.labels.services;

import io.telicent.jena.abac.core.VocabAuthz;
import io.telicent.labels.QuadLabels;
import io.telicent.smart.cache.security.data.labels.SecurityLabelsApplicator;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPlugin;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.system.Txn;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LabelsQueryService {

    private final DatasetGraph datasetGraph;
    private final String datasetName;
    private final DataSecurityPlugin dataSecurityPlugin;

    private static final String DENY = "!";

    public LabelsQueryService(DataSecurityPlugin dataSecurityPlugin, DatasetGraph datasetGraph, String datasetName) {
        this.dataSecurityPlugin = dataSecurityPlugin;
        this.datasetGraph = datasetGraph;
        this.datasetName = datasetName;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public List<QuadLabels> queryOnlyLabelStore(Triple triple) {
        return queryOnlyLabelStore(Quad.create(Quad.defaultGraphIRI, triple));
    }

    public List<QuadLabels> queryOnlyLabelStore(Quad quad) {
        try (final SecurityLabelsApplicator applicator = dataSecurityPlugin.prepareLabelsApplicator(
                DENY.getBytes(StandardCharsets.UTF_8), datasetGraph)) {
            return List.of(new QuadLabels(quad, applicator.labelForQuad(quad)));
        }
    }

    public List<QuadLabels> queryDSGAndLabelStore(Triple triple) {
        return queryDSGAndLabelStore(Quad.create(Quad.defaultGraphIRI, triple));
    }

    public List<QuadLabels> queryDSGAndLabelStore(Quad quad) {
        return Txn.calculateRead(datasetGraph, () -> {
            try (final SecurityLabelsApplicator applicator = dataSecurityPlugin.prepareLabelsApplicator(
                    DENY.getBytes(StandardCharsets.UTF_8), datasetGraph)) {
                final List<QuadLabels> tripleLabels = new ArrayList<>();
                final Iterator<Quad> iter = datasetGraph.find(quad);
                try {
                    while (iter.hasNext()) {
                        final Quad q = normaliseDefaultGraph(iter.next());
                        // The labels graph holds label metadata rather than data
                        if (VocabAuthz.graphForLabels.equals(q.getGraph())) {
                            continue;
                        }
                        tripleLabels.add(new QuadLabels(q, applicator.labelForQuad(q)));
                    }
                } finally {
                    Iter.close(iter);
                }
                return tripleLabels;
            }
        });
    }

    /**
     * Datasets may report the default graph using a different node, labels are stored against
     * {@link Quad#defaultGraphIRI} so use that consistently.
     */
    private static Quad normaliseDefaultGraph(Quad quad) {
        if (quad.isDefaultGraph() && !Quad.defaultGraphIRI.equals(quad.getGraph())) {
            return Quad.create(Quad.defaultGraphIRI, quad.asTriple());
        }
        return quad;
    }
}
