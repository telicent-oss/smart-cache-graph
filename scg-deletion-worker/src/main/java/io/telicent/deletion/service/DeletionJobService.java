package io.telicent.deletion.service;

import io.telicent.deletion.DeletionJobConsumer;
import io.telicent.deletion.DeletionJobProducer;
import io.telicent.deletion.RDFPatchInverter;
import io.telicent.deletion.config.DeletionWorkerProperties;
import io.telicent.deletion.model.JobState;
import io.telicent.deletion.model.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

@Service
public class DeletionJobService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeletionJobService.class);

    private final DeletionWorkerProperties properties;
    private final JobRegistry registry;

    public DeletionJobService(DeletionWorkerProperties properties, JobRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    @Async
    public void runDeletionJob(JobState jobState) {
        String jobId = jobState.jobId();
        String distributionId = jobState.distributionId();
        String bootstrapServers = properties.kafka().bootstrapServers();
        String topic = properties.topic();

        LOGGER.info("[{}] Starting deletion job for distribution '{}'", jobId, distributionId);

        try (DeletionJobConsumer consumer = new DeletionJobConsumer(
                bootstrapServers, properties.kafka().configFilePath(), topic, distributionId, jobId);
             DeletionJobProducer producer = new DeletionJobProducer(
                     bootstrapServers, properties.kafka().configFilePath(), new RDFPatchInverter(), topic, distributionId, jobId)) {

            //TODO
            // handling exceptions on send
            consumer.process(record -> {
                try {
                    producer.sendDeletePatch(record);
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            registry.update(jobState.withStatus(
                    JobStatus.COMPLETED));
            LOGGER.info("[{}] Deletion job completed", jobId);

        } catch (Exception e) {
            registry.update(jobState.withFailure(e.getMessage()));
            LOGGER.error("[{}] Deletion job failed: {}", jobId, e.getMessage());
        }
    }
}
