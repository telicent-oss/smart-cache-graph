package io.telicent.labels.services;

import io.telicent.labels.TripleLabels;
import io.telicent.smart.cache.security.data.labels.SecurityLabelsApplicator;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPlugin;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.system.Txn;
import org.apache.jena.util.iterator.ExtendedIterator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LabelsQueryService {

    private final DatasetGraph datasetGraph;
    private final String datasetName;
    private final DataSecurityPlugin dataSecurityPlugin;

    public LabelsQueryService(DataSecurityPlugin dataSecurityPlugin, DatasetGraph datasetGraph, String datasetName) {
        this.dataSecurityPlugin = dataSecurityPlugin;
        this.datasetGraph = datasetGraph;
        this.datasetName = datasetName;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public List<TripleLabels> queryOnlyLabelStore(Triple triple) {
        try(final SecurityLabelsApplicator applicator = dataSecurityPlugin.prepareLabelsApplicator("!".getBytes(StandardCharsets.UTF_8), datasetGraph)){
            return List.of(new TripleLabels(triple, applicator.labelForTriple(triple)));
        }
    }

    public List<TripleLabels> queryDSGAndLabelStore(Triple triple) {
        return Txn.calculateRead(datasetGraph, () -> {
            try(final SecurityLabelsApplicator applicator = dataSecurityPlugin.prepareLabelsApplicator("!".getBytes(StandardCharsets.UTF_8), datasetGraph)){
                List<TripleLabels> tripleLabels = new ArrayList<>();
                ExtendedIterator<Triple> iter = datasetGraph.getDefaultGraph().find(triple);
                try {
                    while (iter.hasNext()) {
                        Triple t = iter.next();
                        tripleLabels.add(new TripleLabels(t, applicator.labelForTriple(t)));
                    }
                } finally {
                    iter.close();
                }
                return tripleLabels;
            }
        });
    }
}
