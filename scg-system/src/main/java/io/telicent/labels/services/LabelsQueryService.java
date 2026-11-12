package io.telicent.labels.services;

import io.telicent.labels.QuadLabels;
import io.telicent.smart.cache.security.data.labels.SecurityLabelsApplicator;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPlugin;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.system.Txn;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static io.telicent.labels.LabelsFinder.DENY_LABEL;
import static io.telicent.labels.LabelsFinder.findWithLabels;

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

    public List<QuadLabels> queryOnlyLabelStore(Triple triple) {
        return queryOnlyLabelStore(Quad.create(Quad.defaultGraphIRI, triple));
    }

    public List<QuadLabels> queryOnlyLabelStore(Quad quad) {
        try (final SecurityLabelsApplicator applicator = dataSecurityPlugin.prepareLabelsApplicator(
                DENY_LABEL.getBytes(StandardCharsets.UTF_8), datasetGraph)) {
            return List.of(new QuadLabels(quad, applicator.labelForQuad(quad)));
        }
    }

    public List<QuadLabels> queryDSGAndLabelStore(Triple triple) {
        return queryDSGAndLabelStore(Quad.create(Quad.defaultGraphIRI, triple));
    }

    public List<QuadLabels> queryDSGAndLabelStore(Quad quad) {
        return Txn.calculateRead(datasetGraph, () -> {
            try (final SecurityLabelsApplicator applicator = dataSecurityPlugin.prepareLabelsApplicator(
                    DENY_LABEL.getBytes(StandardCharsets.UTF_8), datasetGraph)) {
                final List<QuadLabels> tripleLabels = new ArrayList<>();
                final Node labelsGraph = dataSecurityPlugin.labelsGraphName().orElse(null);
                findWithLabels(datasetGraph, quad, applicator, labelsGraph, tripleLabels);
                return tripleLabels;
            }
        });
    }
}
