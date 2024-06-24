package io.telicent;

import io.jsonwebtoken.Jwts;
import org.apache.jena.atlas.lib.FileOps;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.http.HttpOp;
import org.apache.jena.sparql.engine.http.QueryExceptionHTTP;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.exec.RowSetOps;
import org.apache.jena.sparql.exec.http.QueryExecHTTPBuilder;
import org.apache.jena.web.HttpSC;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static io.telicent.TestSmartCacheGraphIntegration.launchServer;
import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractBearerAuthTests {
    protected static final String DIR = "target/databases";
    private static final String QUERY = "SELECT * {?s ?p ?o}";
    protected static FusekiServer server;
    protected static String URL;

    protected static void setupFuseki() {
        FileOps.ensureDir(DIR);
        FileOps.clearAll(DIR);
        FusekiLogging.setLogging();
        server = launchServer("config-simple-auth.ttl");
        URL = "http://localhost:" + server.getHttpPort() + "/ds";
    }

    protected static void teardownFuseki() {
        if (null != server) {
            server.stop();
        }
    }

    @Test
    void givenValidToken_whenMakingARequest_thenSuccess() {
        // given
        String validToken = LibTestsSCG.tokenForUser("u1");
        // when
        RowSet rowSet = QueryExecHTTPBuilder.service(URL)
                                            .query(QUERY)
                                            .httpHeader(LibTestsSCG.tokenHeader(),
                                                        LibTestsSCG.tokenHeaderValue(validToken))
                                            .select();
        // then
        assertNotNull(rowSet);
        assertEquals(0, RowSetOps.count(rowSet));
    }


    private static void verifyRequestFailure(String token, String header, int expectedStatus) {
        // given
        Throwable actual = null;
        try {
            // when
            LibTestsSCG.withLevel(Fuseki.actionLog, "ERROR",
                                  () -> QueryExecHTTPBuilder.service(URL)
                                      .query(QUERY)
                                      .httpHeader(header, LibTestsSCG.tokenHeaderValue(token))
                                      .select());
        } catch (Throwable e) {
            actual = e;
        }
        // then
        assertNotNull(actual);
        assertInstanceOf(QueryExceptionHTTP.class, actual);
        QueryExceptionHTTP q = (QueryExceptionHTTP) actual;
        assertEquals(expectedStatus, q.getStatusCode());
    }

    @Test
    void givenValidTokenInWrongHeader_whenMakingARequest_thenUnauthorized() {
        verifyRequestFailure(LibTestsSCG.tokenForUser("u2"), "X-Custom", HttpSC.UNAUTHORIZED_401);
    }

    @Test
    void givenExpiredToken_whenMakingARequest_thenUnauthorized() {
        verifyRequestFailure(LibTestsSCG.tokenBuilder("u1")
                                        .expiration(Date.from(Instant.now().minus(5, ChronoUnit.MINUTES)))
                                        .compact(), LibTestsSCG.tokenHeader(), HttpSC.UNAUTHORIZED_401);
    }

    @Test
    void givenFutureDatedToken_whenMakingARequest_thenUnauthorized() {
        verifyRequestFailure(LibTestsSCG.tokenBuilder("u1")
                                        .notBefore(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                                        .compact(), LibTestsSCG.tokenHeader(), HttpSC.UNAUTHORIZED_401);
    }

    @Test
    void givenTokenSignedWithWrongKey_whenMakingARequest_thenBadRequest() {
        verifyRequestFailure(
                LibTestsSCG.tokenBuilder("u1").signWith(Jwts.SIG.RS512.keyPair().build().getPrivate()).compact(),
                LibTestsSCG.tokenHeader(), HttpSC.BAD_REQUEST_400);
    }

    @Test
    void givenTokenWithIncorrectKeyId_whenMakingARequest_thenUnauthorized() {
        verifyRequestFailure(
                LibTestsSCG.tokenBuilder("u1").header().keyId("wrong").and().compact(),
                LibTestsSCG.tokenHeader(), HttpSC.UNAUTHORIZED_401);
    }

    @Test
    void givenNonJwtToken_whenMakingARequest_thenUnauthorized() {
        verifyRequestFailure("username:password", LibTestsSCG.tokenHeader(), HttpSC.UNAUTHORIZED_401);
    }

    @Test
    void givenValidTokenForNonExistentUser_whenMakingARequest_thenForbidden() {
        verifyRequestFailure(LibTestsSCG.tokenForUser("u3"), LibTestsSCG.tokenHeader(), 403);
    }

    @Test
    void givenNoToken_whenMakingARequest_thenUnauthorized() {
        // given
        Throwable actual = null;
        try {
            // when
            QueryExecHTTPBuilder.service(URL).query(QUERY).select();
        } catch (Throwable e) {
            actual = e;
        }
        // then
        assertNotNull(actual);
        assertInstanceOf(QueryExceptionHTTP.class, actual);
        QueryExceptionHTTP q = (QueryExceptionHTTP) actual;
        assertEquals(401, q.getStatusCode());
        assertEquals("Unauthorized", q.getMessage());
    }

    @Test
    void givenNoToken_whenMakingAPingRequest_thenSuccess() {
        // Given

        // When
        String result = HttpOp.httpGetString("http://localhost:" + server.getHttpPort() + "/$/ping");

        // Then
        assertNotNull(result);
    }
}
