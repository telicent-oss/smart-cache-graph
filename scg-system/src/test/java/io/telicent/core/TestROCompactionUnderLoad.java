package io.telicent.core;

import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import io.telicent.smart.caches.configuration.auth.AuthConstants;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.http.HttpEnv;
import org.apache.jena.system.Txn;
import org.apache.jena.tdb2.DatabaseMgr;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Requests an RO Compaction over HTTP while queries are continuously running against the dataset, as in
 * production. With Jena's own compaction this hangs as soon as a query spans the switch to the compacted copy
 * (apache/jena#3488).
 */
public class TestROCompactionUnderLoad {

    private static final int TRIPLES = 80_000;
    private static final String COUNT_QUERY = "SELECT (COUNT(*) AS ?n) { ?s ?p ?o }";

    private FusekiServer server;
    private ExecutorService pool;

    @AfterEach
    void tearDown() {
        if (pool != null) {
            pool.shutdownNow();
        }
        if (server != null) {
            server.stop();
        }
        DatasetMaintenanceRegistry.ACTIVE_MAINTENANCE.clear();
        FMod_InitialCompaction.SIZES.clear();
        FMod_InitialCompaction.CURRENT_COMPACTIONS.clear();
        Configurator.reset();
    }

    @Test
    void givenContinuousQueries_whenROCompactionRequested_thenCompactsAndQueriesSucceedOrAreRejectedDuringSwitch() throws Exception {
        // given
        Properties properties = new Properties();
        properties.put(AuthConstants.ENV_JWKS_URL, AuthConstants.AUTH_DISABLED);
        Configurator.setSingleSource(new PropertiesSource(properties));
        DatasetGraphSwitchable dsg = (DatasetGraphSwitchable) DatabaseMgr.connectDatasetGraph(
                Files.createTempDirectory("ro-compaction-load").toString());
        server = SmartCacheGraph.serverBuilder().port(0).add("/ds", dsg).build().start();
        // Written after startup (which compacts) so the requested compaction has work to do
        for (int batch = 0; batch < 40; batch++) {
            final int b = batch;
            Txn.executeWrite(dsg, () -> {
                for (int i = 0; i < TRIPLES / 40; i++) {
                    dsg.getDefaultGraph().add(NodeFactory.createURI("urn:s" + b + "_" + i),
                                              NodeFactory.createURI("urn:p"), NodeFactory.createLiteralString("o" + i));
                }
            });
        }
        String queryUrl = server.serverURL() + "ds/query?query=" + URLEncoder.encode(COUNT_QUERY, StandardCharsets.UTF_8);
        AtomicBoolean stop = new AtomicBoolean();
        CountDownLatch readersRunning = new CountDownLatch(8);
        CountDownLatch answeredDuringCompaction = new CountDownLatch(1);
        Map<Integer, Integer> statuses = new ConcurrentHashMap<>();
        List<String> wrongAnswers = new CopyOnWriteArrayList<>();
        pool = Executors.newFixedThreadPool(9);
        List<Future<?>> readers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            readers.add(pool.submit(() -> {
                while (!stop.get()) {
                    try {
                        boolean startedDuringCompaction = !DatasetMaintenanceRegistry.ACTIVE_MAINTENANCE.isEmpty();
                        HttpResponse<String> response = HttpEnv.getDftHttpClient().send(
                                HttpRequest.newBuilder(URI.create(queryUrl))
                                           .header("Accept", "text/csv")
                                           .GET()
                                           .build(), HttpResponse.BodyHandlers.ofString());
                        statuses.merge(response.statusCode(), 1, Integer::sum);
                        readersRunning.countDown();
                        if (startedDuringCompaction && response.statusCode() == 200) {
                            answeredDuringCompaction.countDown();
                        }
                        if (response.statusCode() == 200 && !response.body().contains(Integer.toString(TRIPLES))) {
                            wrongAnswers.add(response.body());
                        }
                    } catch (Exception e) {
                        statuses.merge(-1, 1, Integer::sum);
                    }
                }
            }));
        }
        assertTrue(readersRunning.await(30, TimeUnit.SECONDS), "Readers did not start");
        // A read that is open when the compaction starts, and stays open until a query that started during the
        // compaction has been answered. The compaction can't switch over while it is open, so this establishes both
        // that reads are served mid-compaction and that a transaction spans the start of it.
        CountDownLatch heldReadOpen = new CountDownLatch(1);
        Future<Long> heldRead = pool.submit(() -> Txn.calculateRead(dsg, () -> {
            heldReadOpen.countDown();
            try {
                assertTrue(answeredDuringCompaction.await(60, TimeUnit.SECONDS),
                           "No query was answered during the compaction");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return (long) dsg.getDefaultGraph().size();
        }));
        assertTrue(heldReadOpen.await(10, TimeUnit.SECONDS));

        // when
        HttpResponse<String> compaction;
        try {
            compaction = HttpEnv.getDftHttpClient().sendAsync(
                    HttpRequest.newBuilder(URI.create(server.serverURL() + "$/compact/ds"))
                               .POST(HttpRequest.BodyPublishers.noBody())
                               .build(), HttpResponse.BodyHandlers.ofString()).get(90, TimeUnit.SECONDS);
        } finally {
            stop.set(true);
            for (Future<?> reader : readers) {
                reader.get(30, TimeUnit.SECONDS);
            }
        }

        // then
        assertEquals(200, compaction.statusCode(), compaction.body());
        assertTrue(compaction.body().contains("\"COMPACTED\""), compaction.body());
        assertTrue(Set.of(200, 503).containsAll(statuses.keySet()), () -> "Query statuses: " + statuses);
        assertTrue(statuses.getOrDefault(200, 0) > 0);
        assertEquals(TRIPLES, heldRead.get(10, TimeUnit.SECONDS));
        assertTrue(wrongAnswers.isEmpty(), () -> "Wrong answers: " + wrongAnswers.subList(0, 1));
        assertTrue(DatasetMaintenanceRegistry.ACTIVE_MAINTENANCE.isEmpty());
        assertEquals(TRIPLES, Txn.calculateRead(dsg, () -> dsg.getDefaultGraph().size()));
    }
}
