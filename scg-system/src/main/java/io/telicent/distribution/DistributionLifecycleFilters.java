package io.telicent.distribution;

import io.telicent.core.FMod_DistributionLifecycleFilter;
import io.telicent.jena.abac.DatasetFilterProvider;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.smart.cache.configuration.Configurator;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/** Installs the lifecycle-aware dataset filter when the feature is configured. */
public class DistributionLifecycleFilters {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributionLifecycleFilters.class);

    private DistributionLifecycleFilters() {
    }

    public static boolean installIfConfigured(DatasetGraphABAC dataset, boolean routeToNamedGraphs) {
        if (!routeToNamedGraphs) {
            return false;
        }
        if (dataset.getFilterProvider() instanceof DistributionLifecycleDatasetFilterProvider) {
            return false;
        }

        String stateFile = Configurator.get(FMod_DistributionLifecycleFilter.DISTRIBUTION_LIFECYCLE_STATE_FILE);
        if (StringUtils.isBlank(stateFile)) {
            return false;
        }

        String applicationId = Configurator.get(FMod_DistributionLifecycleFilter.DISTRIBUTION_LIFECYCLE_APP_ID);
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
