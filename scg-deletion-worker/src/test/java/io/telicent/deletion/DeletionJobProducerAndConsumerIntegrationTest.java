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
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.utils.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static io.telicent.deletion.DeletionWorkerConstants.*;
import static org.junit.jupiter.api.Assertions.*;


@Testcontainers
class DeletionJobProducerAndConsumerIntegrationTest extends KafkaIntegrationTestBase{

    private String topic;
    private RDFPatchInverter rdfPatchInverter;
    private KafkaProducer<Bytes, Bytes> setUpProducer;
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
        return setUpProducer;
    }

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.7.0")
    );

    private String jobId;

    @BeforeEach
    void setUp() {
        jobId = "test-job-" + UUID.randomUUID();
        topic = "knowledge-" + UUID.randomUUID();
        createTopic(topic);
        rdfPatchInverter = new RDFPatchInverter();
        setUpProducer = createProducer();
    }

    @AfterEach
    void tearDown() {
        setUpProducer.close();
    }

    @Test
    void consumerReadsFromBeginningProducerSendsDeletes() {
        try {
            publishRecord(DISTRIBUTION_ID, null, nquadsPayload("sub1", "triple1"));
            publishRecord(OTHER_DISTRIBUTION_ID, null, nquadsPayload("sub2", "triple2"));
            publishRecord(DISTRIBUTION_ID, null, nquadsPayload("sub3", "triple3"));
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        List<ConsumerRecord<Bytes, Bytes>> handled = new ArrayList<>();
        try (DeletionJobConsumer consumer = new DeletionJobConsumer(kafka.getBootstrapServers(), null,  topic, DISTRIBUTION_ID, jobId))
        {
            consumer.process(handled::add);
        }

        try (DeletionJobProducer producer = new DeletionJobProducer(kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            for (ConsumerRecord<Bytes, Bytes> record : handled) {
                producer.sendDeletePatch(record);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ConsumerRecords<Bytes, Bytes> records;
        try (KafkaConsumer<Bytes, Bytes> verifier = createConsumer()) {
            verifier.assign(List.of(new TopicPartition(topic, 0)));
            verifier.seekToBeginning(List.of(new TopicPartition(topic, 0)));

            records = verifier.poll(Duration.ofSeconds(20));
        }
        assertEquals(5, records.count());

        List<ConsumerRecord<Bytes, Bytes>> deletePatches = new ArrayList<>();
        for (ConsumerRecord<Bytes, Bytes> record : records) {
            Header ct = record.headers().lastHeader(TelicentHeaders.CONTENT_TYPE);
            if (ct != null && new String(ct.value(), StandardCharsets.UTF_8)
                    .equals("application/rdf-patch")) {
                deletePatches.add(record);
            }
        }
        assertEquals(2, deletePatches.size());

        for (ConsumerRecord<Bytes, Bytes> patch : deletePatches) {
            Header jobIdHeader = patch.headers().lastHeader(DELETION_JOB_ID);
            assertNotNull(jobIdHeader);
            assertEquals(jobId, new String(jobIdHeader.value(), StandardCharsets.UTF_8));

            Header distId = patch.headers().lastHeader(TelicentHeaders.DISTRIBUTION_ID);
            assertNotNull(distId);
            assertEquals(DISTRIBUTION_ID + DELETION_JOB_SUFFIX, new String(distId.value(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void noDeletePatchesSentWhenNoRecordsMatchDistribution() throws Exception {
        publishRecord(OTHER_DISTRIBUTION_ID, null, nquadsPayload("1", "Alice"));
        publishRecord(OTHER_DISTRIBUTION_ID, null, nquadsPayload("2", "Bob"));

        List<ConsumerRecord<Bytes, Bytes>> handled = new ArrayList<>();
        try (DeletionJobConsumer consumer = new DeletionJobConsumer(
                kafka.getBootstrapServers(), null, topic, DISTRIBUTION_ID, jobId)) {
            consumer.process(handled::add);
        }

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            for (ConsumerRecord<Bytes, Bytes> record : handled) {
                producer.sendDeletePatch(record);
            }
        }

        List<ConsumerRecord<Bytes, Bytes>> allRecords = readAllRecords(2);

        List<ConsumerRecord<Bytes, Bytes>> deletePatches = filterDeletePatches(allRecords);
        assertEquals(0, deletePatches.size());
        assertEquals(2, allRecords.size());
    }

    @Test
    void noDeletePatchesSentWhenTopicIsEmpty() {
        List<ConsumerRecord<Bytes, Bytes>> handled = new ArrayList<>();
        try (DeletionJobConsumer consumer = new DeletionJobConsumer(
                kafka.getBootstrapServers(), null, topic, DISTRIBUTION_ID, jobId)) {
            consumer.process(handled::add);
        }

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            for (ConsumerRecord<Bytes, Bytes> record : handled) {
                producer.sendDeletePatch(record);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertEquals(0, handled.size());
        List<ConsumerRecord<Bytes, Bytes>> allRecords = readAllRecords(0);
        assertEquals(0, allRecords.size());
    }

    @Test
    void deletePatchesHaveCorrectHeaders() throws Exception {
        publishRecord(DISTRIBUTION_ID, null, nquadsPayload("1", "Alice"));
        publishRecord(DISTRIBUTION_ID, null, nquadsPayload("2", "Bob"));

        List<ConsumerRecord<Bytes, Bytes>> handled = new ArrayList<>();
        try (DeletionJobConsumer consumer = new DeletionJobConsumer(
                kafka.getBootstrapServers(), null, topic, DISTRIBUTION_ID, jobId)) {
            consumer.process(handled::add);
        }

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            for (ConsumerRecord<Bytes, Bytes> record : handled) {
                producer.sendDeletePatch(record);
            }
        }

        List<ConsumerRecord<Bytes, Bytes>> allRecords = readAllRecords(4);
        List<ConsumerRecord<Bytes, Bytes>> deletePatches = filterDeletePatches(allRecords);

        assertEquals(2, deletePatches.size());

        for (ConsumerRecord<Bytes, Bytes> patch : deletePatches) {
            Header ct = patch.headers().lastHeader(TelicentHeaders.CONTENT_TYPE);
            assertNotNull(ct);
            assertEquals("application/rdf-patch",
                    new String(ct.value(), StandardCharsets.UTF_8));

            Header jobIdHeader = patch.headers().lastHeader(DELETION_JOB_ID);
            assertNotNull(jobIdHeader);
            assertEquals(jobId, new String(jobIdHeader.value(), StandardCharsets.UTF_8));

            Header distId = patch.headers().lastHeader(TelicentHeaders.DISTRIBUTION_ID);
            assertNotNull(distId);
            assertEquals(DISTRIBUTION_ID + DELETION_JOB_SUFFIX, new String(distId.value(), StandardCharsets.UTF_8));

            Header operation = patch.headers().lastHeader(OPERATION);
            assertNotNull(operation);
            assertEquals("delete", new String(operation.value(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void deletePatchPayloadsAreValidRdfPatches() throws Exception {
        publishRecord(DISTRIBUTION_ID, null, nquadsPayload("1", "Alice"));

        List<ConsumerRecord<Bytes, Bytes>> handled = new ArrayList<>();
        try (DeletionJobConsumer consumer = new DeletionJobConsumer(
                kafka.getBootstrapServers(), null, topic, DISTRIBUTION_ID, jobId)) {
            consumer.process(handled::add);
        }

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            for (ConsumerRecord<Bytes, Bytes> record : handled) {
                producer.sendDeletePatch(record);
            }
        }

        List<ConsumerRecord<Bytes, Bytes>> allRecords = readAllRecords(2);
        List<ConsumerRecord<Bytes, Bytes>> deletePatches = filterDeletePatches(allRecords);

        assertEquals(1, deletePatches.size());

        byte[] patchBytes = deletePatches.getFirst().value().get();
        RDFPatch patch = RDFPatchOps.read(new ByteArrayInputStream(patchBytes));

        RDFPatchInverterTest.RecordingChanges changes = new RDFPatchInverterTest.RecordingChanges();
        patch.apply(changes);

        assertEquals(1, changes.deletes.size());
        assertEquals(0, changes.adds.size());
        assertTrue(changes.hasTransaction);
    }

    @Test
    void recordsWithInvalidPayloadsAreSkipped() throws Exception {
        publishRecord(DISTRIBUTION_ID, null, nquadsPayload("1", "Alice"));
        publishRawRecord(DISTRIBUTION_ID, "this is not valid nquads".getBytes(StandardCharsets.UTF_8));

        List<ConsumerRecord<Bytes, Bytes>> handled = new ArrayList<>();
        try (DeletionJobConsumer consumer = new DeletionJobConsumer(
                kafka.getBootstrapServers(), null, topic, DISTRIBUTION_ID, jobId)) {
            consumer.process(handled::add);
        }

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            for (ConsumerRecord<Bytes, Bytes> record : handled) {
                producer.sendDeletePatch(record);
            }
        }

        // 2 originals + 1 delete patch (malformed record skipped)
        List<ConsumerRecord<Bytes, Bytes>> allRecords = readAllRecords(3);
        List<ConsumerRecord<Bytes, Bytes>> deletePatches = filterDeletePatches(allRecords);

        assertEquals(1, deletePatches.size());
    }

    @Test
    void onlyRecordsForTargetDistributionGetDeletePatches() throws Exception {
        publishRecord(DISTRIBUTION_ID, null, nquadsPayload("1", "Alice"));
        publishRecord(OTHER_DISTRIBUTION_ID, null, nquadsPayload("2", "Bob"));
        publishRecord(DISTRIBUTION_ID, null, nquadsPayload("3", "Carol"));

        List<ConsumerRecord<Bytes, Bytes>> handled = new ArrayList<>();
        try (DeletionJobConsumer consumer = new DeletionJobConsumer(
                kafka.getBootstrapServers(), null, topic, DISTRIBUTION_ID, jobId)) {
            consumer.process(handled::add);
        }

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            for (ConsumerRecord<Bytes, Bytes> record : handled) {
                producer.sendDeletePatch(record);
            }
        }

        // 3 originals + 2 delete patches (only for DISTRIBUTION_ID records)
        List<ConsumerRecord<Bytes, Bytes>> allRecords = readAllRecords(5);
        List<ConsumerRecord<Bytes, Bytes>> deletePatches = filterDeletePatches(allRecords);

        assertEquals(2, deletePatches.size());

        for (ConsumerRecord<Bytes, Bytes> patch : deletePatches) {
            Header distId = patch.headers().lastHeader(TelicentHeaders.DISTRIBUTION_ID);
            assertNotNull(distId);
            assertEquals(DISTRIBUTION_ID  + DELETION_JOB_SUFFIX, new String(distId.value(), StandardCharsets.UTF_8));
        }
    }


    private void publishRecord(String distributionId, String deletionJobId, byte[] payload)
            throws ExecutionException, InterruptedException {
        ProducerRecord<Bytes, Bytes> record = new ProducerRecord<>(
                topic,
                Bytes.wrap("key".getBytes(StandardCharsets.UTF_8)),
                Bytes.wrap(payload)
        );
        if (distributionId != null) {
            record.headers().add(
                    TelicentHeaders.DISTRIBUTION_ID,
                    distributionId.getBytes(StandardCharsets.UTF_8)
            );
        }
        record.headers().add(
                TelicentHeaders.CONTENT_TYPE,
                "application/n-quads".getBytes(StandardCharsets.UTF_8)
        );
        if (deletionJobId != null) {
            record.headers().add(
                    DELETION_JOB_ID,
                    deletionJobId.getBytes(StandardCharsets.UTF_8)
            );
        }
        setUpProducer.send(record).get(); // synchronous — ensures ordering
    }
}
