package io.telicent;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.telicent.core.FMod_InitialCompaction;
import io.telicent.core.auth.FMod_JwtServletAuth;
import io.telicent.core.SmartCacheGraph;
import io.telicent.jena.abac.core.Attributes;
import io.telicent.servlet.auth.jwt.PathExclusion;
import io.telicent.servlet.auth.jwt.verification.JwtVerifier;
import io.telicent.servlet.auth.jwt.verifier.aws.AwsConstants;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import io.telicent.smart.caches.configuration.auth.AuthConstants;
import org.apache.jena.atlas.web.HttpException;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.cmds.FusekiMain;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.http.HttpEnv;
import org.apache.jena.http.HttpLib;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Properties;

import static graphql.Assert.assertNotEmpty;
import static io.telicent.servlet.auth.jwt.JwtServletConstants.ATTRIBUTE_JWT_VERIFIER;
import static io.telicent.servlet.auth.jwt.JwtServletConstants.ATTRIBUTE_PATH_EXCLUSIONS;
import static org.apache.jena.graph.Graph.emptyGraph;
import static org.apache.jena.http.HttpLib.*;
import static org.apache.jena.riot.web.HttpNames.METHOD_GET;
import static org.apache.jena.riot.web.HttpNames.METHOD_POST;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

public class TestJwtServletAuth {

    @AfterEach
    void clear() {
        Configurator.reset();
    }

    @Test
    public void test_name() {
        // given
        FMod_JwtServletAuth jwtServletAuth = new FMod_JwtServletAuth();
        // when
        String name = jwtServletAuth.name();
        // then
        assertNotNull(name);
    }

    @Test
    public void test_prepare_disabledAuth() {
        // given
        disableAuth();
        FMod_JwtServletAuth jwtServletAuth = new FMod_JwtServletAuth();

        FusekiServer.Builder mockBuilder = mock(FusekiServer.Builder.class);
        Model mockConfig = mock(Model.class);

        // when
        jwtServletAuth.prepare(mock(), null, mockConfig);

        // then
        verifyNoInteractions(mockBuilder, mockConfig);
    }

