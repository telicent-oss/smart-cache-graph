package io.telicent.access;

import io.telicent.access.services.AccessQueryService;
import io.telicent.access.servlets.AccessQueryServlet;
import io.telicent.access.servlets.AccessTriplesServlet;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiAutoModule;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.DatasetGraph;

public class FMod_AccessQuery implements FusekiAutoModule {

    @Override
    public String name() {
        return "Access Query Module";
    }

    @Override
    public void configured(final FusekiServer.Builder serverBuilder, final DataAccessPointRegistry dapRegistry, final Model configModel) {
        for (DataAccessPoint dap : dapRegistry.accessPoints()) {
            final DatasetGraph dsg = dap.getDataService().getDataset();
            if (dsg instanceof DatasetGraphABAC abac) {
                final String datasetName = dap.getName();
                final AccessQueryService queryService = new AccessQueryService(abac);
                serverBuilder.addServlet(datasetName + "/access/query", new AccessQueryServlet(queryService));
                serverBuilder.addServlet( datasetName + "/access/triples", new AccessTriplesServlet(queryService));
            }
        }
    }

}
