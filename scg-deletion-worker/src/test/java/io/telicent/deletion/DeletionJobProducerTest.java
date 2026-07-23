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
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.StreamRDFLib;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.BytesSerializer;
import org.apache.kafka.common.utils.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static io.telicent.deletion.DeletionWorkerConstants.DELETION_JOB_SUFFIX;
import static io.telicent.deletion.DeletionWorkerConstants.OPERATION;
import static org.junit.jupiter.api.Assertions.*;


class DeletionJobProducerTest {

    private static final String TOPIC = "knowledge";
    private static final String DISTRIBUTION_ID = "dist-abc-123";
    private static final String DELETION_JOB_ID = "Deletion-Job-Id";
    private static final String JOB_ID = "test-job-001";

    private static final Node S = NodeFactory.createURI("http://example.org/subject");
    private static final Node P = NodeFactory.createURI("http://example.org/predicate");
    private static final Node O = NodeFactory.createURI("http://example.org/object");
    private static final Node G = NodeFactory.createURI("http://example.org/graph");


    private MockProducer<Bytes, Bytes> mockProducer;
    private RDFPatchInverter rdfPatchInverter;

    @BeforeEach
    void setUp() {
        mockProducer = new MockProducer<>(true, new BytesSerializer(), new BytesSerializer());
        rdfPatchInverter = new RDFPatchInverter();
    }

