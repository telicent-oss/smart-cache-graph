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
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.utils.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;


class DeletionJobConsumerTest {

    private static final String TOPIC = "knowledge";
    private static final String DISTRIBUTION_ID = "dist-abc-123";
    private static final String OTHER_DISTRIBUTION_ID = "dist-xyz-999";
    private static final String JOB_ID = "test-job-001";
    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);

    private MockConsumer<Bytes, Bytes> mockConsumer;

    @BeforeEach
    void setUp() {
        mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        mockConsumer.updatePartitions(TOPIC, List.of(
                new PartitionInfo(TOPIC, 0, null, null, null)
        ));
        mockConsumer.updateBeginningOffsets(Map.of(PARTITION, 0L));
        mockConsumer.assign(List.of(PARTITION));
    }

    @Test
    void handlerIsCalledForMatchingDistribution() {
        addRecord(0L, DISTRIBUTION_ID, null, "triple data");
        addEndOfTopic(1L);

        List<ConsumerRecord<Bytes, Bytes>> handled = new ArrayList<>();
        try (DeletionJobConsumer consumer = new DeletionJobConsumer(
                mockConsumer, TOPIC, DISTRIBUTION_ID, JOB_ID)) {
            consumer.process(handled::add);
        }

        assertEquals(1, handled.size());    }

    @Test
    void handlerIsNotCalledForDifferentDistribution() {
        addRecord(0L, OTHER_DISTRIBUTION_ID, null, "other triple data");
        addEndOfTopic(1L);

        List<ConsumerRecord<Bytes, Bytes>> handled = new ArrayList<>();
        try (DeletionJobConsumer consumer = new DeletionJobConsumer(
                mockConsumer, TOPIC, DISTRIBUTION_ID, JOB_ID)) {
            consumer.process(handled::add);
        }
        assertEquals(0, handled.size());
    }

    @Test
    void stopsAndDoesNotCallHandlerWhenOwnJobIdEncountered() {
        addRecord(0L, DISTRIBUTION_ID, null, "triple one");
        addRecord(1L, DISTRIBUTION_ID, null, "triple two");
        addRecord(2L, DISTRIBUTION_ID, JOB_ID, "our own event — stop here");
        addRecord(3L, DISTRIBUTION_ID, null, "triple four — must not be processed");
        addEndOfTopic(4L);

        List<ConsumerRecord<Bytes, Bytes>> handled = new ArrayList<>();
        try (DeletionJobConsumer consumer = new DeletionJobConsumer(
                mockConsumer, TOPIC, DISTRIBUTION_ID, JOB_ID)) {
            consumer.process(handled::add);
        }
        assertEquals(2, handled.size());
    }

    @Test
    void handlerIsNotCalledForRecordWithNoDistributionId() {
        addRecord(0L, null, null, "record with no distribution");
        addEndOfTopic(1L);

        List<ConsumerRecord<Bytes, Bytes>> handled = new ArrayList<>();
        try (DeletionJobConsumer consumer = new DeletionJobConsumer(
                mockConsumer, TOPIC, DISTRIBUTION_ID, JOB_ID)) {
            consumer.process(handled::add);
        }
        assertEquals(0, handled.size());
    }

    @Test
    void handlesEmptyTopicGracefully() {
        addEndOfTopic(0L);

        List<ConsumerRecord<Bytes, Bytes>> handled = new ArrayList<>();
        try (DeletionJobConsumer consumer = new DeletionJobConsumer(
                mockConsumer, TOPIC, DISTRIBUTION_ID, JOB_ID)) {
            consumer.process(handled::add);
        }
        assertEquals(0, handled.size());
    }

    @Test
    void handlesMixOfMatchingAndNonMatchingRecords() {
        addRecord(0L, DISTRIBUTION_ID, null, "match one");
        addRecord(1L, OTHER_DISTRIBUTION_ID, null, "no match");
        addRecord(2L, DISTRIBUTION_ID, null, "match two");
        addRecord(3L, null, null, "no distribution");
        addRecord(4L, DISTRIBUTION_ID, null, "match three");
        addEndOfTopic(5L);

        List<ConsumerRecord<Bytes, Bytes>> handled = new ArrayList<>();
        try (DeletionJobConsumer consumer = new DeletionJobConsumer(
                mockConsumer, TOPIC, DISTRIBUTION_ID, JOB_ID)) {
            consumer.process(handled::add);
        }
        assertEquals(3, handled.size());
    }

    private void addRecord(long offset, String distributionId, String deletionJobId, String payload) {
        ConsumerRecord<Bytes, Bytes> record = new ConsumerRecord<>(
                TOPIC, 0, offset,
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
        mockConsumer.addRecord(record);
    }

    // simulates the topic being exhausted at the given offset
    private void addEndOfTopic(long offset) {
        mockConsumer.updateEndOffsets(Map.of(PARTITION, offset));
    }
}
