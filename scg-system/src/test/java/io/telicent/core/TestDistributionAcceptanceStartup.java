/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.telicent.core;

import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import io.telicent.smart.cache.distribution.lifecycle.DistributionLifecycleState;
import io.telicent.smart.cache.distribution.lifecycle.store.DistributionLifecycleStateStore;
import io.telicent.smart.cache.distribution.lifecycle.tracker.DistributionLifecycleTracker;
import io.telicent.smart.cache.payloads.RdfPayload;
import io.telicent.smart.cache.projectors.SinkException;
import io.telicent.smart.cache.sources.Header;
import io.telicent.smart.cache.sources.TelicentHeaders;
import io.telicent.smart.cache.sources.memory.SimpleEvent;
import org.apache.jena.fuseki.kafka.FKS;
import org.apache.jena.fuseki.kafka.FKRegistry;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.kafka.JenaKafkaException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.kafka.common.utils.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.telicent.smart.cache.distribution.lifecycle.config.DistributionLifecycleConfiguration.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestDistributionAcceptanceStartup {
    @TempDir
    Path directory;

    @AfterEach
    void reset() {
        Configurator.reset();
        DistributionLifecycleReadiness.getInstance().reset();
    }

    private void configure(boolean lifecycleEnabled) {
        Properties config = new Properties();
        config.setProperty(FMod_DistributionLifecycle.ROUTE_TO_NAMED_GRAPHS, "true");
        config.setProperty(DISTRIBUTION_LIFECYCLE_ENABLED, Boolean.toString(lifecycleEnabled));
        config.setProperty(DISTRIBUTION_LIFECYCLE_STATE_FILE, directory.resolve("state.json").toString());
        config.setProperty(FMod_DistributionLifecycle.DISTRIBUTION_LIFECYCLE_BOOTSTRAP_SERVERS, "test:9092");
        config.setProperty(DISTRIBUTION_LIFECYCLE_STARTUP_TIMEOUT, "PT2S");
        Configurator.setSingleSource(new PropertiesSource(config));
    }

    private Model connectorConfig(String... groupIds) {
        StringBuilder ttl = new StringBuilder("@prefix fk: <http://jena.apache.org/fuseki/kafka#> .\n");
        for (int i = 0; i < groupIds.length; i++) {
            ttl.append("""
                    [] a fk:Connector ; fk:bootstrapServers "test:9092" ;
                       fk:fusekiServiceName "/dataset" ; fk:topic "topic-%d" ;
                       fk:stateFile "%s" ; fk:groupId "%s" .
                    """.formatted(i, directory.resolve("offsets-" + i + ".json"), groupIds[i]));
        }
        return ModelFactory.createDefaultModel().read(new StringReader(ttl.toString()), null, "TTL");
    }

    private static FMod_DistributionLifecycle readyLifecycle(DistributionLifecycleStateStore store) {
        var lifecycle = mock(FMod_DistributionLifecycle.class);
        when(lifecycle.startupInitiated()).thenReturn(true);
        when(lifecycle.awaitIngestStateStore()).thenReturn(store);
        return lifecycle;
    }

    @Test
    void connectorsWaitForCatchUpRetry_andSinkUsesSuccessfulTrackersLiveStore() throws Exception {
        configure(true);
        var store = mock(DistributionLifecycleStateStore.class);
        var tracker = mock(DistributionLifecycleTracker.class);
        when(tracker.getStateStore()).thenReturn(store);
        CountDownLatch retryEntered = new CountDownLatch(1);
        CountDownLatch caughtUp = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        var lifecycle = new FMod_DistributionLifecycle() {
            @Override
            DistributionLifecycleTracker createTracker(FusekiServer server, String bootstrap, Properties properties,
                    String app, String topic, String dlq, String group, String stateFile) {
                if (attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("cannot make up to date decisions about distribution lifecycle");
                }
                retryEntered.countDown();
                try {
                    assertTrue(caughtUp.await(3, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return tracker;
            }
        };
        AtomicBoolean connectorsStarted = new AtomicBoolean();
        var kafka = new FMod_FusekiKafkaSCG(lifecycle) {
            @Override
            protected void startKafkaConnectors(FusekiServer server) {
                connectorsStarted.set(true);
            }
        };
        var server = mock(FusekiServer.class);

        // Fuseki order: every module's serverBeforeStarting(), the Kafka module first as in SmartCacheGraph.modules()
        kafka.serverBeforeStarting(server);
        lifecycle.serverBeforeStarting(server);
        assertFalse(connectorsStarted.get(), "Gated connectors must not start in serverBeforeStarting()");

        CompletableFuture<Void> afterStarting = CompletableFuture.runAsync(() -> kafka.serverAfterStarting(server));
        try {
            assertTrue(retryEntered.await(3, TimeUnit.SECONDS));
            assertFalse(connectorsStarted.get());
            assertFalse(afterStarting.isDone());
            assertFalse(DistributionLifecycleReadiness.getInstance().snapshot().ready());
            caughtUp.countDown();
            afterStarting.get(3, TimeUnit.SECONDS);
            assertTrue(connectorsStarted.get());
            assertEquals(2, attempts.get());

            String distribution = "http://example/distribution";
            when(store.getLifecycleState(distribution)).thenReturn(DistributionLifecycleState.Deleted);
            try (var sink = kafka.getSinkBuilder().apply(DatasetGraphFactory.createTxnMem())) {
                var event = new SimpleEvent<Bytes, RdfPayload>(
                        List.of(new Header(TelicentHeaders.DISTRIBUTION_ID, distribution)), null,
                        RdfPayload.of(DatasetGraphFactory.create()));
                assertThrows(SinkException.class, () -> sink.send(event));
                assertDoesNotThrow(() -> sink.send(event));
                // Change the shared store after sink construction: it must query live state, not a snapshot.
                when(store.getLifecycleState(distribution)).thenReturn(DistributionLifecycleState.Active);
                assertDoesNotThrow(() -> sink.send(event));
                verify(store, times(3)).getLifecycleState(distribution);
            }
            verify(store, never()).close();
        } finally {
            caughtUp.countDown();
            kafka.serverStopped(server);
            lifecycle.serverStopped(server);
        }
        verify(store).close();
        verify(tracker).close();
    }

    @Test
    void trackerFailureLeavesServerRunningButUnready() {
        configure(true);
        var lifecycle = new FMod_DistributionLifecycle() {
            @Override
            DistributionLifecycleTracker createTracker(FusekiServer server, String bootstrap, Properties properties,
                    String app, String topic, String dlq, String group, String stateFile) {
                throw new IllegalStateException("Invalid lifecycle configuration");
            }
        };
        AtomicBoolean connectorsStarted = new AtomicBoolean();
        var kafka = new FMod_FusekiKafkaSCG(lifecycle) {
            @Override
            protected void startKafkaConnectors(FusekiServer server) {
                connectorsStarted.set(true);
            }
        };
        var server = mock(FusekiServer.class);
        try {
            kafka.serverBeforeStarting(server);
            lifecycle.serverBeforeStarting(server);
            assertDoesNotThrow(() -> kafka.serverAfterStarting(server));
            assertFalse(connectorsStarted.get());
            assertFalse(DistributionLifecycleReadiness.getInstance().snapshot().ready());
            verify(server, never()).stop();
        } finally {
            kafka.serverStopped(server);
            lifecycle.serverStopped(server);
        }
    }

    @Test
    void unregisteredLifecycleModuleFailsStartupInsteadOfWaitingForever() {
        configure(true);
        var lifecycle = new FMod_DistributionLifecycle();
        var kafka = new FMod_FusekiKafkaSCG(lifecycle);
        var server = mock(FusekiServer.class);
        try {
            kafka.serverBeforeStarting(server);
            // lifecycle.serverBeforeStarting() deliberately not called: the module was never registered
            var error = assertThrows(IllegalStateException.class, () -> kafka.serverAfterStarting(server));
            assertTrue(error.getMessage().contains("FMod_DistributionLifecycle"));
            verify(server).stop();
        } finally {
            kafka.serverStopped(server);
            lifecycle.serverStopped(server);
        }
    }

    @Test
    void shutdownReleasesStartupWaiter() {
        var lifecycle = new FMod_DistributionLifecycle();
        var waiting = CompletableFuture.supplyAsync(lifecycle::awaitIngestStateStore);
        lifecycle.serverStopped(mock(FusekiServer.class));
        var error = assertThrows(ExecutionException.class, () -> waiting.get(1, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }

    @Test
    void shutdownDuringTrackerConstructionClosesLateStoreWithoutPublishingIt() throws Exception {
        configure(true);
        var store = mock(DistributionLifecycleStateStore.class);
        var tracker = mock(DistributionLifecycleTracker.class);
        when(tracker.getStateStore()).thenReturn(store);
        CountDownLatch entered = new CountDownLatch(1);
        var lifecycle = new FMod_DistributionLifecycle() {
            @Override
            DistributionLifecycleTracker createTracker(FusekiServer server, String bootstrap, Properties properties,
                    String app, String topic, String dlq, String group, String stateFile) {
                entered.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    // Simulate a constructor returning a resource as shutdown interrupts startup.
                }
                return tracker;
            }
        };
        var server = mock(FusekiServer.class);
        lifecycle.serverBeforeStarting(server);
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
        } finally {
            lifecycle.serverStopped(server);
        }
        assertThrows(IllegalStateException.class, lifecycle::awaitIngestStateStore);
        verify(tracker).close();
        verify(store).close();
    }

    @Test
    void slowCatchUpHasNoAdditionalIngestStartupDeadline() {
        Properties config = new Properties();
        config.setProperty(DISTRIBUTION_LIFECYCLE_STARTUP_TIMEOUT, "PT0.001S");
        Configurator.setSingleSource(new PropertiesSource(config));
        var lifecycle = new FMod_DistributionLifecycle();
        var waiting = CompletableFuture.supplyAsync(lifecycle::awaitIngestStateStore);
        try {
            assertThrows(TimeoutException.class, () -> waiting.get(50, TimeUnit.MILLISECONDS));
        } finally {
            lifecycle.serverStopped(mock(FusekiServer.class));
        }
        assertThrows(ExecutionException.class, () -> waiting.get(1, TimeUnit.SECONDS));
    }

    @Test
    void httpStartsWhileCatchUpIsPending_andShutdownCancelsDeferredIngest() throws Exception {
        configure(true);
        CountDownLatch trackerEntered = new CountDownLatch(1);
        AtomicBoolean connectorsStarted = new AtomicBoolean();
        var lifecycle = new FMod_DistributionLifecycle() {
            @Override
            DistributionLifecycleTracker createTracker(FusekiServer server, String bootstrap, Properties properties,
                    String app, String topic, String dlq, String group, String stateFile) {
                trackerEntered.countDown();
                try {
                    new CountDownLatch(1).await();
                    throw new AssertionError("Unexpected release");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        };
        CountDownLatch httpUp = new CountDownLatch(1);
        var kafka = new FMod_FusekiKafkaSCG(lifecycle) {
            @Override
            public void serverAfterStarting(FusekiServer server) {
                // Fuseki only calls this once Jetty is serving HTTP
                httpUp.countDown();
                super.serverAfterStarting(server);
            }

            @Override
            protected void startKafkaConnectors(FusekiServer server) {
                connectorsStarted.set(true);
            }
        };
        // Same module order as SmartCacheGraph.modules(): Kafka before lifecycle
        var server = FusekiServer.create().port(0).add("/dataset", DatasetGraphFactory.createTxnMem())
                                 .fusekiModules(FusekiModules.create(kafka, lifecycle)).build();
        // start() blocks in serverAfterStarting() until catch-up, so run it as MainSmartCacheGraph would
        CompletableFuture<FusekiServer> starting = CompletableFuture.supplyAsync(server::start);
        try {
            assertTrue(trackerEntered.await(2, TimeUnit.SECONDS));
            assertTrue(httpUp.await(2, TimeUnit.SECONDS));
            var request = HttpRequest.newBuilder(URI.create(server.datasetURL("/dataset") + "?query=ASK%7B%7D"))
                                     .timeout(Duration.ofSeconds(2)).GET().build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertFalse(DistributionLifecycleReadiness.getInstance().snapshot().ready());
            assertFalse(connectorsStarted.get());
            assertFalse(starting.isDone());
        } finally {
            server.stop();
        }
        // Shutdown releases the blocked start() without starting connectors
        assertDoesNotThrow(() -> starting.get(5, TimeUnit.SECONDS));
        assertFalse(connectorsStarted.get());
        assertThrows(IllegalStateException.class, lifecycle::awaitIngestStateStore);
    }

    @Test
    void ungatedConnectorsStartInServerBeforeStarting() {
        configure(false);
        var kafka = new FMod_FusekiKafkaSCG(mock(FMod_DistributionLifecycle.class));
        var server = mock(FusekiServer.class);
        var model = connectorConfig("group-a");
        try (var fks = mockStatic(FKS.class)) {
            kafka.prepare(FusekiServer.create(), Set.of(), model);
            kafka.serverBeforeStarting(server);
            fks.verify(() -> FKS.addConnectorToServer(any(), eq(server), any(), any()), times(1));
            kafka.serverAfterStarting(server);
            fks.verify(() -> FKS.addConnectorToServer(any(), any(), any(), any()), times(1));
        } finally {
            kafka.serverStopped(server);
            model.close();
        }
    }

    @Test
    void gatedConnectorsStartInServerAfterStartingFromParentsPreparedConnectors() {
        configure(true);
        var kafka = new FMod_FusekiKafkaSCG(readyLifecycle(mock(DistributionLifecycleStateStore.class)));
        var server = mock(FusekiServer.class);
        var model = connectorConfig("group-a");
        try (var fks = mockStatic(FKS.class)) {
            kafka.prepare(FusekiServer.create(), Set.of(), model);
            kafka.serverBeforeStarting(server);
            fks.verifyNoInteractions();
            // Same thread as prepare(), so the parent's per-thread connector list is still populated
            kafka.serverAfterStarting(server);
            fks.verify(() -> FKS.addConnectorToServer(any(), eq(server), any(), any()), times(1));
        } finally {
            kafka.serverStopped(server);
            model.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void duplicateConsumerGroupsFailStartup(boolean lifecycleEnabled) {
        configure(lifecycleEnabled);
        var kafka = new FMod_FusekiKafkaSCG(readyLifecycle(mock(DistributionLifecycleStateStore.class)));
        var server = mock(FusekiServer.class);
        var model = connectorConfig("duplicate-group", "duplicate-group");
        try (var ignored = mockStatic(FKS.class)) {
            kafka.prepare(FusekiServer.create(), Set.of(), model);
            JenaKafkaException error;
            if (lifecycleEnabled) {
                kafka.serverBeforeStarting(server);
                error = assertThrows(JenaKafkaException.class, () -> kafka.serverAfterStarting(server));
                // HTTP is already up at this point, so the module stops the server rather than leave it half started
                verify(server).stop();
            } else {
                error = assertThrows(JenaKafkaException.class, () -> kafka.serverBeforeStarting(server));
            }
            assertTrue(error.getMessage().contains("duplicate-group"));
        } finally {
            kafka.serverStopped(server);
            model.close();
        }
    }

    @Test
    void trackerUsingDefaultStateFileStillControlsReadiness() {
        Properties config = new Properties();
        config.setProperty(FMod_DistributionLifecycle.ROUTE_TO_NAMED_GRAPHS, "true");
        config.setProperty(DISTRIBUTION_LIFECYCLE_ENABLED, "true");
        Configurator.setSingleSource(new PropertiesSource(config));
        var lifecycle = new FMod_DistributionLifecycle();
        var server = mock(FusekiServer.class);
        try {
            // No bootstrap servers: the tracker fails, even though no explicit state-file path was supplied.
            lifecycle.serverBeforeStarting(server);
            assertFalse(DistributionLifecycleReadiness.getInstance().snapshot().ready());
        } finally {
            lifecycle.serverStopped(server);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void moduleClosesExternalStateStoreEvenWhenParentShutdownFails(boolean parentShutdownFails) throws Exception {
        configure(false);
        String distribution = "http://example/distribution";
        Files.writeString(directory.resolve("state.json"),
                          "{\"distributions\":{\"" + distribution + "\":\"Active\"}}");
        var kafka = new FMod_FusekiKafkaSCG();
        var server = mock(FusekiServer.class);
        var registry = FKRegistry.get();
        var model = connectorConfig("external-store-group");
        try (var ignored = mockStatic(FKS.class)) {
            kafka.prepare(FusekiServer.create(), Set.of(), model);
            var connector = registry.getConnectorDescriptor("topic-0");
            try (var sink = kafka.getSinkBuilder().apply(DatasetGraphFactory.createTxnMem());
                 var otherSink = kafka.getSinkBuilder().apply(DatasetGraphFactory.createTxnMem())) {
                var event = new SimpleEvent<Bytes, RdfPayload>(
                        List.of(new Header(TelicentHeaders.DISTRIBUTION_ID, distribution)), null,
                        RdfPayload.of(DatasetGraphFactory.create()));
                assertDoesNotThrow(() -> sink.send(event), "The store must remain open while sinks are active");
                if (parentShutdownFails) {
                    try (var mockedRegistry = mockStatic(FKRegistry.class)) {
                        mockedRegistry.when(FKRegistry::get).thenThrow(new IllegalStateException("Parent shutdown failed"));
                        var error = assertThrows(IllegalStateException.class, () -> kafka.serverStopped(server));
                        assertEquals("Parent shutdown failed", error.getMessage());
                    }
                } else {
                    kafka.serverStopped(server);
                }
                assertEquals("State store is closed",
                             assertThrows(IllegalStateException.class, () -> sink.send(event)).getMessage());
                assertEquals("State store is closed",
                             assertThrows(IllegalStateException.class, () -> otherSink.send(event)).getMessage());
                assertThrows(IllegalStateException.class, kafka::getSinkBuilder,
                             "A stopped module must not reopen the external store");
            } finally {
                registry.unregister(connector.getTopics(), connector);
            }
        } finally {
            model.close();
        }
    }
}
