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

import java.util.ArrayList;
import java.util.List;

public class FMod_LabelsQuery implements FusekiAutoModule {

    /**
     * Configuration flag to enable/disable functionality.
     */
    public static final String ENABLE_LABELS_QUERY = "ENABLE_LABELS_QUERY";

    private List<LabelsQueryService> getLabelsQueryService(DataAccessPointRegistry dapRegistry) {
        List<LabelsQueryService> labelsQueryServices = new ArrayList<>();
        for (DataAccessPoint dap : dapRegistry.accessPoints()) {
            final DatasetGraph dsg = dap.getDataService().getDataset();
            final String datasetName = dap.getName();
            if (dsg instanceof DatasetGraphABAC abac) {
                final LabelsStore labelsStore = abac.labelsStore();
                labelsQueryServices.add(new LabelsQueryService(labelsStore, abac, datasetName));
            }
        }
        return labelsQueryServices;
    }

    @Override
    public String name() {
        return "Labels Query Module";
    }

    @Override
    public void configured(FusekiServer.Builder serverBuilder, DataAccessPointRegistry dapRegistry, Model configModel) {
        if (isLabelsQueryEnabled()) {
            final List<LabelsQueryService> queryServices = getLabelsQueryService(dapRegistry);
            for(LabelsQueryService queryService : queryServices) {
                serverBuilder.addServlet("/$/labels" + queryService.getDatasetName(), new LabelsQueryServlet(queryService));
            }
        }
    }

    private static boolean isLabelsQueryEnabled() {
        return Configurator.get(ENABLE_LABELS_QUERY, Boolean::parseBoolean, false);
    }

}
