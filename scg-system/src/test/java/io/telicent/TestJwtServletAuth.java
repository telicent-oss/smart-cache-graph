package io.telicent;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.telicent.core.FMod_JwtServletAuth;
import io.telicent.core.SmartCacheGraph;
import io.telicent.servlet.auth.jwt.PathExclusion;
import io.telicent.servlet.auth.jwt.verification.JwtVerifier;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import io.telicent.smart.caches.configuration.auth.AuthConstants;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static graphql.Assert.assertNotEmpty;
import static io.telicent.servlet.auth.jwt.JwtServletConstants.ATTRIBUTE_JWT_VERIFIER;
import static io.telicent.servlet.auth.jwt.JwtServletConstants.ATTRIBUTE_PATH_EXCLUSIONS;
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
                new PathExclusion("/$/stats/*"),
                new PathExclusion("/$/compact/*")
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

    private void disableAuth() {
        Properties properties = new Properties();
        properties.put(AuthConstants.ENV_JWKS_URL, AuthConstants.AUTH_DISABLED);
        Configurator.addSource(new PropertiesSource(properties));
    }

    class TestJwtVerifier implements JwtVerifier {

        @Override
        public Jws<Claims> verify(String s) {
            return null;
        }
    }

    static void assertExclusionListsEqual(List<PathExclusion> expected, List<PathExclusion> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).getPattern(), actual.get(i).getPattern());
        }
    }
}
