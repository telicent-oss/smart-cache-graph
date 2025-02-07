package io.telicent.labels;

import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.labels.services.LabelsQueryService;
import io.telicent.labels.servlets.LabelsQueryServlet;
import io.telicent.smart.cache.configuration.Configurator;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiAutoModule;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.DatasetGraph;

public class FMod_LabelsQuery implements FusekiAutoModule {

    /**
     * Configuration flag to enable/disable functionality.
     */
    public static final String ENABLE_LABELS_QUERY = "ENABLE_LABELS_QUERY";

    private LabelsQueryService getLabelsQueryService(DataAccessPointRegistry dapRegistry) {
        LabelsQueryService labelsQueryService = null;
        for (DataAccessPoint dap : dapRegistry.accessPoints()) {
            final DatasetGraph dsg = dap.getDataService().getDataset();
            if (dsg instanceof DatasetGraphABAC abac) {
                final LabelsStore labelsStore = abac.labelsStore();
                labelsQueryService = new LabelsQueryService(labelsStore);
            }
        }
        return labelsQueryService;
    }

    @Override
    public String name() {
        return "Labels Query Module";
    }

    @Override
    public void configured(FusekiServer.Builder serverBuilder, DataAccessPointRegistry dapRegistry, Model configModel) {
        if (isLabelsQueryEnabled()) {
            final LabelsQueryService queryService = getLabelsQueryService(dapRegistry);
            if(queryService != null) {
                serverBuilder.addServlet("/$/labels/query", new LabelsQueryServlet(queryService));
            }
        }
    }

    private static boolean isLabelsQueryEnabled() {
        return Configurator.get(ENABLE_LABELS_QUERY, Boolean::parseBoolean, false);
    }

}
