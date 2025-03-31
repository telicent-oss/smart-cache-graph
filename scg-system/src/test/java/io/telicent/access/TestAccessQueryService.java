package io.telicent.access;

import io.telicent.LibTestsSCG;
import io.telicent.jena.abac.fuseki.SysFusekiABAC;
import io.telicent.smart.cache.configuration.Configurator;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static io.telicent.LibTestsSCG.tokenHeader;
import static io.telicent.LibTestsSCG.tokenHeaderValue;
import static io.telicent.core.SmartCacheGraph.construct;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestAccessQueryService {

    private static final Path DIR = Path.of("src/test/files");
    private static final String SERVICE_NAME_1 = "ds1";
    private static final String SERVICE_NAME_2 = "ds2";

    private FusekiServer server;
    private HttpClient httpClient;

    private static final String REQUEST_LONDON_COUNTRY = """
            {
              "subject":"http://dbpedia.org/resource/London",
              "predicate":"http://dbpedia.org/ontology/country"
            }""";

    private static final String REQUEST_PARIS_COUNTRY = """
            {
              "subject":"http://dbpedia.org/resource/Paris",
              "predicate":"http://dbpedia.org/ontology/country"
            }""";

    private static final String REQUEST_LONDON_POPULATION = """
            {
              "subject":"http://dbpedia.org/resource/London",
              "predicate":"http://dbpedia.org/ontology/populationTotal"
            }""";

    private static final String REQUEST_PARIS_POPULATION = """
            {
              "subject":"http://dbpedia.org/resource/Paris",
              "predicate":"http://dbpedia.org/ontology/populationTotal"
            }""";

    private static final String USER1 = "User1";
    private static final String USER2 = "User2";

    @BeforeEach
    void setUp() throws Exception {
        FusekiLogging.setLogging();
        SysFusekiABAC.init();
        LibTestsSCG.setupAuthentication();
        LibTestsSCG.disableInitialCompaction();
        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void clearDown() throws Exception {
        LibTestsSCG.teardownAuthentication();
        if (null != server) {
            server.stop();
        }
        Configurator.reset();
    }

    @AfterAll
    static void afterAll() throws Exception {
        FileUtils.deleteDirectory(new File("labels"));
    }

    /**
     * In this test User1 successfully accesses only one country for London in dataset 1
     */
    @Test
    void test_user1_access_country_dataset1() throws Exception {
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/London",
                  "predicate" : "http://dbpedia.org/ontology/country",
                  "objects" : [ "http://dbpedia.org/resource/United_Kingdom" ]
                }""";

        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(REQUEST_LONDON_COUNTRY, USER1, SERVICE_NAME_1);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * In this test User1 successfully accesses one country object for London in dataset 2
     */
    @Test
    void test_user1_access_country_dataset2() throws Exception {
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/Paris",
                  "predicate" : "http://dbpedia.org/ontology/country",
                  "objects" : [ "http://dbpedia.org/resource/France" ]
                }""";

        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(REQUEST_PARIS_COUNTRY, USER1, SERVICE_NAME_2);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * In this test User1 is unable to access the population of London in dataset 1
     */
    @Test
    void test_user1_no_access_population_dataset1() throws Exception {
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/London",
                  "predicate" : "http://dbpedia.org/ontology/populationTotal",
                  "objects" : null
                }""";

        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(REQUEST_LONDON_POPULATION, USER1, SERVICE_NAME_1);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * In this test User1 is unable to access the population of Paris in dataset 2
     */
    @Test
    void test_user1_no_access_population_dataset2() throws Exception {
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/Paris",
                  "predicate" : "http://dbpedia.org/ontology/populationTotal",
                  "objects" : null
                }""";

        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(REQUEST_PARIS_POPULATION, USER1, SERVICE_NAME_1);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * In this test User1 attempts to access the country of Paris which is not in dataset 1
     */
    @Test
    void test_user1_access_country_no_match_dataset1() throws Exception {
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/Paris",
                  "predicate" : "http://dbpedia.org/ontology/country",
                  "objects" : null
                }""";
        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(REQUEST_PARIS_COUNTRY, USER1, SERVICE_NAME_1);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * In this test User1 attempts to access the country of London which is not in dataset 2
     */
    @Test
    void test_user1_access_country_no_match_dataset2() throws Exception {
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/London",
                  "predicate" : "http://dbpedia.org/ontology/country",
                  "objects" : null
                }""";
        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(REQUEST_LONDON_COUNTRY, USER1, SERVICE_NAME_2);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * In this test User1 user has not provided a predicate in the request
     */
    @Test
    void test_user1_incomplete_request_error() throws Exception {
        final String noMatchRequest = """
                {
                  "subject":"http://dbpedia.org/resource/Paris"
                }""";
        final String expectedResponseBody = """
                {
                  "error" : "Unable to process request as missing required values"
                }""";
        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(noMatchRequest, USER1, SERVICE_NAME_1);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * In this test the User2 successfully accesses both country objects for London in dataset 1
     */
    @Test
    void test_user2_access_country_dataset1() throws Exception {
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/London",
                  "predicate" : "http://dbpedia.org/ontology/country",
                  "objects" : [ "http://dbpedia.org/resource/United_Kingdom", "http://dbpedia.org/resource/England" ]
                }""";

        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(REQUEST_LONDON_COUNTRY, USER2, SERVICE_NAME_1);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * In this test User2 successfully accesses a population value in dataset 1
     */
    @Test
    void test_user2_access_population_dataset1() throws Exception {
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/London",
                  "predicate" : "http://dbpedia.org/ontology/populationTotal",
                  "objects" : [ "\\"8799800\\"^^xsd:integer" ]
                }""";

        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(REQUEST_LONDON_POPULATION, USER2, SERVICE_NAME_1);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * In this test User2 successfully accesses the population of Paris in dataset 1
     */
    @Test
    void test_user2_access_population_dataset2() throws Exception {
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/Paris",
                  "predicate" : "http://dbpedia.org/ontology/populationTotal",
                  "objects" : [ "\\"2165423\\"^^xsd:integer" ]
                }""";

        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(REQUEST_PARIS_POPULATION, USER2, SERVICE_NAME_2);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * In this test User2 attempts to access the country of Paris which is not in dataset 1
     */
    @Test
    void test_user2_access_country_no_match_dataset1() throws Exception {
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/Paris",
                  "predicate" : "http://dbpedia.org/ontology/country",
                  "objects" : null
                }""";
        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(REQUEST_PARIS_COUNTRY, USER2, SERVICE_NAME_1);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * In this test User2 attempts to access the country of London which is not in dataset 2
     */
    @Test
    void test_user2_access_country_no_match_dataset2() throws Exception {
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/London",
                  "predicate" : "http://dbpedia.org/ontology/country",
                  "objects" : null
                }""";
        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(REQUEST_LONDON_COUNTRY, USER2, SERVICE_NAME_2);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * In this test User2 attempts to access the population of Paris which is not in dataset 1
     */
    @Test
    void test_user2_access_population_no_match_dataset1() throws Exception {
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/Paris",
                  "predicate" : "http://dbpedia.org/ontology/populationTotal",
                  "objects" : null
                }""";
        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(REQUEST_PARIS_POPULATION, USER2, SERVICE_NAME_1);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * In this test User2 attempts to access the population of London which is not in dataset 2
     */
    @Test
    void test_user2_access_population_no_match_dataset2() throws Exception {
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/London",
                  "predicate" : "http://dbpedia.org/ontology/populationTotal",
                  "objects" : null
                }""";
        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(REQUEST_LONDON_POPULATION, USER2, SERVICE_NAME_2);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }


    /**
     * In this test User2 successfully accesses the country of France in dataset 2
     */
    @Test
    void test_user2_access_country_dataset2() throws Exception {
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/Paris",
                  "predicate" : "http://dbpedia.org/ontology/country",
                  "objects" : [ "http://dbpedia.org/resource/France" ]
                }""";
        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(REQUEST_PARIS_COUNTRY, USER2, SERVICE_NAME_2);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * In this test User2 has not provided a predicate in the request
     */
    @Test
    void test_user2_incomplete_request_error() throws Exception {
        final String noMatchRequest = """
                {
                  "subject":"http://dbpedia.org/resource/Paris"
                }""";
        final String expectedResponseBody = """
                {
                  "error" : "Unable to process request as missing required values"
                }""";
        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(noMatchRequest, USER2, SERVICE_NAME_1);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }


    /**
     * Calls the upload endpoint passing in two distinct datasets for two different service endpoints
     */
    private void loadData() {
        LibTestsSCG.uploadFile(server.serverURL() + SERVICE_NAME_1 + "/upload", DIR + "/access-query-data-labelled-ds1.trig");
        LibTestsSCG.uploadFile(server.serverURL() + SERVICE_NAME_2 + "/upload", DIR + "/access-query-data-labelled-ds2.trig");
    }

    private String callAccessQueryEndpoint(final String requestBody, final String user, final String serviceName) throws Exception {
        final String accessQueryUrl = server.serverURL() + serviceName + "/access/query";
        final String bearerToken = LibTestsSCG.tokenForUser(user);
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(accessQueryUrl))
                .header("Content-type", "application/json")
                .header(tokenHeader(), tokenHeaderValue(bearerToken))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private void startServer() {
        final List<String> arguments = List.of("--conf", DIR + "/access-query-test-config.ttl");
        server = construct(arguments.toArray(new String[0])).start();
    }

}
