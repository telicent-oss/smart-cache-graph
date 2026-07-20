package io.telicent.deletion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class UserInfoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserInfoService.class);
    private static final String ADMIN_SYSTEM_ROLE = "ADMIN_SYSTEM";

    private final HttpClient httpClient;
    private final String userInfoUrl;
    private final ObjectMapper objectMapper;

    public UserInfoService(@Value("${deletion-worker.auth.userinfo-url}") String userInfoUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.userInfoUrl = userInfoUrl;
        this.objectMapper = new ObjectMapper();
    }

    public enum AuthResult {
        AUTHORIZED,       // 200 from /userinfo + ADMIN_SYSTEM role present
        FORBIDDEN,        // 200 from /userinfo + ADMIN_SYSTEM role absent
        UNAUTHORIZED      // 401/non-200 from /userinfo — invalid/expired session
    }

    public AuthResult isSystemAdmin(String authorization) {
        try {
            // the Authorization header is the JWT access token
            // injected by Traefik after forward auth validation.
            // calls auth-server's /userinfo directly via internal hostname
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(userInfoUrl))
                    .header("Authorization", authorization)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) {
                LOGGER.warn("UserInfo returned 401 — invalid or expired session");
                return AuthResult.UNAUTHORIZED;
            }
            if (response.statusCode() != 200) {
                LOGGER.warn("UserInfo endpoint returned {} for role check", response.statusCode());
                return AuthResult.UNAUTHORIZED;
            }

            Map<String, Object> userInfo = objectMapper.readValue(response.body(), Map.class);
            List<String> roles = (List<String>) userInfo.get("roles");
            if (roles == null) {
                LOGGER.warn("No roles found in userinfo response");
                return AuthResult.FORBIDDEN;
            }
            boolean isAdmin = roles.stream().anyMatch(ADMIN_SYSTEM_ROLE::equalsIgnoreCase);
            LOGGER.info("User roles: {} — isAdmin: {}", roles, isAdmin);
            return isAdmin ? AuthResult.AUTHORIZED : AuthResult.FORBIDDEN;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("UserInfo request interrupted: {}", e.getMessage());
            return AuthResult.UNAUTHORIZED;
        } catch (IOException e) {
            LOGGER.error("Failed to call userinfo endpoint: {}", e.getMessage());
            return AuthResult.UNAUTHORIZED;
        }
    }
}