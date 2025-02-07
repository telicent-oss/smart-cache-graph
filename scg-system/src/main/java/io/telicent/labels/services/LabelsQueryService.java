package io.telicent.labels.services;

import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.labels.TripleLabels;
import org.apache.jena.graph.Triple;

import java.util.List;

public class LabelsQueryService {


    private final LabelsStore labelStore;

    public LabelsQueryService(LabelsStore labelStore) {
        this.labelStore = labelStore;
    }

    public TripleLabels queryLabelStore(Triple triple) {
        final List<String> labels = labelStore.labelsForTriples(triple);
        return new TripleLabels(labels);
    }
}
