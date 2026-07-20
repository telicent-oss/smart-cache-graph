package io.telicent.deletion;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.telicent.deletion.service.UserInfoService;
import io.telicent.smart.cache.sources.TelicentHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.utils.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static io.telicent.deletion.DeletionWorkerConstants.DELETION_JOB_SUFFIX;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class DeletionJobSpringIntegrationTest extends KafkaIntegrationTestBase{

    private KafkaProducer<Bytes, Bytes> setUpProducer;
    static final String TOPIC = "RDF-test-" + UUID.randomUUID();

    @Override
    protected String getBootstrapServers() {
        return kafka.getBootstrapServers();
    }

    @Override
    protected String getTopic() {
        return TOPIC;
    }

    @Override
    protected KafkaProducer<Bytes, Bytes> getSetUpProducer() {
        return setUpProducer;
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserInfoService userInfoService;

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
        when(userInfoService.isSystemAdmin(any())).thenReturn(UserInfoService.AuthResult.AUTHORIZED);
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
    void mockIsWorking() {
        assertEquals(UserInfoService.AuthResult.AUTHORIZED, userInfoService.isSystemAdmin("any-token"));
    }

    @Test
    public void testDeletesDistributions() throws Exception {
        String distributionId = "dist-001";
        publishRecord(distributionId, null,  new String(nquadsPayload("subject", "name"), StandardCharsets.UTF_8));

        MvcResult postResult = mockMvc.perform(post("/jobs/delete-distribution").param("distribution-id", "dist-001")
                .header("Authorization", "Bearer test-token"))
                .andExpect(status().is2xxSuccessful()).andReturn();
        String jobIdJSON = postResult.getResponse().getContentAsString();
        String jobId = new ObjectMapper().readTree(jobIdJSON).get("jobId").asText();
        MvcResult getResult = mockMvc.perform(MockMvcRequestBuilders.
                get("/jobs/" + jobId).contentType("application/n-quads").header("Authorization", "Bearer test-token")).andExpect(status().isOk()).andReturn();
        System.out.println(getResult.getResponse().getContentAsString());
        await().atMost(30, SECONDS).untilAsserted(() -> {
            MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobId)
                    .header("Authorization", "Bearer test-token"))
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
            assertNotNull(distId);
            assertEquals(distributionId + DELETION_JOB_SUFFIX, new String(distId.value(), StandardCharsets.UTF_8));
        }

    }

    @Test
    void invalidRecordsDoNotGetPatches() throws Exception {
        publishRecord("dist-001", null, new String(nquadsPayload("1", "Alice"), StandardCharsets.UTF_8));
        publishRawRecord("dist-001", "this is not valid nquads".getBytes(StandardCharsets.UTF_8));
        publishRecord("dist-001", null, new String(nquadsPayload("3", "Carol"), StandardCharsets.UTF_8));

        MvcResult postResult = mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id", "dist-001")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isAccepted())
                .andReturn();

        String jobId = new ObjectMapper()
                .readTree(postResult.getResponse().getContentAsString())
                .get("jobId").asText();

        await().atMost(60, SECONDS).untilAsserted(() -> {
            MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobId)
                            .header("Authorization", "Bearer test-token"))
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
        mockMvc.perform(post("/jobs/delete-distribution")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void triggerWithEmptyDistributionIdReturns400() throws Exception {
        mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id", "")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getStatusOfNonExistentJobReturns404() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/jobs/does-not-exist")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletionOnEmptyTopicCompletesWithNoPatches() throws Exception {
        MvcResult postResult = mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id", "dist-empty")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isAccepted())
                .andReturn();

        String jobId = new ObjectMapper()
                .readTree(postResult.getResponse().getContentAsString())
                .get("jobId").asText();

        await().atMost(40, SECONDS).untilAsserted(() -> {
            MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobId)
                            .header("Authorization", "Bearer test-token"))
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
        publishRecord("dist-other", null, new String(nquadsPayload("1", "Alice"), StandardCharsets.UTF_8));

        MvcResult postResult = mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id",  "dist-no-match")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isAccepted())
                .andReturn();

        String jobId = new ObjectMapper()
                .readTree(postResult.getResponse().getContentAsString())
                .get("jobId").asText();

        await().atMost(40, SECONDS).untilAsserted(() -> {
            MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobId)
                            .header("Authorization", "Bearer test-token"))
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
        publishRecord("dist-target", null, new String(nquadsPayload("1", "Alice"), StandardCharsets.UTF_8));
        publishRecord("dist-other", null, new String(nquadsPayload("2", "Bob"), StandardCharsets.UTF_8));
        publishRecord("dist-target", null, new String(nquadsPayload("3", "Carol"), StandardCharsets.UTF_8));

        MvcResult postResult = mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id", "dist-target")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isAccepted())
                .andReturn();

        String jobId = new ObjectMapper()
                .readTree(postResult.getResponse().getContentAsString())
                .get("jobId").asText();

        await().atMost(60, SECONDS).untilAsserted(() -> {
            MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobId)
                            .header("Authorization", "Bearer test-token"))
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
            assertNotNull(distId);
            assertEquals("dist-target-deletion", new String(distId.value(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void jobStatusTransitionsToCompleted() throws Exception {
        publishRecord("dist-001", null, new String(nquadsPayload("1", "Alice"), StandardCharsets.UTF_8));

        MvcResult postResult = mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id", "dist-001")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isAccepted())
                .andReturn();

        String jobId = new ObjectMapper()
                .readTree(postResult.getResponse().getContentAsString())
                .get("jobId").asText();

        await().atMost(60, SECONDS).untilAsserted(() -> {
            MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobId)
                            .header("Authorization", "Bearer test-token"))
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
        publishRecord("dist-A", null, new String(nquadsPayload("1", "Alice"), StandardCharsets.UTF_8));
        publishRecord("dist-B", null, new String(nquadsPayload("2", "Bob"), StandardCharsets.UTF_8));

        MvcResult postA = mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id",  "dist-A")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isAccepted()).andReturn();

        MvcResult postB = mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id", "dist-B")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isAccepted()).andReturn();

        String jobIdA = new ObjectMapper()
                .readTree(postA.getResponse().getContentAsString()).get("jobId").asText();
        String jobIdB = new ObjectMapper()
                .readTree(postB.getResponse().getContentAsString()).get("jobId").asText();

        await().atMost(60, SECONDS).untilAsserted(() -> {
            String statusA = new ObjectMapper().readTree(mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobIdA).header("Authorization", "Bearer test-token"))
                    .andReturn().getResponse().getContentAsString()).get("status").asText();
            String statusB = new ObjectMapper().readTree(mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobIdB).header("Authorization", "Bearer test-token"))
                    .andReturn().getResponse().getContentAsString()).get("status").asText();
            String patchNumberA = new ObjectMapper().readTree(mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobIdA).header("Authorization", "Bearer test-token"))
                    .andReturn().getResponse().getContentAsString()).get("patchesSent").asText();
            String patchNumberB = new ObjectMapper().readTree(mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobIdB).header("Authorization", "Bearer test-token"))
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
    void postWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id", "dist-001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postWithNonAdminTokenReturns403() throws Exception {
        when(userInfoService.isSystemAdmin(any())).thenReturn(UserInfoService.AuthResult.FORBIDDEN);
        mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id", "dist-001")
                        .header("Authorization", "Bearer some-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postWithAdminTokenSucceeds() throws Exception {
        when(userInfoService.isSystemAdmin(any())).thenReturn(UserInfoService.AuthResult.AUTHORIZED);
        mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id", "dist-001")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isAccepted());
        verify(userInfoService, atLeastOnce()).isSystemAdmin(any());
    }

    @Test
    void getWithoutTokenReturns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/jobs/some-job-id"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getWithNonAdminTokenReturns403() throws Exception {
        MvcResult postResult = mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id", "dist-001")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isAccepted())
                .andReturn();
        String jobId = new ObjectMapper().readTree(postResult.getResponse().getContentAsString()).get("jobId").asText();

        when(userInfoService.isSystemAdmin(any())).thenReturn(UserInfoService.AuthResult.FORBIDDEN);
        mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobId)
                        .header("Authorization", "Bearer some-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getWithAdminTokenSucceeds() throws Exception {
        MvcResult postResult = mockMvc.perform(post("/jobs/delete-distribution")
                        .param("distribution-id", "dist-001")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isAccepted())
                .andReturn();
        String jobId = new ObjectMapper().readTree(postResult.getResponse().getContentAsString()).get("jobId").asText();

        mockMvc.perform(MockMvcRequestBuilders.get("/jobs/" + jobId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }
}
