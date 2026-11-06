package io.telicent.core;

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
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.telicent.LibTestsSCG.disableInitialCompaction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestQueryConcurrencyLimit {

    private static final String BLOCK_FN = "urn:test:block";
    private static final CountDownLatch[] LATCHES = new CountDownLatch[2]; // [0]=started, [1]=release

    private final HttpClient client = HttpClient.newHttpClient();
    private FusekiServer server;

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
    }

    private void start(String max) {
        final var properties = new Properties();
        properties.put(AuthConstants.ENV_JWKS_URL, AuthConstants.AUTH_DISABLED);
        if (max != null) {
            properties.put(FMod_QueryConcurrencyLimit.ENV_MAX_CONCURRENT_QUERIES, max);
        }
        Configurator.setSingleSource(new PropertiesSource(properties));
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

    private HttpResponse<String> query(String sparql) throws Exception {
        return get("/ds/query?query=" + URLEncoder.encode(sparql, StandardCharsets.UTF_8));
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

        LATCHES[1].countDown();
        assertEquals(200, blocked.get(10, TimeUnit.SECONDS).statusCode());
        assertEquals(200, query(SIMPLE).statusCode(), "permit must be released once the query finishes");
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
