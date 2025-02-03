package io.telicent.labels.services;

import io.telicent.core.MainSmartCacheGraph;
import io.telicent.smart.cache.configuration.Configurator;
import org.apache.jena.fuseki.main.FusekiServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SystemStubsExtension.class)
public class LabelsQueryIntegrationTest {

    @SystemStub
    private static EnvironmentVariables env;

    private static FusekiServer server;

    private static String baseUri;

    @BeforeAll
    public static void beforeAll() throws Exception {
        Configurator.reset();
        final URL backupUrl = LabelsQueryIntegrationTest.class.getClassLoader().getResource("config-labels-query-test.ttl");
        assert backupUrl != null;
        env.set("JWKS_URL", "disabled");
        env.set("ENABLE_LABELS_QUERY", true);
        System.out.println("ENABLE var=" + System.getenv("ENABLE_LABELS_QUERY"));
        assertEquals(System.getenv("ENABLE_LABELS_QUERY"), "true");
        server = MainSmartCacheGraph.buildAndRun("--config", backupUrl.getPath());
        baseUri = "http://localhost:" + server.getHttpPort();
        uploadData();
    }

    @Test
    public void test_one_label() throws Exception {
        final String jsonRequestBody = """
                {
                  "subject": "http://dbpedia.org/resource/London",
                  "predicate": "http://dbpedia.org/ontology/country",
                  "object": "http://dbpedia.org/resource/United_Kingdom"
                }""";
        final String expectedJsonResponse = """
                {
                  "labels" : [ "everyone" ]
                }""";
        final HttpRequest request = HttpRequest.newBuilder(new URI(baseUri + "/$/labels/query"))
                .headers("accept", "application/json", "Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequestBody)).build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(expectedJsonResponse, response.body());
        }
    }

    @Test
    public void test_two_labels() throws Exception {
        final String jsonRequestBody = """
                {
                  "subject": "http://dbpedia.org/resource/London",
                  "predicate": "http://dbpedia.org/ontology/populationTotal",
                  "object": 8799800
                }""";
        final String expectedJsonResponse = """
                {
                  "labels" : [ "census", "admin" ]
                }""";
        final HttpRequest request = HttpRequest.newBuilder(new URI(baseUri + "/$/labels/query"))
                .headers("accept", "application/json", "Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequestBody)).build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(expectedJsonResponse, response.body());
        }
    }

    @Test
    public void test_no_labels() throws Exception {
        final String jsonRequestBody = """
                {
                  "subject": "http://dbpedia.org/resource/Rome",
                  "predicate": "http://dbpedia.org/ontology/country",
                  "object": "http://dbpedia.org/resource/Italy"
                }""";
        final String expectedJsonResponse = """
                {
                  "labels" : [ ]
                }""";
        final HttpRequest request = HttpRequest.newBuilder(new URI(baseUri + "/$/labels/query"))
                .headers("accept", "application/json", "Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequestBody)).build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(expectedJsonResponse, response.body());
        }
    }

    private static void uploadData() throws Exception {
        final URL dataUrl = LabelsQueryIntegrationTest.class.getClassLoader().getResource("test-data-labelled.trig");
        assert dataUrl != null;
        final HttpRequest request = HttpRequest.newBuilder(new URI(baseUri + "/securedDataset/upload"))
                .headers("Security-Label", "!", "Content-Type", "application/trig")
                .POST(HttpRequest.BodyPublishers.ofFile(Paths.get(dataUrl.toURI()))).build();
        try (final HttpClient client = HttpClient.newHttpClient()) {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
        }
    }

    @AfterAll
    public static void afterAll() throws IOException {
        server.stop();
        FileUtils.deleteDirectory(new File("labels"));
    }

}
