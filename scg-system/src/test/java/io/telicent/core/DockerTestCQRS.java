package io.telicent.core;

import io.telicent.LibTestsSCG;
import io.telicent.smart.cache.sources.TelicentHeaders;
import io.telicent.smart.cache.sources.kafka.BasicKafkaTestCluster;
import io.telicent.smart.cache.sources.kafka.KafkaTestCluster;
import io.telicent.smart.cache.sources.kafka.config.KafkaConfiguration;
import org.apache.jena.atlas.lib.FileOps;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.exec.RowSetOps;
import org.apache.jena.sparql.exec.RowSetRewindable;
import org.apache.jena.sparql.exec.http.QueryExecHTTPBuilder;
import org.apache.jena.sparql.exec.http.UpdateExecHTTPBuilder;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static io.telicent.TestSmartCacheGraphIntegration.launchServer;

public class DockerTestCQRS {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerTestCQRS.class);

    protected static final String DIR = "target/databases";
    private static final String QUERY = "SELECT * {?s ?p ?o}";
    private static final String FIND_NAME_QUERY = """
            SELECT ?name
            WHERE {
              ?s <https://example.org/name> ?name .
            }
            """;
    public static final String DATASET_NAME = "ds";
    public static final String USER_1 = "u1";
    public static final String USER_2 = "u2";
    public static final String QUERY_ENDPOINT = "/query";
    public static final String UPDATE_ENDPOINT = "/update";
    public static final String SCG_CQRS_CONFIG = "config-cqrs.ttl";
    public static final String ADMIN = "admin";
    public static final Duration DEFAULT_POLL_DELAY = Duration.ofSeconds(2);
    private static final AtomicInteger CONSUMER_ID = new AtomicInteger(0);
    public static final String DELETE_INSERT_WHERE = """
            DELETE {
              ?s <https://example.org/name> "John Smith" .
            }
            INSERT {
              ?s <https://example.org/name> "Johnathon Frederick Smith" .
            }
            WHERE
            {
              ?s <https://example.org/name> "John Smith" .
            }
            """;
    public static final String INSERT_JOHN_SMITH = """
            INSERT DATA {
              <https://example.org/subject> <https://example.org/name> "John Smith" .
            }
            """;
    public static final String EMPLOYEE = "employee";
    public static final String INSERT_GENERIC_TRIPLE = """
            INSERT DATA {
              <https://example.org/s> <https://example.org/p> <https://example.org/o> .
            }
            """;
    public static final String INSERT_USER_1_TRIPLE = """
            INSERT DATA {
              <https://example.org/s> <https://example.org/p> "u1" .
            }
            """;
    public static final String INSERT_USER_2_TRIPLE = """
            INSERT DATA {
              <https://example.org/s> <https://example.org/p> "u2" .
            }
            """;
    public static final String CONTRACTOR = "contractor";
    protected static KafkaTestCluster KAFKA;

    protected FusekiServer server;

    @BeforeAll
    protected static void setupFuseki() throws Exception {
        FileOps.ensureDir(DIR);
        FileOps.clearAll(DIR);
        FusekiLogging.setLogging();
        LibTestsSCG.setupAuthentication();
        LibTestsSCG.disableInitialCompaction();
    }

    @BeforeEach
    protected void setup() {
        if (KAFKA == null) {
            // First test starts the Kafka cluster
            KAFKA = new BasicKafkaTestCluster();
            KAFKA.setup();
        } else {
            // Subsequent tests merely resets our test topic
            KAFKA.resetTestTopic();
        }
        System.setProperty(KafkaConfiguration.CONSUMER_GROUP, "cqrs-consumer-" + CONSUMER_ID.incrementAndGet());
        System.setProperty(KafkaConfiguration.BOOTSTRAP_SERVERS, KAFKA.getBootstrapServers());
    }

    @AfterEach
    protected void teardown() {
        // Stop the Fuseki server (if any) after each test
        if (server != null) {
            server.stop();
            server = null;
        }
        System.clearProperty(KafkaConfiguration.BOOTSTRAP_SERVERS);
        System.clearProperty(KafkaConfiguration.CONSUMER_GROUP);
    }

    private String url(String x) {
        return server.datasetURL(DATASET_NAME) + x;
    }

    private void executeSparqlUpdate(String token, String update, String securityLabel) {
        UpdateExecHTTPBuilder.create()
                             .endpoint(url(UPDATE_ENDPOINT))
                             .update(update)
                             .httpHeader(LibTestsSCG.tokenHeader(), LibTestsSCG.tokenHeaderValue(token))
                             .httpHeader(TelicentHeaders.SECURITY_LABEL, securityLabel)
                             .execute();
    }

    /**
     * Verifies data is visible
     *
     * @param url      Query URL
     * @param query    Query to run
     * @param token    Authentication token to use
     * @param expected Number of expected results
     * @return Results
     */
    private RowSetRewindable verifyDataVisible(String url, String query, String token, int expected) {
        return verifyDataVisible(url, query, token, DEFAULT_POLL_DELAY, expected);
    }

    /**
     * Verifies data is visible
     * <p>
     * Since CQRS updates have a delay in being applied as they are first written to Kafka, and then must be read and
     * applied by the Kafka Connector, we use {@link Awaitility} to potentially run the query multiple times until we
     * receive the expected number of results.
     * </p>
     *
     * @param url       Query URL
     * @param query     Query to run
     * @param token     Authentication token to use
     * @param pollDelay Initial poll delay, where this method is being called several times subsequent calls may wish to
     *                  supply a different delay as if a previous call to this method has succeeded the test can assume
     *                  that the CQRS update has been applied.
     * @param expected  Number of expected results
     * @return Results
     */
    private RowSetRewindable verifyDataVisible(String url, String query, String token, Duration pollDelay,
                                               int expected) {
        return Awaitility.await()
                         .pollDelay(pollDelay)
                         .pollInterval(Duration.ofSeconds(1))
                         .atMost(Duration.ofSeconds(10))
                         .until(() -> {
                             RowSet rs = QueryExecHTTPBuilder.service(url)
                                                             .query(query)
                                                             .httpHeader(LibTestsSCG.tokenHeader(),
                                                                         LibTestsSCG.tokenHeaderValue(token))
                                                             .select();
                             RowSetRewindable rewindable = rs.rewindable();
                             LOGGER.info("{} Got {} results ({} expected)",
                                         rewindable.size() == expected ? "GOOD" : "BAD", rewindable.size(), expected);
                             if (rewindable.size() != expected) {
                                 RowSetOps.out(rewindable);
                             }
                             return rewindable;
                         }, rw -> rw.size() == expected);

    }

    /**
     * Verifies that no data is visible, runs the generic {@link #QUERY} that selects everything against the query
     * endpoint and verifies that the number of results returned is zero for the given user
     *
     * @param user User to run the query as
     */
    private void verifyNothingVisible(String user) {
        verifyNothingVisible(user, DEFAULT_POLL_DELAY);
    }

    /**
     * Verifies that no data is visible, runs the generic {@link #QUERY} that selects everything against the query
     * endpoint and verifies that the number of results returned is zero for the given user
     *
     * @param pollDelay Initial poll delay, where this method is being called several times subsequent calls may wish to
     *                  supply a different delay as if a previous call to this method has succeeded the test can assume
     *                  that the CQRS update has been applied.
     * @param user      User to run the query as
     */
    private void verifyNothingVisible(String user, Duration pollDelay) {
        String token = LibTestsSCG.tokenForUser(user, DATASET_NAME);
        verifyDataVisible(url(QUERY_ENDPOINT), QUERY, token, pollDelay, 0);
    }

    @Test
    public void givenEmptyDataset_whenInsertingDataViaCqrs_thenDataInserted_andNotVisibleToUsersWithWrongAttributes() {
        // Given
        server = launchServer(SCG_CQRS_CONFIG);
        String token = LibTestsSCG.tokenForUser(USER_1, DATASET_NAME);

        // When
        executeSparqlUpdate(token, INSERT_GENERIC_TRIPLE, EMPLOYEE);

        // Then
        verifyDataVisible(url(QUERY_ENDPOINT), QUERY, token, 1);

        // And
        verifyNothingVisible(USER_2, Duration.ZERO);
    }

    @Test
    public void givenPopulatedData_whenPerformingDeleteInsertWhereViaCqrsByUserWhoCantSeeData_thenDataNotModified() {
        // Given
        server = launchServer(SCG_CQRS_CONFIG);
        String u1 = LibTestsSCG.tokenForUser(USER_1, DATASET_NAME);
        String u2 = LibTestsSCG.tokenForUser(USER_2, DATASET_NAME);
        executeSparqlUpdate(u1, INSERT_JOHN_SMITH, EMPLOYEE);
        verifyDataVisible(url(QUERY_ENDPOINT), FIND_NAME_QUERY, u1, 1);
        verifyNothingVisible(USER_2, Duration.ZERO);

        // When
        executeSparqlUpdate(u2, DELETE_INSERT_WHERE, EMPLOYEE);

        // Then
        RowSetRewindable results = verifyDataVisible(url(QUERY_ENDPOINT), FIND_NAME_QUERY, u1, 1);
        Assert.assertEquals("John Smith", results.next().get("name").getLiteralLexicalForm());
    }

    @Test
    public void givenPopulatedData_whenPerformingDeleteInsertWhereViaCqrs_thenDataModified_andNotVisibleToUsersWithWrongAttributes() {
        // Given
        server = launchServer(SCG_CQRS_CONFIG);
        String token = LibTestsSCG.tokenForUser(USER_1, DATASET_NAME);
        executeSparqlUpdate(token, INSERT_JOHN_SMITH, EMPLOYEE);
        verifyDataVisible(url(QUERY_ENDPOINT), FIND_NAME_QUERY, token, 1);

        // When
        executeSparqlUpdate(token, DELETE_INSERT_WHERE, EMPLOYEE);

        // Then
        RowSetRewindable results = verifyDataVisible(url(QUERY_ENDPOINT), QUERY, token, 1);
        Assert.assertEquals("Johnathon Frederick Smith", results.next().get("o").getLiteralLexicalForm());

        // And
        verifyNothingVisible(USER_2, Duration.ZERO);
    }

    @Test
    public void givenEmptyDataset_whenMultipleUsersInsertViaCqrsWithDifferentLabels_thenDataInserted() {
        // Given
        server = launchServer(SCG_CQRS_CONFIG);
        String u1 = LibTestsSCG.tokenForUser(USER_1, DATASET_NAME);
        String u2 = LibTestsSCG.tokenForUser(USER_2, DATASET_NAME);
        String admin = LibTestsSCG.tokenForUser(ADMIN, DATASET_NAME);

        // When
        executeSparqlUpdate(u1, INSERT_USER_1_TRIPLE, EMPLOYEE);
        executeSparqlUpdate(u2, INSERT_USER_2_TRIPLE, CONTRACTOR);

        // Then
        verifyDataVisible(url(QUERY_ENDPOINT), QUERY, u1, 1);
        verifyDataVisible(url(QUERY_ENDPOINT), QUERY, u2, Duration.ZERO, 1);
        verifyDataVisible(url(QUERY_ENDPOINT), QUERY, admin, Duration.ZERO, 2);
    }

    @Test
    public void givenEmptyDataset_whenMultipleUsersInsertSameDataWithDifferentLabelsViaCqrs_thenDataLabelIsFromLastUpdate() {
        // Given
        server = launchServer(SCG_CQRS_CONFIG);
        String u1 = LibTestsSCG.tokenForUser(USER_1, DATASET_NAME);
        String u2 = LibTestsSCG.tokenForUser(USER_2, DATASET_NAME);
        String admin = LibTestsSCG.tokenForUser(ADMIN, DATASET_NAME);

        // When
        executeSparqlUpdate(u1, INSERT_GENERIC_TRIPLE, EMPLOYEE);
        executeSparqlUpdate(u2, INSERT_GENERIC_TRIPLE, CONTRACTOR);

        // Then
        verifyNothingVisible(USER_1);
        verifyDataVisible(url(QUERY_ENDPOINT), QUERY, u2, Duration.ZERO, 1);
        verifyDataVisible(url(QUERY_ENDPOINT), QUERY, admin, Duration.ZERO, 1);
    }

    @Test
    public void givenPopulatedData_whenPerformingDropAll_thenOnlyVisibleDataDropped() {
        // Given
        server = launchServer(SCG_CQRS_CONFIG);
        String u1 = LibTestsSCG.tokenForUser(USER_1, DATASET_NAME);
        String u2 = LibTestsSCG.tokenForUser(USER_2, DATASET_NAME);
        String admin = LibTestsSCG.tokenForUser(ADMIN, DATASET_NAME);
        executeSparqlUpdate(u1, INSERT_USER_1_TRIPLE, EMPLOYEE);
        executeSparqlUpdate(u2, INSERT_USER_2_TRIPLE, CONTRACTOR);
        verifyDataVisible(url(QUERY_ENDPOINT), QUERY, admin, 2);

        // When
        executeSparqlUpdate(u2, "DROP ALL", EMPLOYEE);

        // Then
        verifyDataVisible(url(QUERY_ENDPOINT), QUERY, u1, 1);
        verifyNothingVisible(USER_2, Duration.ZERO);
        verifyDataVisible(url(QUERY_ENDPOINT), QUERY, admin, Duration.ZERO, 1);
    }

    @Test
    public void givenPopulatedData_whenPerformingDropAllByUserWhoCanSeeAllData_thenAllDataDropped() {
        // Given
        server = launchServer(SCG_CQRS_CONFIG);
        String u1 = LibTestsSCG.tokenForUser(USER_1, DATASET_NAME);
        String u2 = LibTestsSCG.tokenForUser(USER_2, DATASET_NAME);
        String admin = LibTestsSCG.tokenForUser(ADMIN, DATASET_NAME);
        executeSparqlUpdate(u1, INSERT_USER_1_TRIPLE, EMPLOYEE);
        executeSparqlUpdate(u2, INSERT_USER_2_TRIPLE, CONTRACTOR);
        verifyDataVisible(url(QUERY_ENDPOINT), QUERY, admin, 2);

        // When
        executeSparqlUpdate(admin, "DROP ALL", EMPLOYEE);

        // Then
        verifyNothingVisible(USER_1);
        verifyNothingVisible(USER_2, Duration.ZERO);
        verifyNothingVisible(ADMIN, Duration.ZERO);
    }
}
