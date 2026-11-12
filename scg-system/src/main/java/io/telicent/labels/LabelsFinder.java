package io.telicent.labels;

import io.telicent.smart.cache.security.data.labels.SecurityLabelsApplicator;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;

import java.util.Iterator;
import java.util.List;

/**
 * Finds the quads matching a pattern and resolves their labels, shared by {@code /$/labels/<dataset>} and
 * {@code /<dataset>/access/labels}.
 */
public final class LabelsFinder {

    /**
     * Label that nobody can see, used for unlabelled quads when there's no other default
     */
    public static final String DENY_LABEL = "!";

    private LabelsFinder() {
    }

    /**
     * Finds the quads matching a pattern in a dataset and adds each of them, with its label, to the results. Quads in
     * the labels graph are always skipped since that holds label metadata rather than data, and must never be
     * reported by the access controlled endpoint.
     *
     * @param datasetGraph Dataset to search, for the access controlled endpoint this must be the user's filtered view
     * @param pattern      Quad pattern, may contain {@link Node#ANY} wildcards
     * @param applicator   Resolves the label for each quad found
     * @param labelsGraph  Labels graph name, or {@code null} if there is none
     * @param results      Results to add to
     */
    public static void findWithLabels(DatasetGraph datasetGraph, Quad pattern, SecurityLabelsApplicator applicator,
                                      Node labelsGraph, List<QuadLabels> results) {
        final Iterator<Quad> iter = datasetGraph.find(pattern);
        try {
            while (iter.hasNext()) {
                final Quad q = normaliseDefaultGraph(iter.next());
                if (labelsGraph != null && labelsGraph.equals(q.getGraph())) {
                    continue;
                }
                results.add(new QuadLabels(q, applicator.labelForQuad(q)));
            }
        } finally {
            Iter.close(iter);
        }
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
