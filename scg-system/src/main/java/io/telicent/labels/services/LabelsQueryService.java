package io.telicent.labels.services;

import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.labels.TripleLabels;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.system.Txn;
import org.apache.jena.util.iterator.ExtendedIterator;

import java.util.ArrayList;
import java.util.List;

public class LabelsQueryService {


    private final LabelsStore labelStore;
    private final DatasetGraph datasetGraph;

    public LabelsQueryService(LabelsStore labelStore, DatasetGraph datasetGraph) {
        this.labelStore = labelStore;
        this.datasetGraph = datasetGraph;
    }

    public List<TripleLabels> queryOnlyLabelStore(Triple triple) {
        return List.of(new TripleLabels(triple, labelStore.labelsForTriples(triple)));
    }

    public List<TripleLabels> queryDSGAndLabelStore(Triple triple) {
        return Txn.calculateRead(datasetGraph, () -> {
            List<TripleLabels> tripleLabels = new ArrayList<>();
            ExtendedIterator<Triple> iter = datasetGraph.getDefaultGraph().find(triple);
            while (iter.hasNext()) {
                Triple t = iter.next();
                tripleLabels.add(new TripleLabels(t, labelStore.labelsForTriples(t)));
            }
            return tripleLabels;
        });
    }
}
