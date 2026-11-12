package io.telicent.core;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.telicent.otel.JenaMetrics;
import io.telicent.smart.caches.configuration.auth.AuthConstants;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionBase0;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.junit.jupiter.api.AfterEach;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.server.EndpointSet;
import org.apache.jena.fuseki.server.Operation;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.telicent.LibTestsSCG.disableInitialCompaction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestQueryConcurrencyLimit {

    private static final String BLOCK_FN = "urn:test:block";
    private static final CountDownLatch[] LATCHES = new CountDownLatch[2]; // [0]=started, [1]=release

    private final HttpClient client = HttpClient.newHttpClient();
    private FusekiServer server;
    private InMemoryMetricReader metrics;

    @AfterEach
    void stop() {
        if (LATCHES[1] != null) {
            LATCHES[1].countDown();
        }
        if (server != null) {
            server.stop();
        }
        FunctionRegistry.get().remove(BLOCK_FN);
        Configurator.reset();
        JenaMetrics.reset();
    }

    private void start(String max) {
        start(max, null);
    }

    private void start(String max, String waitMs) {
        final var properties = new Properties();
        properties.put(AuthConstants.ENV_JWKS_URL, AuthConstants.AUTH_DISABLED);
        if (max != null) {
            properties.put(FMod_QueryConcurrencyLimit.ENV_MAX_CONCURRENT_QUERIES, max);
        }
        if (waitMs != null) {
            properties.put(FMod_QueryConcurrencyLimit.ENV_WAIT_MS, waitMs);
        }
        Configurator.setSingleSource(new PropertiesSource(properties));
        metrics = InMemoryMetricReader.create();
        JenaMetrics.set(OpenTelemetrySdk.builder()
                                        .setMeterProvider(SdkMeterProvider.builder()
                                                                          .registerMetricReader(metrics)
                                                                          .build())
                                        .build());
        disableInitialCompaction();
        LATCHES[0] = new CountDownLatch(1);
        LATCHES[1] = new CountDownLatch(1);
        FunctionRegistry.get().put(BLOCK_FN, uri -> new FunctionBase0() {
            @Override
            public NodeValue exec() {
                LATCHES[0].countDown();
                try {
                    LATCHES[1].await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return NodeValue.TRUE;
            }
        });
        final DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        dsg.add(new Quad(Quad.defaultGraphNodeGenerated, NodeFactory.createURI("urn:s"),
                         NodeFactory.createURI("urn:p"), NodeFactory.createURI("urn:o")));
        server = SmartCacheGraph.serverBuilder().port(0).add("/ds", dsg).build().start();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + path)).build(),
                           HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String path, String contentType, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + path))
                                      .header("Content-Type", contentType)
                                      .POST(HttpRequest.BodyPublishers.ofString(body))
                                      .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> query(String sparql) throws Exception {
        return get("/ds/query?query=" + URLEncoder.encode(sparql, StandardCharsets.UTF_8));
    }

    private long rejectedMetric() {
        return metrics.collectAllMetrics()
                      .stream()
                      .filter(m -> FMod_QueryConcurrencyLimit.METRIC_QUERIES_REJECTED.equals(m.getName()))
                      .mapToLong(TestQueryConcurrencyLimit::sum)
                      .sum();
    }

    private static long sum(MetricData data) {
        return data.getLongSumData().getPoints().stream().mapToLong(LongPointData::getValue).sum();
    }

    private static final String SIMPLE = "SELECT * WHERE { ?s ?p ?o }";
    private static final String BLOCKING = "SELECT * WHERE { ?s ?p ?o FILTER(<" + BLOCK_FN + ">()) }";

    @Test
    public void givenLimitReached_whenQuerying_thenExtraQueryRejectedButOtherEndpointsServed() throws Exception {
        start("1");
        final CompletableFuture<HttpResponse<String>> blocked = CompletableFuture.supplyAsync(() -> {
            try {
                return query(BLOCKING);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertTrue(LATCHES[0].await(10, TimeUnit.SECONDS), "blocking query should have started");

        final HttpResponse<String> rejected = query(SIMPLE);
        assertEquals(503, rejected.statusCode());
        assertEquals("1", rejected.headers().firstValue("Retry-After").orElse(null));
        assertEquals(200, get("/$/ping").statusCode(), "non-query endpoints must not be limited");
        assertNotEquals(503, post("/ds/update", "application/x-www-form-urlencoded",
                                  "update=" + URLEncoder.encode("INSERT DATA { <urn:a> <urn:b> <urn:c> }",
                                                                StandardCharsets.UTF_8)).statusCode(),
                        "updates must not be limited");
        assertEquals(503, post("/ds/query", "application/sparql-query", SIMPLE).statusCode(),
                     "POSTed queries are limited too");
        assertEquals(503, post("/ds", "application/sparql-query", SIMPLE).statusCode(),
                     "queries POSTed to the dataset are limited too");
        assertEquals(503, get("/ds?query=" + URLEncoder.encode(SIMPLE, StandardCharsets.UTF_8)).statusCode(),
                     "queries sent to the dataset are limited too");

        assertEquals(4, rejectedMetric(), "every rejection should be counted");

        LATCHES[1].countDown();
        assertEquals(200, blocked.get(10, TimeUnit.SECONDS).statusCode());
        assertEquals(200, query(SIMPLE).statusCode(), "permit must be released once the query finishes");
    }

    @Test
    public void givenWaitConfigured_whenPermitFreedInTime_thenQueryProceeds() throws Exception {
        start("1", "10000");
        final CompletableFuture<HttpResponse<String>> blocked = CompletableFuture.supplyAsync(() -> {
            try {
                return query(BLOCKING);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertTrue(LATCHES[0].await(10, TimeUnit.SECONDS));
        CompletableFuture<HttpResponse<String>> waiting = CompletableFuture.supplyAsync(() -> {
            try {
                return query(SIMPLE);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        Thread.sleep(300);
        assertFalse(waiting.isDone(), "second query should be waiting for a permit");
        LATCHES[1].countDown();
        assertEquals(200, blocked.get(10, TimeUnit.SECONDS).statusCode());
        assertEquals(200, waiting.get(10, TimeUnit.SECONDS).statusCode());
        assertEquals(0, rejectedMetric());
    }

    @Test
    public void givenWaitConfigured_whenPermitNotFreedInTime_thenQueryRejected() throws Exception {
        start("1", "200");
        final  CompletableFuture<HttpResponse<String>> blocked = CompletableFuture.supplyAsync(() -> {
            try {
                return query(BLOCKING);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertTrue(LATCHES[0].await(10, TimeUnit.SECONDS));
        assertEquals(503, query(SIMPLE).statusCode());
        assertEquals(1, rejectedMetric());
        LATCHES[1].countDown();
        blocked.get(10, TimeUnit.SECONDS);
    }

    private static HttpServletRequest request(String method, String uri, String contentType, String queryParam) {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getContentType()).thenReturn(contentType);
        when(request.getParameter("query")).thenReturn(queryParam);
        return request;
    }

    private static DataService service(String endpoint, Set<Operation> operations) {
        final DataService service = mock(DataService.class);
        final EndpointSet endpoints = mock(EndpointSet.class);
        when(endpoints.operations()).thenReturn(operations);
        when(service.getEndpointSet(endpoint)).thenReturn(endpoints);
        return service;
    }

    @Test
    public void isQueryRequest_methods() {
        final DataService service = service("query", Set.of(Operation.Query));
        assertTrue(FMod_QueryConcurrencyLimit.isQueryRequest(request("GET", "/ds/query", null, null), "/ds", service));
        assertTrue(FMod_QueryConcurrencyLimit.isQueryRequest(request("POST", "/ds/query", null, null), "/ds", service));
        assertFalse(FMod_QueryConcurrencyLimit.isQueryRequest(request("PUT", "/ds/query", null, null), "/ds", service));
        assertFalse(FMod_QueryConcurrencyLimit.isQueryRequest(request("DELETE", "/ds/query", null, null), "/ds",
                                                              service));
    }

    @Test
    public void isQueryRequest_endpoints() {
        final DataService service = service("update", Set.of(Operation.Update));
        assertFalse(FMod_QueryConcurrencyLimit.isQueryRequest(request("POST", "/ds/update", null, null), "/ds", service),
                    "update endpoint is not a query");
        assertFalse(FMod_QueryConcurrencyLimit.isQueryRequest(request("GET", "/ds/unknown", null, null), "/ds", service),
                    "unknown endpoint is not a query");
        assertFalse(FMod_QueryConcurrencyLimit.isQueryRequest(request("GET", "/ds/query", null, null), "/ds", null),
                    "no data service means the endpoint can't be classified");
        DataService both = service("sparql", Set.of(Operation.Query, Operation.Update));
        assertTrue(FMod_QueryConcurrencyLimit.isQueryRequest(request("POST", "/ds/sparql/", null, null), "/ds", both),
                   "endpoint offering several operations including Query counts, and a trailing path is ignored");
    }

    @Test
    public void isQueryRequest_datasetRoot() {
        assertTrue(FMod_QueryConcurrencyLimit.isQueryRequest(request("GET", "/ds", null, "SELECT * {}"), "/ds", null));
        assertTrue(FMod_QueryConcurrencyLimit.isQueryRequest(
                request("POST", "/ds", "application/sparql-query; charset=UTF-8", null), "/ds", null));
        assertTrue(FMod_QueryConcurrencyLimit.isQueryRequest(request("POST", "/ds/", "APPLICATION/SPARQL-QUERY", null),
                                                             "/ds", null));
        assertTrue(FMod_QueryConcurrencyLimit.isQueryRequest(
                request("POST", "/ds", "application/x-www-form-urlencoded", "SELECT * {}"), "/ds", null));
        assertFalse(FMod_QueryConcurrencyLimit.isQueryRequest(
                request("POST", "/ds", "application/x-www-form-urlencoded", null), "/ds", null));
        assertFalse(FMod_QueryConcurrencyLimit.isQueryRequest(request("POST", "/ds", null, null), "/ds", null));
        assertFalse(FMod_QueryConcurrencyLimit.isQueryRequest(request("GET", "/ds", null, null), "/ds", null));
        assertFalse(FMod_QueryConcurrencyLimit.isQueryRequest(request("GET", "/ds", "application/sparql-query", null),
                                                              "/ds", null),
                    "a sparql-query content type only counts on a POST");
    }

    @Test
    public void givenNoLimitConfigured_whenQuerying_thenNotLimited() throws Exception {
        start(null);
        final CompletableFuture<HttpResponse<String>> blocked = CompletableFuture.supplyAsync(() -> {
            try {
                return query(BLOCKING);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertTrue(LATCHES[0].await(10, TimeUnit.SECONDS));
        assertEquals(200, query(SIMPLE).statusCode());
        LATCHES[1].countDown();
        blocked.get(10, TimeUnit.SECONDS);
    }

}
