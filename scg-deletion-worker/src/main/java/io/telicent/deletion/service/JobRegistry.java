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
