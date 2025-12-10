package io.telicent.core;

import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import io.telicent.smart.caches.configuration.auth.AuthConstants;
import org.apache.jena.fuseki.main.FusekiServer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Scenario-level benchmark that:
 *   - starts Smart Cache Graph in-process via MainSmartCacheGraph
 *   - uses the config-graphql-plain.ttl test config (no ABAC, in-memory dataset)
 *   - disables JWT auth and Kafka ingestion
 *   - exercises real HTTP endpoints: /ds/sparql and /ds/graphql
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
@Fork(1)
@State(Scope.Benchmark)
public class SCGraphScenarioBenchmark {

    private static final String CONFIG_PATH =
            "scg-system/src/test/files/config-graphql-plain.ttl";

    private static final String DATASET_PATH = "/ds";

    private static final String SIMPLE_NONTRIVIAL_SPARQL_QUERY =
            "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 50";

    private static final String GRAPHQL_QUERY_JSON =
            "{\"query\":\"{ __schema { queryType { name } } }\"}";

    private FusekiServer server;
    private HttpClient httpClient;
    private String baseUrl;

    @Setup(Level.Trial)
    public void startServer() {
        Properties properties = new Properties();
        properties.put(AuthConstants.ENV_JWKS_URL, AuthConstants.AUTH_DISABLED);
        Configurator.addSource(new PropertiesSource(properties));
        System.setProperty("scg.disable.kafka", "true");
        server = MainSmartCacheGraph.buildAndRun("--conf", CONFIG_PATH);
        int port = server.getHttpPort();
        baseUrl = "http://localhost:" + port + DATASET_PATH;
        httpClient = HttpClient.newHttpClient();
    }

    @TearDown(Level.Trial)
    public void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Benchmark
    public void benchmarkSparqlSelect(Blackhole bh) throws Exception {
        String encoded = URLEncoder.encode(SIMPLE_NONTRIVIAL_SPARQL_QUERY, StandardCharsets.UTF_8);
        URI uri = URI.create(baseUrl + "/sparql?query=" + encoded);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        bh.consume(response.statusCode());
        bh.consume(response.body());
    }

    @Benchmark
    public void benchmarkGraphQL(Blackhole bh) throws Exception {
        URI uri = URI.create(baseUrl + "/graphql");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GRAPHQL_QUERY_JSON))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        bh.consume(response.statusCode());
        bh.consume(response.body());
    }
}
