package io.telicent.deletion.service;

import io.telicent.deletion.model.JobState;
import io.telicent.deletion.model.JobStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JobRegistry {
    private final ConcurrentHashMap<String, JobState> jobs = new ConcurrentHashMap<>();

    public JobState register(String distributionId) {
        String jobId = UUID.randomUUID().toString();
        JobState state = new JobState(jobId, distributionId, JobStatus.RUNNING, Instant.now(), null, 0);
        jobs.put(jobId, state);
        return state;
    }

    public Optional<JobState> find(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public void update(JobState state) {
        jobs.put(state.jobId(), state);
    }
}
