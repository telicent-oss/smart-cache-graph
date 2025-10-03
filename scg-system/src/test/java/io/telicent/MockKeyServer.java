package io.telicent;

import io.jsonwebtoken.*;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import io.jsonwebtoken.security.*;
import io.telicent.servlet.auth.jwt.JwtHttpConstants;
import io.telicent.servlet.auth.jwt.verification.SignedJwtVerifier;
import io.telicent.smart.caches.configuration.auth.AuthConstants;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.Strings;
import org.apache.jena.fuseki.main.JettyServer;
import org.apache.jena.riot.WebContent;
import org.apache.jena.sys.JenaSystem;
import org.junit.platform.commons.util.StringUtils;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.security.Key;
import java.security.KeyPair;
import java.util.*;

public class MockKeyServer {

    static {
        JenaSystem.init();
    }

    private final JwkSet privateKeys;
    private final JwkSet publicKeys;
    private final List<String> keyIds;
    private final JettyServer server;
    private final int port;
    private final Random random = new Random();

    public MockKeyServer(int port) {
        this.port = port;

        // Generate some Key Pairs using a variety of different algorithms
        List<KeyPair> keyPairs = List.of(Jwts.SIG.ES256.keyPair().build(), Jwts.SIG.ES384.keyPair().build(),
                                         Jwts.SIG.ES512.keyPair().build());
        JwkSetBuilder privateJwks = Jwks.set();
        JwkSetBuilder publicJwks = Jwks.set();
        keyPairs.forEach(p -> {
            privateJwks.add(Jwks.builder().keyPair(p).idFromThumbprint().build());
            publicJwks.add(Jwks.builder().key(p.getPublic()).idFromThumbprint().build());
        });
        this.privateKeys = privateJwks.build();
        this.publicKeys = publicJwks.build();

        this.keyIds = new ArrayList<>();
        this.publicKeys.getKeys().stream().forEach(k -> this.keyIds.add(k.getId()));

        this.server = JettyServer.create()
                                 .port(this.port)
                                 .loopback(true)
                                 .contextPath("/")
                                 .addServlet("/jwks.json", new JwksServlet(this.publicKeys))
                                 .addServlet("/aws/*", new AwsElbServlet(this.publicKeys))
                                 .addServlet("/userinfo", new UserInfoServlet(this.publicKeys))
                                 .build();
    }

    public void start() {
        if (!this.server.getJettyServer().isStarted()) {
            this.server.start();
        }
    }

    public void stop() {
        if (this.server.getJettyServer().isStarted()) {
            this.server.stop();
        }
    }

    public String getJwksUrl() {
        return String.format("http://localhost:%d/jwks.json", this.port);
    }

    public String getAwsElbUrlFormat() {
        return String.format("http://localhost:%d/aws/", this.port) + "%s";
    }

    public String getUserInfoUrl() {
        return String.format("http://localhost:%d/userinfo", this.port);
    }

    public String selectKeyId() {
        return this.keyIds.get(random.nextInt(0, this.keyIds.size()));
    }

    public Key getPrivateKey(String keyId) {
        return this.privateKeys.getKeys()
                               .stream()
                               .filter(k -> Objects.equals(k.getId(), keyId))
                               .map(Jwk::toKey)
                               .findFirst()
                               .orElse(null);
    }

    private static final class JwksServlet extends HttpServlet {

        private final JwkSet jwks;
        private static final JacksonSerializer<Object> SERIALIZER = new JacksonSerializer<>();

        public JwksServlet(JwkSet jwks) {
            this.jwks = jwks;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType(WebContent.contentTypeJSON);
            resp.setStatus(HttpServletResponse.SC_OK);
            SERIALIZER.serialize(this.jwks, resp.getOutputStream());
            resp.getOutputStream().close();
        }
    }

    private static final class AwsElbServlet extends HttpServlet {
        private final JwkSet jwks;

        public AwsElbServlet(JwkSet jwks) {
            this.jwks = jwks;
        }


        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            String keyId = req.getRequestURI().substring(req.getRequestURI().lastIndexOf('/') + 1);
            Jwk<?> key = jwks.getKeys().stream().filter(k -> Objects.equals(k.getId(), keyId)).findFirst().orElse(null);
            if (key == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            } else {
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.setContentType(WebContent.contentTypeTextPlain);
                resp.getOutputStream().println("---BEGIN PUBLIC KEY---");
                resp.getOutputStream().println(Base64.getEncoder().encodeToString(key.toKey().getEncoded()));
                resp.getOutputStream().println("---END PUBLIC KEY---");
                resp.getOutputStream().close();
            }
        }
    }

    private static final class UserInfoServlet extends HttpServlet {
        private final JwkSet jwks;
        private final SignedJwtVerifier verifier;
        private final ObjectMapper json = new ObjectMapper();

        public UserInfoServlet(JwkSet jwks) {
            this.jwks = jwks;
            this.verifier = new SignedJwtVerifier(new LocatorAdapter<Key>() {
                @Override
                protected Key locate(JwsHeader header) {
                    Key key = jwks.getKeys()
                                  .stream()
                                  .filter(k -> Objects.equals(k.getId(), header.getKeyId()))
                                  .findFirst()
                                  .map(Jwk::toKey)
                                  .orElse(null);
                    if (key == null) {
                        throw new InvalidKeyException(
                                "Failed to locate key " + header.getKeyId() + " which was used to sign the presented JWT");
                    } else {
                        return key;
                    }
                }
            });
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            String authHeader = req.getHeader(JwtHttpConstants.HEADER_AUTHORIZATION);
            if (StringUtils.isBlank(authHeader)) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                resp.setContentType(WebContent.contentTypeTextPlain);
                resp.getWriter().write("No JWT presented");
                return;
            }

            String token = Strings.CI.removeStart(authHeader, JwtHttpConstants.AUTH_SCHEME_BEARER).strip();
            Jws<Claims> jws = this.verifier.verify(token);
            Claims claims = jws.getPayload();

            // Create a mock response, similar to Auth Server
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("sub", claims.getSubject());
            userInfo.put("preferred_name", claims.get("preferred_name", String.class));
            userInfo.put("roles", claims.getOrDefault("roles", new String[] { "USER" }));
            userInfo.put("permissions", claims.getOrDefault("permissions", new String[] { "api.read" }));
            userInfo.put("attributes", claims.getOrDefault("attributes", Map.of()));

            resp.setContentType(WebContent.contentTypeJSON);
            resp.setStatus(HttpServletResponse.SC_OK);
            this.json.writeValue(resp.getOutputStream(), userInfo);
        }
    }
}
