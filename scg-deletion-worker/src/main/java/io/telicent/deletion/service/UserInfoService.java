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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.telicent.deletion.config.DeletionWorkerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class UserInfoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserInfoService.class);
    private static final String ADMIN_SYSTEM_ROLE = "ADMIN_SYSTEM";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final URI userInfoUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public UserInfoService(DeletionWorkerProperties properties) {
        this(userInfoUrl(properties), HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
             new ObjectMapper());
    }

    UserInfoService(String userInfoUrl, HttpClient httpClient, ObjectMapper objectMapper) {
        this.userInfoUrl = URI.create(userInfoUrl);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    private static String userInfoUrl(DeletionWorkerProperties properties) {
        final String url = properties.auth() != null ? properties.auth().userinfoUrl() : null;
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "deletion-worker.auth.userinfo-url must be configured (see the USERINFO_URL environment variable)");
        }
        return url;
    }

    public enum AuthResult {
        AUTHORIZED,       // 200 from /userinfo + ADMIN_SYSTEM role present
        FORBIDDEN,        // 200 from /userinfo + ADMIN_SYSTEM role absent
        UNAUTHORIZED      // 401/non-200 from /userinfo — invalid/expired session
    }

    /**
     * The presented JWT is never trusted on its own. It is exchanged for User Info at the Auth Server's
     * {@code /userinfo} endpoint, which both proves the token is valid and yields the authoritative roles for the
     * user. A self-signed JWT claiming {@code ADMIN_SYSTEM} therefore gets nowhere, as the Auth Server will reject it.
     *
     * @param authorization The {@code Authorization} request header, expected to carry a Bearer access token
     * @return Whether the caller is authorized, lacks the required role, or has no valid session
     */
    public AuthResult checkAdminRole(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return AuthResult.UNAUTHORIZED;
        }
        final String token = authorization.substring(BEARER_PREFIX.length()).strip();
        if (token.isEmpty()) {
            return AuthResult.UNAUTHORIZED;
        }

        final Optional<HttpResponse<String>> response = getUserInfoResponse(token);
        if (response.isEmpty()) {
            return AuthResult.UNAUTHORIZED;
        }

        if (response.get().statusCode() != 200) {
            LOGGER.warn("Rejecting request, {} returned status {}", this.userInfoUrl, response.get().statusCode());
            return AuthResult.UNAUTHORIZED;
        }

        final Optional<List<String>> roles = getRoles(response.get());
        if (roles.isEmpty()) {
            return AuthResult.UNAUTHORIZED;
        }

        boolean isAdmin = roles.get().stream().anyMatch(ADMIN_SYSTEM_ROLE::equalsIgnoreCase);
        LOGGER.info("User Info roles: {} — isAdmin: {}", roles, isAdmin);
        return isAdmin ? AuthResult.AUTHORIZED : AuthResult.FORBIDDEN;
    }

    /**
     * The subset of the Auth Server's {@code /userinfo} response that we care about
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserInfoResponse(List<String> roles) {
        UserInfoResponse {
            roles = roles != null ? roles : Collections.emptyList();
        }
    }

    private Optional<HttpResponse<String>> getUserInfoResponse(String token) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(this.userInfoUrl)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Authorization", BEARER_PREFIX + token)
                    .GET()
                    .build();
            return Optional.of(this.httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while calling {}", this.userInfoUrl);
            return Optional.empty();
        } catch (IOException e) {
            // Fail closed - if we cannot confirm the session is valid then we must not authorize the request
            LOGGER.error("Failed to call {}: {}", this.userInfoUrl, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<List<String>> getRoles(HttpResponse<String> response) {
        try {
            return Optional.of(this.objectMapper.readValue(response.body(), UserInfoResponse.class).roles());
        } catch (IOException e) {
            LOGGER.error("Failed to parse the response from {}: {}", this.userInfoUrl, e.getMessage());
            return Optional.empty();
        }
    }

}
