package io.telicent.labels;

import io.telicent.labels.services.LabelsQueryService;
import io.telicent.labels.servlets.LabelsQueryServlet;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPlugin;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPluginLoader;
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
        final List<LabelsQueryService> labelsQueryServices = new ArrayList<>();
        final DataSecurityPlugin dataSecurityPlugin = DataSecurityPluginLoader.load();

        for (DataAccessPoint dap : dapRegistry.accessPoints()) {
            final DatasetGraph dsg = dap.getDataService().getDataset();
            final String datasetName = dap.getName();
            labelsQueryServices.add(new LabelsQueryService(dataSecurityPlugin, dsg, datasetName));
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
