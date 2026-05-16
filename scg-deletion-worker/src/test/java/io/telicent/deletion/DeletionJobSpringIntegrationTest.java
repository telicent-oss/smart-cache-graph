package io.telicent.deletion;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.telicent.smart.cache.sources.TelicentHeaders;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static io.telicent.deletion.DeletionJobConsumer.DELETION_JOB_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class DeletionJobSpringIntegrationTest {

    private KafkaProducer<Bytes, Bytes> setUpProducer;
//    private static String topic;
    static final String TOPIC = "RDF-test-" + UUID.randomUUID();
    private String distributionId;

    @Autowired
    private MockMvc mockMvc;

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.7.0")
    );

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("deletion-worker.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("deletion-worker.topic", () -> TOPIC);
    }

    @BeforeEach
    void setUp() {
//        topic = "RDF-test-" + UUID.randomUUID();
        createTopic(TOPIC);
        setUpProducer = createProducer();
    }

    @AfterEach
    void tearDown() {
        setUpProducer.close();
    }

    @Test
    void contextLoads() {
        // Verifies the Spring context starts up correctly
    }

    @Test
    public void testDeletesDistributions() throws Exception {
        distributionId = "dist-001";
        publishRecord(distributionId, null, nquads("aa", "aa").getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.add(TelicentHeaders.DISTRIBUTION_ID,
                distributionId);

        MvcResult postResult = mockMvc.perform(post("/jobs/delete-distribution").param("distribution-id", "dist-001").contentType("application/n-quads").headers(headers))
                .andExpect(status().is2xxSuccessful()).andReturn();
        String jobIdJSON = postResult.getResponse().getContentAsString();
        String jobId = new ObjectMapper().readTree(jobIdJSON).get("jobId").asText();
        MvcResult getResult = mockMvc.perform(MockMvcRequestBuilders.
                get("/jobs/" + jobId).contentType("application/n-quads")).andExpect(status().isOk()).andReturn();
        System.out.println(getResult.getResponse().getContentAsString());
        Thread.sleep(1000);
        MvcResult getResultComplete = mockMvc.perform(MockMvcRequestBuilders.
                get("/jobs/" + jobId).contentType("application/n-quads")).andExpect(status().isOk()).andReturn();
        String jobCompleteContent = getResultComplete.getResponse().getContentAsString();
        System.out.println(jobCompleteContent);
        String complete = new ObjectMapper().readTree(jobCompleteContent).get("status").asText();
        assertEquals("COMPLETED", complete);

        List<ConsumerRecord<Bytes, Bytes>> allRecords = readAllRecords(2);
        List<ConsumerRecord<Bytes, Bytes>> deletePatches = filterDeletePatches(allRecords);

        assertEquals(1, deletePatches.size());

        for (ConsumerRecord<Bytes, Bytes> patch : deletePatches) {
            Header distId = patch.headers().lastHeader(TelicentHeaders.DISTRIBUTION_ID);
            assertNotNull(distId);
            assertEquals(distributionId, new String(distId.value(), StandardCharsets.UTF_8));
        }

    }

    //TO TEST
    // when there's no msgs of a certain dist to delete
    // when there's multiple dists and I delete one then the other
    // when the topic is empty
    // invalid records don't get patches

    private static String nquads(String subject, String name) {
        return "<" + subject + "> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> " +
                "<http://xmlns.com/foaf/0.1/Person> .\n" +
                "<" + subject + "> <http://xmlns.com/foaf/0.1/name> \"" + name + "\" .";
    }

    private void createTopic(String topic) {
        // Testcontainers Kafka creates topics on first produce by default,
        // ensures the topic exists before the consumer tries partitionsFor()
        try (var admin = org.apache.kafka.clients.admin.AdminClient.create(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new org.apache.kafka.clients.admin.NewTopic(topic, 1, (short) 1)
            )).all().get();
        } catch (Exception e) {
            // topic may already exist, that's fine
        }
    }

    private KafkaProducer<Bytes, Bytes> createProducer() {
        Properties props = new Properties();
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, BytesSerializer.class.getName());
        props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, BytesSerializer.class.getName());
        return new KafkaProducer<>(props);
    }

    private void publishRecord(String distributionId, String deletionJobId, byte[] payload)
            throws ExecutionException, InterruptedException {
        ProducerRecord<Bytes, Bytes> record = new ProducerRecord<>(
                TOPIC,
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

    private List<ConsumerRecord<Bytes, Bytes>> readAllRecords(int expectedCount) {
        List<ConsumerRecord<Bytes, Bytes>> allRecords = new ArrayList<>();
        try (KafkaConsumer<Bytes, Bytes> verifier = createConsumer()) {
            verifier.assign(List.of(new TopicPartition(TOPIC, 0)));
            verifier.seekToBeginning(List.of(new TopicPartition(TOPIC, 0)));

            long deadline = System.currentTimeMillis() + 20_000;
            while (allRecords.size() < expectedCount
                    && System.currentTimeMillis() < deadline) {
                ConsumerRecords<Bytes, Bytes> polled = verifier.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<Bytes, Bytes> record : polled) {
                    allRecords.add(record);
                }
            }
        }
        return allRecords;
    }

    private KafkaConsumer<Bytes, Bytes> createConsumer() {
        Properties props = new Properties();
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, BytesSerializer.class.getName());
        props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, BytesSerializer.class.getName());
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

    private List<ConsumerRecord<Bytes, Bytes>> filterDeletePatches(
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
