package io.telicent.deletion.controller;

import io.telicent.deletion.model.JobState;
import io.telicent.deletion.service.DeletionJobService;
import io.telicent.deletion.service.JobRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/jobs")
public class DeletionJobController {
    private final DeletionJobService jobService;
    private final JobRegistry registry;

    public DeletionJobController(DeletionJobService jobService, JobRegistry registry) {
        this.jobService = jobService;
        this.registry = registry;
    }

    @PostMapping("/delete-distribution")
    public ResponseEntity<Map<String, String>> deleteDistribution(@RequestParam("distribution-id") String distributionId) {
        if (distributionId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "distributionId is required"));
        }
        JobState jobState = registry.register(distributionId);
        jobService.runDeletionJob(jobState);
        return ResponseEntity.accepted()
                .body(Map.of("jobId", jobState.jobId()));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobState> getJobStatus(@PathVariable("jobId") String jobId) {
        return registry.find(jobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}
