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

import io.telicent.deletion.service.DeletionJobService;
import io.telicent.deletion.service.JobRegistry;
import io.telicent.deletion.service.UserInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests the authorization outcomes of {@link DeletionJobController}, which decide the response before any
 * deletion work is scheduled.
 */
class DeletionJobControllerTest {

    private static final String AUTHORIZATION = "Bearer test-token";
    private static final String DISTRIBUTION_ID = "distribution-1";
    private static final String JOB_ID = "job-1";
    private static final String ERROR = "error";

    private UserInfoService userInfoService;
    private DeletionJobService jobService;
    private JobRegistry registry;
    private DeletionJobController controller;

    @BeforeEach
    void setUp() {
        userInfoService = mock(UserInfoService.class);
        jobService = mock(DeletionJobService.class);
        registry = mock(JobRegistry.class);
        controller = new DeletionJobController(jobService, registry, userInfoService);
    }

    @Test
    @DisplayName("Deleting a distribution without the admin role is forbidden")
    void deleteDistribution_whenAdminRoleAbsent_isForbidden() {
        // given
        when(userInfoService.checkAdminRole(AUTHORIZATION)).thenReturn(UserInfoService.AuthResult.FORBIDDEN);
        // when
        ResponseEntity<Map<String, String>> response = controller.deleteDistribution(DISTRIBUTION_ID, AUTHORIZATION);
        // then
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(Map.of(ERROR, "ROLE_ADMIN_SYSTEM required"), response.getBody());
        verifyNoInteractions(registry, jobService);
    }

    @Test
    @DisplayName("Deleting a distribution with an invalid session is unauthorized")
    void deleteDistribution_whenSessionInvalid_isUnauthorized() {
        // given
        when(userInfoService.checkAdminRole(AUTHORIZATION)).thenReturn(UserInfoService.AuthResult.UNAUTHORIZED);
        // when
        ResponseEntity<Map<String, String>> response = controller.deleteDistribution(DISTRIBUTION_ID, AUTHORIZATION);
        // then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(Map.of(ERROR, "Invalid or expired session"), response.getBody());
        verifyNoInteractions(registry, jobService);
    }

    @Test
    @DisplayName("Deleting a distribution with no Authorization header is unauthorized")
    void deleteDistribution_whenHeaderMissing_isUnauthorized() {
        // given
        // when
        ResponseEntity<Map<String, String>> response = controller.deleteDistribution(DISTRIBUTION_ID, null);
        // then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(Map.of(ERROR, "Authorization header is required"), response.getBody());
    }

    @Test
    @DisplayName("Reading job status without the admin role is forbidden")
    void getJobStatus_whenAdminRoleAbsent_isForbidden() {
        // given
        when(userInfoService.checkAdminRole(AUTHORIZATION)).thenReturn(UserInfoService.AuthResult.FORBIDDEN);
        // when
        ResponseEntity<?> response = controller.getJobStatus(AUTHORIZATION, JOB_ID);
        // then
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(Map.of(ERROR, "ROLE_ADMIN_SYSTEM required"), response.getBody());
        verifyNoInteractions(registry);
    }

    @Test
    @DisplayName("Reading job status with an invalid session is unauthorized")
    void getJobStatus_whenSessionInvalid_isUnauthorized() {
        // given
        when(userInfoService.checkAdminRole(AUTHORIZATION)).thenReturn(UserInfoService.AuthResult.UNAUTHORIZED);
        // when
        ResponseEntity<?> response = controller.getJobStatus(AUTHORIZATION, JOB_ID);
        // then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(Map.of(ERROR, "Invalid or expired session"), response.getBody());
    }

    @Test
    @DisplayName("Reading job status with no Authorization header is unauthorized")
    void getJobStatus_whenHeaderMissing_isUnauthorized() {
        // given
        // when
        ResponseEntity<?> response = controller.getJobStatus(null, JOB_ID);
        // then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(Map.of(ERROR, "Authorization header is required"), response.getBody());
    }
}