    @Test
    void sentRecordContainsDeletionJobIdHeader() {
        ConsumerRecord<Bytes, Bytes> record = buildRecord(DISTRIBUTION_ID, "application/n-quads");
        try (DeletionJobProducer producer = new DeletionJobProducer(
                mockProducer, rdfPatchInverter, TOPIC, DISTRIBUTION_ID, JOB_ID)) {
            producer.sendDeletePatch(record);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ProducerRecord<Bytes, Bytes> sent = mockProducer.history().getFirst();
        Header jobIdHeader = sent.headers().lastHeader(DELETION_JOB_ID);
        assertNotNull(jobIdHeader);
        assertEquals(JOB_ID, new String(jobIdHeader.value(), StandardCharsets.UTF_8));
    }

    @Test
    void sentRecordContainsOriginalDistributionId() {
        ConsumerRecord<Bytes, Bytes> record = buildRecord(DISTRIBUTION_ID, "application/n-quads");
        try (DeletionJobProducer producer = new DeletionJobProducer(
                mockProducer, rdfPatchInverter, TOPIC, DISTRIBUTION_ID, JOB_ID)) {
            producer.sendDeletePatch(record);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ProducerRecord<Bytes, Bytes> sent = mockProducer.history().getFirst();
        Header distId = sent.headers().lastHeader(TelicentHeaders.DISTRIBUTION_ID);
        assertNotNull(distId);
        assertEquals(DISTRIBUTION_ID + DELETION_JOB_SUFFIX, new String(distId.value(), StandardCharsets.UTF_8));
    }

    @Test
    void sentRecordHasOperationDeleteHeader() {
        ConsumerRecord<Bytes, Bytes> record = buildRecord(DISTRIBUTION_ID, "application/n-quads");
        try (DeletionJobProducer producer = new DeletionJobProducer(
                mockProducer, rdfPatchInverter, TOPIC, DISTRIBUTION_ID, JOB_ID)) {
            producer.sendDeletePatch(record);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ProducerRecord<Bytes, Bytes> sent = mockProducer.history().getFirst();
        Header operation = sent.headers().lastHeader(OPERATION);
        assertNotNull(operation);
        assertEquals("delete", new String(operation.value(), StandardCharsets.UTF_8));
    }

    @Test
    void originalHeadersArePreservedOnSentRecord() {
        ConsumerRecord<Bytes, Bytes> record = buildRecord(DISTRIBUTION_ID, "application/n-quads");
        record.headers().add("extra-header", "extra-value".getBytes(StandardCharsets.UTF_8));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                mockProducer, rdfPatchInverter, TOPIC, DISTRIBUTION_ID, JOB_ID)) {
            producer.sendDeletePatch(record);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ProducerRecord<Bytes, Bytes> sent = mockProducer.history().getFirst();
        Header extraHeader = sent.headers().lastHeader("extra-header");
        assertNotNull(extraHeader);
        assertEquals("extra-value", new String(extraHeader.value(), StandardCharsets.UTF_8));
    }

    @Test
    void returnsEmptyAndSendsNothingWhenContentTypeIsMissing() {
        ConsumerRecord<Bytes, Bytes> record = new ConsumerRecord<>(
                TOPIC, 0, 0L,
                Bytes.wrap("key".getBytes(StandardCharsets.UTF_8)),
                Bytes.wrap(nquadsPayload())
        );
        record.headers().add(TelicentHeaders.DISTRIBUTION_ID,
                DISTRIBUTION_ID.getBytes(StandardCharsets.UTF_8));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                mockProducer, rdfPatchInverter, TOPIC, DISTRIBUTION_ID, JOB_ID)) {
            Optional<RecordMetadata> result = producer.sendDeletePatch(record);
            assertEquals(Optional.empty(), result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertEquals(0, mockProducer.history().size());
    }

    @Test
    void returnsEmptyAndSendsNothingWhenContentTypeIsUnrecognised() {
        ConsumerRecord<Bytes, Bytes> record = buildRecord(DISTRIBUTION_ID, "application/json");

        try (DeletionJobProducer producer = new DeletionJobProducer(
                mockProducer, rdfPatchInverter, TOPIC, DISTRIBUTION_ID, JOB_ID)) {
            Optional<RecordMetadata> result = producer.sendDeletePatch(record);
            assertEquals(Optional.empty(), result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertEquals(0, mockProducer.history().size());
    }

    @Test
    void returnsEmptyAndSendsNothingWhenPayloadIsMalformed() {
        ConsumerRecord<Bytes, Bytes> record = new ConsumerRecord<>(
                TOPIC, 0, 0L,
                Bytes.wrap("key".getBytes(StandardCharsets.UTF_8)),
                Bytes.wrap("this is not valid nquads".getBytes(StandardCharsets.UTF_8))
        );
        record.headers().add(TelicentHeaders.CONTENT_TYPE,
                "application/n-quads".getBytes(StandardCharsets.UTF_8));
        record.headers().add(TelicentHeaders.DISTRIBUTION_ID,
                DISTRIBUTION_ID.getBytes(StandardCharsets.UTF_8));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                mockProducer, rdfPatchInverter, TOPIC, DISTRIBUTION_ID, JOB_ID)) {
            Optional<RecordMetadata> result = producer.sendDeletePatch(record);
            assertEquals(Optional.empty(), result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertEquals(0, mockProducer.history().size());
    }

    @Test
    void returnsEmptyAndSendsNothingWhenPayloadIsEmpty() {
        ConsumerRecord<Bytes, Bytes> record = new ConsumerRecord<>(
                TOPIC, 0, 0L,
                Bytes.wrap("key".getBytes(StandardCharsets.UTF_8)),
                Bytes.wrap(new byte[0])
        );
        record.headers().add(TelicentHeaders.CONTENT_TYPE,
                "application/n-quads".getBytes(StandardCharsets.UTF_8));
        record.headers().add(TelicentHeaders.DISTRIBUTION_ID,
                DISTRIBUTION_ID.getBytes(StandardCharsets.UTF_8));

        try (DeletionJobProducer producer = new DeletionJobProducer(
                mockProducer, rdfPatchInverter, TOPIC, DISTRIBUTION_ID, JOB_ID)) {
            Optional<RecordMetadata> result = producer.sendDeletePatch(record);
            assertEquals(Optional.empty(), result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertEquals(0, mockProducer.history().size());
    }

    @Test
    void sentPatchPayloadContainsDeleteOperations() throws Exception {
        ConsumerRecord<Bytes, Bytes> record = buildRecord(DISTRIBUTION_ID, "application/n-quads");

        try (DeletionJobProducer producer = new DeletionJobProducer(
                mockProducer, rdfPatchInverter, TOPIC, DISTRIBUTION_ID, JOB_ID)) {
            producer.sendDeletePatch(record);
        }

        ProducerRecord<Bytes, Bytes> sent = mockProducer.history().getFirst();
        RDFPatch patch = RDFPatchOps.read(new ByteArrayInputStream(sent.value().get()));

        RDFPatchInverterTest.RecordingChanges changes = new RDFPatchInverterTest.RecordingChanges();
        patch.apply(changes);

        assertEquals(1, changes.deletes.size());
        assertEquals(0, changes.adds.size());
        assertTrue(changes.hasTransaction);
    }

    @Test
    void producerSendsPatchSingleRecord() {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        dsg.add(G, S, P, O);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RDFDataMgr.write(baos, dsg, Lang.NQUADS);
        byte[] payload = baos.toByteArray();
        String contentTypeHeader = "application/n-quads";
        System.out.println(Arrays.toString(payload));
        System.out.println(dsg);

        ConsumerRecord<Bytes, Bytes> singleRecord = new ConsumerRecord<>(
                TOPIC, 0, 0L,
                Bytes.wrap("key".getBytes(StandardCharsets.UTF_8)),
                Bytes.wrap(payload)
        );
        singleRecord.headers().add(
                TelicentHeaders.DISTRIBUTION_ID,
                DISTRIBUTION_ID.getBytes(StandardCharsets.UTF_8)
        );
        singleRecord.headers().add(
                TelicentHeaders.CONTENT_TYPE,
                contentTypeHeader.getBytes(StandardCharsets.UTF_8)
        );
        try (DeletionJobProducer producer = new DeletionJobProducer(
                mockProducer, rdfPatchInverter, TOPIC, DISTRIBUTION_ID, DELETION_JOB_ID)) {
            Optional<RecordMetadata> result = producer.sendDeletePatch(singleRecord);
            assertNotEquals(Optional.empty(), result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        List<ProducerRecord<Bytes, Bytes>> sent = mockProducer.history();
        assertEquals(1, sent.size());

        ProducerRecord<Bytes, Bytes> sentRecord = sent.getFirst();
        Header contentType = sentRecord.headers().lastHeader(TelicentHeaders.CONTENT_TYPE);
        assertEquals("application/rdf-patch", new String(contentType.value(), StandardCharsets.UTF_8));
    }

    @Test
    void blankNodesAreConsistentWithinSingleRecord() {
        String nquadsWithSharedBlankNode =
                "_:b0 <http://example.org/p1> <http://example.org/o1> <http://example.org/g> .\n" +
                        "_:b0 <http://example.org/p2> <http://example.org/o2> <http://example.org/g> .\n" +
                        "<http://example.org/s> <http://example.org/p3> _:b0 <http://example.org/g> .\n";

        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        RDFParser.create()
                .source(new ByteArrayInputStream(nquadsWithSharedBlankNode.getBytes(StandardCharsets.UTF_8)))
                .lang(Lang.NQUADS)
                .parse(StreamRDFLib.dataset(dsg));

        List<Node> blankNodes = new ArrayList<>();
        dsg.find().forEachRemaining(quad -> {
            if (quad.getSubject().isBlank()) blankNodes.add(quad.getSubject());
            if (quad.getObject().isBlank()) blankNodes.add(quad.getObject());
        });

        assertEquals(3, blankNodes.size(),
                "Expected 3 quads referencing _:b0 (2 as subject, 1 as object)");

        Node first = blankNodes.getFirst();
        assertTrue(blankNodes.stream().allMatch(n -> n.equals(first)),
                "All occurrences of _:b0 within a single record should map to the same internal node ID");
    }

    @Test
    void rdfPatchRecordProducesDeleteOperation() throws Exception {
        RDFChangesCollector collector = new RDFChangesCollector();
        collector.start();
        collector.txnBegin();
        collector.add(G, S, P, O);
        collector.txnCommit();
        collector.finish();
        RDFPatch sourcePatch = collector.getRDFPatch();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RDFPatchOps.write(baos, sourcePatch);

        ConsumerRecord<Bytes, Bytes> record = new ConsumerRecord<>(
                TOPIC, 0, 0L,
                Bytes.wrap("key".getBytes(StandardCharsets.UTF_8)),
                Bytes.wrap(baos.toByteArray())
        );
        record.headers().add(
                TelicentHeaders.DISTRIBUTION_ID,
                DISTRIBUTION_ID.getBytes(StandardCharsets.UTF_8)
        );
        record.headers().add(
                TelicentHeaders.CONTENT_TYPE,
                "application/rdf-patch".getBytes(StandardCharsets.UTF_8)
        );

        try (DeletionJobProducer producer = new DeletionJobProducer(
                mockProducer, rdfPatchInverter, TOPIC, DISTRIBUTION_ID, JOB_ID)) {
            Optional<RecordMetadata> result = producer.sendDeletePatch(record);
            assertTrue(result.isPresent(), "Expected a delete patch to be produced for an RDF Patch record");
        }

        assertEquals(1, mockProducer.history().size());

        ProducerRecord<Bytes, Bytes> sent = mockProducer.history().getFirst();
        RDFPatch deletePatch = RDFPatchOps.read(new ByteArrayInputStream(sent.value().get()));

        RDFPatchInverterTest.RecordingChanges changes = new RDFPatchInverterTest.RecordingChanges();
        deletePatch.apply(changes);

        assertEquals(1, changes.deletes.size(), "Expected one delete operation for the added quad");
        assertEquals(0, changes.adds.size());
    }

    private byte[] nquadsPayload() {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        dsg.add(G, S, P, O);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RDFDataMgr.write(baos, dsg, Lang.NQUADS);
        return baos.toByteArray();
    }

    private ConsumerRecord<Bytes, Bytes> buildRecord(String distributionId, String contentType) {
        ConsumerRecord<Bytes, Bytes> record = new ConsumerRecord<>(
                TOPIC, 0, 0L,
                Bytes.wrap("key".getBytes(StandardCharsets.UTF_8)),
                Bytes.wrap(nquadsPayload())
        );
        record.headers().add(
                TelicentHeaders.DISTRIBUTION_ID,
                distributionId.getBytes(StandardCharsets.UTF_8)
        );
        record.headers().add(
                TelicentHeaders.CONTENT_TYPE,
                contentType.getBytes(StandardCharsets.UTF_8)
        );
        return record;
    }
}
