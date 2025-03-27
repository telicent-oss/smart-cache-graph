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
    private static final String SERVICE_NAME = "ds";
    private FusekiServer server;
    private HttpClient httpClient;
    private static final String REQUEST_COUNTRY = """
            {
              "subject":"http://dbpedia.org/resource/London",
              "predicate":"http://dbpedia.org/ontology/country"
            }""";

    private static final String REQUEST_POPULATION = """
            {
              "subject":"http://dbpedia.org/resource/London",
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
     * In this test the User should be able to access one country object got the subject and predicate
     */
    @Test
    void testUserAccessCountry() throws Exception {
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/London",
                  "predicate" : "http://dbpedia.org/ontology/country",
                  "objects" : [ "http://dbpedia.org/resource/United_Kingdom" ]
                }""";

        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(REQUEST_COUNTRY, USER1);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * In this test the User should not be able to access a population value for the subject and predicate
     */
    @Test
    void testUserNoAccessPopulation() throws Exception {
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/London",
                  "predicate" : "http://dbpedia.org/ontology/populationTotal",
                  "objects" : null
                }""";

        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(REQUEST_POPULATION, USER1);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * In this test the Admin user should be able to access two country objects for the subject and predicate
     */
    @Test
    void testAdminAccessCountry() throws Exception {
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/London",
                  "predicate" : "http://dbpedia.org/ontology/country",
                  "objects" : [ "http://dbpedia.org/resource/United_Kingdom", "http://dbpedia.org/resource/England" ]
                }""";

        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(REQUEST_COUNTRY, USER2);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * In this test the Admin user should be able to access the population value for the subject and predicate
     */
    @Test
    void testAdminAccessPopulation() throws Exception {
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/London",
                  "predicate" : "http://dbpedia.org/ontology/populationTotal",
                  "objects" : [ "\\"8799800\\"^^xsd:integer" ]
                }""";

        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(REQUEST_POPULATION, USER2);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * In this test the Admin user attempts to access a subject and predicate for which there is no match
     */
    @Test
    void testAdminAccessNoMatch() throws Exception {
        final String noMatchRequest = """
            {
              "subject":"http://dbpedia.org/resource/Paris",
              "predicate":"http://dbpedia.org/ontology/country"
            }""";
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/Paris",
                  "predicate" : "http://dbpedia.org/ontology/country",
                  "objects" : null
                }""";
        startServer();
        loadData();
        final String response = callAccessQueryEndpoint(noMatchRequest, USER2);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }


    /**
     * Calls the upload endpoint passing in the data file for loading
     */
    private void loadData() {
        LibTestsSCG.uploadFile(server.serverURL() + SERVICE_NAME + "/upload", DIR + "/access-query-data-labelled.trig");
    }

    private String callAccessQueryEndpoint(String requestBody, String user) throws Exception {
        final String accessQueryUrl = server.serverURL() + "$/access/query";
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
