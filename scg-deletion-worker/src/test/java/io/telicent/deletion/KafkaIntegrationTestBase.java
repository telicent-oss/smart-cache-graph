package io.telicent.deletion;

import io.telicent.smart.cache.sources.TelicentHeaders;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.BytesDeserializer;
import org.apache.kafka.common.serialization.BytesSerializer;
import org.apache.kafka.common.utils.Bytes;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.telicent.deletion.DeletionWorkerConstants.DELETION_JOB_ID;

public abstract class KafkaIntegrationTestBase {

    protected abstract String getBootstrapServers();

    protected abstract String getTopic();

    protected abstract KafkaProducer<Bytes, Bytes> getSetUpProducer();

    protected byte[] nquadsPayload(String subjectId, String name) {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        Node g = NodeFactory.createURI("http://example.org/graph");
        Node s = NodeFactory.createURI("http://example.org/emp/" + subjectId);
        Node p = NodeFactory.createURI("http://xmlns.com/foaf/0.1/name");
        Node o = NodeFactory.createLiteralString(name);
        dsg.add(g, s, p, o);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RDFDataMgr.write(baos, dsg, Lang.NQUADS);
        return baos.toByteArray();
    }

    protected KafkaProducer<Bytes, Bytes> createProducer() {
        Properties props = new Properties();
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, BytesSerializer.class.getName());
        props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, BytesSerializer.class.getName());
        return new KafkaProducer<>(props);
    }

    protected KafkaConsumer<Bytes, Bytes> createConsumer() {
        Properties props = new Properties();
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        props.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, BytesDeserializer.class.getName());
        props.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, BytesDeserializer.class.getName());
        props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");
        props.setProperty(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG,
                Integer.toString(10 * 1024 * 1024));
        props.setProperty(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");
        return new KafkaConsumer<>(props);
    }

    protected void createTopic(String topic) {
        try (var admin = org.apache.kafka.clients.admin.AdminClient.create(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new org.apache.kafka.clients.admin.NewTopic(topic, 1, (short) 1)
            )).all().get();
        } catch (Exception e) {
            // topic may already exist
        }
    }

    protected void deleteTopic(String topic) {
        try (var admin = org.apache.kafka.clients.admin.AdminClient.create(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers()))) {
            admin.deleteTopics(List.of(topic)).all().get();
            Thread.sleep(500);
        } catch (Exception e) {
            // topic may not exist yet
        }
    }

    protected void publishRecord(String distributionId, String deletionJobId, String payload)
            throws ExecutionException, InterruptedException {
        ProducerRecord<Bytes, Bytes> record = new ProducerRecord<>(
                getTopic(),
                Bytes.wrap("key".getBytes(StandardCharsets.UTF_8)),
                Bytes.wrap(payload.getBytes(StandardCharsets.UTF_8))
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
        getSetUpProducer().send(record).get();
    }

    protected void publishRawRecord(String distributionId, byte[] payload)
            throws ExecutionException, InterruptedException {
        ProducerRecord<Bytes, Bytes> record = new ProducerRecord<>(
                getTopic(),
                Bytes.wrap("key".getBytes(StandardCharsets.UTF_8)),
                Bytes.wrap(payload)
        );
        record.headers().add(
                TelicentHeaders.CONTENT_TYPE,
                "application/n-quads".getBytes(StandardCharsets.UTF_8)
        );
        if (distributionId != null) {
            record.headers().add(
                    TelicentHeaders.DISTRIBUTION_ID,
                    distributionId.getBytes(StandardCharsets.UTF_8)
            );
        }
        getSetUpProducer().send(record).get();
    }

    protected List<ConsumerRecord<Bytes, Bytes>> readAllRecords(int expectedCount) {
        List<ConsumerRecord<Bytes, Bytes>> allRecords = new ArrayList<>();
        try (KafkaConsumer<Bytes, Bytes> verifier = createConsumer()) {
            verifier.assign(List.of(new TopicPartition(getTopic(), 0)));
            verifier.seekToBeginning(List.of(new TopicPartition(getTopic(), 0)));
            long deadline = System.currentTimeMillis() + 20_000;
            while (allRecords.size() < expectedCount && System.currentTimeMillis() < deadline) {
                ConsumerRecords<Bytes, Bytes> polled = verifier.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<Bytes, Bytes> r : polled) {
                    allRecords.add(r);
                }
            }
        }
        return allRecords;
    }

    protected List<ConsumerRecord<Bytes, Bytes>> filterDeletePatches(
            List<ConsumerRecord<Bytes, Bytes>> records) {
        List<ConsumerRecord<Bytes, Bytes>> patches = new ArrayList<>();
        for (ConsumerRecord<Bytes, Bytes> record : records) {
            Header ct = record.headers().lastHeader(TelicentHeaders.CONTENT_TYPE);
            if (ct != null && "application/rdf-patch"
                    .equals(new String(ct.value(), StandardCharsets.UTF_8))) {
                patches.add(record);
            }
        }
        return patches;
    }
}
