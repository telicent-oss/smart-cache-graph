package io.telicent.deletion.controller;

import io.telicent.deletion.model.JobState;
import io.telicent.deletion.service.DeletionJobService;
import io.telicent.deletion.service.JobRegistry;
import io.telicent.deletion.service.UserInfoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/jobs")
public class DeletionJobController {
    private final DeletionJobService jobService;
    private final JobRegistry registry;
    private final UserInfoService userInfoService;

    public DeletionJobController(DeletionJobService jobService, JobRegistry registry, UserInfoService userInfoService) {
        this.jobService = jobService;
        this.registry = registry;
        this.userInfoService = userInfoService;
    }

    @PostMapping("/delete-distribution")
    public ResponseEntity<Map<String, String>> deleteDistribution(
            @RequestParam("distribution-id") String distributionId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        if (authorization == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                     .body(Map.of("error", "Authorization header is required"));
        }

        switch (userInfoService.isSystemAdmin(authorization, request)) {
            case UNAUTHORIZED -> {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid or expired session"));
            }
            case FORBIDDEN -> {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "ROLE_ADMIN_SYSTEM required"));
            }
            case AUTHORIZED -> {}
        }

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
    public ResponseEntity<?> getJobStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("jobId") String jobId,
            HttpServletRequest request) {
        if (authorization == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authorization header is required"));
        }
        switch (userInfoService.isSystemAdmin(authorization, request)) {
            case UNAUTHORIZED -> {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid or expired session"));
            }
            case FORBIDDEN -> {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "ROLE_ADMIN_SYSTEM required"));
            }
            case AUTHORIZED -> {}
        }

        return registry.find(jobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
