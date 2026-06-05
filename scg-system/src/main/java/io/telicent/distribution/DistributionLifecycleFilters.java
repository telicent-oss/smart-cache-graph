package io.telicent.distribution;

import io.telicent.jena.abac.DatasetFilterProvider;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/** Installs the lifecycle-aware dataset filter when the feature is configured. */
public class DistributionLifecycleFilters {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributionLifecycleFilters.class);

    private DistributionLifecycleFilters() {
    }

    public static boolean installIfConfigured(DatasetGraphABAC dataset, String applicationId, String stateFile) {
        if (dataset.getFilterProvider() instanceof DistributionLifecycleDatasetFilterProvider) {
            LOGGER.info("Lifecycle-aware dataset filter already installed for this dataset; skipping");
            return false;
        }

        DatasetFilterProvider delegate = dataset.getFilterProvider();
        dataset.setFilterProvider(new DistributionLifecycleDatasetFilterProvider(
                new DistributionLifecycleStateFile(Path.of(stateFile), applicationId), delegate));
        if (delegate != null) {
            LOGGER.info("Installed lifecycle-aware dataset filter for SCG named-graph routing by wrapping existing dataset filter provider");
        } else {
            LOGGER.info("Installed lifecycle-aware dataset filter for SCG named-graph routing");
        }
        return true;
    }
}
