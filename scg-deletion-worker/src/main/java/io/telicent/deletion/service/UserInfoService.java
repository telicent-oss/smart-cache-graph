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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class UserInfoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserInfoService.class);
    private static final String ADMIN_SYSTEM_ROLE = "ADMIN_SYSTEM";

    private final ObjectMapper objectMapper;

    public UserInfoService() {
        this.objectMapper = new ObjectMapper();
    }

    public enum AuthResult {
        AUTHORIZED,       // 200 from /userinfo + ADMIN_SYSTEM role present
        FORBIDDEN,        // 200 from /userinfo + ADMIN_SYSTEM role absent
        UNAUTHORIZED      // 401/non-200 from /userinfo — invalid/expired session
    }

    public AuthResult checkAdminRole(String authorization) {
        // the Authorization header is the JWT access token
        // injected by Traefik after forward auth validation.
        // calls auth-server's /userinfo directly via internal hostname
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return AuthResult.UNAUTHORIZED;
        }
        try {
            // Extract the JWT payload and remove "Bearer"
            String token = authorization.substring(7);
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                LOGGER.warn("Invalid JWT format");
                return AuthResult.UNAUTHORIZED;
            }
            String payload = new String(
                    Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8
            );

            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);
            List<String> roles = (List<String>) claims.get("roles");

            if (roles == null) {
                LOGGER.warn("No roles claim found in JWT");
                return AuthResult.FORBIDDEN;
            }
            boolean isAdmin = roles.stream().anyMatch(ADMIN_SYSTEM_ROLE::equalsIgnoreCase);
            LOGGER.info("JWT roles: {} — isAdmin: {}", roles, isAdmin);
            return isAdmin ? AuthResult.AUTHORIZED : AuthResult.FORBIDDEN;

        } catch (Exception e) {
            LOGGER.error("Failed to parse JWT: {}", e.getMessage());
            return AuthResult.UNAUTHORIZED;
        }
    }
}