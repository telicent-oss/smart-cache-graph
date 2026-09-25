package io.telicent.labels.services;

import io.telicent.jena.abac.ABAC;
import io.telicent.jena.abac.SysABAC;
import io.telicent.jena.abac.attributes.syntax.AEX;
import io.telicent.jena.abac.core.AttributesStoreLocal;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.core.VocabAuthz;
import io.telicent.jena.abac.labels.Label;
import io.telicent.jena.abac.labels.Labels;
import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.labels.QuadLabels;
import io.telicent.smart.cache.security.data.plugins.rdf.abac.RdfAbacPlugin;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.system.Txn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link LabelsQueryService} against datasets using named graphs, using the real RDF-ABAC plugin and an ABAC
 * dataset rather than mocks.
 */
public class TestLabelsQueryServiceNamedGraphs {

    private static final String DATASET_NAME = "test";
    // LabelsQueryService passes "!" as the default label to the applicator
    private static final String DEFAULT_LABEL = "!";

    private static final Node GRAPH = NodeFactory.createURI("http://example.org/graph");
    private static final Node SUBJECT = NodeFactory.createURI("http://example.org/subject");
    private static final Node PREDICATE = NodeFactory.createURI("http://example.org/predicate");
    private static final Node OBJECT_IN_DEFAULT = NodeFactory.createURI("http://example.org/inDefault");
    private static final Node OBJECT_IN_NAMED = NodeFactory.createURI("http://example.org/inNamed");
    private static final Node OBJECT_IN_BOTH = NodeFactory.createURI("http://example.org/inBoth");

    private static final Triple TRIPLE_IN_DEFAULT = Triple.create(SUBJECT, PREDICATE, OBJECT_IN_DEFAULT);
    private static final Triple TRIPLE_IN_NAMED = Triple.create(SUBJECT, PREDICATE, OBJECT_IN_NAMED);
    private static final Triple TRIPLE_IN_BOTH = Triple.create(SUBJECT, PREDICATE, OBJECT_IN_BOTH);

    private LabelsQueryService queryService;

    @BeforeEach
    public void setUp() {
        final DatasetGraph base = DatasetGraphFactory.createTxnMem();
        final LabelsStore labelsStore = Labels.createLabelsStoreMem();
        final DatasetGraphABAC dsgz = ABAC.authzDataset(base, AEX.strALLOW, labelsStore, SysABAC.denyLabel,
                                                        new AttributesStoreLocal());
        Txn.executeWrite(dsgz, () -> {
            addQuad(dsgz, labelsStore, Quad.defaultGraphIRI, TRIPLE_IN_DEFAULT, "default");
            addQuad(dsgz, labelsStore, GRAPH, TRIPLE_IN_NAMED, "named");
            addQuad(dsgz, labelsStore, Quad.defaultGraphIRI, TRIPLE_IN_BOTH, "both-default");
            addQuad(dsgz, labelsStore, GRAPH, TRIPLE_IN_BOTH, "both-named");
        });
        queryService = new LabelsQueryService(new RdfAbacPlugin(), dsgz, DATASET_NAME);
    }

    private static void addQuad(DatasetGraphABAC dsgz, LabelsStore labelsStore, Node graph, Triple triple,
                                String label) {
        final Quad quad = Quad.create(graph, triple);
        dsgz.getData().add(quad);
        labelsStore.add(quad, Label.fromText(label));
    }

    private static Quad quad(Node graph, Node s, Node p, Node o) {
        return Quad.create(graph, s, p, o);
    }

    private static QuadLabels resultFor(List<QuadLabels> results, Node graph, Node object) {
        return results.stream()
                      .filter(r -> r.graph.equals(graph) && r.triple.getObject().equals(object))
                      .findFirst()
                      .orElseThrow(() -> new AssertionError("No result for " + graph + " " + object));
    }

    @Test
    public void givenTripleInDefaultGraph_whenTripleOnlyWildcardQuery_thenFoundWithLabel() {
        final List<QuadLabels> results =
                queryService.queryDSGAndLabelStore(Triple.create(SUBJECT, PREDICATE, OBJECT_IN_DEFAULT));
        assertEquals(1, results.size());
        assertEquals("default", results.getFirst().label.toDebugString());
    }

