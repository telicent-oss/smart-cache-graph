package io.telicent.deletion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
        this.httpClient = HttpClient.newHttpClient();
        this.userInfoUrl = userInfoUrl;
        this.objectMapper = new ObjectMapper();
    }

    public boolean isSystemAdmin(String authorization) {
        try {
            LOGGER.warn("isSystemAdmin called");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(userInfoUrl))
                    .header("Authorization", authorization)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOGGER.warn("UserInfo endpoint returned {} for role check", response.statusCode());
                return false;
            }

            Map<String, Object> userInfo = objectMapper.readValue(response.body(), Map.class);
            List<String> roles = (List<String>) userInfo.get("roles");
            if (roles == null) {
                LOGGER.warn("No roles found in userinfo response");
                return false;
            }

            return roles.stream().anyMatch(ADMIN_SYSTEM_ROLE::equalsIgnoreCase);

        } catch (IOException | InterruptedException e) {
            LOGGER.error("Failed to call userinfo endpoint: {}", e.getMessage());
            return false;
        }
    }
}