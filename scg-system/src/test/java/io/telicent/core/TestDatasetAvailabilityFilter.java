package io.telicent.core;

import io.telicent.LibTestsSCG;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import io.telicent.smart.caches.configuration.auth.AuthConstants;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.http.HttpEnv;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestDatasetAvailabilityFilter {

    private static final String DATASET_NAME = "/ds";
    private static final Properties AUTH_DISABLED_PROPERTIES = new Properties();
    private static final PropertiesSource AUTH_DISABLED_SOURCE = new PropertiesSource(AUTH_DISABLED_PROPERTIES);

    private FusekiServer server;
    private DatasetGraphSwitchable dataset;

    private void startServer() throws IOException {
        startServer(createPersistentSwitchableDataset());
    }

    private void startServer(DatasetGraphSwitchable dsg) {
        AUTH_DISABLED_PROPERTIES.put(AuthConstants.ENV_JWKS_URL, AuthConstants.AUTH_DISABLED);
        Configurator.setSingleSource(AUTH_DISABLED_SOURCE);
        LibTestsSCG.disableInitialCompaction();
        dataset = dsg;
        server = SmartCacheGraph.serverBuilder()
                                .port(0)
                                .add(DATASET_NAME, dataset)
                                .build()
                                .start();
    }

    @AfterEach
    void tearDown() {
        FMod_InitialCompaction.CURRENT_COMPACTIONS.clear();
        DatasetMaintenanceRegistry.ACTIVE_MAINTENANCE.clear();
        Configurator.reset();
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void givenDatasetQueryDuringCompaction_whenProcessingRequest_then503() throws IOException, InterruptedException {
        startServer();
        markMaintenanceInProgress(dataset, DatasetMaintenanceRegistry.MaintenanceOperation.COMPACTION);

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(URI.create(server.serverURL() + "ds/query?query="
                                                 + URLEncoder.encode("SELECT * WHERE { ?s ?p ?o }",
                                                                     StandardCharsets.UTF_8)))
                                         .GET()
                                         .build();

        HttpResponse<String> response =
                HttpEnv.getDftHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("temporarily unavailable"));
        assertTrue(response.body().contains(DATASET_NAME));
        // Exclusive compaction can take a long time, so no Retry-After encouraging clients to retry straight away
        assertTrue(response.headers().firstValue("Retry-After").isEmpty());
        assertTrue(response.body().contains("compaction"));
        assertTrue(response.headers().firstValue("Request-ID").isPresent());
    }

    @Test
    void givenPingDuringCompaction_whenProcessingRequest_thenHealthCheckStillPasses() throws IOException, InterruptedException {
        startServer();
        markMaintenanceInProgress(dataset, DatasetMaintenanceRegistry.MaintenanceOperation.COMPACTION);

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(URI.create(server.serverURL() + "$/ping"))
                                         .GET()
                                         .build();

        HttpResponse<String> response =
                HttpEnv.getDftHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertFalse(response.body().isBlank());
    }

    @Test
    void givenDatasetQueryDuringRestore_whenProcessingRequest_then503() throws IOException, InterruptedException {
        startServer();
        markMaintenanceInProgress(dataset, DatasetMaintenanceRegistry.MaintenanceOperation.RESTORE);

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(URI.create(server.serverURL() + "ds/query?query="
                                                 + URLEncoder.encode("SELECT * WHERE { ?s ?p ?o }",
                                                                     StandardCharsets.UTF_8)))
                                         .GET()
                                         .build();

        HttpResponse<String> response =
                HttpEnv.getDftHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("restore"));
    }

    @Test
    void givenDatasetQueryDuringBackup_whenProcessingRequest_then503() throws IOException, InterruptedException {
        startServer();
        markMaintenanceInProgress(dataset, DatasetMaintenanceRegistry.MaintenanceOperation.BACKUP);

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(URI.create(server.serverURL() + "ds/query?query="
                                                 + URLEncoder.encode("SELECT * WHERE { ?s ?p ?o }",
                                                                     StandardCharsets.UTF_8)))
                                         .GET()
                                         .build();

        HttpResponse<String> response =
                HttpEnv.getDftHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("backup"));
    }

    @Test
    void datasetNameForPath_identifiesDatasetBackedPaths() {
        Set<String> datasetNames = Set.of("/ds", "/other");

        assertEquals(Optional.of("/ds"),
                     FMod_DatasetAvailabilityFilter.datasetNameForPath("/ds", datasetNames));
        assertEquals(Optional.of("/ds"),
                     FMod_DatasetAvailabilityFilter.datasetNameForPath("/ds/query", datasetNames));
        assertEquals(Optional.of("/ds"),
                     FMod_DatasetAvailabilityFilter.datasetNameForPath("/ds/access/query", datasetNames));
        assertEquals(Optional.of("/ds"),
                     FMod_DatasetAvailabilityFilter.datasetNameForPath("/$/labels/ds", datasetNames));
    }

    @Test
    void datasetNameForPath_ignoresControlPaths() {
        Set<String> datasetNames = Set.of("/ds");

        assertTrue(FMod_DatasetAvailabilityFilter.datasetNameForPath("/$/ping", datasetNames).isEmpty());
        assertTrue(FMod_DatasetAvailabilityFilter.datasetNameForPath("/$/compact/ds", datasetNames).isEmpty());
        assertTrue(FMod_DatasetAvailabilityFilter.datasetNameForPath("/$/backups/create", datasetNames).isEmpty());
        assertTrue(FMod_DatasetAvailabilityFilter.datasetNameForPath("/unknown/query", datasetNames).isEmpty());
    }

    @Test
    void givenSameDatasetPathOnDifferentServers_whenOneDatasetCompacting_thenOtherDatasetIsNotMarkedUnavailable()
            throws IOException {
        DatasetGraphSwitchable datasetOne = createPersistentSwitchableDataset();
        DatasetGraphSwitchable datasetTwo = createPersistentSwitchableDataset();
        Set<String> datasetNames = Set.of(DATASET_NAME);
        Map<String, DatasetGraphSwitchable> serverOneDatasets = Map.of(DATASET_NAME, datasetOne);
        Map<String, DatasetGraphSwitchable> serverTwoDatasets = Map.of(DATASET_NAME, datasetTwo);
        markMaintenanceInProgress(datasetOne, DatasetMaintenanceRegistry.MaintenanceOperation.COMPACTION);

        Optional<DatasetGraphSwitchable> resolvedOne =
                FMod_DatasetAvailabilityFilter.datasetForPath("/ds/query", datasetNames, serverOneDatasets);
        Optional<DatasetGraphSwitchable> resolvedTwo =
                FMod_DatasetAvailabilityFilter.datasetForPath("/ds/query", datasetNames, serverTwoDatasets);

        assertTrue(resolvedOne.isPresent());
        assertTrue(resolvedTwo.isPresent());
        assertTrue(DatasetMaintenanceRegistry.isMaintenanceInProgress(resolvedOne.get()));
        assertFalse(DatasetMaintenanceRegistry.isMaintenanceInProgress(resolvedTwo.get()));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // RO Compaction: reads are served, writes are rejected
    // ---------------------------------------------------------------------------------------------------------------

    private int send(String method, String path, String contentType, String body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(server.serverURL() + path));
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                                                           : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest request = builder.method(method, publisher).build();
        return HttpEnv.getDftHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private static String q(String query) {
        return URLEncoder.encode(query, StandardCharsets.UTF_8);
    }

    @Test
    void givenROCompaction_whenReading_thenRequestsAreServed() throws IOException, InterruptedException {
        // A real TDB2 dataset, so requests that get through the filter are actually answered
        startServer((DatasetGraphSwitchable) org.apache.jena.tdb2.DatabaseMgr.connectDatasetGraph(
                java.nio.file.Files.createTempDirectory("availability-filter-tdb2").toString()));
        markMaintenanceInProgress(dataset, DatasetMaintenanceRegistry.MaintenanceOperation.RO_COMPACTION);

        assertEquals(200, send("GET", "ds/query?query=" + q("SELECT * { ?s ?p ?o }"), null, null));
        assertEquals(200, send("POST", "ds/query", "application/sparql-query", "SELECT * { ?s ?p ?o }"));
        assertEquals(200, send("POST", "ds/sparql", "application/x-www-form-urlencoded",
                               "query=" + q("ASK { ?s ?p ?o }")));
        assertEquals(200, send("POST", "ds", "application/sparql-query", "SELECT * { ?s ?p ?o }"));
        assertEquals(200, send("GET", "ds/get?default", null, null));
        assertEquals(200, send("GET", "ds/data?default", null, null));
    }

    @Test
    void givenROCompaction_whenWriting_thenRequestsAre503() throws IOException, InterruptedException {
        startServer();
        markMaintenanceInProgress(dataset, DatasetMaintenanceRegistry.MaintenanceOperation.RO_COMPACTION);

        assertEquals(503, send("POST", "ds/update", "application/sparql-update", "INSERT DATA { <urn:a> <urn:b> <urn:c> }"));
        assertEquals(503, send("POST", "ds", "application/sparql-update", "INSERT DATA { <urn:a> <urn:b> <urn:c> }"));
        assertEquals(503, send("PUT", "ds/data?default", "text/turtle", "<urn:a> <urn:b> <urn:c> ."));
        assertEquals(503, send("POST", "ds/data?default", "text/turtle", "<urn:a> <urn:b> <urn:c> ."));
        assertEquals(503, send("DELETE", "ds/data?default", null, null));
        assertEquals(503, send("POST", "ds/upload", "text/turtle", "<urn:a> <urn:b> <urn:c> ."));
    }

    @Test
    void givenExclusiveCompaction_whenReading_thenStill503() throws IOException, InterruptedException {
        startServer();
        markMaintenanceInProgress(dataset, DatasetMaintenanceRegistry.MaintenanceOperation.COMPACTION);

        assertEquals(503, send("POST", "ds/query", "application/sparql-query", "SELECT * { ?s ?p ?o }"));
    }

    @Test
    void givenRequests_whenClassifyingReads_thenOnlyReadOnlyRequestsQualify() {
        org.apache.jena.fuseki.server.DataService service =
                org.apache.jena.fuseki.server.DataService.newBuilder()
                        .dataset(org.apache.jena.sparql.core.DatasetGraphFactory.createTxnMem())
                        .addEndpoint(org.apache.jena.fuseki.server.Operation.Query, "query")
                        .addEndpoint(org.apache.jena.fuseki.server.Operation.Update, "update")
                        .addEndpoint(org.apache.jena.fuseki.server.Operation.GSP_RW, "data")
                        .addEndpoint(io.telicent.jena.graphql.fuseki.SysGraphQL.OP_GRAPHQL, "graphql")
                        .build();

        assertTrue(FMod_DatasetAvailabilityFilter.isReadRequest(request("GET", "/ds/update", null), "/ds", service));
        assertTrue(FMod_DatasetAvailabilityFilter.isReadRequest(request("HEAD", "/ds/data", null), "/ds", service));
        assertTrue(FMod_DatasetAvailabilityFilter.isReadRequest(request("POST", "/ds/query", null), "/ds", service));
        assertTrue(FMod_DatasetAvailabilityFilter.isReadRequest(request("POST", "/$/labels/ds", null), "/ds", service));
        assertTrue(FMod_DatasetAvailabilityFilter.isReadRequest(
                request("POST", "/ds/graphql", "application/json"), "/ds", service));
        assertTrue(FMod_DatasetAvailabilityFilter.isReadRequest(
                request("POST", "/ds", "application/sparql-query; charset=utf-8"), "/ds", service));
        assertFalse(FMod_DatasetAvailabilityFilter.isReadRequest(request("POST", "/ds", "text/turtle"), "/ds", service));
        assertFalse(FMod_DatasetAvailabilityFilter.isReadRequest(request("POST", "/ds/update", null), "/ds", service));
        assertFalse(FMod_DatasetAvailabilityFilter.isReadRequest(request("POST", "/ds/data", null), "/ds", service));
        assertFalse(FMod_DatasetAvailabilityFilter.isReadRequest(request("POST", "/ds/unknown", null), "/ds", service));
        assertFalse(FMod_DatasetAvailabilityFilter.isReadRequest(request("POST", "/ds/query", null), "/ds", null));
        assertFalse(FMod_DatasetAvailabilityFilter.isReadRequest(request("PUT", "/ds/data", null), "/ds", service));
        assertFalse(FMod_DatasetAvailabilityFilter.isReadRequest(request("PATCH", "/ds", null), "/ds", service));
    }

    private static jakarta.servlet.http.HttpServletRequest request(String method, String uri, String contentType) {
        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getContentType()).thenReturn(contentType);
        return request;
    }

    private static void markMaintenanceInProgress(DatasetGraphSwitchable dsg,
                                                  DatasetMaintenanceRegistry.MaintenanceOperation operation) {
        assertTrue(DatasetMaintenanceRegistry.begin(dsg, DATASET_NAME, operation).isPresent());
    }

    private static DatasetGraphSwitchable createPersistentSwitchableDataset() throws IOException {
        java.nio.file.Path container = java.nio.file.Files.createTempDirectory("availability-filter-dsg");
        java.nio.file.Path dataDir = container.resolve("Data-0001");
        java.nio.file.Files.createDirectories(dataDir);
        java.nio.file.Files.writeString(dataDir.resolve("marker.txt"), "x", StandardCharsets.UTF_8);
        return new DatasetGraphSwitchable(container, null, org.apache.jena.sparql.core.DatasetGraphFactory.createTxnMem());
    }
}
