package io.telicent.access.services;

import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.Label;
import io.telicent.labels.QuadLabels;
import io.telicent.labels.services.LabelsQueryService;
import io.telicent.smart.cache.security.data.DataAccessAuthorizer;
import io.telicent.smart.cache.security.data.labels.SecurityLabelsApplicator;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPlugin;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.system.Txn;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.telicent.labels.LabelsFinder.findWithLabels;
import static io.telicent.utils.ServletUtils.requestContextFrom;

/**
 * Provides the labels of quads, restricted to the quads that the requesting user is permitted to see.
 * <p>
 * Unlike {@link LabelsQueryService}, every pattern, including fully concrete ones, is evaluated against the user's
 * filtered view of the dataset, so no label is ever returned for a quad that the user can't see. A quad that doesn't
 * exist and one that exists but isn't visible are indistinguishable in the results.
 * </p>
 */
public class AccessLabelsService {

    private static final byte[] DENY = "!".getBytes(StandardCharsets.UTF_8);

    private final DatasetGraph datasetGraph;
    private final DataSecurityPlugin dataSecurityPlugin;

    public AccessLabelsService(DatasetGraph datasetGraph, DataSecurityPlugin dataSecurityPlugin) {
        this.datasetGraph = datasetGraph;
        this.dataSecurityPlugin = dataSecurityPlugin;
    }

    /**
     * Gets the visible quads, and their labels, matching the given patterns
     *
     * @param action   HTTP Action for the authenticated request
     * @param patterns Quad patterns, may contain {@link Node#ANY} wildcards
     * @return Visible quads and their labels, empty if nothing matches or nothing is visible
     */
    public List<QuadLabels> getLabels(final HttpAction action, final List<Quad> patterns) {
        try (DataAccessAuthorizer authorizer = dataSecurityPlugin.prepareAuthorizer(requestContextFrom(action))) {
            return Txn.calculateRead(datasetGraph, () -> {
                final Optional<DatasetGraph> datasetGraphForUser = authorizer.decideDataset(action, datasetGraph);
                if (datasetGraphForUser.isEmpty()) {
                    return List.of();
                }
                try (SecurityLabelsApplicator applicator = dataSecurityPlugin.prepareLabelsApplicator(
                        defaultLabel(), datasetGraph)) {
                    final Node labelsGraph = dataSecurityPlugin.labelsGraphName().orElse(null);
                    final List<QuadLabels> results = new ArrayList<>();
                    for (Quad pattern : patterns) {
                        findWithLabels(datasetGraphForUser.get(), pattern, applicator, labelsGraph, results);
                    }
                    return results;
                }
            });
        }
    }

    /**
     * Unlabelled quads are visible according to the dataset's default label (authz:tripleDefaultAttributes) so report
     * that, falling back to deny if the dataset has none
     */
    private byte[] defaultLabel() {
        if (datasetGraph instanceof DatasetGraphABAC datasetGraphABAC) {
            final Label label = datasetGraphABAC.getDefaultLabel();
            if (label != null) {
                return label.data();
            }
        }
        return DENY;
    }
}
