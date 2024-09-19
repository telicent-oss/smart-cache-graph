package io.telicent.core;

import io.telicent.core.queryTracker.SPARQL_QueryDataset_Wrapper;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.server.Endpoint;
import org.apache.jena.fuseki.server.Operation;
import org.apache.jena.fuseki.servlets.ActionProcessor;
import org.apache.jena.fuseki.servlets.SPARQL_QueryDataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.DatasetGraph;

public class FMod_QueryTracker implements FusekiModule {

    @Override
    public String name() {
        return "Test Module for tracking queries";
    }

    @Override
    public void configDataAccessPoint(DataAccessPoint dap, Model configModel) {
        DataService dataService = dap.getDataService();
        DatasetGraph dsg = dataService.getDataset();
        if (!(dsg instanceof DatasetGraphABAC)) {
            // Not ABAC
            // Replace standard SPARQL STUFF
            replaceNormal(dataService);
        } else {
            // ABAC. Replace Secured ABAC Stuff
            replaceABAC(dataService);
        }
    }

    private void replaceNormal(DataService dataService) {
        // Replace the SPARQL Query Dataset
        for (Endpoint endpoint : dataService.getEndpoints(Operation.Query)) {
            ActionProcessor processor = new SPARQL_QueryDataset_Wrapper(new SPARQL_QueryDataset());
            endpoint.setProcessor(processor);
        }
    }

    private void replaceABAC(DataService dataService) {
        // Replace ABAC endpoints. NO-OP for now
    }
}
