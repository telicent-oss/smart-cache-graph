package io.telicent.core;

import io.telicent.jena.abac.AttributeValueSet;
import io.telicent.jena.abac.core.AttributesStoreLocal;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.Label;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.servlets.ActionService;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static io.telicent.jena.abac.labels.Labels.createLabelsStoreMem;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
@Fork(1)
@State(Scope.Thread)
public class CQRSUpdateBenchmark {

    private static final Logger LOG = LoggerFactory.getLogger(CQRSUpdateBenchmark.class);

    private static final String SPARQL_UPDATE =
            "PREFIX ex: <http://example.org/> INSERT DATA { ex:s ex:p \"v\" }";

    private DatasetGraphABAC datasetGraph;
    private DataService dataService;
    private HttpAction httpAction;
    private ActionService updateService;
    private MockProducer<String, byte[]> producer;

    private static final HttpServletRequest MOCK_REQUEST = mock(HttpServletRequest.class);
    private static final HttpServletResponse MOCK_RESPONSE = mock(HttpServletResponse.class);
    private static final ServletContextHandler SERVLET_CONTEXT_HANDLER = new ServletContextHandler();
    private static final ServletContext SERVLET_CONTEXT = SERVLET_CONTEXT_HANDLER.getServletContext();

    @Setup(Level.Trial)
    public void setup() throws IOException {
        createDatasetAndService();
        configureMocks();
        httpAction = createHttpAction();
        configureHttpActionWithDataService();
        producer = new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        updateService = CQRS.updateActionWithProducer("benchmark-topic", producer);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        closeResources();
    }

    @Benchmark
    public void benchmarkCqrsUpdate(Blackhole bh) {
        updateService.execute(httpAction);
        bh.consume(datasetGraph.size());
        bh.consume(producer.history().size());
    }

    private void createDatasetAndService() {
        AttributesStoreLocal store = new AttributesStoreLocal();
        store.put("user", AttributeValueSet.of("UPDATE"));

        datasetGraph = new DatasetGraphABAC(
                DatasetGraphFactory.createTxnMem(),
                "attribute",
                createLabelsStoreMem(),
                Label.fromText("test"),
                store
        );
        dataService = DataService.newBuilder(datasetGraph).build();
        dataService.goActive();
    }

    private void configureMocks() throws IOException {
        when(MOCK_REQUEST.getMethod()).thenReturn("POST");
        when(MOCK_REQUEST.getContentType()).thenReturn("application/sparql-update");
        when(MOCK_REQUEST.getCharacterEncoding()).thenReturn(StandardCharsets.UTF_8.name());
        when(MOCK_REQUEST.getServletContext()).thenReturn(SERVLET_CONTEXT);

        byte[] body = SPARQL_UPDATE.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(body);

        ServletInputStream servletInputStream = new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return in.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // not used in benchmark
            }

            @Override
            public int read() {
                return in.read();
            }
        };

        when(MOCK_REQUEST.getInputStream()).thenReturn(servletInputStream);

        // minimal URL info
        when(MOCK_REQUEST.getScheme()).thenReturn("http");
        when(MOCK_REQUEST.getServerName()).thenReturn("localhost");
        when(MOCK_REQUEST.getServerPort()).thenReturn(0);
        when(MOCK_REQUEST.getRequestURI()).thenReturn("/benchmark/update");
        when(MOCK_REQUEST.getQueryString()).thenReturn(null);

        // response – we don't care about body, just avoid NPEs
        ServletOutputStream outputStream = mock(ServletOutputStream.class);
        when(MOCK_RESPONSE.getOutputStream()).thenReturn(outputStream);
    }

    private HttpAction createHttpAction() {
        return new HttpAction(
                1L,
                LOG,
                org.apache.jena.fuseki.system.ActionCategory.ACTION,
                MOCK_REQUEST,
                MOCK_RESPONSE
        );
    }

    private void configureHttpActionWithDataService() {
        DataAccessPoint dap = new DataAccessPoint("CQRSBenchmark", dataService);
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
