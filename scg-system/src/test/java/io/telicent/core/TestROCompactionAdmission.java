package io.telicent.core;

import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import io.telicent.smart.caches.configuration.auth.AuthConstants;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.http.HttpEnv;
import org.apache.jena.system.Txn;
import org.apache.jena.tdb2.DatabaseMgr;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class TestROCompactionAdmission {
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final CountDownLatch admitted = new CountDownLatch(1);
    private final CountDownLatch proceed = new CountDownLatch(1);
    private FusekiServer server;
    private DatasetGraphSwitchable dataset;
    private DatasetMaintenanceRegistry.MaintenanceHandle maintenance;

    private void start() throws Exception {
        Properties config = new Properties();
        config.setProperty(AuthConstants.ENV_JWKS_URL, AuthConstants.AUTH_DISABLED);
        config.setProperty(FMod_InitialCompaction.DISABLE_INITIAL_COMPACTION, "true");
        Configurator.setSingleSource(new PropertiesSource(config));
        dataset = (DatasetGraphSwitchable) DatabaseMgr.connectDatasetGraph(
                Files.createTempDirectory("ro-admission").toString());
        Txn.executeWrite(dataset, () -> dataset.getDefaultGraph().add(NodeFactory.createURI("urn:s"),
                NodeFactory.createURI("urn:p"), NodeFactory.createURI("urn:o")));
        server = SmartCacheGraph.serverBuilder().port(0).numServerThreads(8, 16).add("/ds", dataset)
                .addServlet("/ds/held", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                        // Already admitted by the filter, but deliberately not yet in a Jena transaction.
                        admitted.countDown();
                        try {
                            if (!proceed.await(20, TimeUnit.SECONDS)) {
                                response.sendError(500);
                                return;
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            response.sendError(500);
                            return;
                        }
                        Txn.executeRead(dataset, () -> assertEquals(1, dataset.getDefaultGraph().size()));
                        response.setStatus(200);
                    }
                }).build().start();
        maintenance = DatasetMaintenanceRegistry.begin(dataset, "/ds",
                DatasetMaintenanceRegistry.MaintenanceOperation.RO_COMPACTION).orElseThrow();
    }

    private CompletableFuture<HttpResponse<String>> get(String path) {
        return HttpEnv.getDftHttpClient().sendAsync(HttpRequest.newBuilder(URI.create(server.serverURL() + path))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private void awaitSwitch() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (DatasetMaintenanceRegistry.compactionPhase(dataset)
                != DatasetMaintenanceRegistry.CompactionPhase.SWITCHING) {
            assertTrue(System.nanoTime() < deadline, "Compaction did not enter switching phase");
            Thread.sleep(5);
        }
    }

    @AfterEach
    void cleanUp() throws Exception {
        proceed.countDown();
        workers.shutdown();
        assertTrue(workers.awaitTermination(20, TimeUnit.SECONDS));
        DatasetMaintenanceRegistry.end(maintenance);
        if (server != null) server.stop();
        Configurator.reset();
    }

    @Test
    void acceptedRequestBeforeTransaction_whenSwitching_thenDrainsAndHealthChecksSurviveTraffic() throws Exception {
        start();
        assertEquals(200, get("ds/query?query=ASK%7B%7D").get(5, TimeUnit.SECONDS).statusCode());
        var held = get("ds/held");
        assertTrue(admitted.await(5, TimeUnit.SECONDS));
        Future<?> compaction = workers.submit(() -> ROCompaction.compact(dataset, true, Duration.ofSeconds(15)));
        awaitSwitch();
        assertFalse(compaction.isDone(), "Switch must wait for a request that has not begun its transaction");

        // More requests than the 16-thread Jetty pool: none may wait for a dataset transaction.
        List<CompletableFuture<HttpResponse<String>>> queries = new ArrayList<>();
        for (int i = 0; i < 48; i++) queries.add(get("ds/query?query=ASK%7B%7D"));
        assertEquals(200, get("$/ping").get(5, TimeUnit.SECONDS).statusCode());
        for (var query : queries) {
            HttpResponse<String> response = query.get(5, TimeUnit.SECONDS);
            assertEquals(503, response.statusCode());
            assertEquals(Optional.of("1"), response.headers().firstValue("Retry-After"),
                         "Requests rejected while switching should be told they can retry almost immediately");
        }
        var updateRequest = HttpRequest.newBuilder(
                URI.create(server.serverURL() + "ds/update")).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/sparql-update")
                .POST(HttpRequest.BodyPublishers.ofString("INSERT DATA { <urn:a> <urn:b> <urn:c> }")).build();
        var update = HttpEnv.getDftHttpClient().sendAsync(updateRequest, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> updateResponse = update.get(5, TimeUnit.SECONDS);
        assertEquals(503, updateResponse.statusCode());
        assertEquals(Optional.of("1"), updateResponse.headers().firstValue("Retry-After"));
        assertFalse(compaction.isDone());
        proceed.countDown();
        assertEquals(200, held.get(5, TimeUnit.SECONDS).statusCode());
        compaction.get(10, TimeUnit.SECONDS);
        assertEquals(200, get("ds/query?query=ASK%7B%7D").get(5, TimeUnit.SECONDS).statusCode());
        assertEquals(DatasetMaintenanceRegistry.CompactionPhase.COPYING,
                DatasetMaintenanceRegistry.compactionPhase(dataset));
        DatasetMaintenanceRegistry.end(maintenance);
        assertEquals(204, HttpEnv.getDftHttpClient().send(updateRequest,
                HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void acceptedRequestOutlastsTimeout_whenSwitchAbandoned_thenReadsResumeBeforeRequestFinishes() throws Exception {
        start();
        var held = get("ds/held");
        assertTrue(admitted.await(5, TimeUnit.SECONDS));
        Future<?> compaction = workers.submit(() -> ROCompaction.compact(dataset, true, Duration.ofMillis(200)));
        ExecutionException failure = assertThrows(ExecutionException.class, () -> compaction.get(10, TimeUnit.SECONDS));
        assertInstanceOf(ROCompaction.SwitchAbandonedException.class, failure.getCause());
        assertFalse(held.isDone());
        assertEquals(200, get("ds/query?query=ASK%7B%7D").get(5, TimeUnit.SECONDS).statusCode());
        proceed.countDown();
        assertEquals(200, held.get(5, TimeUnit.SECONDS).statusCode());
        ROCompaction.compact(dataset, true, Duration.ofSeconds(5));
    }

    @Test
    void switchActionFails_thenAdmissionReopens() throws Exception {
        start();
        assertThrows(IllegalStateException.class, () -> ROCompaction.runExclusively(dataset, Duration.ofSeconds(5),
                () -> { throw new IllegalStateException("failed switch"); }));
        assertEquals(200, get("ds/query?query=ASK%7B%7D").get(5, TimeUnit.SECONDS).statusCode());
    }
}
