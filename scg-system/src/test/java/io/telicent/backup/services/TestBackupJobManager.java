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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests the asynchronous job bookkeeping in {@link BackupJobManager}, in particular the failure paths:
 * a task that reports a non 2xx status, and a task that throws.
 */
public class TestBackupJobManager {

    private static final String OPERATION = "TEST_OPERATION";
    private static final String STATUS_PATH_PREFIX = "/$/backups/jobs/";

    /**
     * @param errorMessage value for the "error" field, or null to omit the field entirely
     * @return a response body
     */
    private static ObjectNode body(String errorMessage) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        if (null != errorMessage) {
            node.put("error", errorMessage);
        }
        return node;
    }

    private static String submit(BackupJobManager manager, Callable<BackupOperationResponse> task) {
        // Note: the status in the submission response is deliberately not asserted. The job may already
        // have been picked up by the executor by the time submit() returns.
        return manager.submit(OPERATION, STATUS_PATH_PREFIX, task).path("job-id").asText();
    }

    private static ObjectNode awaitTerminalState(BackupJobManager manager, String jobId) {
        for (int attempt = 0; attempt < 200; attempt++) {
            ObjectNode status = manager.getJob(jobId).orElseThrow();
            String state = status.path("status").asText();
            if (BackupJobManager.STATUS_SUCCEEDED.equals(state) || BackupJobManager.STATUS_FAILED.equals(state)) {
                return status;
            }
            try {
                Thread.sleep(25L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return fail("Interrupted while waiting for job " + jobId);
            }
        }
        return fail("Job " + jobId + " did not reach a terminal state");
    }

    @Test
    @DisplayName("A task reporting a server error fails the job and surfaces the error from the body")
    public void test_submit_serverErrorWithErrorField_failsWithBodyMessage() {
        // given
        BackupJobManager manager = new BackupJobManager();
        // when
        String jobId = submit(manager, () -> new BackupOperationResponse(500, body("no space left on device")));
        ObjectNode status = awaitTerminalState(manager, jobId);
        // then
        assertEquals(BackupJobManager.STATUS_FAILED, status.path("status").asText());
        assertEquals("no space left on device", status.path("message").asText());
        assertEquals(500, status.path("http-status").asInt());
        assertEquals(STATUS_PATH_PREFIX + jobId, status.path("status-path").asText());
        assertEquals(OPERATION, status.path("operation").asText());
        assertTrue(status.has("completed-at"));
        assertTrue(status.has("result"));
    }

    @Test
    @DisplayName("A task reporting a server error with no error field fails with the default message")
    public void test_submit_serverErrorWithoutErrorField_failsWithDefaultMessage() {
        // given
        BackupJobManager manager = new BackupJobManager();
        // when
        String jobId = submit(manager, () -> new BackupOperationResponse(503, body(null)));
        ObjectNode status = awaitTerminalState(manager, jobId);
        // then
        assertEquals(BackupJobManager.STATUS_FAILED, status.path("status").asText());
        assertEquals("Job failed.", status.path("message").asText());
        assertEquals(503, status.path("http-status").asInt());
    }

    @Test
    @DisplayName("A task reporting an informational status below 200 is also treated as a failure")
    public void test_submit_statusBelowTwoHundred_isTreatedAsFailure() {
        // given
        BackupJobManager manager = new BackupJobManager();
        // when
        String jobId = submit(manager, () -> new BackupOperationResponse(100, body(null)));
        ObjectNode status = awaitTerminalState(manager, jobId);
        // then
        assertEquals(BackupJobManager.STATUS_FAILED, status.path("status").asText());
        assertEquals(100, status.path("http-status").asInt());
    }

    @Test
    @DisplayName("A task that throws fails the job with a 500 and the exception message")
    public void test_submit_taskThrows_failsWithUnhandledError() {
        // given
        BackupJobManager manager = new BackupJobManager();
        // when
        String jobId = submit(manager, () -> {
            throw new IllegalStateException("backup aborted");
        });
        ObjectNode status = awaitTerminalState(manager, jobId);
        // then
        assertEquals(BackupJobManager.STATUS_FAILED, status.path("status").asText());
        assertEquals(500, status.path("http-status").asInt());
        assertEquals("backup aborted", status.path("message").asText());
        assertEquals("backup aborted", status.path("result").path("error").asText());
    }

    @Test
    @DisplayName("A successful task whose body carries an error field reports that error as the message")
    public void test_submit_successWithErrorField_takesMessageFromBody() {
        // given
        BackupJobManager manager = new BackupJobManager();
        // when
        String jobId = submit(manager, () -> new BackupOperationResponse(200, body("one dataset was skipped")));
        ObjectNode status = awaitTerminalState(manager, jobId);
        // then
        assertEquals(BackupJobManager.STATUS_SUCCEEDED, status.path("status").asText());
        assertEquals("one dataset was skipped", status.path("message").asText());
    }

    @Test
    @DisplayName("A successful task with no error field reports the default success message")
    public void test_submit_success_reportsDefaultMessage() {
        // given
        BackupJobManager manager = new BackupJobManager();
        // when
        String jobId = submit(manager, () -> new BackupOperationResponse(201, body(null)));
        ObjectNode status = awaitTerminalState(manager, jobId);
        // then
        assertEquals(BackupJobManager.STATUS_SUCCEEDED, status.path("status").asText());
        assertEquals("Job completed successfully.", status.path("message").asText());
        assertEquals(201, status.path("http-status").asInt());
        assertTrue(status.has("started-at"));
    }

    @Test
    @DisplayName("An unknown job id is not found")
    public void test_getJob_unknownId_isEmpty() {
        // given
        BackupJobManager manager = new BackupJobManager();
        // when
        // then
        assertFalse(manager.getJob("no-such-job").isPresent());
    }

    @Test
    @DisplayName("A submitted job is listed by listJobs")
    public void test_listJobs_containsSubmittedJob() {
        // given
        BackupJobManager manager = new BackupJobManager();
        // when
        String jobId = submit(manager, () -> new BackupOperationResponse(200, body(null)));
        awaitTerminalState(manager, jobId);
        // then
        boolean found = false;
        for (JsonNode job : manager.listJobs()) {
            if (jobId.equals(job.path("job-id").asText())) {
                found = true;
            }
        }
        assertTrue(found, "Expected listJobs() to include job " + jobId);
    }
}
