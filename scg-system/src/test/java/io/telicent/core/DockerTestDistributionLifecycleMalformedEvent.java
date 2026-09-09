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
import io.telicent.smart.cache.distribution.lifecycle.events.LifecycleAction;
import io.telicent.smart.cache.distribution.lifecycle.events.utils.LifecycleStateTransition;
import io.telicent.smart.cache.payloads.Envelope;
import io.telicent.smart.cache.payloads.LazyEnvelope;
import io.telicent.smart.cache.payloads.Metadata;
import io.telicent.smart.cache.projectors.Sink;
import io.telicent.smart.cache.sources.Event;
import io.telicent.smart.cache.sources.EventSource;
import io.telicent.smart.cache.sources.TelicentHeaders;
import io.telicent.smart.cache.sources.kafka.BasicKafkaTestCluster;
import io.telicent.smart.cache.sources.kafka.KafkaEventSource;
import io.telicent.smart.cache.sources.kafka.KafkaTestCluster;
import io.telicent.smart.cache.sources.kafka.serializers.LazyEnvelopeDeserializer;
import io.telicent.smart.cache.sources.kafka.serializers.LazyEnvelopeSerializer;
import io.telicent.smart.cache.sources.kafka.sinks.KafkaSink;
import io.telicent.smart.cache.sources.memory.SimpleEvent;
import org.apache.kafka.common.serialization.UUIDDeserializer;
import org.apache.kafka.common.serialization.UUIDSerializer;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static io.telicent.smart.cache.distribution.lifecycle.config.DistributionLifecycleConfiguration.DISTRIBUTION_LIFECYCLE_ENABLED;
import static io.telicent.smart.cache.distribution.lifecycle.config.DistributionLifecycleConfiguration.DISTRIBUTION_LIFECYCLE_STATE_FILE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for malformed historic lifecycle records.  Graph must dead-letter the bad record and continue
 * tracking subsequent valid records rather than failing readiness during a Kafka replay.
 */
class DockerTestDistributionLifecycleMalformedEvent {

    private static final String APPLICATION = "smart-cache-graph-malformed-event-test";
    private static final String TOPIC = "scg-lifecycle-malformed-event";
    private static final String DLQ_TOPIC = TOPIC + ".dlq";
    private static final AtomicInteger CONSUMER_ID = new AtomicInteger();
    private static final KafkaTestCluster KAFKA = new BasicKafkaTestCluster();

    private FMod_DistributionLifecycle module;
    private FusekiServer server;
    private Path stateFile;

    @BeforeAll
    static void startKafka() {
        KAFKA.setup();
        KAFKA.createTopic(TOPIC);
        KAFKA.createTopic(DLQ_TOPIC);
    }

    @AfterAll
    static void stopKafka() {
        KAFKA.teardown();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (this.module != null) {
            this.module.serverStopped(this.server);
        }
        if (this.server != null) {
            this.server.stop();
        }
        if (this.stateFile != null) {
            Files.deleteIfExists(this.stateFile);
        }
        DistributionLifecycleReadiness.getInstance().reset();
        Configurator.reset();
        KAFKA.resetTopic(TOPIC);
        KAFKA.resetTopic(DLQ_TOPIC);
    }

    @Test
    void malformedLifecycleRecord_isDeadLetteredAndDoesNotStopGraphLifecycleTracking() throws Exception {
        // Given a Graph instance consuming an isolated lifecycle topic with a configured DLQ.
        this.stateFile = Files.createTempFile("scg-distribution-lifecycle", ".json");
        Files.deleteIfExists(this.stateFile);
        configureLifecycle();
        try (Sink<Event<UUID, LazyEnvelope>> sink = createSink(TOPIC)) {
            // Populate Kafka before Graph starts: initial replay must survive poisoned history.
            UUID before = sendAction(sink, "before", DistributionLifecycleState.Unregistered,
                                     DistributionLifecycleState.Registered);
            UUID malformed = sendMalformedAction(sink);
            UUID after = sendAction(sink, "after", DistributionLifecycleState.Unregistered,
                                    DistributionLifecycleState.Registered);

            this.server = FusekiServer.create().port(0).add("/knowledge", DatasetGraphFactory.createTxnMem()).build();
            this.server.start();
            this.module = new FMod_DistributionLifecycle();
            this.module.serverBeforeStarting(this.server);

            // Then valid actions either side of the malformed one are acknowledged and Graph stays ready.
            Awaitility.await("Graph lifecycle tracker to process valid records")
                      .atMost(Duration.ofSeconds(15))
                      .untilAsserted(() -> {
                          assertTrue(this.module.isRunning());
                          assertEquals(DistributionLifecycleReadiness.State.READY,
                                       DistributionLifecycleReadiness.getInstance().snapshot().state());
                      });

            EventSource<UUID, LazyEnvelope> dlq = createSource(DLQ_TOPIC);
            try {
                Event<UUID, LazyEnvelope> deadLetter = dlq.poll(Duration.ofSeconds(10));
                assertNotNull(deadLetter, "The malformed record should be sent to Graph's lifecycle DLQ");
                assertEquals(malformed, deadLetter.key());
                assertTrue(deadLetter.lastHeader(TelicentHeaders.DEAD_LETTER_REASON)
                                     .contains("LifecycleEventRejectedException"));
                assertEquals("io.telicent.smart.cache.distribution.lifecycle.LifecycleEventRejectedException",
                             deadLetter.lastHeader(TelicentHeaders.DEAD_LETTER_EXCEPTION_CLASS));
                assertNotNull(deadLetter.lastHeader(TelicentHeaders.DEAD_LETTER_SOURCE_TOPIC));
                assertNotNull(deadLetter.lastHeader(TelicentHeaders.DEAD_LETTER_SOURCE_PARTITION));
                assertNotNull(deadLetter.lastHeader(TelicentHeaders.DEAD_LETTER_SOURCE_OFFSET));
            } finally {
                dlq.close();
            }

            // Closing Graph flushes its application state so the assertion observes the persisted state file.
            this.module.serverStopped(this.server);
            this.module = null;
            String state = Files.readString(this.stateFile);
            assertTrue(state.contains(before.toString()), "The first valid action should be persisted");
            assertTrue(state.contains(after.toString()), "The valid action after the malformed record should be persisted");
        }
    }

