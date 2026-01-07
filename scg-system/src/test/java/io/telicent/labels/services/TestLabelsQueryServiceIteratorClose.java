package io.telicent.labels.services;

import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.labels.TripleLabels;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestLabelsQueryServiceIteratorClose {

    @Test
    void queryDSGAndLabelStoreClosesIterator() {
        DatasetGraph datasetGraph = mock(DatasetGraph.class);
        Graph graph = mock(Graph.class);
        when(datasetGraph.getDefaultGraph()).thenReturn(graph);

        Triple triple = Triple.create(
                NodeFactory.createURI("urn:s"),
                NodeFactory.createURI("urn:p"),
                NodeFactory.createURI("urn:o")
        );
        @SuppressWarnings("unchecked")
        ExtendedIterator<Triple> iter = mock(ExtendedIterator.class);
        when(iter.hasNext()).thenReturn(true, false);
        when(iter.next()).thenReturn(triple);
        when(graph.find(triple)).thenReturn(iter);

        LabelsStore labelsStore = mock(LabelsStore.class);
        when(labelsStore.labelsForTriples(triple)).thenReturn(List.of());

        LabelsQueryService service = new LabelsQueryService(labelsStore, datasetGraph, "dataset");
        List<TripleLabels> results = service.queryDSGAndLabelStore(triple);

        assertEquals(1, results.size());
        verify(iter).close();
    }
}
