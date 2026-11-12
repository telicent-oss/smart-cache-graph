package io.telicent.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.telicent.LibTestsSCG;
import io.telicent.jena.abac.fuseki.SysFusekiABAC;
import io.telicent.labels.FMod_LabelsQuery;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import io.telicent.smart.cache.sources.TelicentHeaders;
import io.telicent.smart.caches.configuration.auth.policy.TelicentPermissions;
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
import java.util.Properties;
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
 * triples. User3 has the {@code catalogue} attribute so only sees the Catalog dataset triple.
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
    private static final String USER3 = "User3";
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
        // Enables the admin labels endpoint, for comparison with the access controlled one
        final Properties properties = new Properties();
        properties.put(FMod_LabelsQuery.ENABLE_LABELS_QUERY, "true");
        Configurator.addSource(new PropertiesSource(properties));
        HTTP_CLIENT = HttpClient.newHttpClient();
        SERVER = construct("--config", CONFIG_URL.getPath()).start();
        uploadData(DATA_URL, "!");
        uploadData(NAMED_GRAPH_DATA_URL, "europe");
        uploadData(HttpRequest.BodyPublishers.ofString(CATALOG_DATA), "catalogue");
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
        uploadData(HttpRequest.BodyPublishers.ofFile(Paths.get(dataUrl.toURI())), securityLabel);
    }

    private static void uploadData(HttpRequest.BodyPublisher data, String securityLabel) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(SERVER.serverURL() + DATASET_NAME + "/upload"))
                .header(tokenHeader(), tokenHeaderValue(LibTestsSCG.tokenForUser("admin", DATASET_NAME)))
                .header(TelicentHeaders.SECURITY_LABEL, securityLabel)
                .header("Content-Type", "application/trig")
                .POST(data)
                .build();
        final HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
    }

    private static HttpResponse<String> call(String user, String requestBody) throws Exception {
        return callWithToken(DATASET_NAME + ENDPOINT, LibTestsSCG.tokenForUser(user, DATASET_NAME), requestBody);
    }

    /**
     * Calls an endpoint, without an auth header if the token is {@code null}
     */
    private static HttpResponse<String> callWithToken(String path, String token, String requestBody)
            throws Exception {
        final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(SERVER.serverURL() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
        if (token != null) {
            request.header(tokenHeader(), tokenHeaderValue(token));
        }
        return HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
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
                                            ", \"dataType\": \"xsd:\"",
                                            ", \"dataType\": \"xsd:string\", \"language\": \"en\"")) {
            final HttpResponse<String> response = call(USER2, populationRequest("8799800", dataTypeField));
            assertEquals(400, response.statusCode(), "Expected bad request for " + dataTypeField);
        }
    }

    // Catalog looks up the label of a dataset's type triple, then applies it to everything under that dataset

    private static final String CATALOG_GRAPH = "http://telicent.io/catalog";
    private static final String CATALOG_DATASET = "http://telicent.io/catalog/datasets#";
    private static final String CATALOG_DATA = """
            GRAPH <http://telicent.io/catalog> {
                <http://telicent.io/catalog/datasets#1b9b5e4a> a <http://www.w3.org/ns/dcat#Dataset> .
            }""";

    private static String catalogRequest(String datasetId) {
        return """
                {
                  "quads": [
                    {
                      "graph": "http://telicent.io/catalog",
                      "subject": "http://telicent.io/catalog/datasets#%s",
                      "predicate": "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
                      "object": { "value": "http://www.w3.org/ns/dcat#Dataset" }
                    }
                  ]
                }""".formatted(datasetId);
    }

    @Test
    public void givenCatalogDatasetTypeQuery_whenDatasetVisible_thenDatasetLabelReturned() throws Exception {
        final String body = callOk(USER3, catalogRequest("1b9b5e4a"));

        assertEquals(Set.of(String.join("|", CATALOG_GRAPH, CATALOG_DATASET + "1b9b5e4a",
                                        "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
                                        "http://www.w3.org/ns/dcat#Dataset", "[\"catalogue\"]")), results(body));
    }

    @Test
    public void givenCatalogDatasetTypeQuery_whenDatasetHidden_thenResponseIdenticalToNonExistentDataset()
            throws Exception {
        final String hidden = callOk(USER1, catalogRequest("1b9b5e4a"));
        final String nonExistent = callOk(USER3, catalogRequest("00000000"));

        assertEquals(EMPTY_RESPONSE, hidden);
        assertEquals(nonExistent, hidden);
    }

    // Comparison with the admin endpoint, /$/labels/<dataset>

    private static String adminCallOk(String requestBody) throws Exception {
        final HttpResponse<String> response =
                callWithToken("$/labels/" + DATASET_NAME, LibTestsSCG.tokenForUser("admin", DATASET_NAME),
                              requestBody);
        assertEquals(200, response.statusCode(), response.body());
        return response.body();
    }

    @Test
    public void givenVisibleTriples_whenQueryingAdminEndpoint_thenIdenticalResponse() throws Exception {
        assertEquals(adminCallOk(triplesRequest(null, DBR + "London", COUNTRY, DBR + "United_Kingdom")),
                     callOk(USER1, triplesRequest(null, DBR + "London", COUNTRY, DBR + "United_Kingdom")));
        assertEquals(adminCallOk(triplesRequest(EUROPE_GRAPH, DBR + "Rome", COUNTRY, DBR + "Italy")),
                     callOk(USER2, triplesRequest(EUROPE_GRAPH, DBR + "Rome", COUNTRY, DBR + "Italy")));
        assertEquals(adminCallOk(catalogRequest("1b9b5e4a")), callOk(USER3, catalogRequest("1b9b5e4a")));
    }

    @Test
    public void givenWildcardQuery_whenComparedWithAdminEndpoint_thenOnlyHiddenTriplesMissing() throws Exception {
        final String request = triplesRequest("*", "*", "*", "*");
        final Set<String> all = results(adminCallOk(request));
        final Set<String> visibleToAnyone = new HashSet<>();
        for (String user : List.of(USER1, USER2, USER3)) {
            final Set<String> visible = results(callOk(user, request));
            assertTrue(all.containsAll(visible), user + " got results the admin endpoint didn't: " + visible);
            visibleToAnyone.addAll(visible);
        }
        // Between them the users can see everything, so nothing is lost beyond what each user can't see
        assertEquals(all, visibleToAnyone);
    }

    // Authentication and authorization

    @Test
    public void givenNoToken_whenQuerying_thenUnauthorized() throws Exception {
        final HttpResponse<String> response =
                callWithToken(DATASET_NAME + ENDPOINT, null, triplesRequest(null, "*", "*", "*"));

        assertEquals(401, response.statusCode(), response.body());
    }

    @Test
    public void givenTokenWithoutRequiredRolesOrPermissions_whenQuerying_thenForbidden() throws Exception {
        final String readPermission = TelicentPermissions.readPermission(DATASET_NAME);
        final List<String> tokens = List.of(
                // No read permission
                LibTestsSCG.tokenBuilder(USER1)
                           .claim("roles", List.of("USER"))
                           .claim("permissions", List.of(TelicentPermissions.writePermission(DATASET_NAME)))
                           .compact(),
                // Read permission for a different dataset
                LibTestsSCG.tokenBuilder(USER1)
                           .claim("roles", List.of("USER"))
                           .claim("permissions", List.of(TelicentPermissions.readPermission("securedDataset2")))
                           .compact(),
                // No USER role
                LibTestsSCG.tokenBuilder(USER1)
                           .claim("roles", List.of())
                           .claim("permissions", List.of(readPermission))
                           .compact());
        for (String token : tokens) {
            final HttpResponse<String> response =
                    callWithToken(DATASET_NAME + ENDPOINT, token, triplesRequest(null, "*", "*", "*"));

            assertEquals(403, response.statusCode(), response.body());
        }
    }

    @Test
    public void givenTokenWithOnlyRequiredRolesAndPermissions_whenQuerying_thenVisibleResultsReturned()
            throws Exception {
        final String token = LibTestsSCG.tokenBuilder(USER1)
                                        .claim("roles", List.of("USER"))
                                        .claim("permissions", List.of(TelicentPermissions.readPermission(DATASET_NAME)))
                                        .compact();
        final HttpResponse<String> response =
                callWithToken(DATASET_NAME + ENDPOINT, token, triplesRequest(null, DBR + "Paris", "*", "*"));

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(Set.of(country("", "Paris", "France", "everyone")), results(response.body()));
    }

    @Test
    public void givenUserNotInAttributeStore_whenQuerying_thenForbidden() throws Exception {
        final HttpResponse<String> response = call("UnknownUser", triplesRequest("*", "*", "*", "*"));

        assertEquals(403, response.statusCode(), response.body());
        assertEquals("No request attributes for user = UnknownUser",
                     MAPPER.readTree(response.body()).get("error").asText());
    }
}
