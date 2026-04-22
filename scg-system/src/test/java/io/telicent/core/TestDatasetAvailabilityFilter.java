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
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDatasetAvailabilityFilter {

    private static final String DATASET_NAME = "/ds";
    private static final Properties AUTH_DISABLED_PROPERTIES = new Properties();
    private static final PropertiesSource AUTH_DISABLED_SOURCE = new PropertiesSource(AUTH_DISABLED_PROPERTIES);

    private FusekiServer server;
    private DatasetGraphSwitchable dataset;

    private void startServer() throws IOException {
        AUTH_DISABLED_PROPERTIES.put(AuthConstants.ENV_JWKS_URL, AuthConstants.AUTH_DISABLED);
        Configurator.setSingleSource(AUTH_DISABLED_SOURCE);
        LibTestsSCG.disableInitialCompaction();
        dataset = createPersistentSwitchableDataset();
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
