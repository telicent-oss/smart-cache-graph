package io.telicent.core;

import io.telicent.jena.abac.AttributeValueSet;
import io.telicent.jena.abac.core.AttributesStoreLocal;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.fuseki.ABAC_SPARQL_QueryDataset;
import io.telicent.jena.abac.labels.Label;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.openjdk.jmh.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static io.telicent.jena.abac.labels.Labels.createLabelsStoreMem;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class SCGBenchmark {

    private static final Logger LOG = LoggerFactory.getLogger(SCGBenchmark.class);

    @Param({"10", "100", "1000", "10000","1000000"})
    private int arraySize;

    private final Random random = new Random();
    private Triple[] randomisedTriples;
    private static DataService dataService;
    private static DatasetGraphABAC datasetGraph;
    private static ABAC_SPARQL_QueryDataset abacSparqlQueryDataset;
    private static HttpAction httpAction;

    private static final HttpServletRequest MOCK_REQUEST = mock(HttpServletRequest.class);
    private static final HttpServletResponse MOCK_RESPONSE = mock(HttpServletResponse.class);
    private static final ServletContextHandler SERVLET_CONTEXT_HANDLER = new ServletContextHandler();
    private static final ServletContext SERVLET_CONTEXT = SERVLET_CONTEXT_HANDLER.getServletContext();

    @Setup(Level.Trial)
    public void setup() {
        randomiseTriples();
        createDataService();
        abacSparqlQueryDataset = new ABAC_SPARQL_QueryDataset(httpAction -> "user");
        populateGraph(arraySize);
        configureMockRequest();
        httpAction = createHttpAction();
        configureHttpActionWithDataService();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        closeResources();
    }

    @Benchmark
    public void test_executeQuery() {
        abacSparqlQueryDataset.execute(httpAction);
    }

    private void randomiseTriples() {
        randomisedTriples = new Triple[arraySize];
        for (int i = 0; i < arraySize; i++) {
            randomisedTriples[i] = generateRandomTriple();
        }
    }

    private Triple generateRandomTriple() {
        int subjectIndex = random.nextInt(arraySize * 2);
        int predicateIndex = random.nextInt(10);
        int objectIndex = random.nextInt(arraySize * 2);

        return Triple.create(
                NodeFactory.createURI("subject-" + subjectIndex),
                NodeFactory.createURI("predicate-" + predicateIndex),
                NodeFactory.createLiteralString("object-" + objectIndex)
        );
    }

    private static void createDataService() {
        AttributesStoreLocal store = new AttributesStoreLocal();
        store.put("user", AttributeValueSet.of("userAttribute"));
        datasetGraph = new DatasetGraphABAC(DatasetGraphFactory.createTxnMem(), "userAttribute", createLabelsStoreMem(), Label.fromText("test"), store);
        dataService = DataService.newBuilder(datasetGraph).build();
        dataService.goActive();
    }

    private void populateGraph(int amount) {
        for (int i = 0; i < amount; i++) {
            datasetGraph.getDefaultGraph().add(randomisedTriples[i]);
        }
    }

    private void configureMockRequest() {
        when(MOCK_REQUEST.getMethod()).thenReturn("GET");
        String sparql = "SELECT * WHERE { ?s ?p ?o . }";
        when(MOCK_REQUEST.getParameter("query")).thenReturn(sparql);
        when(MOCK_REQUEST.getServletContext()).thenReturn(SERVLET_CONTEXT);
        when(MOCK_REQUEST.getHeaders("Accept")).thenReturn(Collections.enumeration(List.of("application/json")));
        try {
            ServletOutputStream outputStream = mock(ServletOutputStream.class);
            when(MOCK_RESPONSE.getOutputStream()).thenReturn(outputStream);
        } catch (Exception e) {
            LOG.error("Error setting up mock response", e);
        }
    }

    private HttpAction createHttpAction() {
        return new HttpAction(1L, LOG, org.apache.jena.fuseki.system.ActionCategory.ACTION, MOCK_REQUEST, MOCK_RESPONSE);
    }

    private void configureHttpActionWithDataService() {
        DataAccessPoint dap = new DataAccessPoint("TestCase", dataService);
        httpAction.setRequest(dap, dataService);
    }

    private void closeResources() {
        if (datasetGraph != null) {
            datasetGraph.close();
        }
        if (dataService != null) {
            dataService.shutdown();
        }
    }
}