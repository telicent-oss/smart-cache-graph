package io.telicent.deletion.model;

import java.time.Instant;

public record JobState(
        String jobId,
        String distributionId,
        JobStatus status,
        Instant startedAt,
        String errorMessage,
        Integer patchesSent
) {
    public JobState withStatus(JobStatus newStatus, Integer patchesSent) {
        return new JobState(jobId, distributionId, newStatus, startedAt, null, patchesSent);
    }

    public JobState withFailure(String error) {
        return new JobState(jobId, distributionId, JobStatus.FAILED, startedAt, error, null);
    }
}
