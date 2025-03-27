package io.telicent.access.services;

import io.telicent.jena.abac.fuseki.ABAC_Processor;
import io.telicent.jena.abac.fuseki.ABAC_Request;
import io.telicent.jena.abac.fuseki.ServerABAC;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.DatasetGraph;

import java.util.List;

public class AccessQueryService implements ABAC_Processor {

    private final DatasetGraph datasetGraph;

    public AccessQueryService(DatasetGraph datasetGraph) {
        this.datasetGraph = datasetGraph;
    }

    public List<Triple> getTriples(HttpAction action, Triple triple) {
        final DatasetGraph datasetGraphForUser = ABAC_Request.decideDataset(action, datasetGraph, ServerABAC.userForRequest());
        return datasetGraphForUser.getDefaultGraph().find(triple).toList();
    }

}
