package io.telicent.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.telicent.LibTestsSCG;
import io.telicent.jena.abac.fuseki.SysFusekiABAC;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.sources.TelicentHeaders;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.telicent.LibTestsSCG.tokenHeader;
import static io.telicent.LibTestsSCG.tokenHeaderValue;
import static io.telicent.core.SmartCacheGraph.construct;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the access controlled labels query endpoint, {@code POST /<dataset>/access/labels}.
 * <p>
 * User1 has the {@code everyone} attribute so only sees the country triples, User2 has the {@code admin}, {@code census}
 * and {@code europe} attributes so sees the population triples and the named graph triples, but not the country
 * triples.
 * </p>
 */
public class TestAccessLabels {

    private static final URL CONFIG_URL =
            TestAccessLabels.class.getClassLoader().getResource("config-labels-query-test.ttl");
    private static final URL DATA_URL =
            TestAccessLabels.class.getClassLoader().getResource("test-data-labelled-1.trig");
    private static final URL NAMED_GRAPH_DATA_URL =
            TestAccessLabels.class.getClassLoader().getResource("test-data-labelled-named-graph.trig");

    private static final String DATASET_NAME = "securedDataset1";
    private static final String ENDPOINT = "/access/labels";
    private static final String USER1 = "User1";
    private static final String USER2 = "User2";
    private static final String EUROPE_GRAPH = "http://example.org/graph/europe";
    private static final String EMPTY_RESPONSE = """
            {
              "results" : [ ]
            }""";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static FusekiServer SERVER;
    private static HttpClient HTTP_CLIENT;

    @BeforeAll
    public static void beforeAll() throws Exception {
        FusekiLogging.setLogging();
        SysFusekiABAC.init();
        LibTestsSCG.setupAuthentication();
        LibTestsSCG.disableInitialCompaction();
        HTTP_CLIENT = HttpClient.newHttpClient();
        SERVER = construct("--config", CONFIG_URL.getPath()).start();
        uploadData(DATA_URL, "!");
        uploadData(NAMED_GRAPH_DATA_URL, "europe");
    }

    @AfterAll
    public static void afterAll() throws Exception {
        if (SERVER != null) {
            SERVER.stop();
        }
        HTTP_CLIENT.close();
        LibTestsSCG.teardownAuthentication();
        LibTestsSCG.closeAndClearLabelsStores();
        Configurator.reset();
        FileUtils.deleteDirectory(new File("target/labels1"));
        FileUtils.deleteDirectory(new File("target/labels2"));
    }

