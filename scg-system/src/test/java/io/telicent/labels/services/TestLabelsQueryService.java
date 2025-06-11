package io.telicent.labels.services;

import io.telicent.jena.abac.labels.Label;
import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.labels.TripleLabels;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestLabelsQueryService {

    private static final LabelsStore mockLabelsStore = mock(LabelsStore.class);
    private static final DatasetGraph emptyDsg = DatasetGraphFactory.create();
    private static final LabelsQueryService queryService = new LabelsQueryService(mockLabelsStore, emptyDsg);

    private static final Triple TRIPLE = Triple.create(
            NodeFactory.createURI("http://example.org/subject"),
            NodeFactory.createURI("http://example.org/predicate"),
            NodeFactory.createURI("http://example.org/object")
    );

    private static final Triple TRIPLE_2 = Triple.create(
            NodeFactory.createURI("http://example.org/subject2"),
            NodeFactory.createURI("http://example.org/predicate2"),
            NodeFactory.createURI("http://example.org/object2")
    );

    @BeforeAll
    public static void beforeAll() {
        when(mockLabelsStore.labelsForTriples(any(Triple.class))).thenReturn(List.of(Label.fromText("example")));
    }

    @AfterEach
    public void after() {
        emptyDsg.clear();
    }

    @Test
    public void testLabelQuery_queryOnlyLabelStore() {
        // given, when
        List<TripleLabels> labels = queryService.queryOnlyLabelStore(TRIPLE);
        // then
        Assertions.assertEquals(1, labels.size());
        Assertions.assertEquals(1, labels.getFirst().labels.size());
    }

    @Test
    public void testLabelQuery_queryDSGAndLabelStore() {
        // given
        emptyDsg.getDefaultGraph().add(TRIPLE);
        // when
        List<TripleLabels> labels = queryService.queryDSGAndLabelStore(TRIPLE);
        // then
        Assertions.assertEquals(1, labels.size());
        Assertions.assertEquals(1, labels.getFirst().labels.size());
    }

    @Test
    public void testLabelQuery_queryDSGAndLabelStore_empty() {
        // given, when
        List<TripleLabels> labels = queryService.queryDSGAndLabelStore(TRIPLE);
        // then
        Assertions.assertEquals(0, labels.size());
    }

    @Test
    public void testLabelQuery_queryDSGAndLabelStore_all_wildcards() {
        // given
        emptyDsg.getDefaultGraph().add(TRIPLE);

        final Triple queryTriple = Triple.create(
                Node.ANY,
                Node.ANY,
                Node.ANY
        );
        // when
        List<TripleLabels> labels = queryService.queryDSGAndLabelStore(queryTriple);
        // then
        Assertions.assertEquals(1, labels.size());
        Assertions.assertEquals(1, labels.getFirst().labels.size());
    }

    @Test
    public void testLabelQuery_queryDSGAndLabelStore_predicate_wildcard() {
        // given
        emptyDsg.getDefaultGraph().add(TRIPLE);
        emptyDsg.getDefaultGraph().add(TRIPLE_2);
        final Triple queryTriple = Triple.create(
                NodeFactory.createURI("http://example.org/subject"),
                Node.ANY,
                NodeFactory.createURI("http://example.org/object")
        );
        // when
        List<TripleLabels> labels = queryService.queryDSGAndLabelStore(queryTriple);
        // then
        Assertions.assertEquals(1, labels.size());
        Assertions.assertEquals(1, labels.getFirst().labels.size());
    }

    @Test
    public void testLabelQuery_queryDSGAndLabelStore_subject_wildcard() {
        // given
        emptyDsg.getDefaultGraph().add(TRIPLE);
        emptyDsg.getDefaultGraph().add(TRIPLE_2);
        Triple queryTriple = Triple.create(
                Node.ANY,
                NodeFactory.createURI("http://example.org/predicate"),
                NodeFactory.createURI("http://example.org/object")
        );
        // when
        List<TripleLabels> labels = queryService.queryDSGAndLabelStore(queryTriple);
        // then
        Assertions.assertEquals(1, labels.size());
        Assertions.assertEquals(1, labels.getFirst().labels.size());
    }

    @Test
    public void testLabelQuery_queryDSGAndLabelStore_object_wildcard() {
        // given
        emptyDsg.getDefaultGraph().add(TRIPLE);
        emptyDsg.getDefaultGraph().add(TRIPLE_2);
        Triple queryTriple = Triple.create(
                NodeFactory.createURI("http://example.org/subject"),
                NodeFactory.createURI("http://example.org/predicate"),
                Node.ANY
                );
        // when
        List<TripleLabels> labels = queryService.queryDSGAndLabelStore(queryTriple);
        // then
        Assertions.assertEquals(1, labels.size());
        Assertions.assertEquals(1, labels.getFirst().labels.size());
    }
}
