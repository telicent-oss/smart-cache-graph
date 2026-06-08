package io.telicent.deletion;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.telicent.smart.cache.sources.TelicentHeaders;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.kafka.clients.admin.AdminClient;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static io.telicent.deletion.DeletionWorkerConstants.DELETION_JOB_ID;
import static io.telicent.deletion.DeletionWorkerConstants.DELETION_JOB_SUFFIX;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class DeletionJobSpringIntegrationTest {

    private KafkaProducer<Bytes, Bytes> setUpProducer;
    static final String TOPIC = "RDF-test-" + UUID.randomUUID();

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
        deleteTopic(TOPIC);
        createTopic(TOPIC);
        setUpProducer = createProducer();
    }

    @AfterEach
    void tearDown() {
        setUpProducer.close();
    }

    @Test
    void contextLoads() { }

    @Test
    public void testDeletesDistributions() throws Exception {
        String distributionId = "dist-001";
        publishRecord(distributionId, null, nquadsPayload("subject", "name"));

        MvcResult postResult = mockMvc.perform(post("/jobs/delete-distribution").param("distribution-id", "dist-001"))
                .andExpect(status().is2xxSuccessful()).andReturn();
        String jobIdJSON = postResult.getResponse().getContentAsString();
        String jobId = new ObjectMapper().readTree(jobIdJSON).get("jobId").asText();
        MvcResult getResult = mockMvc.perform(MockMvcRequestBuilders.
                get("/jobs/" + jobId).contentType("application/n-quads")).andExpect(status().isOk()).andReturn();
        System.out.println(getResult.getResponse().getContentAsString());
        await().atMost(30, SECONDS).untilAsserted(() -> {
            MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobId))
                    .andExpect(status().isOk())
                    .andReturn();
            String status = new ObjectMapper().readTree(
                    result.getResponse().getContentAsString()).get("status").asText();
            assertEquals("COMPLETED", status);
        });

        List<ConsumerRecord<Bytes, Bytes>> allRecords = readAllRecords(2);
        List<ConsumerRecord<Bytes, Bytes>> deletePatches = filterDeletePatches(allRecords);

        assertEquals(1, deletePatches.size());

        for (ConsumerRecord<Bytes, Bytes> patch : deletePatches) {
            Header distId = patch.headers().lastHeader(TelicentHeaders.DISTRIBUTION_ID);
//            assertNotNull(distId);
            assertEquals(distributionId + DELETION_JOB_SUFFIX, new String(distId.value(), StandardCharsets.UTF_8));
        }

    }

    @Test
    void invalidRecordsDoNotGetPatches() throws Exception {
        publishRecord("dist-001", null, nquadsPayload("1", "Alice"));
        publishRawRecord("dist-001", "this is not valid nquads".getBytes(StandardCharsets.UTF_8));
        publishRecord("dist-001", null, nquadsPayload("3", "Carol"));

        MvcResult postResult = mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id", "dist-001"))
                .andExpect(status().isAccepted())
                .andReturn();

        String jobId = new ObjectMapper()
                .readTree(postResult.getResponse().getContentAsString())
                .get("jobId").asText();

        await().atMost(60, SECONDS).untilAsserted(() -> {
            MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobId))
                    .andExpect(status().isOk()).andReturn();
            assertEquals("COMPLETED", new ObjectMapper()
                    .readTree(result.getResponse().getContentAsString())
                    .get("status").asText());
        });

        List<ConsumerRecord<Bytes, Bytes>> allRecords = readAllRecords(5);
        List<ConsumerRecord<Bytes, Bytes>> patches = filterDeletePatches(allRecords);
        assertEquals(2, patches.size());
    }

    @Test
    void triggerWithMissingDistributionIdReturns400() throws Exception {
        mockMvc.perform(post("/jobs/delete-distribution"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void triggerWithEmptyDistributionIdReturns400() throws Exception {
        mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getStatusOfNonExistentJobReturns404() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/jobs/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletionOnEmptyTopicCompletesWithNoPatches() throws Exception {
        MvcResult postResult = mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id", "dist-empty"))
                .andExpect(status().isAccepted())
                .andReturn();

        String jobId = new ObjectMapper()
                .readTree(postResult.getResponse().getContentAsString())
                .get("jobId").asText();

        await().atMost(40, SECONDS).untilAsserted(() -> {
            MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobId))
                    .andExpect(status().isOk()).andReturn();
            assertEquals("COMPLETED", new ObjectMapper()
                    .readTree(result.getResponse().getContentAsString())
                    .get("status").asText());
        });

        List<ConsumerRecord<Bytes, Bytes>> allRecords = readAllRecords(0);
        assertEquals(0, filterDeletePatches(allRecords).size());
    }

    @Test
    void deletionWithNoMatchingDistributionSendsNoPatches() throws Exception {
        publishRecord("dist-other", null, nquadsPayload("1", "Alice"));

        MvcResult postResult = mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id",  "dist-no-match"))
                .andExpect(status().isAccepted())
                .andReturn();

        String jobId = new ObjectMapper()
                .readTree(postResult.getResponse().getContentAsString())
                .get("jobId").asText();

        await().atMost(40, SECONDS).untilAsserted(() -> {
            MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobId))
                    .andExpect(status().isOk()).andReturn();
            assertEquals("COMPLETED", new ObjectMapper()
                    .readTree(result.getResponse().getContentAsString())
                    .get("status").asText());
        });

        List<ConsumerRecord<Bytes, Bytes>> allRecords = readAllRecords(1);
        assertEquals(0, filterDeletePatches(allRecords).size());
    }

    @Test
    void deletionOnlyAffectsTargetDistribution() throws Exception {
        publishRecord("dist-target", null, nquadsPayload("1", "Alice"));
        publishRecord("dist-other", null, nquadsPayload("2", "Bob"));
        publishRecord("dist-target", null, nquadsPayload("3", "Carol"));

        MvcResult postResult = mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id", "dist-target"))
                .andExpect(status().isAccepted())
                .andReturn();

        String jobId = new ObjectMapper()
                .readTree(postResult.getResponse().getContentAsString())
                .get("jobId").asText();

        await().atMost(60, SECONDS).untilAsserted(() -> {
            MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobId))
                    .andExpect(status().isOk()).andReturn();
            assertEquals("COMPLETED", new ObjectMapper()
                    .readTree(result.getResponse().getContentAsString())
                    .get("status").asText());
            assertEquals("2", new ObjectMapper()
                    .readTree(result.getResponse().getContentAsString())
                    .get("patchesSent").asText());
        });

        List<ConsumerRecord<Bytes, Bytes>> allRecords = readAllRecords(5);
        List<ConsumerRecord<Bytes, Bytes>> patches = filterDeletePatches(allRecords);
        assertEquals(2, patches.size());

        for (ConsumerRecord<Bytes, Bytes> patch : patches) {
            Header distId = patch.headers().lastHeader(TelicentHeaders.DISTRIBUTION_ID);
            //assertNotNull(distId);
            assertEquals("dist-target-deletion", new String(distId.value(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void jobStatusTransitionsToCompleted() throws Exception {
        publishRecord("dist-001", null, nquadsPayload("1", "Alice"));

        MvcResult postResult = mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id", "dist-001"))
                .andExpect(status().isAccepted())
                .andReturn();

        String jobId = new ObjectMapper()
                .readTree(postResult.getResponse().getContentAsString())
                .get("jobId").asText();

        await().atMost(60, SECONDS).untilAsserted(() -> {
            MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobId))
                    .andExpect(status().isOk()).andReturn();
            String status = new ObjectMapper()
                    .readTree(result.getResponse().getContentAsString())
                    .get("status").asText();
            assertNotEquals("FAILED", status);
            assertEquals("COMPLETED", status);
        });
    }

    @Test
    void twoConcurrentJobsForDifferentDistributions() throws Exception {
        publishRecord("dist-A", null, nquadsPayload("1", "Alice"));
        publishRecord("dist-B", null, nquadsPayload("2", "Bob"));

        MvcResult postA = mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id",  "dist-A"))
                .andExpect(status().isAccepted()).andReturn();

        MvcResult postB = mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id", "dist-B"))
                .andExpect(status().isAccepted()).andReturn();

        String jobIdA = new ObjectMapper()
                .readTree(postA.getResponse().getContentAsString()).get("jobId").asText();
        String jobIdB = new ObjectMapper()
                .readTree(postB.getResponse().getContentAsString()).get("jobId").asText();

        await().atMost(60, SECONDS).untilAsserted(() -> {
            String statusA = new ObjectMapper().readTree(mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobIdA))
                    .andReturn().getResponse().getContentAsString()).get("status").asText();
            String statusB = new ObjectMapper().readTree(mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobIdB))
                    .andReturn().getResponse().getContentAsString()).get("status").asText();
            String patchNumberA = new ObjectMapper().readTree(mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobIdA))
                    .andReturn().getResponse().getContentAsString()).get("patchesSent").asText();
            String patchNumberB = new ObjectMapper().readTree(mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobIdB))
                    .andReturn().getResponse().getContentAsString()).get("patchesSent").asText();
            assertEquals("COMPLETED", statusA);
            assertEquals("COMPLETED", statusB);
            assertEquals("1", patchNumberA);
            assertEquals("1", patchNumberB);
        });

        // 2 originals + 2 delete patches
        List<ConsumerRecord<Bytes, Bytes>> allRecords = readAllRecords(4);
        assertEquals(2, filterDeletePatches(allRecords).size());
    }

    @Test
    void deletingSameDistributionTwiceOnlySendsPatchOnFirstDelete() throws Exception {
        publishRecord("dist-A", null, nquadsPayload("1", "Alice"));
        publishRecord("dist-A", null, nquadsPayload("2", "Bob"));

        // first delete job
        MvcResult firstPost = mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id", "dist-A"))
                .andExpect(status().isAccepted()).andReturn();

        String firstJobId = new ObjectMapper()
                .readTree(firstPost.getResponse().getContentAsString()).get("jobId").asText();

        await().atMost(60, SECONDS).untilAsserted(() -> {
            String status = new ObjectMapper().readTree(mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + firstJobId))
                    .andReturn().getResponse().getContentAsString()).get("status").asText();
            assertEquals("COMPLETED", status);
        });

        String firstJobPatches = new ObjectMapper()
                .readTree(mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + firstJobId))
                        .andReturn().getResponse().getContentAsString()).get("patchesSent").asText();
        assertEquals("2", firstJobPatches);

        // second delete job for the same distribution
        MvcResult secondPost = mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id", "dist-A"))
                .andExpect(status().isAccepted()).andReturn();

        String secondJobId = new ObjectMapper()
                .readTree(secondPost.getResponse().getContentAsString()).get("jobId").asText();

        await().atMost(60, SECONDS).untilAsserted(() -> {
            String status = new ObjectMapper().readTree(mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + secondJobId))
                    .andReturn().getResponse().getContentAsString()).get("status").asText();
            assertEquals("COMPLETED", status);
        });

        String secondJobPatches = new ObjectMapper()
                .readTree(mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + secondJobId))
                        .andReturn().getResponse().getContentAsString()).get("patchesSent").asText();
        assertEquals("0", secondJobPatches);

        List<ConsumerRecord<Bytes, Bytes>> allRecords = readAllRecords(4);
        assertEquals(2, filterDeletePatches(allRecords).size());
    }


    private byte[] nquadsPayload(String subjectId, String name) {
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

    private void createTopic(String topic) {
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

    private void deleteTopic(String topic) {
        try (AdminClient admin = AdminClient.create(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            admin.deleteTopics(List.of(topic)).all().get();
            Thread.sleep(500);
        } catch (Exception e) {
            // Topic may not exist yet, that's fine
        }
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
        setUpProducer.send(record).get();
    }

    private void publishRawRecord(String distributionId, byte[] payload)
            throws ExecutionException, InterruptedException {
        ProducerRecord<Bytes, Bytes> record = new ProducerRecord<>(
                TOPIC,
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
        setUpProducer.send(record).get();
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