    private static void uploadData(URL dataUrl, String securityLabel) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(SERVER.serverURL() + DATASET_NAME + "/upload"))
                .header(tokenHeader(), tokenHeaderValue(LibTestsSCG.tokenForUser("admin", DATASET_NAME)))
                .header(TelicentHeaders.SECURITY_LABEL, securityLabel)
                .header("Content-Type", "application/trig")
                .POST(HttpRequest.BodyPublishers.ofFile(Paths.get(dataUrl.toURI())))
                .build();
        final HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
    }

    private static HttpResponse<String> call(String user, String requestBody) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(SERVER.serverURL() + DATASET_NAME + ENDPOINT))
                .header("Content-Type", "application/json")
                .header(tokenHeader(), tokenHeaderValue(LibTestsSCG.tokenForUser(user, DATASET_NAME)))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String callOk(String user, String requestBody) throws Exception {
        final HttpResponse<String> response = call(user, requestBody);
        assertEquals(200, response.statusCode(), response.body());
        return response.body();
    }

    /**
     * Gets the results as a set of {@code graph|subject|predicate|object|labels} strings for order independent
     * comparisons
     */
    private static Set<String> results(String responseBody) throws Exception {
        final Set<String> results = new HashSet<>();
        for (JsonNode result : MAPPER.readTree(responseBody).get("results")) {
            results.add(String.join("|", result.has("graph") ? result.get("graph").asText() : "",
                                    result.get("subject").asText(), result.get("predicate").asText(),
                                    result.get("object").asText(), result.get("labels").toString()));
        }
        return results;
    }

    private static String triplesRequest(String graph, String subject, String predicate, String object) {
        final String graphField = graph == null ? "" : "\"graph\": \"" + graph + "\",";
        return """
                {
                  "triples": [ {
                    %s
                    "subject": "%s",
                    "predicate": "%s",
                    "object": { "value": "%s" }
                  } ]
                }""".formatted(graphField, subject, predicate, object);
    }

    private static final String DBR = "http://dbpedia.org/resource/";
    private static final String COUNTRY = "http://dbpedia.org/ontology/country";
    private static final String POPULATION = "http://dbpedia.org/ontology/populationTotal";

    private static String country(String graph, String city, String country, String label) {
        return String.join("|", graph, DBR + city, COUNTRY, DBR + country, "[\"" + label + "\"]");
    }

    private static String population(String city, long population) {
        return String.join("|", "", DBR + city, POPULATION, "\"" + population + "\"^^xsd:integer",
                           "[\"admin && census\"]");
    }

    @Test
    public void givenWildcardQuery_whenUser1_thenOnlyEveryoneTriplesReturned() throws Exception {
        final String body = callOk(USER1, triplesRequest(null, "*", "*", "*"));

        assertEquals(Set.of(country("", "London", "United_Kingdom", "everyone"),
                            country("", "Paris", "France", "everyone"),
                            country("", "Berlin", "Germany", "everyone")), results(body));
    }

    @Test
    public void givenWildcardQuery_whenUser2_thenOnlyAdminAndCensusTriplesReturned() throws Exception {
        final String body = callOk(USER2, triplesRequest(null, "*", "*", "*"));

        assertEquals(Set.of(population("London", 8799800), population("Paris", 2165423),
                            population("Berlin", 3677472)), results(body));
    }

    @Test
    public void givenConcreteQuery_whenTripleVisible_thenTripleReturnedWithLabel() throws Exception {
        final String body = callOk(USER1, triplesRequest(null, DBR + "London", COUNTRY, DBR + "United_Kingdom"));

        assertEquals("""
                             {
                               "results" : [ {
                                 "subject" : "http://dbpedia.org/resource/London",
                                 "predicate" : "http://dbpedia.org/ontology/country",
                                 "object" : "http://dbpedia.org/resource/United_Kingdom",
                                 "labels" : [ "everyone" ]
                               } ]
                             }""", body);
    }

    @Test
    public void givenConcreteQuery_whenTripleHidden_thenResponseIdenticalToNonExistentTriple() throws Exception {
        // User2 lacks the everyone attribute needed to see this triple
        final String hidden = callOk(USER2, triplesRequest(null, DBR + "London", COUNTRY, DBR + "United_Kingdom"));
        final String nonExistent = callOk(USER2, triplesRequest(null, DBR + "London", COUNTRY, DBR + "France"));

        assertEquals(EMPTY_RESPONSE, hidden);
        assertEquals(nonExistent, hidden);
    }

    @Test
    public void givenConcreteNamedGraphQuery_whenUserHasGraphAttributes_thenReturnedWithGraphAndLabel()
            throws Exception {
        final String request = triplesRequest(EUROPE_GRAPH, DBR + "Rome", COUNTRY, DBR + "Italy");

        assertEquals(Set.of(country(EUROPE_GRAPH, "Rome", "Italy", "europe")), results(callOk(USER2, request)));
    }

    @Test
    public void givenConcreteNamedGraphQuery_whenUserLacksGraphAttributes_thenNothingReturned() throws Exception {
        final String hidden = callOk(USER1, triplesRequest(EUROPE_GRAPH, DBR + "Rome", COUNTRY, DBR + "Italy"));
        final String nonExistent = callOk(USER1, triplesRequest(EUROPE_GRAPH, DBR + "Rome", COUNTRY, DBR + "Spain"));

        assertEquals(EMPTY_RESPONSE, hidden);
        assertEquals(nonExistent, hidden);
    }

    @Test
    public void givenWildcardGraphQuery_whenUser1_thenOnlyVisibleTriplesFromAllGraphsReturned() throws Exception {
        final String body = callOk(USER1, triplesRequest("*", "*", "*", "*"));

        assertEquals(Set.of(country("", "London", "United_Kingdom", "everyone"),
                            country("", "Paris", "France", "everyone"),
                            country("", "Berlin", "Germany", "everyone")), results(body));
    }

    @Test
    public void givenWildcardGraphQuery_whenUser2_thenOnlyVisibleTriplesFromAllGraphsReturned() throws Exception {
        final String body = callOk(USER2, triplesRequest("*", "*", "*", "*"));

        assertEquals(Set.of(population("London", 8799800), population("Paris", 2165423),
                            population("Berlin", 3677472),
                            country(EUROPE_GRAPH, "Rome", "Italy", "europe"),
                            country(EUROPE_GRAPH, "Madrid", "Spain", "europe")), results(body));
    }

    @Test
    public void givenQuadsKey_whenQuerying_thenSameResultsAsTriplesKey() throws Exception {
        final String triples = callOk(USER1, triplesRequest(null, DBR + "Paris", "*", "*"));
        final String quads = callOk(USER1, triplesRequest(null, DBR + "Paris", "*", "*")
                .replace("\"triples\"", "\"quads\""));

        assertEquals(Set.of(country("", "Paris", "France", "everyone")), results(quads));
        assertEquals(triples, quads);
    }

    @Test
    public void givenInvalidRequests_whenQuerying_thenBadRequest() throws Exception {
        for (String requestBody : List.of(
                "not json",
                "{}",
                "{\"quads\":[null]}",
                "{\"quads\":{\"subject\":\"http://example.org/s\",\"predicate\":\"http://example.org/p\",\"object\":{\"value\":\"x\"}}}",
                "{\"quads\":[{\"subject\":\"http://example.org/s\",\"predicate\":\"http://example.org/p\"}]}",
                """
                {
                  "triples": [{"subject":"http://example.org/s","predicate":"http://example.org/p","object":{"value":"x"}}],
                  "quads": [{"subject":"http://example.org/s","predicate":"http://example.org/p","object":{"value":"y"}}]
                }""")) {
            final HttpResponse<String> response = call(USER1, requestBody);
            assertEquals(400, response.statusCode(), "Expected bad request for " + requestBody);
        }
    }

    @Test
    public void givenAnyQuery_whenQuerying_thenLabelsGraphNeverReturned() throws Exception {
        for (String user : List.of(USER1, USER2)) {
            for (JsonNode result : MAPPER.readTree(callOk(user, triplesRequest("*", "*", "*", "*"))).get("results")) {
                assertTrue(!result.has("graph") || EUROPE_GRAPH.equals(result.get("graph").asText()),
                           "Unexpected graph in " + result);
            }
        }
    }

    private static String populationRequest(String value, String dataTypeField) {
        return """
                {
                  "triples": [ {
                    "subject": "http://dbpedia.org/resource/London",
                    "predicate": "http://dbpedia.org/ontology/populationTotal",
                    "object": { "value": "%s"%s }
                  } ]
                }""".formatted(value, dataTypeField);
    }

    @Test
    public void givenConcreteTypedLiteralQuery_whenTripleVisible_thenTripleReturnedWithLabel() throws Exception {
        for (String dataType : List.of("xsd:integer", "http://www.w3.org/2001/XMLSchema#integer")) {
            final String body = callOk(USER2, populationRequest("8799800", ", \"dataType\": \"" + dataType + "\""));

            assertEquals(Set.of(population("London", 8799800)), results(body), dataType);
        }
    }

    @Test
    public void givenConcreteTypedLiteralQuery_whenTripleHidden_thenResponseIdenticalToNonExistentTriple()
            throws Exception {
        final String hidden = callOk(USER1, populationRequest("8799800", ", \"dataType\": \"xsd:integer\""));
        final String nonExistent = callOk(USER1, populationRequest("1", ", \"dataType\": \"xsd:integer\""));

        assertEquals(EMPTY_RESPONSE, hidden);
        assertEquals(nonExistent, hidden);
    }

    @Test
    public void givenConcreteLiteralQueryWithoutDataType_whenTripleStoredAsTypedLiteral_thenNotMatched()
            throws Exception {
        // Without a dataType the value is a plain string literal, which is a different term to the stored integer
        assertEquals(EMPTY_RESPONSE, callOk(USER2, populationRequest("8799800", "")));
    }

    @Test
    public void givenInvalidObjectTypes_whenQuerying_thenBadRequest() throws Exception {
        for (String dataTypeField : List.of(", \"dataType\": \"ex:unknown\"",
                                            ", \"dataType\": \"xsd:string\", \"language\": \"en\"")) {
            final HttpResponse<String> response = call(USER2, populationRequest("8799800", dataTypeField));
            assertEquals(400, response.statusCode(), "Expected bad request for " + dataTypeField);
        }
    }
}
