package io.telicent.labels.services;

import io.telicent.labels.QuadLabels;
import io.telicent.smart.cache.security.data.labels.SecurityLabelsApplicator;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPlugin;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.atlas.iterator.IteratorCloseable;
import org.apache.jena.sparql.core.Quad;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TestLabelsQueryServiceIteratorClose {

    @Test
    void queryDSGAndLabelStoreClosesIterator() {
        DatasetGraph datasetGraph = mock(DatasetGraph.class);

        Triple triple = Triple.create(
                NodeFactory.createURI("urn:s"),
                NodeFactory.createURI("urn:p"),
                NodeFactory.createURI("urn:o")
        );
        @SuppressWarnings("unchecked")
        IteratorCloseable<Quad> iter = mock(IteratorCloseable.class);
        final Quad quad = Quad.create(Quad.defaultGraphIRI, triple);
        when(iter.hasNext()).thenReturn(true, false);
        when(iter.next()).thenReturn(quad);
        when(datasetGraph.find(quad)).thenReturn(iter);

        //LabelsStore labelsStore = mock(LabelsStore.class);
        DataSecurityPlugin mockDataSecurityPlugin = mock(DataSecurityPlugin.class);
        SecurityLabelsApplicator mockSecurityLabelsApplicator = mock(SecurityLabelsApplicator.class);
        when(mockDataSecurityPlugin.prepareLabelsApplicator(any(),any())).thenReturn(mockSecurityLabelsApplicator);
        when(mockSecurityLabelsApplicator.labelForQuad(quad)).thenReturn(null);

        LabelsQueryService service = new LabelsQueryService(mockDataSecurityPlugin, datasetGraph, "dataset");
        List<QuadLabels> results = service.queryDSGAndLabelStore(triple);

        assertEquals(1, results.size());
        verify(iter).close();
    }
}
