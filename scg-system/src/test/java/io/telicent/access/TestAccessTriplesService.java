package io.telicent.access;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestAccessTriplesService extends TestAccessBase {

    private static final String ENDPOINT_UNDER_TEST = "/access/triples";

    private static final String SERVICE_NAME_1 = "ds1";
    private static final String SERVICE_NAME_2 = "ds2";

    private final String countryTripleLondonUK = getRequestTripleUri("London", "country", "United_Kingdom");
    private final String countryTripleLondonEngland = getRequestTripleUri("London", "country", "England");
    private final String countryTripleParis = getRequestTripleUri("Paris", "country", "France");

    private final String populationTripleLondon = getRequestTripleLiteral("London", "populationTotal", "8799800");
    private final String populationTripleParis = getRequestTripleLiteral("Paris", "populationTotal", "2165423");


    private final String requestCountryLondonUK = """
            {
              "triples": [%s]
            }""".formatted(countryTripleLondonUK);

    private final String requestCountryLondonEngland = """
            {
              "triples": [%s]
            }""".formatted(countryTripleLondonEngland);

    private final String requestLondon = """
            {
              "triples": [%s,%s]
            }""".formatted(countryTripleLondonUK, populationTripleLondon);

    private static final String USER1 = "User1";
    private static final String USER2 = "User2";

    /**
     * Request London -> country -> United Kingdom returns true for User1 in dataset 1
     */
    @Test
    void test_London_country_UK_user1_ds1_visible_true() throws Exception {
        final String expectedResponseBody = """
                {
                  "triples" : [ {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/United_Kingdom"
                  } ],
                  "visible" : true
                }""";

        startServer();
        loadData();
        final String response = callServiceEndpoint(requestCountryLondonUK, USER1, SERVICE_NAME_1, ENDPOINT_UNDER_TEST, "?all=true");
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * Request London -> country -> England returns false for User1 in dataset 1 as no access
     */
    @Test
    void test_London_country_England_user1_ds1_visible_false() throws Exception {
        final String expectedResponseBody = """
                {
                  "triples" : [ {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/England"
                  } ],
                  "visible" : false
                }""";

        startServer();
        loadData();
        final String response = callServiceEndpoint(requestCountryLondonEngland, USER1, SERVICE_NAME_1, ENDPOINT_UNDER_TEST, "?all=true");
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * Request London -> country -> England returns true for User2 in dataset 1 as has access
     */
    @Test
    void test_London_country_England_user2_ds1_visible_true() throws Exception {
        final String expectedResponseBody = """
                {
                  "triples" : [ {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/England"
                  } ],
                  "visible" : true
                }""";

        startServer();
        loadData();
        final String response = callServiceEndpoint(requestCountryLondonEngland, USER2, SERVICE_NAME_1, ENDPOINT_UNDER_TEST, "?all=true");
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * Request London -> country -> United Kingdom returns false for User1 as triple not present in dataset 2
     */
    @Test
    void test_London_country_England_ds2_visible_false() throws Exception {
        final String expectedResponseBody = """
                {
                  "triples" : [ {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/United_Kingdom"
                  } ],
                  "visible" : false
                }""";

        startServer();
        loadData();
        final String response = callServiceEndpoint(requestCountryLondonUK, USER1, SERVICE_NAME_2, ENDPOINT_UNDER_TEST, "?all=true");
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * Request Paris -> country -> France returns true for User1 as triple in dataset 2
     */
    @Test
    void test_Paris_country_France_ds2_visible_true() throws Exception {
        final String requestCountryParis = """
            {
              "triples": [%s]
            }""".formatted(countryTripleParis);
        final String expectedResponseBody = """
                {
                  "triples" : [ {
                    "subject" : "http://dbpedia.org/resource/Paris",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/France"
                  } ],
                  "visible" : true
                }""";

        startServer();
        loadData();
        final String response = callServiceEndpoint(requestCountryParis, USER1, SERVICE_NAME_2, ENDPOINT_UNDER_TEST, "?all=true");
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * Request Paris -> population -> 2165423 returns false for User1 as no access to triple in dataset 2
     */
    @Test
    void test_Paris_population_ds2_visible_false() throws Exception {
        final String requestPopulationParis = """
            {
              "triples": [%s]
            }""".formatted(populationTripleParis);
        final String expectedResponseBody = """
                {
                  "triples" : [ {
                    "subject" : "http://dbpedia.org/resource/Paris",
                    "predicate" : "http://dbpedia.org/ontology/populationTotal",
                    "object" : "2165423"
                  } ],
                  "visible" : false
                }""";

        startServer();
        loadData();
        final String response = callServiceEndpoint(requestPopulationParis, USER1, SERVICE_NAME_2, ENDPOINT_UNDER_TEST, "?all=true");
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * Request Paris -> population -> 2165423 returns true for User2 with access in dataset 2
     */
    @Test
    @Disabled("Disabled until CORE-772 completed")
    void test_Paris_population_ds2_visible_true() throws Exception {
        final String requestPopulationParis = """
            {
              "triples": [%s]
            }""".formatted(populationTripleParis);
        final String expectedResponseBody = """
                {
                  "triples" : [ {
                    "subject" : "http://dbpedia.org/resource/Paris",
                    "predicate" : "http://dbpedia.org/ontology/populationTotal",
                    "object" : "2165423"
                  } ],
                  "visible" : true
                }""";

        startServer();
        loadData();
        final String response = callServiceEndpoint(requestPopulationParis, USER2, SERVICE_NAME_2, ENDPOINT_UNDER_TEST, "?all=true");
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * Request London -> population total -> 8799800 returns false for User1 as no access to triple in dataset 1
     */
    @Test
    void test_London_one_triple_visible_false() throws Exception {
        final String requestPopulationLondon = """
            {
              "triples": [%s]
            }""".formatted(populationTripleLondon);
        final String expectedResponseBody = """
                {
                  "triples" : [ {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/populationTotal",
                    "object" : "8799800"
                  } ],
                  "visible" : false
                }""";
        startServer();
        loadData();
        final String response = callServiceEndpoint(requestPopulationLondon, USER1, SERVICE_NAME_1, ENDPOINT_UNDER_TEST, "?all=true");
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * Request London data returns false for User1 with all=true as no access to 2nd triple in dataset 1
     */
    @Test
    void test_London_two_triples_one_visible_with_all_required_false() throws Exception {
        final String expectedResponseBody = """
                {
                  "triples" : [ {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/United_Kingdom"
                  }, {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/populationTotal",
                    "object" : "8799800"
                  } ],
                  "visible" : false
                }""";

        startServer();
        loadData();
        final String response = callServiceEndpoint(requestLondon, USER1, SERVICE_NAME_1, ENDPOINT_UNDER_TEST, "?all=true");
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * Request London data returns false for User1 with all at default value (true) as no access to 2nd triple in dataset 1
     */
    @Test
    void test_London_two_triples_one_visible_with_all_required_as_default_false() throws Exception {
        final String expectedResponseBody = """
                {
                  "triples" : [ {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/United_Kingdom"
                  }, {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/populationTotal",
                    "object" : "8799800"
                  } ],
                  "visible" : false
                }""";

        startServer();
        loadData();
        final String response = callServiceEndpoint(requestLondon, USER1, SERVICE_NAME_1, ENDPOINT_UNDER_TEST);
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * Request London data returns true for User1 with all=false as has access to 1st triple in dataset 1
     */
    @Test
    void test_two_triples_one_visible_with_all_required_false() throws Exception {
        final String expectedResponseBody = """
                {
                  "triples" : [ {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/United_Kingdom"
                  }, {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/populationTotal",
                    "object" : "8799800"
                  } ],
                  "visible" : true
                }""";

        startServer();
        loadData();
        final String response = callServiceEndpoint(requestLondon, USER1, SERVICE_NAME_1, ENDPOINT_UNDER_TEST, "?all=false");
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }


    @Test
    void test_two_triples_one_visible_without_all_required_true() throws Exception {
        final String expectedResponseBody = """
                {
                  "triples" : [ {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/United_Kingdom"
                  }, {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/populationTotal",
                    "object" : "8799800"
                  } ],
                  "visible" : true
                }""";

        startServer();
        loadData();
        final String response = callServiceEndpoint(requestLondon, USER1, SERVICE_NAME_1, ENDPOINT_UNDER_TEST, "?all=false");
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    /**
     * Request London data returns true for User2 with all=true as has access to all triples in dataset 1
     */
    @Test
    @Disabled("Disabled until CORE-772 completed")
    void test_two_triples_all_visible_with_all_required_true() throws Exception {
        final String expectedResponseBody = """
                {
                  "triples" : [ {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/United_Kingdom"
                  }, {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/populationTotal",
                    "object" : "8799800"
                  } ],
                  "visible" : true
                }""";

        startServer();
        loadData();
        final String response = callServiceEndpoint(requestLondon, USER2, SERVICE_NAME_1, ENDPOINT_UNDER_TEST, "?all=true");
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    @Test
    void test_two_triples_none_visible_without_all_required_true() throws Exception {
        final String requestLondonEngland = """
            {
              "triples": [%s,%s]
            }""".formatted(countryTripleLondonEngland, populationTripleLondon);
        final String expectedResponseBody = """
                {
                  "triples" : [ {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/country",
                    "object" : "http://dbpedia.org/resource/England"
                  }, {
                    "subject" : "http://dbpedia.org/resource/London",
                    "predicate" : "http://dbpedia.org/ontology/populationTotal",
                    "object" : "8799800"
                  } ],
                  "visible" : false
                }""";
        startServer();
        loadData();
        final String response = callServiceEndpoint(requestLondonEngland, USER1, SERVICE_NAME_1, ENDPOINT_UNDER_TEST, "?all=false");
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    @Test
    void bad_request_returns_error() throws Exception {
        final String expectedResponseBody = """
                {
                  "error" : "Unable to interpret JSON request"
                }""";
        startServer();
        loadData();
        final String response = callServiceEndpoint(countryTripleLondonEngland, USER1, SERVICE_NAME_1, ENDPOINT_UNDER_TEST, "?all=false");
        assertEquals(expectedResponseBody, response, "Unexpected access query response");
    }

    private String getRequestTripleUri(final String s, final String p, final String o) {
        return """
                {
                  "subject" : "http://dbpedia.org/resource/%s",
                  "predicate" : "http://dbpedia.org/ontology/%s",
                  "object" : "http://dbpedia.org/resource/%s"
                }""".formatted(s, p, o);
    }

    private String getRequestTripleLiteral(final String s, final String p, final String o) {
        return """
                {
                  "subject" : "http://dbpedia.org/resource/%s",
                  "predicate" : "http://dbpedia.org/ontology/%s",
                  "object" : "%s"
                }""".formatted(s, p, o);
    }
}
