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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.telicent.deletion.config.DeletionWorkerProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests that {@link UserInfoService} authorizes on the basis of what the Auth Server's {@code /userinfo} endpoint says,
 * rather than on the unverified contents of the presented JWT.
 */
class UserInfoServiceTest {

    /**
     * An unsigned JWT whose payload claims the {@code ADMIN_SYSTEM} role, i.e. what an attacker forging a token would
     * present.
     */
    private static final String FORGED_ADMIN_JWT = "Bearer " + jwt("{\"roles\":[\"ADMIN_SYSTEM\"]}");

    private HttpServer userInfoServer;
    private UserInfoService service;

    /** The Authorization header seen by the last request to the mock /userinfo endpoint */
    private final AtomicReference<String> receivedAuthorization = new AtomicReference<>();
    /** The response the mock /userinfo endpoint should return */
    private final AtomicReference<Response> userInfoResponse = new AtomicReference<>();

    private record Response(int status, String body) {}

    @BeforeEach
    void setUp() throws IOException {
        userInfoServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        userInfoServer.createContext("/userinfo", this::handle);
        userInfoServer.start();
        service = new UserInfoService(
                "http://localhost:" + userInfoServer.getAddress().getPort() + "/userinfo",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        userInfoServer.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        receivedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        try (final InputStream ignored = exchange.getRequestBody()) {
            final Response response = userInfoResponse.get();
            final byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    @Test
    @DisplayName("A token the Auth Server rejects is unauthorized even when its payload claims the admin role")
    void checkAdminRole_whenUserInfoRejectsForgedToken_isUnauthorized() {
        // given
        userInfoResponse.set(new Response(401, "{\"error\":\"invalid_token\"}"));
        // when
        final UserInfoService.AuthResult result = service.checkAdminRole(FORGED_ADMIN_JWT);
        // then
        assertEquals(UserInfoService.AuthResult.UNAUTHORIZED, result);
    }

    @Test
    @DisplayName("The roles returned by /userinfo are used, not the roles claimed by the JWT")
    void checkAdminRole_whenUserInfoOmitsAdminRole_isForbidden() {
        // given a valid session for a user who is not an admin, presenting a JWT claiming that they are
        userInfoResponse.set(new Response(200, "{\"sub\":\"user-1\",\"roles\":[\"USER\"]}"));
        // when
        final UserInfoService.AuthResult result = service.checkAdminRole(FORGED_ADMIN_JWT);
        // then
        assertEquals(UserInfoService.AuthResult.FORBIDDEN, result);
    }

    @Test
    @DisplayName("A valid session with the admin role is authorized")
    void checkAdminRole_whenUserInfoReturnsAdminRole_isAuthorized() {
        // given
        userInfoResponse.set(new Response(200, "{\"sub\":\"admin-1\",\"roles\":[\"USER\",\"ADMIN_SYSTEM\"]}"));
        // when
        final UserInfoService.AuthResult result = service.checkAdminRole("Bearer valid-token");
        // then
        assertEquals(UserInfoService.AuthResult.AUTHORIZED, result);
        assertEquals("Bearer valid-token", receivedAuthorization.get());
    }

    @Test
    @DisplayName("The admin role is matched case insensitively")
    void checkAdminRole_whenRoleDiffersInCase_isAuthorized() {
        // given
        userInfoResponse.set(new Response(200, "{\"roles\":[\"admin_system\"]}"));
        // when
        final UserInfoService.AuthResult result = service.checkAdminRole("Bearer valid-token");
        // then
        assertEquals(UserInfoService.AuthResult.AUTHORIZED, result);
    }

    @Test
    @DisplayName("A /userinfo response with no roles is forbidden")
    void checkAdminRole_whenUserInfoHasNoRoles_isForbidden() {
        // given
        userInfoResponse.set(new Response(200, "{\"sub\":\"user-1\"}"));
        // when
        final UserInfoService.AuthResult result = service.checkAdminRole("Bearer valid-token");
        // then
        assertEquals(UserInfoService.AuthResult.FORBIDDEN, result);
    }

    @Test
    @DisplayName("An unparseable /userinfo response is unauthorized")
    void checkAdminRole_whenUserInfoResponseIsNotJson_isUnauthorized() {
        // given
        userInfoResponse.set(new Response(200, "not json"));
        // when
        final UserInfoService.AuthResult result = service.checkAdminRole("Bearer valid-token");
        // then
        assertEquals(UserInfoService.AuthResult.UNAUTHORIZED, result);
    }

    @Test
    @DisplayName("An unreachable /userinfo endpoint fails closed")
    void checkAdminRole_whenUserInfoUnreachable_isUnauthorized() {
        // given
        userInfoServer.stop(0);
        // when
        final UserInfoService.AuthResult result = service.checkAdminRole(FORGED_ADMIN_JWT);
        // then
        assertEquals(UserInfoService.AuthResult.UNAUTHORIZED, result);
    }

    @Test
    @DisplayName("A missing or non-Bearer Authorization header is rejected without calling /userinfo")
    void checkAdminRole_whenHeaderUnusable_isUnauthorizedWithoutCallout() {
        // given
        userInfoResponse.set(new Response(200, "{\"roles\":[\"ADMIN_SYSTEM\"]}"));
        // when / then
        assertEquals(UserInfoService.AuthResult.UNAUTHORIZED, service.checkAdminRole(null));
        assertEquals(UserInfoService.AuthResult.UNAUTHORIZED, service.checkAdminRole("Basic dXNlcjpwYXNz"));
        assertEquals(UserInfoService.AuthResult.UNAUTHORIZED, service.checkAdminRole("Bearer "));
        assertNull(receivedAuthorization.get());
    }

    @Test
    @DisplayName("Startup fails when the userinfo URL is not configured")
    void construct_whenUserInfoUrlMissing_throws() {
        // given
        final DeletionWorkerProperties noAuth = new DeletionWorkerProperties(null, "RDF", null);
        final DeletionWorkerProperties blankUrl =
                new DeletionWorkerProperties(null, "RDF", new DeletionWorkerProperties.Auth("  "));
        // when / then
        assertThrows(IllegalStateException.class, () -> new UserInfoService(noAuth));
        assertThrows(IllegalStateException.class, () -> new UserInfoService(blankUrl));
    }

    /** Builds an unsigned JWT with the given payload, as an attacker would */
    private static String jwt(String payload) {
        final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".";
    }
}