    @Test
    public void test_prepare_noVerifier() {
        FMod_JwtServletAuth jwtServletAuth = new FMod_JwtServletAuth();

        FusekiServer.Builder mockBuilder = mock(FusekiServer.Builder.class);
        Model mockConfig = mock(Model.class);

        // when
        Throwable actualException = null;
        try {
            jwtServletAuth.prepare(mock(), null, mockConfig);
        } catch (Exception e) {
            actualException = e;
        }

        // then
        verifyNoInteractions(mockBuilder, mockConfig);
        assertInstanceOf(RuntimeException.class, actualException);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void test_prepare_happyPath() {
        // given
        List<PathExclusion> expectedList = List.of(
                new PathExclusion("/$/ping"),
                new PathExclusion("/$/metrics"),
                new PathExclusion("/\\$/stats/*")
        );
        FMod_JwtServletAuth jwtServletAuth = new FMod_JwtServletAuth();
        FusekiServer.Builder builder = SmartCacheGraph.serverBuilder().addServletAttribute(ATTRIBUTE_JWT_VERIFIER, new TestJwtVerifier());
        // when
        jwtServletAuth.prepare(builder, null, null);
        // then
        Object actualExclusions = builder.getServletAttribute(ATTRIBUTE_PATH_EXCLUSIONS);
        assertNotNull(actualExclusions);
        List<PathExclusion> actualList = (List<PathExclusion>) actualExclusions;
        assertNotEmpty(actualList);
        assertExclusionListsEqual(expectedList, actualList);
    }

    @Test
    public void test_exclusionLogic() {
        FMod_JwtServletAuth jwtServletAuth = new FMod_JwtServletAuth();
        FMod_InitialCompaction initialCompaction = new FMod_InitialCompaction ();
        Attributes.buildStore(emptyGraph);
        FusekiServer server = FusekiMain.builder("--port=0", "--empty")
                .fusekiModules(FusekiModules.create(List.of(jwtServletAuth, initialCompaction)))
                .addServletAttribute(ATTRIBUTE_JWT_VERIFIER, new TestJwtVerifier())
                .enablePing(true)
                .enableMetrics(true)
                .enableStats(true)
                .build().start();

        // Correct path
        HttpResponse<InputStream> pingResponse = makePOSTCallWithPath(server, "$/ping");
        assertEquals(200, pingResponse.statusCode());

        // Correct path
        HttpResponse<InputStream> metricsResponse = makePOSTCallWithPath(server, "$/metrics");
        assertEquals(200, metricsResponse.statusCode());

        // Fails - due to missing path but NOT due to Auth.
        HttpResponse<InputStream> statsResponse = makePOSTCallWithPath(server, "$/stats/unrecognised");
        assertEquals(404, statsResponse.statusCode());
        HttpException httpException = assertThrows(HttpException.class, () -> handleResponseNoBody(statsResponse));
        assertTrue(httpException.getResponse().startsWith("/unrecognised"));

        // Fails - due to Auth not path which is missing
        HttpResponse<InputStream> otherResponse = makePOSTCallWithPath(server, "$/unrecognisedPath");
        assertEquals(401, otherResponse.statusCode());
        httpException = assertThrows(HttpException.class, () -> handleResponseNoBody(otherResponse));
        assertTrue(httpException.getResponse().contains("Unauthorized"));

        server.stop();
    }

    private void disableAuth() {
        Properties properties = new Properties();
        properties.put(AuthConstants.ENV_JWKS_URL, AuthConstants.AUTH_DISABLED);
        Configurator.addSource(new PropertiesSource(properties));
    }

    private static class TestJwtVerifier implements JwtVerifier {

        @Override
        public Jws<Claims> verify(String s) {
            return null;
        }
    }

    private static void assertExclusionListsEqual(List<PathExclusion> expected, List<PathExclusion> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).getPattern(), actual.get(i).getPattern());
        }
    }

    public static HttpResponse<InputStream> makePOSTCallWithPath(FusekiServer server, String path) {
        HttpRequest.Builder builder =
                HttpLib.requestBuilderFor(server.serverURL())
                        .uri(toRequestURI(server.serverURL()+ path))
                        .method(METHOD_POST, HttpRequest.BodyPublishers.noBody());
        return execute(HttpEnv.getDftHttpClient(), builder.build());
    }
    public static HttpResponse<InputStream> makeAuthPOSTCallWithPath(FusekiServer server, String path, String user) {
        return makeAuthCallWithPathForMethod(server, path, user, METHOD_POST);
    }

    public static HttpResponse<InputStream> makeAuthGETCallWithPath(FusekiServer server, String path, String user) {
        return makeAuthCallWithPathForMethod(server, path, user, METHOD_GET);
    }

    public static HttpResponse<InputStream> makeAuthCallWithCustomToken(FusekiServer server, String path, String jwt, String method) {
        HttpRequest.Builder builder =
                HttpLib.requestBuilderFor(server.serverURL())
                       .uri(toRequestURI(server.serverURL() + path))
                       .headers(AwsConstants.HEADER_DATA, jwt)
                       .method(method, HttpRequest.BodyPublishers.noBody());
        return execute(HttpEnv.getDftHttpClient(), builder.build());
    }

    public static HttpResponse<InputStream> makeAuthCallWithPathForMethod(FusekiServer server, String path, String user, String method) {
        HttpRequest.Builder builder =
                HttpLib.requestBuilderFor(server.serverURL())
                        .uri(toRequestURI(server.serverURL() + path))
                        .headers(AwsConstants.HEADER_DATA, LibTestsSCG.tokenForUser(user))
                        .method(method, HttpRequest.BodyPublishers.noBody());
        return execute(HttpEnv.getDftHttpClient(), builder.build());
    }

}
