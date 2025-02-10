package io.telicent.labels.services;

import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.labels.TripleLabels;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestLabelsQueryService {

    @Test
    public void testLabelQuery() {
        final LabelsStore mockLabelsStore = mock(LabelsStore.class);
        final LabelsQueryService queryService = new LabelsQueryService(mockLabelsStore);
        when(mockLabelsStore.labelsForTriples(any(Triple.class))).thenReturn(List.of("example"));
        final Triple triple = Triple.create(
                NodeFactory.createURI("http://example.org/subject"),
                NodeFactory.createURI("http://example.org/predicate"),
                NodeFactory.createURI("http://example.org/object")
        );
        final TripleLabels labels = queryService.queryLabelStore(triple);
        Assertions.assertEquals(1, labels.labels.size());
    }
}
