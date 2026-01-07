package io.telicent.core;

import io.telicent.access.services.AccessQueryService;
import io.telicent.jena.abac.AttributeValueSet;
import io.telicent.jena.abac.core.AttributesStoreLocal;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.Label;
import io.telicent.jena.abac.labels.Labels;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.system.ActionCategory;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
@Fork(1)
@State(Scope.Benchmark)
public class AccessQueryServiceBenchmark {

    private static final Logger LOG = LoggerFactory.getLogger(AccessQueryServiceBenchmark.class);

    @Param({"1000", "10000"})
    private int tripleCount;

    private DatasetGraphABAC datasetGraph;
    private AccessQueryService accessQueryService;
    private HttpAction httpAction;
    private List<Triple> allTriples;
    private DataService dataService;

    private static final HttpServletRequest MOCK_REQUEST = mock(HttpServletRequest.class);
    private static final HttpServletResponse MOCK_RESPONSE = mock(HttpServletResponse.class);
    private static final ServletContext MOCK_CONTEXT = mock(ServletContext.class);

    @Setup(Level.Trial)
    public void setup() {
        AttributesStoreLocal store = new AttributesStoreLocal();
        store.put("user", AttributeValueSet.of("A"));
        Label dataLabel = Label.fromText("A");
        datasetGraph = new DatasetGraphABAC(
                DatasetGraphFactory.createTxnMem(),
                null,
                Labels.createLabelsStoreMem(),
                dataLabel,
                store
        );
        for (int i = 0; i < tripleCount; i++) {
            Triple triple = Triple.create(
                    NodeFactory.createURI("urn:s:" + i),
                    NodeFactory.createURI("urn:p:" + (i % 17)),
                    NodeFactory.createLiteralString("o-" + i)
            );
            datasetGraph.getDefaultGraph().add(triple);
            datasetGraph.labelsStore().add(triple.getSubject(), triple.getPredicate(), triple.getObject(), dataLabel);
        }
        accessQueryService = new AccessQueryService(datasetGraph);
        allTriples = datasetGraph.getDefaultGraph().find(Triple.ANY).toList();
        configureMocks();
        httpAction = createHttpAction();
        configureHttpActionWithDataService();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (dataService != null) {
            dataService.shutdown();
        }
        if (datasetGraph != null) {
            datasetGraph.close();
        }
    }

    @Benchmark
    public void benchmarkGetTriples(Blackhole bh) {
        List<Triple> triples = accessQueryService.getTriples(httpAction, Triple.ANY);
        bh.consume(triples.size());
    }

    @Benchmark
    public void benchmarkVisibleTriplesCount(Blackhole bh) {
        int visibleCount = accessQueryService.getVisibleTriplesCount(httpAction, allTriples);
        bh.consume(visibleCount);
    }

    private void configureMocks() {
        when(MOCK_REQUEST.getMethod()).thenReturn("GET");
        when(MOCK_REQUEST.getServletContext()).thenReturn(MOCK_CONTEXT);
        when(MOCK_REQUEST.getRemoteUser()).thenReturn("user");
        when(MOCK_REQUEST.getHeader("Authorization")).thenReturn("Bearer user:user");
    }

    private HttpAction createHttpAction() {
        return new HttpAction(1L, LOG, ActionCategory.ACTION, MOCK_REQUEST, MOCK_RESPONSE);
    }

    private void configureHttpActionWithDataService() {
        dataService = DataService.newBuilder(datasetGraph).build();
        dataService.goActive();
        DataAccessPoint dap = new DataAccessPoint("AccessBenchmark", dataService);
        httpAction.setRequest(dap, dataService);
    }
}