    @Test
    public void givenNoGraph_whenWildcardQuery_thenOnlyDefaultGraphSearched() {
        final List<QuadLabels> results =
                queryService.queryDSGAndLabelStore(quad(Quad.defaultGraphIRI, SUBJECT, PREDICATE, Node.ANY));

        final List<Node> objects = results.stream().map(r -> r.triple.getObject()).toList();
        assertEquals(2, results.size());
        assertTrue(objects.contains(OBJECT_IN_DEFAULT));
        assertTrue(objects.contains(OBJECT_IN_BOTH));
        assertFalse(objects.contains(OBJECT_IN_NAMED));
        assertEquals("both-default", resultFor(results, Quad.defaultGraphIRI, OBJECT_IN_BOTH).label.toDebugString());
    }

    @Test
    public void givenNamedGraph_whenWildcardQuery_thenOnlyThatGraphSearched() {
        final List<QuadLabels> results = queryService.queryDSGAndLabelStore(quad(GRAPH, SUBJECT, PREDICATE, Node.ANY));

        assertEquals(2, results.size());
        assertEquals("named", resultFor(results, GRAPH, OBJECT_IN_NAMED).label.toDebugString());
        assertEquals("both-named", resultFor(results, GRAPH, OBJECT_IN_BOTH).label.toDebugString());
    }

    @Test
    public void givenWildcardGraph_whenQuery_thenAllGraphsSearched() {
        final List<QuadLabels> results = queryService.queryDSGAndLabelStore(quad(Node.ANY, SUBJECT, PREDICATE, Node.ANY));

        assertEquals(4, results.size());
        assertEquals("default", resultFor(results, Quad.defaultGraphIRI, OBJECT_IN_DEFAULT).label.toDebugString());
        assertEquals("named", resultFor(results, GRAPH, OBJECT_IN_NAMED).label.toDebugString());
        assertEquals("both-default", resultFor(results, Quad.defaultGraphIRI, OBJECT_IN_BOTH).label.toDebugString());
        assertEquals("both-named", resultFor(results, GRAPH, OBJECT_IN_BOTH).label.toDebugString());
    }

    @Test
    public void givenWildcardGraphAndConcreteTriple_whenQuery_thenEveryGraphContainingTripleReturned() {
        final List<QuadLabels> results = queryService.queryDSGAndLabelStore(quad(Node.ANY, SUBJECT, PREDICATE, OBJECT_IN_BOTH));

        assertEquals(2, results.size());
        assertEquals("both-default", resultFor(results, Quad.defaultGraphIRI, OBJECT_IN_BOTH).label.toDebugString());
        assertEquals("both-named", resultFor(results, GRAPH, OBJECT_IN_BOTH).label.toDebugString());
    }

    @Test
    public void givenNamedGraph_whenConcreteQuery_thenNamedGraphLabelReturned() {
        final List<QuadLabels> results = queryService.queryOnlyLabelStore(Quad.create(GRAPH, TRIPLE_IN_NAMED));
        assertEquals(1, results.size());
        assertEquals(GRAPH, results.getFirst().graph);
        assertEquals("named", results.getFirst().label.toDebugString());
    }

    @Test
    public void givenTripleInBothGraphs_whenConcreteQueryPerGraph_thenEachGraphsLabelReturned() {
        assertEquals("both-default",
                     queryService.queryOnlyLabelStore(TRIPLE_IN_BOTH).getFirst().label.toDebugString());
        assertEquals("both-named",
                     queryService.queryOnlyLabelStore(Quad.create(GRAPH, TRIPLE_IN_BOTH)).getFirst().label.toDebugString());
    }

    @Test
    public void givenNamedGraphTriple_whenConcreteQueryWithoutGraph_thenDefaultLabelReturned() {
        // Without a graph the default graph is assumed, and the triple isn't labelled there
        final List<QuadLabels> results = queryService.queryOnlyLabelStore(TRIPLE_IN_NAMED);
        assertEquals(1, results.size());
        assertEquals(DEFAULT_LABEL, results.getFirst().label.toDebugString());
    }

    @Test
    public void givenLabelsGraphInDataset_whenWildcardGraphQuery_thenLabelsGraphExcluded() {
        final DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        dsg.add(Quad.create(GRAPH, TRIPLE_IN_NAMED));
        dsg.add(Quad.create(VocabAuthz.graphForLabels, TRIPLE_IN_DEFAULT));
        final LabelsQueryService service = new LabelsQueryService(new RdfAbacPlugin(), dsg, DATASET_NAME);

        final List<QuadLabels> results = service.queryDSGAndLabelStore(quad(Node.ANY, Node.ANY, Node.ANY, Node.ANY));

        assertEquals(1, results.size());
        assertEquals(GRAPH, results.getFirst().graph);
    }
}
