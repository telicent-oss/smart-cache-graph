package io.telicent.labels.services;

import io.telicent.core.MainSmartCacheGraph;
import io.telicent.labels.FMod_LabelsQuery;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SystemStubsExtension.class)
public class TestLabelsQuery {

    @SystemStub
    private static EnvironmentVariables ENV;

    private static FusekiServer SERVER;

    private static String BASE_URI;

    private final static URL BACKUP_URL = TestLabelsQuery.class.getClassLoader().getResource("config-labels-query-test.ttl");

    private final static URL DATA_URL = TestLabelsQuery.class.getClassLoader().getResource("test-data-labelled.trig");

    private final static String JSON_HEADER = "application/json";

    @BeforeAll
    public static void beforeAll() throws Exception {
        Configurator.reset();
        ENV.set("JWKS_URL", "disabled");
        ENV.set("ENABLE_LABELS_QUERY", true);
        SERVER = MainSmartCacheGraph.buildAndRun("--config", BACKUP_URL.getPath());
        BASE_URI = "http://localhost:" + SERVER.getHttpPort();
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
                  "results" : [ [ {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/United_Kingdom",
                    "labels" : [ "everyone" ]
                  } ] ]
                }""";
        callAndAssert(jsonRequestBody, expectedJsonResponse);
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
                  "results" : [ [ {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/populationTotal",
                    "object" : "\\"8799800\\"",
                    "labels" : [ "census", "admin" ]
                  } ] ]
                }""";
        callAndAssert(jsonRequestBody, expectedJsonResponse);
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
                  "results" : [ [ {
                    "subject" : "http://dbpedia.org/resource/Rome",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/Italy",
                    "labels" : [ ]
                  } ] ]
                }""";
        callAndAssert(jsonRequestBody, expectedJsonResponse);
    }

    @Test
    public void test_notConfigured() throws Exception {
        SERVER.stop();
        ENV.set("ENABLE_LABELS_QUERY", false);
        SERVER = MainSmartCacheGraph.buildAndRun("--config", BACKUP_URL.getPath());
        BASE_URI = "http://localhost:" + SERVER.getHttpPort();

        final String jsonRequestBody = """
                {
                  "subject": "http://dbpedia.org/resource/Rome",
                  "predicate": "http://dbpedia.org/ontology/country",
                  "object": "http://dbpedia.org/resource/Italy"
                }""";

        final HttpRequest request = HttpRequest.newBuilder(new URI(BASE_URI + "/$/labels/query"))
                .headers("accept", JSON_HEADER, "Content-Type", JSON_HEADER)
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequestBody)).build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode());
        }

        // reset back to the norm
        SERVER.stop();
        beforeAll();
    }

    @Test
    public void test_name() {
        // given
        FMod_LabelsQuery fModLabelsQuery = new FMod_LabelsQuery();
        // when, then
        assertNotNull(fModLabelsQuery.name());
    }

    private static void callAndAssert(String jsonRequestBody, String expectedJsonResponse) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(new URI(BASE_URI + "/$/labels/query"))
                .headers("accept", JSON_HEADER, "Content-Type", JSON_HEADER)
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequestBody)).build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(expectedJsonResponse, response.body());
        }
    }

    private static void uploadData() throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(new URI(BASE_URI + "/securedDataset/upload"))
                .headers("Security-Label", "!", "Content-Type", "application/trig")
                .POST(HttpRequest.BodyPublishers.ofFile(Paths.get(DATA_URL.toURI()))).build();
        try (final HttpClient client = HttpClient.newHttpClient()) {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
        }
    }

    @AfterAll
    public static void afterAll() throws IOException {
        SERVER.stop();
        FileUtils.deleteDirectory(new File("labels"));
    }

}
