package io.telicent.access;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestAccessQueryService extends TestAccessBase {

    private static final String ENDPOINT_UNDER_TEST = "/access/query";

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





    /**
     * In this test User1 successfully accesses only one country for London in dataset 1
     */
    @Test
    void test_user1_access_country_dataset1() throws Exception {
        final String expectedResponseBody = """
                {
                  "subject" : "http://dbpedia.org/resource/London",
                  "predicate" : "http://dbpedia.org/ontology/country",
                  "objects" : [ {
                    "dataType" : "http://www.w3.org/2001/XMLSchema#anyURI",
                    "value" : "http://dbpedia.org/resource/United_Kingdom"
                  } ]
                }""";

        startServer();
        loadData();
        final String response = callServiceEndpoint(REQUEST_LONDON_COUNTRY, USER1, SERVICE_NAME_1, ENDPOINT_UNDER_TEST);
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
                  "objects" : [ {
                    "dataType" : "http://www.w3.org/2001/XMLSchema#anyURI",
                    "value" : "http://dbpedia.org/resource/France"
                  } ]
                }""";

        startServer();
        loadData();
        final String response = callServiceEndpoint(REQUEST_PARIS_COUNTRY, USER1, SERVICE_NAME_2, ENDPOINT_UNDER_TEST);
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
        final String response = callServiceEndpoint(REQUEST_LONDON_POPULATION, USER1, SERVICE_NAME_1, ENDPOINT_UNDER_TEST);
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
        final String response = callServiceEndpoint(REQUEST_PARIS_POPULATION, USER1, SERVICE_NAME_1, ENDPOINT_UNDER_TEST);
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
        final String response = callServiceEndpoint(REQUEST_PARIS_COUNTRY, USER1, SERVICE_NAME_1, ENDPOINT_UNDER_TEST);
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
        final String response = callServiceEndpoint(REQUEST_LONDON_COUNTRY, USER1, SERVICE_NAME_2, ENDPOINT_UNDER_TEST);
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
        final String response = callServiceEndpoint(noMatchRequest, USER1, SERVICE_NAME_1, ENDPOINT_UNDER_TEST);
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
                  "objects" : [ {
                    "dataType" : "http://www.w3.org/2001/XMLSchema#anyURI",
                    "value" : "http://dbpedia.org/resource/United_Kingdom"
                  }, {
                    "dataType" : "http://www.w3.org/2001/XMLSchema#anyURI",
                    "value" : "http://dbpedia.org/resource/England"
                  } ]
                }""";

        startServer();
        loadData();
        final String response = callServiceEndpoint(REQUEST_LONDON_COUNTRY, USER2, SERVICE_NAME_1, ENDPOINT_UNDER_TEST);
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
                  "objects" : [ {
                    "dataType" : "http://www.w3.org/2001/XMLSchema#integer",
                    "value" : "8799800"
                  } ]
                }""";

        startServer();
        loadData();
        final String response = callServiceEndpoint(REQUEST_LONDON_POPULATION, USER2, SERVICE_NAME_1, ENDPOINT_UNDER_TEST);
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
                  "objects" : [ {
                    "dataType" : "http://www.w3.org/2001/XMLSchema#integer",
                    "value" : "2165423"
                  } ]
                }""";

        startServer();
        loadData();
        final String response = callServiceEndpoint(REQUEST_PARIS_POPULATION, USER2, SERVICE_NAME_2, ENDPOINT_UNDER_TEST);
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
        final String response = callServiceEndpoint(REQUEST_PARIS_COUNTRY, USER2, SERVICE_NAME_1, ENDPOINT_UNDER_TEST);
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
        final String response = callServiceEndpoint(REQUEST_LONDON_COUNTRY, USER2, SERVICE_NAME_2, ENDPOINT_UNDER_TEST);
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
        final String response = callServiceEndpoint(REQUEST_PARIS_POPULATION, USER2, SERVICE_NAME_1, ENDPOINT_UNDER_TEST);
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
        final String response = callServiceEndpoint(REQUEST_LONDON_POPULATION, USER2, SERVICE_NAME_2, ENDPOINT_UNDER_TEST);
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
                  "objects" : [ {
                    "dataType" : "http://www.w3.org/2001/XMLSchema#anyURI",
                    "value" : "http://dbpedia.org/resource/France"
                  } ]
                }""";
        startServer();
        loadData();
        final String response = callServiceEndpoint(REQUEST_PARIS_COUNTRY, USER2, SERVICE_NAME_2, ENDPOINT_UNDER_TEST);
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
        final String response = callServiceEndpoint(noMatchRequest, USER2, SERVICE_NAME_1, ENDPOINT_UNDER_TEST);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

}
