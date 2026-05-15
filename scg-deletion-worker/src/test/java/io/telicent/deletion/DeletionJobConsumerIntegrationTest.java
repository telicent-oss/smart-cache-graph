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

package io.telicent.deletion;

import io.telicent.smart.cache.sources.TelicentHeaders;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.BytesSerializer;
import org.apache.kafka.common.utils.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;


@Testcontainers
class DeletionJobConsumerIntegrationTest {

    private String topic;
    private static final String DISTRIBUTION_ID = "dist-integration-001";
    private static final String OTHER_DISTRIBUTION_ID = "dist-other-002";

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.7.0")
    );

    private KafkaProducer<Bytes, Bytes> producer;
    private String jobId;

    @BeforeEach
    void setUp() {
        jobId = "test-job-" + UUID.randomUUID();
        topic = "knowledge-" + UUID.randomUUID();
        createTopic();
        producer = createProducer();
    }

    @AfterEach
    void tearDown() {
        producer.close();
    }

    @Test
    void readsFromBeginningAndFiltersCorrectDistribution() throws Exception {
        publishRecord(DISTRIBUTION_ID, null, "triple one");
        publishRecord(OTHER_DISTRIBUTION_ID, null, "other distribution");
        publishRecord(DISTRIBUTION_ID, null, "triple two");

        List<ConsumerRecord<Bytes, Bytes>> handled = new ArrayList<>();
        try (DeletionJobConsumer consumer = new DeletionJobConsumer(
                kafka.getBootstrapServers(), topic, DISTRIBUTION_ID, jobId)) {
            consumer.process(handled::add);
        }
        assertEquals(2, handled.size());
    }

    @Test
    void stopsOnOwnJobId() throws Exception {
        publishRecord(DISTRIBUTION_ID, null, "triple one");
        publishRecord(DISTRIBUTION_ID, null, "triple two");
        publishRecord(DISTRIBUTION_ID, jobId, "our own delete patch");
        publishRecord(DISTRIBUTION_ID, null, "triple four");

        List<ConsumerRecord<Bytes, Bytes>> handled = new ArrayList<>();
        try (DeletionJobConsumer consumer = new DeletionJobConsumer(
                kafka.getBootstrapServers(), topic, DISTRIBUTION_ID, jobId)) {
            consumer.process(handled::add);
        }
        assertEquals(2, handled.size());
    }

    @Test
    void handlesEmptyTopicGracefully() {
        List<ConsumerRecord<Bytes, Bytes>> handled = new ArrayList<>();
        try (DeletionJobConsumer consumer = new DeletionJobConsumer(
                kafka.getBootstrapServers(), topic, DISTRIBUTION_ID, jobId)) {
            consumer.process(handled::add);
        }
        assertEquals(0, handled.size());
    }

    private void publishRecord(String distributionId, String deletionJobId, String payload)
            throws ExecutionException, InterruptedException {
        ProducerRecord<Bytes, Bytes> record = new ProducerRecord<>(
                topic,
                Bytes.wrap("key".getBytes(StandardCharsets.UTF_8)),
                Bytes.wrap(payload.getBytes(StandardCharsets.UTF_8))
        );
        if (distributionId != null) {
            record.headers().add(
                    TelicentHeaders.DISTRIBUTION_ID,
                    distributionId.getBytes(StandardCharsets.UTF_8)
            );
        }
        if (deletionJobId != null) {
            record.headers().add(
                    DeletionJobConsumer.DELETION_JOB_ID,
                    deletionJobId.getBytes(StandardCharsets.UTF_8)
            );
        }
        // synchronous — ensures ordering
        producer.send(record).get();
    }

    private KafkaProducer<Bytes, Bytes> createProducer() {
        Properties props = new Properties();
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, BytesSerializer.class.getName());
        props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, BytesSerializer.class.getName());
        return new KafkaProducer<>(props);
    }

    private void createTopic() {
        try (var admin = org.apache.kafka.clients.admin.AdminClient.create(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new org.apache.kafka.clients.admin.NewTopic(topic, 1, (short) 1)
            )).all().get();
        } catch (Exception e) {
            // topic may already exist, that's fine
        }
    }
}
