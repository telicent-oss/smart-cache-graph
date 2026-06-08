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
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.BytesDeserializer;
import org.apache.kafka.common.serialization.BytesSerializer;
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
import java.time.Duration;
import java.util.*;

import static io.telicent.deletion.DeletionWorkerConstants.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class DeletionJobProducerIntegrationTest {

    private String topic;
    private RDFPatchInverter rdfPatchInverter;
    private KafkaProducer<Bytes, Bytes> setUpProducer;
    private static final String DISTRIBUTION_ID = "dist-integration-001";
    private String jobId;

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.7.0")
    );

    @BeforeEach
    void setUp() {
        jobId = "test-job-" + UUID.randomUUID();
        topic = "knowledge-" + UUID.randomUUID();
        createTopic();
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

        Header originalOffset = patch.headers().lastHeader(ORIGINAL_OFFSET);
        assertNotNull(originalOffset);
        assertEquals("0", new String(originalOffset.value(), StandardCharsets.UTF_8));
    }

    @Test
    void sentDeletePatchHasOriginalKey() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = buildRecord(0L, DISTRIBUTION_ID, nquadsPayload("1", "Alice"));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            producer.sendDeletePatch(record);
        }

        List<ConsumerRecord<Bytes, Bytes>> allRecords = readAllRecords(1);
        ConsumerRecord<Bytes, Bytes> patch = allRecords.getFirst();

        assertArrayEquals(record.key().get(), patch.key().get());
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

    private byte[] nquadsPayload(String subject, String name) {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        Node g = NodeFactory.createURI("http://example.org/graph");
        Node s = NodeFactory.createURI("http://example.org/emp/" + subject);
        Node p = NodeFactory.createURI("http://xmlns.com/foaf/0.1/name");
        Node o = NodeFactory.createLiteralString(name);
        dsg.add(g, s, p, o);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RDFDataMgr.write(baos, dsg, Lang.NQUADS);
        return baos.toByteArray();
    }

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

    private KafkaProducer<Bytes, Bytes> createProducer() {
        Properties props = new Properties();
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, BytesSerializer.class.getName());
        props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, BytesSerializer.class.getName());
        return new KafkaProducer<>(props);
    }

    private KafkaConsumer<Bytes, Bytes> createConsumer() {
        Properties props = new Properties();
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, BytesDeserializer.class.getName());
        props.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, BytesDeserializer.class.getName());
        props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");
        props.setProperty(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");
        return new KafkaConsumer<>(props);
    }

    private void createTopic() {
        try (var admin = org.apache.kafka.clients.admin.AdminClient.create(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new org.apache.kafka.clients.admin.NewTopic(topic, 1, (short) 1)
            )).all().get();
        } catch (Exception e) {
            // topic may already exist
        }
    }

    private List<ConsumerRecord<Bytes, Bytes>> readAllRecords(int expectedCount) {
        List<ConsumerRecord<Bytes, Bytes>> allRecords = new ArrayList<>();
        try (KafkaConsumer<Bytes, Bytes> verifier = createConsumer()) {
            verifier.assign(List.of(new TopicPartition(topic, 0)));
            verifier.seekToBeginning(List.of(new TopicPartition(topic, 0)));
            long deadline = System.currentTimeMillis() + 20_000;
            while (allRecords.size() < expectedCount && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<Bytes, Bytes> r : verifier.poll(Duration.ofSeconds(1))) {
                    allRecords.add(r);
                }
            }
        }
        return allRecords;
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
    void contentTypeWithCharsetSuffixIsHandled() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = buildRecordWithContentType(
                0L, DISTRIBUTION_ID, "application/n-quads; charset=utf-8", nquadsPayload("1", "Alice"));

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

    @Test
    void originalOffsetIsCorrectForNonZeroOffset() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = buildRecord(42L, DISTRIBUTION_ID, nquadsPayload("1", "Alice"));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            producer.sendDeletePatch(record);
        }

        ConsumerRecord<Bytes, Bytes> patch = readAllRecords(1).getFirst();
        Header originalOffset = patch.headers().lastHeader(ORIGINAL_OFFSET);
        assertNotNull(originalOffset);
        assertEquals("42", new String(originalOffset.value(), StandardCharsets.UTF_8));
    }

    @Test
    void multipleRecordsGetCorrectOriginalOffsets() throws Exception {
        ConsumerRecord<Bytes, Bytes> record1 = buildRecord(0L, DISTRIBUTION_ID, nquadsPayload("1", "Alice"));
        ConsumerRecord<Bytes, Bytes> record2 = buildRecord(5L, DISTRIBUTION_ID, nquadsPayload("2", "Bob"));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                kafka.getBootstrapServers(), null, rdfPatchInverter, topic, DISTRIBUTION_ID, jobId)) {
            producer.sendDeletePatch(record1);
            producer.sendDeletePatch(record2);
        }

        List<ConsumerRecord<Bytes, Bytes>> patches = readAllRecords(2);
        assertEquals(2, patches.size());

        assertEquals("0", new String(
                patches.get(0).headers().lastHeader(ORIGINAL_OFFSET).value(), StandardCharsets.UTF_8));
        assertEquals("5", new String(
                patches.get(1).headers().lastHeader(ORIGINAL_OFFSET).value(), StandardCharsets.UTF_8));
    }

    @Test
    void recordWithNullKeyProducesPatchWithNullKey() throws Exception {
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
        assertNull(patch.key());
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