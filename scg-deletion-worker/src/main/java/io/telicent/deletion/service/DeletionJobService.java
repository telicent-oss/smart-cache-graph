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

package io.telicent.deletion.service;

import io.telicent.deletion.DeletionJobConsumer;
import io.telicent.deletion.DeletionJobException;
import io.telicent.deletion.DeletionJobProducer;
import io.telicent.deletion.RDFPatchInverter;
import io.telicent.deletion.config.DeletionWorkerProperties;
import io.telicent.deletion.model.JobState;
import io.telicent.deletion.model.JobStatus;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

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
        AtomicInteger patchesSent = new AtomicInteger();

        LOGGER.info("[{}] Starting deletion job for distribution '{}'", jobId, distributionId);

        try (DeletionJobConsumer consumer = new DeletionJobConsumer(
                bootstrapServers, properties.kafka().configFilePath(), topic, distributionId, jobId);
             DeletionJobProducer producer = new DeletionJobProducer(
                     bootstrapServers, properties.kafka().configFilePath(), new RDFPatchInverter(), topic, distributionId, jobId)) {

            consumer.process(record -> {
                try {
                    Optional<RecordMetadata> sentRecord = producer.sendDeletePatch(record);
                    if (sentRecord.isPresent()) {
                        patchesSent.getAndIncrement();
                    }
                } catch (ExecutionException e) {
                    throw new DeletionJobException(
                            "Failed to send delete patch for offset " + record.offset(), e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new DeletionJobException(
                            "Deletion job interrupted at offset " + record.offset(), e);
                }
            });

            registry.update(jobState.withStatus(
                    JobStatus.COMPLETED, patchesSent.get()));
            LOGGER.info("[{}] Deletion job completed", jobId);

        } catch (Exception e) {
            registry.update(jobState.withFailure(e.getMessage()));
            LOGGER.error("[{}] Deletion job failed: {}", jobId, e.getMessage());
        }
    }
}
