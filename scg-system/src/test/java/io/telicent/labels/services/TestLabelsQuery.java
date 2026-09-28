package io.telicent.labels.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.telicent.core.MainSmartCacheGraph;
import io.telicent.labels.FMod_LabelsQuery;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.sources.TelicentHeaders;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SystemStubsExtension.class)
public class TestLabelsQuery {

    @SystemStub
    private static EnvironmentVariables ENV;

    private static FusekiServer SERVER;

    private static String BASE_URI;

    private static final URL BACKUP_URL = TestLabelsQuery.class.getClassLoader().getResource("config-labels-query-test.ttl");

    private static final URL DATA1_URL = TestLabelsQuery.class.getClassLoader().getResource("test-data-labelled-1.trig");

    private static final URL DATA2_URL = TestLabelsQuery.class.getClassLoader().getResource("test-data-labelled-2.trig");

    private static final URL NAMED_GRAPH_DATA_URL = TestLabelsQuery.class.getClassLoader().getResource("test-data-labelled-named-graph.trig");

    private static final String JSON_HEADER = "application/json";

    private static final String DATASET1_NAME = "securedDataset1";

    private static final String DATASET2_NAME = "securedDataset2";


    @BeforeAll
    public static void beforeAll() throws Exception {
        Configurator.reset();
        ENV.set("JWKS_URL", "disabled");
        ENV.set("ENABLE_LABELS_QUERY", true);
        SERVER = MainSmartCacheGraph.buildAndRun("--config", BACKUP_URL.getPath());
        BASE_URI = "http://localhost:" + SERVER.getHttpPort();
        uploadData(DATA1_URL, DATASET1_NAME);
        uploadData(DATA2_URL, DATASET2_NAME);
        uploadData(NAMED_GRAPH_DATA_URL, DATASET1_NAME, "europe");
    }

    @Test
    public void test_one_label() throws Exception {
        final String jsonRequestBody = """
                {
                    "triples": [
                    {
                        "subject": "http://dbpedia.org/resource/London",
                        "predicate": "http://dbpedia.org/ontology/country",
                        "object": {
                          "value" : "http://dbpedia.org/resource/United_Kingdom"
                        }
                    }
                  ]
                }""";
        final String expectedJsonResponse = """
                {
                  "results" : [ {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/United_Kingdom",
                    "labels" : [ "everyone" ]
                  } ]
                }""";
        callAndAssert(jsonRequestBody, expectedJsonResponse, DATASET1_NAME);
    }

    @Test
    public void test_two_labels() throws Exception {
        final String jsonRequestBody = """
                {
                    "triples": [
                        {
                            "subject": "http://dbpedia.org/resource/London",
                            "predicate": "http://dbpedia.org/ontology/populationTotal",
                            "object": {
                              "dataType" : "xsd:nonNegativeInteger",
                              "value" : 8799800
                            }
                        }
                    ]
                }""";
        final String expectedJsonResponse = """
                {
                  "results" : [ {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/populationTotal",
                    "object" : "\\"8799800\\"",
                    "labels" : [ "admin && census" ]
                  } ]
                }""";
        callAndAssert(jsonRequestBody, expectedJsonResponse, DATASET1_NAME);
    }

    @Test
    public void test_no_labels() throws Exception {
        final String jsonRequestBody = """
                {
                    "triples": [
                        {
                            "subject": "http://dbpedia.org/resource/Rome",
                            "predicate": "http://dbpedia.org/ontology/country",
                             "object" : {
                               "value" : "http://dbpedia.org/resource/Italy"
                             }
                        }
                    ]
                }""";
        final String expectedJsonResponse = """
                {
                  "results" : [ {
                    "subject" : "http://dbpedia.org/resource/Rome",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/Italy",
                    "labels" : [ "!" ]
                  } ]
                }""";
        callAndAssert(jsonRequestBody, expectedJsonResponse, DATASET1_NAME);
    }

