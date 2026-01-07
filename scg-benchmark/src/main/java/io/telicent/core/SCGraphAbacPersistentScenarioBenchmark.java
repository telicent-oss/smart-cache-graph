package io.telicent.core;

import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import io.telicent.smart.caches.configuration.auth.AuthConstants;
import org.apache.jena.fuseki.main.FusekiServer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
@Fork(1)
@State(Scope.Benchmark)
public class SCGraphAbacPersistentScenarioBenchmark {

    private static final String CONFIG_PATH =
            "scg-system/src/test/files/config-persistent.ttl";

    private static final String DATASET_PATH = "/knowledge";

    private static final String SIMPLE_SPARQL_QUERY =
            "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 100";

    private static final String SAMPLE_DATA_PATH =
            "scg-system/src/test/files/sample-data-labelled.trig";

    private FusekiServer server;
    private HttpClient httpClient;
    private String baseUrl;

    @Setup(Level.Trial)
    public void startServer() throws Exception {
        Properties properties = new Properties();
        properties.put(AuthConstants.ENV_JWKS_URL, AuthConstants.AUTH_DISABLED);
        properties.put(FMod_InitialCompaction.DISABLE_INITIAL_COMPACTION, "true");
        Configurator.addSource(new PropertiesSource(properties));
        System.setProperty("scg.disable.kafka", "true");
        System.setProperty(FMod_InitialCompaction.DISABLE_INITIAL_COMPACTION, "true");
        server = MainSmartCacheGraph.buildAndRun("--conf", CONFIG_PATH);
        int port = server.getHttpPort();
        baseUrl = "http://localhost:" + port + DATASET_PATH;
        httpClient = HttpClient.newHttpClient();
        uploadSampleData();
    }

    @TearDown(Level.Trial)
    public void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Benchmark
    public void benchmarkAbacSparqlSelect(Blackhole bh) throws Exception {
        String encoded = URLEncoder.encode(SIMPLE_SPARQL_QUERY, StandardCharsets.UTF_8);
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

    private void uploadSampleData() throws Exception {
        Path dataPath = findRepoFile(SAMPLE_DATA_PATH);
        String data = Files.readString(dataPath, StandardCharsets.UTF_8);
        URI uri = URI.create(baseUrl + "/upload");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/trig")
                .POST(HttpRequest.BodyPublishers.ofString(data))
                .build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Upload failed: " + response.statusCode());
        }
    }

    private Path findRepoFile(String relativePath) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Unable to locate benchmark data file: " + relativePath);
    }
}
