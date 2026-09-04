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
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
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
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static io.telicent.deletion.DeletionWorkerConstants.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class DeletionJobProducerIntegrationTest extends KafkaIntegrationTestBase{

    private String topic;
    private RDFPatchInverter rdfPatchInverter;
    private KafkaProducer<Bytes, Bytes> setUpProducer;
    private static final String DISTRIBUTION_ID = "dist-integration-001";
    private String jobId;

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
    void sendDeletePatchProducesRecordOnTopic() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = buildRecord(0L, DISTRIBUTION_ID, nquadsPayload("1", "Alice"));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            producer.sendDeletePatch(record);
        }

        List<ConsumerRecord<Bytes, Bytes>> allRecords = readAllRecords(1);
        assertEquals(1, allRecords.size());
    }

    @Test
    void sentDeletePatchHasCorrectHeaders() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = buildRecord(0L, DISTRIBUTION_ID, nquadsPayload("1", "Alice"));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            producer.sendDeletePatch(record);
        }

        List<ConsumerRecord<Bytes, Bytes>> allRecords = readAllRecords(1);
        ConsumerRecord<Bytes, Bytes> patch = allRecords.getFirst();

        Header ct = patch.headers().lastHeader(TelicentHeaders.CONTENT_TYPE);
        assertNotNull(ct);
        assertEquals("application/rdf-patch", new String(ct.value(), StandardCharsets.UTF_8));

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

    /**
     * A delete patch belongs to a NEW distribution, so it is keyed by that new Distribution ID rather than by the
     * key of the record it was derived from.  Anything else would leave the key disagreeing with the
     * Distribution-Id header, and the key is authoritative.
     */
    @Test
    void sentDeletePatchIsKeyedByTheDeletionDistribution() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = buildRecord(0L, DISTRIBUTION_ID, nquadsPayload("1", "Alice"));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            producer.sendDeletePatch(record);
        }

        List<ConsumerRecord<Bytes, Bytes>> allRecords = readAllRecords(1);
        ConsumerRecord<Bytes, Bytes> patch = allRecords.getFirst();

        String expectedDistributionId = DISTRIBUTION_ID + DELETION_JOB_SUFFIX;
        assertNotNull(patch.key(), "Delete patch should be keyed by its Distribution ID");
        assertEquals(expectedDistributionId, new String(patch.key().get(), StandardCharsets.UTF_8));

        // The key and the header must agree, otherwise the patch is attributed to the wrong distribution
        Header distId = patch.headers().lastHeader(TelicentHeaders.DISTRIBUTION_ID);
        assertNotNull(distId);
        assertEquals(new String(patch.key().get(), StandardCharsets.UTF_8),
                     new String(distId.value(), StandardCharsets.UTF_8));

        // ...and it is deliberately NOT the key of the record it was derived from
        assertNotEquals(new String(record.key().get(), StandardCharsets.UTF_8),
                        new String(patch.key().get(), StandardCharsets.UTF_8),
                        "Delete patch must not inherit the inbound record's key");
    }

    @Test
    void returnsEmptyForUnrecognisedContentType() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = buildRecordWithContentType(
                0L, DISTRIBUTION_ID, "application/unknown", nquadsPayload("1", "Alice"));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            var result = producer.sendDeletePatch(record);
            assertTrue(result.isEmpty());
        }

        assertEquals(0, readAllRecords(0).size());
    }

    @Test
    void returnsEmptyForMalformedPayload() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = buildRecordWithPayload(
                0L, DISTRIBUTION_ID, "this is not valid nquads".getBytes(StandardCharsets.UTF_8));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            var result = producer.sendDeletePatch(record);
            assertTrue(result.isEmpty());
        }

        assertEquals(0, readAllRecords(0).size());
    }

    @Test
    void returnsEmptyForPayloadWithNoQuads() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = buildRecordWithPayload(
                0L, DISTRIBUTION_ID, "".getBytes(StandardCharsets.UTF_8));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            var result = producer.sendDeletePatch(record);
            assertTrue(result.isEmpty());
        }

        assertEquals(0, readAllRecords(0).size());
    }

    @Test
    void returnsMetadataOnSuccessfulSend() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = buildRecord(0L, DISTRIBUTION_ID, nquadsPayload("1", "Alice"));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            var result = producer.sendDeletePatch(record);
            assertTrue(result.isPresent());
        }
    }

