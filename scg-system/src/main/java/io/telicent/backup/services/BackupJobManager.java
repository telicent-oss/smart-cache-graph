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

package io.telicent.backup.services;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;

public class BackupJobManager {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    private static final Logger LOG = LoggerFactory.getLogger(BackupJobManager.class);

    private final Map<String, BackupJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        final Thread thread = new Thread(r, "scg-backup-jobs");
        thread.setDaemon(true);
        return thread;
    });

    public ObjectNode submit(final String operation,
                             final String statusPathPrefix,
                             final Callable<BackupOperationResponse> task) {
        final BackupJob job = new BackupJob(UUID.randomUUID().toString(), operation, statusPathPrefix);
        this.jobs.put(job.jobId, job);
        this.executor.submit(() -> {
            try {
                job.markRunning();
                final BackupOperationResponse response = task.call();
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    job.markSucceeded(response);
                } else {
                    job.markFailed(response);
                }
            } catch (Exception e) {
                LOG.error("Unhandled backup job failure for {}", operation, e);
                final ObjectNode body = OBJECT_MAPPER.createObjectNode();
                body.put("error", e.getMessage());
                job.markFailed(new BackupOperationResponse(500, body));
            }
        });
        return job.submission();
    }

    public ArrayNode listJobs() {
        final ArrayNode jobsArray = OBJECT_MAPPER.createArrayNode();
        this.jobs.values()
                 .stream()
                 .sorted(Comparator.comparing((BackupJob job) -> job.submittedAt).reversed())
                 .map(BackupJob::status)
                 .forEach(jobsArray::add);
        return jobsArray;
    }

    public Optional<ObjectNode> getJob(final String jobId) {
        return Optional.ofNullable(this.jobs.get(jobId)).map(BackupJob::status);
    }

    private static final class BackupJob {
        private final String jobId;
        private final String operation;
        private final String statusPath;
        private final Instant submittedAt = Instant.now();
        private volatile String status = STATUS_PENDING;
        private volatile String message = "Waiting to run.";
        private volatile Instant startedAt;
        private volatile Instant completedAt;
        private volatile Integer httpStatus;
        private volatile ObjectNode result;

        private BackupJob(final String jobId, final String operation, final String statusPathPrefix) {
            this.jobId = jobId;
            this.operation = operation;
            this.statusPath = statusPathPrefix + jobId;
        }

        private void markRunning() {
            this.status = STATUS_RUNNING;
            this.message = "Job is running.";
            this.startedAt = Instant.now();
        }

        private void markSucceeded(final BackupOperationResponse response) {
            this.status = STATUS_SUCCEEDED;
            this.completedAt = Instant.now();
            this.httpStatus = response.statusCode();
            this.result = response.body().deepCopy();
            this.message = response.body().has("error") ? response.body().get("error").asText() :
                           "Job completed successfully.";
        }

        private void markFailed(final BackupOperationResponse response) {
            this.status = STATUS_FAILED;
            this.completedAt = Instant.now();
            this.httpStatus = response.statusCode();
            this.result = response.body().deepCopy();
            this.message = response.body().has("error") ? response.body().get("error").asText() :
                           "Job failed.";
        }

        private ObjectNode submission() {
            final ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("job-id", this.jobId);
            node.put("operation", this.operation);
            node.put("status", this.status);
            node.put("status-path", this.statusPath);
            node.put("message", "Job accepted for asynchronous execution.");
            return node;
        }

        private ObjectNode status() {
            final ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("job-id", this.jobId);
            node.put("operation", this.operation);
            node.put("status", this.status);
            node.put("status-path", this.statusPath);
            node.put("message", this.message);
            node.put("submitted-at", this.submittedAt.toString());
            if (this.startedAt != null) {
                node.put("started-at", this.startedAt.toString());
            }
            if (this.completedAt != null) {
                node.put("completed-at", this.completedAt.toString());
            }
            if (this.httpStatus != null) {
                node.put("http-status", this.httpStatus);
            }
            if (this.result != null) {
                node.set("result", this.result.deepCopy());
            }
            return node;
        }
    }
}