    private void configureLifecycle() {
        Properties properties = new Properties();
        properties.setProperty(DISTRIBUTION_LIFECYCLE_ENABLED, "true");
        properties.setProperty(FMod_DistributionLifecycle.ROUTE_TO_NAMED_GRAPHS, "true");
        properties.setProperty(DISTRIBUTION_LIFECYCLE_STATE_FILE, this.stateFile.toString());
        properties.setProperty(FMod_DistributionLifecycle.DISTRIBUTION_LIFECYCLE_APP_ID, APPLICATION);
        properties.setProperty(FMod_DistributionLifecycle.DISTRIBUTION_LIFECYCLE_BOOTSTRAP_SERVERS,
                               KAFKA.getBootstrapServers());
        properties.setProperty(FMod_DistributionLifecycle.DISTRIBUTION_LIFECYCLE_TOPIC, TOPIC);
        properties.setProperty(FMod_DistributionLifecycle.DISTRIBUTION_LIFECYCLE_DLQ_TOPIC, DLQ_TOPIC);
        properties.setProperty(FMod_DistributionLifecycle.DISTRIBUTION_LIFECYCLE_CONSUMER_GROUP,
                               APPLICATION + "-" + CONSUMER_ID.incrementAndGet());
        properties.setProperty("DISTRIBUTION_LIFECYCLE_STARTUP_TIMEOUT", "PT5S");
        Configurator.setSingleSource(new PropertiesSource(properties));
    }

    private Sink<Event<UUID, LazyEnvelope>> createSink(String topic) {
        return KafkaSink.<UUID, LazyEnvelope>create()
                         .bootstrapServers(KAFKA.getBootstrapServers())
                         .topic(topic)
                         .producerConfig(KAFKA.getClientProperties())
                         .keySerializer(UUIDSerializer.class)
                         .valueSerializer(LazyEnvelopeSerializer.class)
                         .build();
    }

    private EventSource<UUID, LazyEnvelope> createSource(String topic) {
        return KafkaEventSource.<UUID, LazyEnvelope>create()
                               .bootstrapServers(KAFKA.getBootstrapServers())
                               .topic(topic)
                               .consumerGroup(APPLICATION + "-dlq-" + CONSUMER_ID.incrementAndGet())
                               .consumerConfig(KAFKA.getClientProperties())
                               .keyDeserializer(UUIDDeserializer.class)
                               .valueDeserializer(LazyEnvelopeDeserializer.class)
                               .fromBeginning()
                               .build();
    }

    private UUID sendAction(Sink<Event<UUID, LazyEnvelope>> sink, String distributionId,
                            DistributionLifecycleState from, DistributionLifecycleState to) {
        UUID eventId = UUID.randomUUID();
        LifecycleAction action = LifecycleAction.builder()
                                                .eventId(eventId)
                                                .distributionId(distributionId)
                                                .datasetId("dataset")
                                                .user("test@test.org")
                                                .state(new LifecycleStateTransition(from, to))
                                                .build();
        Envelope envelope = Envelope.create()
                                    .id(UUID.randomUUID())
                                    .metadata(Metadata.create()
                                                      .generatedBy("tests")
                                                      .generatedAt(java.util.Date.from(Instant.now()))
                                                      .generatorVersion("1.0")
                                                      .documentFormat(LifecycleAction.DOCUMENT_FORMAT)
                                                      .build())
                                    .bodyFrom(action)
                                    .build();
        sink.send(new SimpleEvent<>(List.of(), eventId, LazyEnvelope.of(envelope)));
        return eventId;
    }

    private UUID sendMalformedAction(Sink<Event<UUID, LazyEnvelope>> sink) {
        UUID eventId = UUID.randomUUID();
        String payload = """
                {"id":"%s","metadata":{"generatedBy":"tests","generatedAt":"2026-09-07T00:00:00Z","generatorVersion":"1.0","documentFormat":"distribution-lifecycle-action/v1"},"body":{"eventId":"%s","distributionId":"malformed","datasetId":"dataset","state":{"from":"Unregistered","to":"Garbage"},"user":"test@test.org"}}
                """.formatted(UUID.randomUUID(), eventId);
        sink.send(new SimpleEvent<>(List.of(), eventId, LazyEnvelope.of(payload.getBytes(StandardCharsets.UTF_8))));
        return eventId;
    }
}
