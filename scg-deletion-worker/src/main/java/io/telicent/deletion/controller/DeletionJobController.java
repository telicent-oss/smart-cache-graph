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
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                     .body(Map.of("error", "Authorization header is required"));
        }

        switch (userInfoService.checkAdminRole(authorization)) {
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
            @PathVariable("jobId") String jobId) {
        if (authorization == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authorization header is required"));
        }
        switch (userInfoService.checkAdminRole(authorization)) {
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