    @Test
    public void test_list_of_labels() throws Exception {
        final String jsonRequestBody = """
                {
                    "triples": [
                        {
                          "subject": "http://dbpedia.org/resource/Rome",
                          "predicate": "http://dbpedia.org/ontology/country",
                          "object": {
                            "value" : "http://dbpedia.org/resource/Italy"
                          }
                        },
                        {
                          "subject": "http://dbpedia.org/resource/Paris",
                          "predicate": "http://dbpedia.org/ontology/country",
                          "object": {
                            "value" : "http://dbpedia.org/resource/France"
                          }
                        }
                    ]
                }""";
        final String expectedJsonResponse = """
                {
                  "results" : [ {
                    "subject" : "http://dbpedia.org/resource/Rome",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/Italy",
                    "labels" : [ "!" ]
                  }, {
                    "subject" : "http://dbpedia.org/resource/Paris",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/France",
                    "labels" : [ "everyone" ]
                  } ]
                }""";
        callAndAssert(jsonRequestBody, expectedJsonResponse, DATASET1_NAME);
    }

    @Test
    public void test_invalidInput() throws Exception {
        final String jsonRequestBody = """
                {
                  "wrong" : {
                    "subject": "http://dbpedia.org/resource/Rome",
                  }
                }""";
        final String expectedJsonResponse = """
                {
                  "error" : "Unable to interpret JSON request"
                }""";
        callAndAssert(jsonRequestBody, expectedJsonResponse, DATASET1_NAME);
    }

    @Test
    public void test_invalidInputJSON() throws Exception {
        final String jsonRequestBody = """
                erroneous
                }""";
        final String expectedJsonResponse = """
                {
                  "error" : "Unable to interpret JSON request"
                }""";
        callAndAssert(jsonRequestBody, expectedJsonResponse, DATASET1_NAME);
    }

    @Test
    public void test_emptyRequestBodyReturnsBadRequest() throws Exception {
        assertBadRequest("");
    }

    @Test
    public void test_tripleWithoutObjectReturnsBadRequest() throws Exception {
        assertBadRequest("""
                {"triples":[{"subject":"http://example.org/s","predicate":"http://example.org/p"}]}
                """);
    }

    @Test
    public void test_tripleWithoutObjectValueReturnsBadRequest() throws Exception {
        assertBadRequest("""
                {"triples":[{"subject":"http://example.org/s","predicate":"http://example.org/p","object":{}}]}
                """);
    }

    @Test
    public void test_nullAndIncompleteTriplesReturnBadRequest() throws Exception {
        for (String requestBody : List.of(
                "{\"triples\":[null]}",
                "{\"triples\":[{\"predicate\":\"http://example.org/p\",\"object\":{\"value\":\"x\"}}]}",
                "{\"triples\":[{\"subject\":\"http://example.org/s\",\"object\":{\"value\":\"x\"}}]}",
                "{\"triples\":[{\"subject\":\"http://example.org/s\",\"predicate\":\"http://example.org/p\",\"object\":null}]}",
                "{\"triples\":[{\"subject\":\"http://example.org/s\",\"predicate\":\"http://example.org/p\",\"object\":{\"value\":null}}]}")) {
            assertBadRequest(requestBody);
        }
    }

