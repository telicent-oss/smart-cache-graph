package io.telicent.access.services;

import io.telicent.jena.abac.fuseki.ABAC_Processor;
import io.telicent.jena.abac.fuseki.ABAC_Request;
import io.telicent.jena.abac.fuseki.ServerABAC;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.impl.LiteralLabel;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.util.iterator.ExtendedIterator;

import java.util.List;

public class AccessQueryService implements ABAC_Processor {

    private final DatasetGraph datasetGraph;

    public AccessQueryService(DatasetGraph datasetGraph) {
        this.datasetGraph = datasetGraph;
    }

    public List<Triple> getTriples(final HttpAction action, final Triple triple) {
        final DatasetGraph datasetGraphForUser = ABAC_Request.decideDataset(action, datasetGraph, ServerABAC.userForRequest());
        return datasetGraphForUser.getDefaultGraph().find(triple).toList();
    }

    public int getVisibleTriplesCount(final HttpAction action, final List<Triple> triples) {
        final DatasetGraph datasetGraphForUser = ABAC_Request.decideDataset(action, datasetGraph, ServerABAC.userForRequest());
        int visibleCount = 0;
        for (Triple triple : triples) {
            if (triple.getObject().isLiteral()) {
                if (isTripleInGraph(datasetGraphForUser.getDefaultGraph(), triple)) {
                    visibleCount++;
                }
            } else {
                if (datasetGraphForUser.getDefaultGraph().contains(triple)) {
                    visibleCount++;
                }
            }
        }
        return visibleCount;
    }

    // datasetGraphForUser.getDefaultGraph().contains(triple) doesn't work for literal values for some reason
    // so instead we have to iterate over the triples that match the subject and predicate and then compare
    // the literal values and datatypes to see if we have a match..
    private boolean isTripleInGraph(Graph graph, Triple triple) {
        final ExtendedIterator<Triple> iter = graph.find(triple.getSubject(), triple.getPredicate(), Node.ANY);
        while (iter.hasNext()) {
            final Triple t = iter.next();
            final LiteralLabel tLiteral = t.getObject().getLiteral();
            final LiteralLabel tripleLiteral = triple.getObject().getLiteral();
            if (tLiteral.getValue().toString().equals(tripleLiteral.getValue().toString())
                    && tLiteral.getDatatypeURI().equals(tripleLiteral.getDatatypeURI())) {
                return true; // there will only ever be one exact match so we can return here
            }
        }
        return false; // no match was found;
    }

}
