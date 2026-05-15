package io.telicent.deletion.model;

import java.time.Instant;

public record JobState(
        String jobId,
        String distributionId,
        JobStatus status,
        Instant startedAt,
        String errorMessage
) {
    public JobState withStatus(JobStatus newStatus) {
        return new JobState(jobId, distributionId, newStatus, startedAt, null);
    }

    public JobState withFailure(String error) {
        return new JobState(jobId, distributionId, JobStatus.FAILED, startedAt, error);
    }
}