//    private byte[] nquadsPayload(String subject, String name) {
//        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
//        Node g = NodeFactory.createURI("http://example.org/graph");
//        Node s = NodeFactory.createURI("http://example.org/emp/" + subject);
//        Node p = NodeFactory.createURI("http://xmlns.com/foaf/0.1/name");
//        Node o = NodeFactory.createLiteralString(name);
//        dsg.add(g, s, p, o);
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        RDFDataMgr.write(baos, dsg, Lang.NQUADS);
//        return baos.toByteArray();
//    }

    private ConsumerRecord<Bytes, Bytes> buildRecord(long offset, String distributionId, byte[] payload) {
        return buildRecordWithContentType(offset, distributionId, "application/n-quads", payload);
    }

    private ConsumerRecord<Bytes, Bytes> buildRecordWithContentType(
            long offset, String distributionId, String contentType, byte[] payload) {
        ConsumerRecord<Bytes, Bytes> record = new ConsumerRecord<>(
                topic, 0, offset,
                Bytes.wrap("key".getBytes(StandardCharsets.UTF_8)),
                Bytes.wrap(payload)
        );
        record.headers().add(TelicentHeaders.DISTRIBUTION_ID,
                distributionId.getBytes(StandardCharsets.UTF_8));
        record.headers().add(TelicentHeaders.CONTENT_TYPE,
                contentType.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private ConsumerRecord<Bytes, Bytes> buildRecordWithPayload(
            long offset, String distributionId, byte[] payload) {
        ConsumerRecord<Bytes, Bytes> record = new ConsumerRecord<>(
                topic, 0, offset,
                Bytes.wrap("key".getBytes(StandardCharsets.UTF_8)),
                Bytes.wrap(payload)
        );
        record.headers().add(TelicentHeaders.DISTRIBUTION_ID,
                distributionId.getBytes(StandardCharsets.UTF_8));
        record.headers().add(TelicentHeaders.CONTENT_TYPE,
                "application/n-quads".getBytes(StandardCharsets.UTF_8));
        return record;
    }

    @Test
    void sendDeletePatchWorksForTurtle() throws Exception {
        byte[] turtlePayload = turtlePayload("1", "Alice");
        ConsumerRecord<Bytes, Bytes> record = buildRecordWithContentType(
                0L, DISTRIBUTION_ID, "text/turtle", turtlePayload);

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            var result = producer.sendDeletePatch(record);
            assertTrue(result.isPresent());
        }

        assertEquals(1, readAllRecords(1).size());
    }

    @Test
    void sendDeletePatchWorksForNTriples() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = buildRecordWithContentType(
                0L, DISTRIBUTION_ID, "application/n-triples", ntriplesPayload("1", "Alice"));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            var result = producer.sendDeletePatch(record);
            assertTrue(result.isPresent());
        }

        assertEquals(1, readAllRecords(1).size());
    }

    @Test
    void sendDeletePatchWorksForTrig() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = buildRecordWithContentType(
                0L, DISTRIBUTION_ID, "application/trig", trigPayload("1", "Alice"));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            var result = producer.sendDeletePatch(record);
            assertTrue(result.isPresent());
        }

        assertEquals(1, readAllRecords(1).size());
    }

    @Test
    void sendDeletePatchWorksForRdfXml() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = buildRecordWithContentType(
                0L, DISTRIBUTION_ID, "application/rdf+xml", rdfXmlPayload("1", "Alice"));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            var result = producer.sendDeletePatch(record);
            assertTrue(result.isPresent());
        }

        assertEquals(1, readAllRecords(1).size());
    }

    @Test
    void returnsEmptyForNullContentType() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = new ConsumerRecord<>(
                topic, 0, 0L,
                Bytes.wrap("key".getBytes(StandardCharsets.UTF_8)),
                Bytes.wrap(nquadsPayload("1", "Alice"))
        );
        record.headers().add(TelicentHeaders.DISTRIBUTION_ID,
                DISTRIBUTION_ID.getBytes(StandardCharsets.UTF_8));
        // no Content-Type header added

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            var result = producer.sendDeletePatch(record);
            assertTrue(result.isEmpty());
        }

        assertEquals(0, readAllRecords(0).size());
    }

    @Test
    void originalHeadersAreCopiedToPatch() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = buildRecord(0L, DISTRIBUTION_ID, nquadsPayload("1", "Alice"));
        record.headers().add("Security-Label", "classification=O".getBytes(StandardCharsets.UTF_8));
        record.headers().add("Owner", "Platform Team".getBytes(StandardCharsets.UTF_8));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            producer.sendDeletePatch(record);
        }

        ConsumerRecord<Bytes, Bytes> patch = readAllRecords(1).getFirst();

        Header securityLabel = patch.headers().lastHeader("Security-Label");
        assertNotNull(securityLabel);
        assertEquals("classification=O", new String(securityLabel.value(), StandardCharsets.UTF_8));

        Header owner = patch.headers().lastHeader("Owner");
        assertNotNull(owner);
        assertEquals("Platform Team", new String(owner.value(), StandardCharsets.UTF_8));
    }

    @Test
    void contentTypeIsReplacedNotDuplicated() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = buildRecord(0L, DISTRIBUTION_ID, nquadsPayload("1", "Alice"));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            producer.sendDeletePatch(record);
        }

        ConsumerRecord<Bytes, Bytes> patch = readAllRecords(1).getFirst();

        List<Header> contentTypeHeaders = new ArrayList<>();
        patch.headers().headers(TelicentHeaders.CONTENT_TYPE).forEach(contentTypeHeaders::add);
        assertEquals(1, contentTypeHeaders.size());
        assertEquals("application/rdf-patch",
                new String(contentTypeHeaders.getFirst().value(), StandardCharsets.UTF_8));
    }

    @Test
    void distributionIdIsReplacedNotDuplicated() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = buildRecord(0L, DISTRIBUTION_ID, nquadsPayload("1", "Alice"));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            producer.sendDeletePatch(record);
        }

        ConsumerRecord<Bytes, Bytes> patch = readAllRecords(1).getFirst();

        List<Header> distIdHeaders = new ArrayList<>();
        patch.headers().headers(TelicentHeaders.DISTRIBUTION_ID).forEach(distIdHeaders::add);
        assertEquals(1, distIdHeaders.size());
        assertEquals(DISTRIBUTION_ID + DELETION_JOB_SUFFIX,
                new String(distIdHeaders.getFirst().value(), StandardCharsets.UTF_8));
    }

    @Test
    void operationIsReplacedNotDuplicatedWhenAlreadyPresent() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = buildRecord(0L, DISTRIBUTION_ID, nquadsPayload("1", "Alice"));
        record.headers().add(OPERATION, "add".getBytes(StandardCharsets.UTF_8));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            producer.sendDeletePatch(record);
        }

        ConsumerRecord<Bytes, Bytes> patch = readAllRecords(1).getFirst();

        List<Header> operationHeaders = new ArrayList<>();
        patch.headers().headers(OPERATION).forEach(operationHeaders::add);
        assertEquals(1, operationHeaders.size());
        assertEquals("delete", new String(operationHeaders.getFirst().value(), StandardCharsets.UTF_8));
    }

    @Test
    void patchPayloadContainsDeleteOperations() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = buildRecord(0L, DISTRIBUTION_ID, nquadsPayload("1", "Alice"));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            producer.sendDeletePatch(record);
        }

        ConsumerRecord<Bytes, Bytes> patch = readAllRecords(1).getFirst();
        byte[] patchBytes = patch.value().get();
        RDFPatch rdfPatch = RDFPatchOps.read(new ByteArrayInputStream(patchBytes));

        RDFPatchInverterTest.RecordingChanges changes = new RDFPatchInverterTest.RecordingChanges();
        rdfPatch.apply(changes);

        assertEquals(1, changes.deletes.size());
        assertEquals(0, changes.adds.size());
        assertTrue(changes.hasTransaction);
    }

    /**
     * Even where the inbound record carried no key at all, the delete patch is still keyed by the deletion
     * distribution - the key is derived from the new Distribution ID, never copied.
     */
    @Test
    void recordWithNullKeyStillProducesPatchKeyedByTheDeletionDistribution() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = new ConsumerRecord<>(
                topic, 0, 0L,
                null,
                Bytes.wrap(nquadsPayload("1", "Alice"))
        );
        record.headers().add(TelicentHeaders.DISTRIBUTION_ID,
                DISTRIBUTION_ID.getBytes(StandardCharsets.UTF_8));
        record.headers().add(TelicentHeaders.CONTENT_TYPE,
                "application/n-quads".getBytes(StandardCharsets.UTF_8));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            var result = producer.sendDeletePatch(record);
            assertTrue(result.isPresent());
        }

        ConsumerRecord<Bytes, Bytes> patch = readAllRecords(1).getFirst();
        assertNotNull(patch.key(), "Delete patch should be keyed even when the inbound record was not");
        assertEquals(DISTRIBUTION_ID + DELETION_JOB_SUFFIX,
                     new String(patch.key().get(), StandardCharsets.UTF_8));
    }

    private byte[] turtlePayload(String subject, String name) {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        Node s = NodeFactory.createURI("http://example.org/emp/" + subject);
        Node p = NodeFactory.createURI("http://xmlns.com/foaf/0.1/name");
        Node o = NodeFactory.createLiteralString(name);
        dsg.getDefaultGraph().add(org.apache.jena.graph.Triple.create(s, p, o));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RDFDataMgr.write(baos, dsg.getDefaultGraph(), Lang.TURTLE);
        return baos.toByteArray();
    }

    private byte[] ntriplesPayload(String subject, String name) {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        Node s = NodeFactory.createURI("http://example.org/emp/" + subject);
        Node p = NodeFactory.createURI("http://xmlns.com/foaf/0.1/name");
        Node o = NodeFactory.createLiteralString(name);
        dsg.getDefaultGraph().add(org.apache.jena.graph.Triple.create(s, p, o));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RDFDataMgr.write(baos, dsg.getDefaultGraph(), Lang.NTRIPLES);
        return baos.toByteArray();
    }

    private byte[] trigPayload(String subject, String name) {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        Node g = NodeFactory.createURI("http://example.org/graph");
        Node s = NodeFactory.createURI("http://example.org/emp/" + subject);
        Node p = NodeFactory.createURI("http://xmlns.com/foaf/0.1/name");
        Node o = NodeFactory.createLiteralString(name);
        dsg.add(g, s, p, o);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RDFDataMgr.write(baos, dsg, Lang.TRIG);
        return baos.toByteArray();
    }

    private byte[] rdfXmlPayload(String subject, String name) {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        Node s = NodeFactory.createURI("http://example.org/emp/" + subject);
        Node p = NodeFactory.createURI("http://xmlns.com/foaf/0.1/name");
        Node o = NodeFactory.createLiteralString(name);
        dsg.getDefaultGraph().add(org.apache.jena.graph.Triple.create(s, p, o));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RDFDataMgr.write(baos, dsg.getDefaultGraph(), Lang.RDFXML);
        return baos.toByteArray();
    }
}