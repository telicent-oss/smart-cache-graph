package io.telicent.deletion;

import io.telicent.jena.abac.labels.node.LabelToNodeGenerator;
import io.telicent.smart.cache.sources.TelicentHeaders;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.StreamRDFLib;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.BytesSerializer;
import org.apache.kafka.common.utils.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.telicent.smart.cache.sources.TelicentHeaders.DISTRIBUTION_ID;
import static org.apache.jena.riot.Lang.NQUADS;

// KafkaProducer that sends the patch event back to knowledge with
// Content-Type: application/rdf-patch, the original Distribution-ID,
// and X-Deletion-Job-ID set to this run's ID

public class DeletionJobProducer implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeletionJobProducer.class);
    //TODO
    // move to TelicentHeaders
    static final String DELETION_JOB_ID = "Deletion-Job-Id";
    static final String OPERATION = "Operation";

    private final Producer<Bytes, Bytes> producer;
    private final RDFPatchInverter inverter;
    private final String topic;
    private final String distributionId;
    private final String jobId;

    public DeletionJobProducer(String bootstrapServers, String configFilePath, RDFPatchInverter inverter, String topic, String distributionId, String jobId) {
        this.inverter = inverter;
        this.topic = topic;
        this.distributionId = distributionId;
        this.jobId = jobId;
        this.producer = createProducer(bootstrapServers, configFilePath);
    }

    /**
     * Test constructor — accepts any Producer implementation.
     */
    DeletionJobProducer(Producer<Bytes, Bytes> producer, RDFPatchInverter inverter, String topic,
                        String distributionId, String jobId) {
        this.inverter = inverter;
        this.topic = topic;
        this.distributionId = distributionId;
        this.jobId = jobId;
        this.producer = producer;
    }

    private KafkaProducer<Bytes, Bytes> createProducer(String bootstrapServers, String configFilePath) {
        Properties props = new Properties();
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        if (configFilePath != null && !configFilePath.isBlank()) {
            try (InputStream is = new FileInputStream(configFilePath)) {
                props.load(is);
                LOGGER.info("Loaded Kafka config from {}", configFilePath);
            } catch (IOException e) {
                LOGGER.warn("Could not load Kafka config file {}: {}", configFilePath, e.getMessage());
            }
        }
        props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                BytesSerializer.class.getName());
        props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                BytesSerializer.class.getName());
        props.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaProducer<>(props);
    }

    /**
     * Translates a consumer record into a delete patch and sends it back onto
     * the topic synchronously. Synchronous send ensures the
     * delete patch is on the topic before the consumer advances, which makes
     * loop detection reliable.
     *
     * Returns the metadata of the sent record, or empty if the record was skipped.
     * @param record
     * @return
     */
    public Optional<RecordMetadata> sendDeletePatch(ConsumerRecord<Bytes, Bytes> record) throws ExecutionException, InterruptedException {
        Lang lang = resolveLang(headerValue(record, TelicentHeaders.CONTENT_TYPE));
        if (lang == null) {
            LOGGER.warn("[{}] Skipping offset {} — unrecognised Content-Type: {}",
                    jobId, record.offset(), headerValue(record, TelicentHeaders.CONTENT_TYPE));
            System.err.println(jobId + " Skipping offset " + record.offset() + " — unrecognised Content-Type: " + headerValue(record, TelicentHeaders.CONTENT_TYPE));
            return Optional.empty();
        }

        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        try {
            RDFParser.create()
                    .source(new ByteArrayInputStream(record.value().get()))
                    .labelToNode(LabelToNodeGenerator.generate())
                    .lang(lang)
                    .parse(StreamRDFLib.dataset(dsg));
        }
        catch (Exception e) {
            LOGGER.warn("[{}] Skipping offset {} — failed to parse payload: {}",
                    jobId, record.offset(), e.getMessage());
            System.err.println(jobId + "Skipping offset " + record.offset() + " — failed to parse payload: "  + e.getMessage());
            return Optional.empty();
        }

        RDFPatch patch = inverter.invert(dsg);
        if (patch == null) {
            LOGGER.warn("[{}] Skipping offset {} — no quads found in payload",
                    jobId, record.offset());
            System.err.println(jobId + "Skipping offset " + record.offset() + " — no quads found in payload");
            return Optional.empty();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RDFPatchOps.write(baos, patch);

        ProducerRecord<Bytes, Bytes> output = new ProducerRecord<>(
                topic,
                record.key(),
                Bytes.wrap(baos.toByteArray())
        );

        for (Header header : record.headers()) {
            output.headers().add(header);
        }

        output.headers().remove(TelicentHeaders.CONTENT_TYPE);
        output.headers().add(
                TelicentHeaders.CONTENT_TYPE,
                "application/rdf-patch".getBytes(StandardCharsets.UTF_8)
        );

        output.headers().remove(OPERATION);
        output.headers().add(
                OPERATION,
                "delete".getBytes(StandardCharsets.UTF_8)
        );

        output.headers().add(
                "Original-Offset",
                String.valueOf(record.offset()).getBytes(StandardCharsets.UTF_8)
        );

        output.headers().add(
                DELETION_JOB_ID,
                jobId.getBytes(StandardCharsets.UTF_8)
        );
        String newDistributionId = distributionId + "-deletion";
        output.headers().remove(DISTRIBUTION_ID);
        output.headers().add(DISTRIBUTION_ID, newDistributionId.getBytes(StandardCharsets.UTF_8));

        try {
            RecordMetadata metadata = producer.send(output).get();
            LOGGER.debug("[{}] Sent delete patch for offset {} -> new offset {}",
                    jobId, record.offset(), metadata.offset());
            System.err.println(jobId + "Sent delete patch for offset " +  record.offset() + "-> new offset " +  metadata.offset());
            return Optional.of(metadata);
        } catch (Exception e) {
            LOGGER.error("[{}] Failed to send delete patch for offset {}: {}",
                    jobId, record.offset(), e.getMessage());
            System.err.println(jobId + " Failed to send delete patch for offset " +  record.offset() + ": " + e.getMessage());
            throw new DeletionJobException("Failed to send delete patch", e);
        }
    }

    private Lang resolveLang(String contentType) {
        if (contentType == null) return null;
        String normalised = contentType.split(";")[0].trim().toLowerCase();
        return switch (normalised) {
            case "nquads", "application/n-quads", "text/x-nquads" -> Lang.NQUADS;
            case "text/turtle", "application/turtle"              -> Lang.TURTLE;
            case "application/trig"                               -> Lang.TRIG;
            case "application/rdf+xml"                            -> Lang.RDFXML;
            default                                               -> null;
        };
    }

    private String headerValue(ConsumerRecord<?, ?> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header == null) return null;
        return new String(header.value(), StandardCharsets.UTF_8);
    }



        @Override
    public void close() throws Exception {
        producer.close();
    }
}