    private static void assertBadRequest(String requestBody) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(new URI(BASE_URI + "/$/labels/" + DATASET1_NAME))
                .headers("accept", JSON_HEADER, "Content-Type", JSON_HEADER)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, response.statusCode(), response.body());
            assertEquals("""
                    {
                      "error" : "Unable to interpret JSON request"
                    }""", response.body());
        }
    }

    @Test
    public void test_notConfigured() throws Exception {
        SERVER.stop();
        ENV.set("ENABLE_LABELS_QUERY", false);
        SERVER = MainSmartCacheGraph.buildAndRun("--config", BACKUP_URL.getPath());
        BASE_URI = "http://localhost:" + SERVER.getHttpPort();

        final String jsonRequestBody = """
                {
                  "triples":[
                    {
                      "subject": "http://dbpedia.org/resource/Rome",
                      "predicate": "http://dbpedia.org/ontology/country",
                      "object": {
                        "value": "http://dbpedia.org/resource/Italy"
                      }
                    }
                  ]
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
    public void test_alternateDataset() throws Exception {
        final String jsonRequestBody = """
                {
                    "triples": [
                    {
                        "subject": "http://dbpedia.org/resource/Birmingham",
                        "predicate": "http://dbpedia.org/ontology/country",
                        "object": {
                          "value" : "http://dbpedia.org/resource/United_Kingdom"
                        }
                    }
                  ]
                }""";
        final String expectedJsonResponse = """
                {
                  "results" : [ {
                    "subject" : "http://dbpedia.org/resource/Birmingham",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/United_Kingdom",
                    "labels" : [ "everyone" ]
                  } ]
                }""";
        callAndAssert(jsonRequestBody, expectedJsonResponse, DATASET2_NAME);
    }

    @Test
    public void givenQueryWithNonAsciiCharacters_whenMakingLabelsQuery_thenOk() throws Exception {
        final String jsonRequestBody = """
                {
                  "triples": [
                     {
                       "subject": "http://telicent.io/catalog#7efb98c6-708e-4c05-9284-866bf5d33bae_DataHandlingPolicy",
                       "predicate": "http://purl.org/dc/terms/description",
                       "object": {
                         "value": "Please be careful: this data is fragile … ! okoka"
                       }
                     }
                  ]
                }
                """;
        final String expectedJsonResponse = """
                {
                  "results" : [ {
                    "subject" : "http://telicent.io/catalog#7efb98c6-708e-4c05-9284-866bf5d33bae_DataHandlingPolicy",
                    "predicate" : "http://purl.org/dc/terms/description",
                    "object" : "\\"Please be careful: this data is fragile … ! okoka\\"",
                    "labels" : [ "!" ]
                  } ]
                }""";

        callAndAssert(jsonRequestBody, expectedJsonResponse, DATASET1_NAME);
    }

    @Test
    public void givenNamedGraph_whenMakingLabelsQuery_thenNamedGraphLabelReturned() throws Exception {
        final String jsonRequestBody = """
                {
                    "triples": [
                    {
                        "graph": "http://example.org/graph/europe",
                        "subject": "http://dbpedia.org/resource/Rome",
                        "predicate": "http://dbpedia.org/ontology/country",
                        "object": {
                          "value" : "http://dbpedia.org/resource/Italy"
                        }
                    }
                  ]
                }""";
        final String expectedJsonResponse = """
                {
                  "results" : [ {
                    "subject" : "http://dbpedia.org/resource/Rome",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/Italy",
                    "graph" : "http://example.org/graph/europe",
                    "labels" : [ "europe" ]
                  } ]
                }""";
        callAndAssert(jsonRequestBody, expectedJsonResponse, DATASET1_NAME);
    }

    @Test
    public void givenQuadsKey_whenMakingLabelsQuery_thenNamedGraphLabelReturned() throws Exception {
        final String jsonRequestBody = """
                {
                    "quads": [
                    {
                        "graph": "http://example.org/graph/europe",
                        "subject": "http://dbpedia.org/resource/Rome",
                        "predicate": "http://dbpedia.org/ontology/country",
                        "object": {
                          "value" : "http://dbpedia.org/resource/Italy"
                        }
                    }
                  ]
                }""";
        final String expectedJsonResponse = """
                {
                  "results" : [ {
                    "subject" : "http://dbpedia.org/resource/Rome",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/Italy",
                    "graph" : "http://example.org/graph/europe",
                    "labels" : [ "europe" ]
                  } ]
                }""";
        callAndAssert(jsonRequestBody, expectedJsonResponse, DATASET1_NAME);
    }

    @Test
    public void givenQuadsKeyWithoutGraph_whenMakingLabelsQuery_thenDefaultGraphLabelReturned() throws Exception {
        final String jsonRequestBody = """
                {
                    "quads": [
                    {
                        "subject": "http://dbpedia.org/resource/London",
                        "predicate": "http://dbpedia.org/ontology/country",
                        "object": {
                          "value" : "http://dbpedia.org/resource/United_Kingdom"
                        }
                    }
                  ]
                }""";
        final String expectedJsonResponse = """
                {
                  "results" : [ {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/United_Kingdom",
                    "labels" : [ "everyone" ]
                  } ]
                }""";
        callAndAssert(jsonRequestBody, expectedJsonResponse, DATASET1_NAME);
    }

    @Test
    public void givenBothQuadsAndTriplesKeys_whenMakingLabelsQuery_thenBadRequest() throws Exception {
        assertBadRequest("""
                {
                  "triples": [{"subject":"http://example.org/s","predicate":"http://example.org/p","object":{"value":"x"}}],
                  "quads": [{"subject":"http://example.org/s","predicate":"http://example.org/p","object":{"value":"y"}}]
                }
                """);
    }

    @Test
    public void givenNonArrayQuads_whenMakingLabelsQuery_thenBadRequest() throws Exception {
        assertBadRequest("""
                {"quads":{"subject":"http://example.org/s","predicate":"http://example.org/p","object":{"value":"x"}}}
                """);
    }

    @Test
    public void givenIncompleteQuads_whenMakingLabelsQuery_thenBadRequest() throws Exception {
        for (String requestBody : List.of(
                "{\"quads\":[null]}",
                "{\"quads\":[{\"graph\":\"http://example.org/g\",\"subject\":\"http://example.org/s\",\"predicate\":\"http://example.org/p\"}]}")) {
            assertBadRequest(requestBody);
        }
    }

    @Test
    public void givenNoGraph_whenMakingWildcardLabelsQuery_thenNamedGraphsNotSearched() throws Exception {
        final String jsonRequestBody = """
                {
                    "triples": [
                    {
                        "subject": "http://dbpedia.org/resource/Rome",
                        "predicate": "*",
                        "object": {
                          "value" : "*"
                        }
                    }
                  ]
                }""";
        final String expectedJsonResponse = """
                {
                  "results" : [ ]
                }""";
        callAndAssert(jsonRequestBody, expectedJsonResponse, DATASET1_NAME);
    }

    @Test
    public void givenWildcardGraph_whenMakingLabelsQuery_thenAllGraphsSearched() throws Exception {
        final String jsonRequestBody = """
                {
                    "triples": [
                    {
                        "graph": "*",
                        "subject": "*",
                        "predicate": "http://dbpedia.org/ontology/country",
                        "object": {
                          "value" : "*"
                        }
                    }
                  ]
                }""";
        final HttpRequest request = HttpRequest.newBuilder(new URI(BASE_URI + "/$/labels/" + DATASET1_NAME))
                .headers("accept", JSON_HEADER, "Content-Type", JSON_HEADER)
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequestBody)).build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            final JsonNode results = new ObjectMapper().readTree(response.body()).get("results");
            // 3 cities in the default graph plus 2 in the named graph
            assertEquals(5, results.size(), response.body());
            int inNamedGraph = 0;
            for (JsonNode result : results) {
                if (result.has("graph")) {
                    assertEquals("http://example.org/graph/europe", result.get("graph").asText());
                    assertEquals("europe", result.get("labels").get(0).asText());
                    inNamedGraph++;
                } else {
                    assertEquals("everyone", result.get("labels").get(0).asText());
                }
            }
            assertEquals(2, inNamedGraph);
        }
    }

    @Test
    public void test_name() {
        // given
        FMod_LabelsQuery fModLabelsQuery = new FMod_LabelsQuery();
        // when, then
        assertNotNull(fModLabelsQuery.name());
    }

    private static void callAndAssert(String jsonRequestBody, String expectedJsonResponse, String datasetName) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(new URI(BASE_URI + "/$/labels/" + datasetName))
                .headers("accept", JSON_HEADER, "Content-Type", JSON_HEADER)
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequestBody)).build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(expectedJsonResponse, response.body(), "Response:\n" + response.body() + "\ndoes not match expected result:\n" + expectedJsonResponse);
        }
    }

    private static void uploadData(URL dataUrl, String datasetName) throws Exception {
        uploadData(dataUrl, datasetName, "!");
    }

    private static void uploadData(URL dataUrl, String datasetName, String securityLabel) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(new URI(BASE_URI + "/" + datasetName + "/upload"))
                .headers(TelicentHeaders.SECURITY_LABEL, securityLabel, "Content-Type", "application/trig")
                .POST(HttpRequest.BodyPublishers.ofFile(Paths.get(dataUrl.toURI()))).build();
        try (final HttpClient client = HttpClient.newHttpClient()) {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
        }
    }

    @AfterAll
    public static void afterAll() throws IOException {
        SERVER.stop();
        FileUtils.deleteDirectory(new File("target/labels"));
        FileUtils.deleteDirectory(new File("target/labels1"));
        FileUtils.deleteDirectory(new File("target/labels2"));
    }

}
