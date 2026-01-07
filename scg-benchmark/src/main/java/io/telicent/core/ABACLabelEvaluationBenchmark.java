package io.telicent.core;

import io.telicent.jena.abac.AttributeValueSet;
import io.telicent.jena.abac.core.AttributesStoreLocal;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.fuseki.ABAC_SPARQL_QueryDataset;
import io.telicent.jena.abac.labels.Label;
import io.telicent.jena.abac.labels.Labels;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.servlets.ActionErrorException;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.system.ActionCategory;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

/**
 * Benchmark focused on ABAC evaluation cost, varying label complexity.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
@Fork(1)
@State(Scope.Benchmark)
public class ABACLabelEvaluationBenchmark {

    private static final Logger LOG = LoggerFactory.getLogger(ABACLabelEvaluationBenchmark.class);

    @Param({"1000", "10000"})
    private int tripleCount;

    @Param({"simple", "medium", "complex"})
    private String labelComplexity;

    private DatasetGraphABAC datasetGraph;
    private DataService dataService;
    private ABAC_SPARQL_QueryDataset abacQueryService;
    private HttpAction httpAction;

    private Label dataLabel;

    private static final HttpServletRequest MOCK_REQUEST = mock(HttpServletRequest.class);
    private static final HttpServletResponse MOCK_RESPONSE = mock(HttpServletResponse.class);
    private static final ServletContext SERVLET_CONTEXT = mock(ServletContext.class);

    @Setup(Level.Trial)
    public void setup() throws IOException {
        // 1. ABAC dataset with attribute store
        AttributesStoreLocal store = new AttributesStoreLocal();
        // "user" is the identity used by ABAC_SPARQL_QueryDataset below
        switch (labelComplexity) {
            case "simple" -> store.put("user", AttributeValueSet.of("nationality:GBR", "clearance:O"));
            case "medium" -> store.put("user", AttributeValueSet.of("nationality:GBR", "nationality:USA", "clearance:S"));
            case "complex" -> store.put("user", AttributeValueSet.of("nationality:GBR", "nationality:USA", "nationality:NOR", "clearance:TS"));
            default -> store.put("user", AttributeValueSet.of("nationality:GBR", "clearance:O"));
        }

        datasetGraph = new DatasetGraphABAC(
                DatasetGraphFactory.createTxnMem(),
                "attributes",
                Labels.createLabelsStoreMem(),
                Label.fromText("DEFAULT"),
                store
        );

        dataLabel = Label.fromText(labelExpressionForComplexity(labelComplexity));
        for (int i = 0; i < tripleCount; i++) {
            Triple t = Triple.create(
                    NodeFactory.createURI("urn:s:" + (i % (tripleCount / 10 + 1))),
                    NodeFactory.createURI("urn:p:" + (i % 17)),
                    NodeFactory.createLiteralString("o-" + i)
            );
            datasetGraph.getDefaultGraph().add(t);
            datasetGraph.labelsStore().add(t.getSubject(), t.getPredicate(), t.getObject(), dataLabel);
        }

        dataService = DataService.newBuilder(datasetGraph).build();
        dataService.goActive();

        abacQueryService = new ABAC_SPARQL_QueryDataset(action -> "user");

        configureMocks();
        httpAction = createHttpAction();
        configureHttpActionWithDataService();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (datasetGraph != null) {
            datasetGraph.close();
        }
        if (dataService != null) {
            dataService.shutdown();
        }
    }

    @Benchmark
    public void benchmarkAbacQuery(Blackhole bh) {
        try {
            abacQueryService.execute(httpAction);
        } catch (ActionErrorException ex) {
            bh.consume(ex.getMessage());
        }
        bh.consume(datasetGraph.size());
    }

    private void configureMocks() throws IOException {
        when(MOCK_REQUEST.getMethod()).thenReturn("GET");
        String sparql = "SELECT * WHERE { ?s ?p ?o . }";
        when(MOCK_REQUEST.getParameter("query")).thenReturn(sparql);
        when(MOCK_REQUEST.getCharacterEncoding()).thenReturn(StandardCharsets.UTF_8.name());

        when(MOCK_REQUEST.getServletContext()).thenReturn(SERVLET_CONTEXT);

        when(MOCK_REQUEST.getScheme()).thenReturn("http");
        when(MOCK_REQUEST.getServerName()).thenReturn("localhost");
        when(MOCK_REQUEST.getServerPort()).thenReturn(0);
        when(MOCK_REQUEST.getRequestURI()).thenReturn("/benchmark");
        when(MOCK_REQUEST.getQueryString()).thenReturn("query=" + sparql);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ServletOutputStream servletOutputStream = new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }
            @Override
            public void setWriteListener(WriteListener writeListener) {
                // no-op
            }
            @Override
            public void write(int b) {
                buffer.write(b);
            }
        };
        when(MOCK_RESPONSE.getOutputStream()).thenReturn(servletOutputStream);
        when(MOCK_RESPONSE.getWriter()).thenReturn(new PrintWriter(new OutputStreamWriter(buffer, StandardCharsets.UTF_8), true));
    }

    private HttpAction createHttpAction() {
        return new HttpAction(
                1L,
                LOG,
                ActionCategory.ACTION,
                MOCK_REQUEST,
                MOCK_RESPONSE
        );
    }

    private void configureHttpActionWithDataService() {
        DataAccessPoint dap = new DataAccessPoint("ABACBenchmark", dataService);
        httpAction.setRequest(dap, dataService);
    }

    private String labelExpressionForComplexity(String complexity) {
        return switch (complexity) {
            case "simple" -> "nationality:GBR & clearance:O";
            case "medium" -> "(nationality:GBR | nationality:USA) & clearance:S";
            case "complex" -> "(nationality:GBR | nationality:USA | nationality:NOR) & (clearance:S | clearance:TS)";
            default -> "nationality:GBR & clearance:O";
        };
    }
}
