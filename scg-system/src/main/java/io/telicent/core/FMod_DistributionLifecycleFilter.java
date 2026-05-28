package io.telicent.core;

import io.telicent.distribution.DistributionLifecycleFilters;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.smart.cache.configuration.Configurator;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiAutoModule;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.rdf.model.Model;

/**
 * Installs lifecycle-aware named graph filtering for ABAC datasets.
 */
public class FMod_DistributionLifecycleFilter implements FusekiAutoModule {

    public static final String DISTRIBUTION_LIFECYCLE_STATE_FILE = "DISTRIBUTION_LIFECYCLE_STATE_FILE";
    public static final String DISTRIBUTION_LIFECYCLE_APP_ID = "DISTRIBUTION_LIFECYCLE_APP_ID";

    private static final String VERSION =
            Version.versionForClass(FMod_DistributionLifecycleFilter.class).orElse("<development>");

    @Override
    public String name() {
        return "Distribution Lifecycle Module";
    }

    @Override
    public void configured(FusekiServer.Builder serverBuilder, DataAccessPointRegistry dapRegistry, Model configModel) {
        String stateFile = Configurator.get(DISTRIBUTION_LIFECYCLE_STATE_FILE);
        if (StringUtils.isBlank(stateFile)) {
            return;
        }

        boolean routeToNamedGraphs = Configurator.get("ROUTE_TO_NAMED_GRAPHS", Boolean::parseBoolean, false);
        if (!routeToNamedGraphs) {
            FmtLog.warn(Fuseki.configLog,
                        "Telicent Distribution Lifecycle Module (%s) ignored because ROUTE_TO_NAMED_GRAPHS is disabled",
                        VERSION);
            return;
        }

        FmtLog.info(Fuseki.configLog, "Telicent Distribution Lifecycle Module (%s)", VERSION);
        for (DataAccessPoint dap : dapRegistry.accessPoints()) {
            if (!(dap.getDataService().getDataset() instanceof DatasetGraphABAC abacDataset)) {
                continue;
            }
            DistributionLifecycleFilters.installIfConfigured(abacDataset);
        }
    }
}