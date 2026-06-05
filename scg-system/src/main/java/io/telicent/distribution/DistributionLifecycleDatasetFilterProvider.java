package io.telicent.distribution;

import io.telicent.jena.abac.DefaultDatasetFilterProvider;
import io.telicent.jena.abac.DatasetFilterProvider;
import io.telicent.jena.abac.core.CxtABAC;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.Label;
import io.telicent.jena.abac.labels.LabelsStore;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFilteredView;

import java.util.Set;

/**
 * A dataset filter provider that limits visible named graphs to distributions that are currently active.
 */
public class DistributionLifecycleDatasetFilterProvider implements DatasetFilterProvider {

    private static final DatasetFilterProvider DEFAULT_PROVIDER = new DefaultDatasetFilterProvider();

    private final DistributionLifecycleStateFile lifecycleStateFile;
    private final DatasetFilterProvider delegate;

    DistributionLifecycleDatasetFilterProvider(DistributionLifecycleStateFile lifecycleStateFile,
                                              DatasetFilterProvider delegate) {
        this.lifecycleStateFile = lifecycleStateFile;
        this.delegate = delegate != null ? delegate : DEFAULT_PROVIDER;
    }

    @Override
    public DatasetGraph filterDataset(DatasetGraphABAC dsgAuthz, CxtABAC cxt) {
        DatasetGraph filtered = this.delegate.filterDataset(dsgAuthz, cxt);
        return applyLifecycleFilter(filtered);
    }

    @Override
    public DatasetGraph filterDataset(DatasetGraph dsgBase, LabelsStore labels, Label defaultLabel, CxtABAC cxt) {
        DatasetGraph filtered = this.delegate.filterDataset(dsgBase, labels, defaultLabel, cxt);
        return applyLifecycleFilter(filtered);
    }

    private DatasetGraph applyLifecycleFilter(DatasetGraph dataset) {
        Set<Node> activeGraphs = this.lifecycleStateFile.activeGraphNodes();
        return new DatasetGraphFilteredView(dataset, null, activeGraphs);
    }
}
