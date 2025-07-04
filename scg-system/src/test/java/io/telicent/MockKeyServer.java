package io.telicent;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.JwkSetBuilder;
import io.jsonwebtoken.security.Jwks;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.fuseki.main.JettyServer;
import org.apache.jena.riot.WebContent;
import org.apache.jena.sys.JenaSystem;

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
}
