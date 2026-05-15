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
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.BytesDeserializer;
import org.apache.kafka.common.utils.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class DeletionJobConsumer implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeletionJobConsumer.class);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_EMPTY_POLLS = 3;

    //TODO
    // move to TelicentHeaders
    static final String DELETION_JOB_ID = "Deletion-Job-Id";

    private final Consumer<Bytes, Bytes> consumer;
    private final String topic;
    private final String distributionId;
    private final String jobId;

    public DeletionJobConsumer(String bootstrapServers, String topic, String distributionId, String jobId) {
        this.topic = topic;
        this.distributionId = distributionId;
        this.jobId = jobId;
        this.consumer = createConsumer(bootstrapServers);
        seekToBeginning();
    }

    /**
     * Test constructor — accepts any Consumer implementation.
     */
    DeletionJobConsumer(Consumer<Bytes, Bytes> consumer, String topic,
                        String distributionId, String jobId) {
        this.topic = topic;
        this.distributionId = distributionId;
        this.jobId = jobId;
        this.consumer = consumer;
        seekToBeginning();
    }

    private KafkaConsumer<Bytes, Bytes> createConsumer(String bootstrapServers) {
        Properties props = new Properties();
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "deletion-worker-" + UUID.randomUUID());
        props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");
        props.setProperty(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG,
                Integer.toString(10 * 1024 * 1024));
        props.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                BytesDeserializer.class.getName());
        props.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                BytesDeserializer.class.getName());
        props.setProperty(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");
        return new KafkaConsumer<>(props);
    }


    /**
     * Finds the offset 0 immediately and deterministically, without waiting for a rebalance. Assigns partitions manually.
     */
    private void seekToBeginning() {
        List<TopicPartition> partitions = consumer.partitionsFor(topic)
                .stream()
                .map(info -> new TopicPartition(info.topic(), info.partition()))
                .toList();
        consumer.assign(partitions);
        consumer.seekToBeginning(partitions);
        LOGGER.info("[{}] Assigned {} partition(s) on topic '{}', seeked to beginning",
                jobId, partitions.size(), topic);
    }

    /**
     * Processes records from the topic, calling the handler for each relevant record.
     * Returns when the job's own events are encountered or the topic
     * is exhausted.
     *
     * @param handler decides what is being done with the records.
     */
    public void process(RecordHandler handler) {
        int emptyPolls = 0;
        while (true) {
            ConsumerRecords<Bytes, Bytes> records = consumer.poll(POLL_TIMEOUT);
            if (records.isEmpty()) {
                emptyPolls++;
                LOGGER.debug("[{}] Empty poll {}/{}", jobId, emptyPolls, MAX_EMPTY_POLLS);
                if (emptyPolls >= MAX_EMPTY_POLLS) {
                    LOGGER.info("[{}] Topic exhausted after {} consecutive empty polls, job complete",
                            jobId, MAX_EMPTY_POLLS);
                    return;
                }
                continue;
            }
            emptyPolls = 0;

            for (ConsumerRecord<Bytes, Bytes> record : records) {
                String incomingJobId = headerValue(record, /*TelicentHeaders.DELETION_JOB_ID*/ DELETION_JOB_ID);
                if (jobId.equals(incomingJobId)) {
                    LOGGER.info("[{}] Encountered own events at offset {}, job complete",
                            jobId, record.offset());
                    return;
                }

                //TODO
                // not sure if necessary, the distributionId is different every time
                String existingJobId = headerValue(record, DELETION_JOB_ID);
                if (existingJobId != null && !existingJobId.equals(jobId)) {
                    // This is a delete patch from a previous job — skip it
                    continue;
                }

                String recordDistributionId = headerValue(record, TelicentHeaders.DISTRIBUTION_ID);
                if (!distributionId.equals(recordDistributionId)) {
                    continue;
                }
                LOGGER.debug("[{}] Found matching record at offset {} for distribution '{}'",
                        jobId, record.offset(), distributionId);

                handler.handle(record);
            }
        }
    }

    /**
     * Helper method that extracts a String header value from a record's given header.
     * @param record the record with the header
     * @param headerName the name of the header
     * @return String header value
     */
    private String headerValue(ConsumerRecord<?, ?> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header == null) return null;
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        consumer.close();
        LOGGER.info("[{}] Consumer closed", jobId);
    }

    /**
     * A functional interface that enables the callback/strategy pattern in {@link DeletionJobConsumer#process(RecordHandler)}
     * Applies a given operation to a ConsumerRecord.
     */
    @FunctionalInterface
    public interface RecordHandler {
        void handle(ConsumerRecord<Bytes, Bytes> record);
    }
}
