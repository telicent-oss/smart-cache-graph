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

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.utils.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;


@Testcontainers
class DeletionJobConsumerIntegrationTest extends KafkaIntegrationTestBase{

    private String topic;
    private static final String DISTRIBUTION_ID = "dist-integration-001";
    private static final String OTHER_DISTRIBUTION_ID = "dist-other-002";

    @Override
    protected String getBootstrapServers() {
        return kafka.getBootstrapServers();
    }

    @Override
    protected String getTopic() {
        return topic;
    }

    @Override
    protected KafkaProducer<Bytes, Bytes> getSetUpProducer() {
        return producer;
    }

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
        createTopic(topic);
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
                kafka.getBootstrapServers(), null, topic, DISTRIBUTION_ID, jobId)) {
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
                kafka.getBootstrapServers(), null, topic, DISTRIBUTION_ID, jobId)) {
            consumer.process(handled::add);
        }
        assertEquals(2, handled.size());
    }

    @Test
    void handlesEmptyTopicGracefully() {
        List<ConsumerRecord<Bytes, Bytes>> handled = new ArrayList<>();
        try (DeletionJobConsumer consumer = new DeletionJobConsumer(
                kafka.getBootstrapServers(), null, topic, DISTRIBUTION_ID, jobId)) {
            consumer.process(handled::add);
        }
        assertEquals(0, handled.size());
    }
}
