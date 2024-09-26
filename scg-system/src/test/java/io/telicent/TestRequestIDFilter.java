package io.telicent;

import io.telicent.core.SmartCacheGraph;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import io.telicent.smart.caches.configuration.auth.AuthConstants;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.http.HttpEnv;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static io.telicent.LibTestsSCG.disableInitialCompaction;
import static org.junit.jupiter.api.Assertions.*;

public class TestRequestIDFilter {

    private static final Properties AUTH_DISABLED_PROPERTIES = new Properties();
    private static final PropertiesSource AUTH_DISABLED_SOURCE = new PropertiesSource(AUTH_DISABLED_PROPERTIES);

    private static final String DATASET_NAME = "/ds";
    private static final String REQUEST_ID = "Request-ID";
    private static final String EXPECTED_REQUEST_ID = UUID.randomUUID().toString();

    private static FusekiServer server;
    private static URI uri;

    @BeforeAll
    static void createAndSetupFusekiServer(){
        AUTH_DISABLED_PROPERTIES.put(AuthConstants.ENV_JWKS_URL, AuthConstants.AUTH_DISABLED);
        Configurator.setSingleSource(AUTH_DISABLED_SOURCE);
        disableInitialCompaction();
        server = SmartCacheGraph.smartCacheGraphBuilder()
                                             .port(0)
                                             .add(DATASET_NAME, DatasetGraphFactory.empty())
                                             .build()
                                             .start();

        uri = URI.create(server.datasetURL(DATASET_NAME));
    }

    @AfterAll
    static void stopFusekiServer(){
        if (null != server)
            server.stop();
    }

    @Test
    void make_request_with_existing_id() throws IOException, InterruptedException {
        // given
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(uri)
                                         .headers(REQUEST_ID, EXPECTED_REQUEST_ID)
                                         .GET()
                                         .build();
        // when
        HttpResponse<Void> response = HttpEnv.getDftHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        // then
        assertEquals(200, response.statusCode());
        Optional<String> optionalHeader =  response.headers().firstValue(REQUEST_ID);
        assertTrue(optionalHeader.isPresent());
        assertTrue(optionalHeader.get().startsWith(EXPECTED_REQUEST_ID));
    }

    @Test
    void make_identical_requests_with_existing_id() throws IOException, InterruptedException {
        // given
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(uri)
                                         .headers(REQUEST_ID, EXPECTED_REQUEST_ID)
                                         .GET()
                                         .build();
        // when
        HttpResponse<Void> response = HttpEnv.getDftHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        HttpResponse<Void> nextResponse = HttpEnv.getDftHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        // then
        assertEquals(200, response.statusCode());
        Optional<String> optionalHeader =  response.headers().firstValue(REQUEST_ID);
        assertTrue(optionalHeader.isPresent());
        String firstResponseRequestId = optionalHeader.get();
        assertTrue(firstResponseRequestId.startsWith(EXPECTED_REQUEST_ID));


        assertEquals(200, response.statusCode());
        Optional<String> nextOptionalHeader =  nextResponse.headers().firstValue(REQUEST_ID);
        assertTrue(nextOptionalHeader.isPresent());
        String secondResponseRequestId = nextOptionalHeader.get();
        assertTrue(secondResponseRequestId.startsWith(EXPECTED_REQUEST_ID));

        assertNotEquals(firstResponseRequestId, secondResponseRequestId);

    }

    @Test
    void make_request_without_id() throws IOException, InterruptedException {
        // given
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(uri)
                                         .GET()
                                         .build();
        // when
        HttpResponse<Void> response = HttpEnv.getDftHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        // then
        assertEquals(200, response.statusCode());
        Optional<String> optionalHeader =  response.headers().firstValue(REQUEST_ID);
        assertTrue(optionalHeader.isPresent());
        String responseRequestId = optionalHeader.get();
        assertFalse(responseRequestId.startsWith(EXPECTED_REQUEST_ID)); // It'll be randomly created
    }

    @Test
    void make_request_with_existing_id_too_long() throws IOException, InterruptedException {
        // given
        String randomID = UUID.randomUUID().toString();
        String longRequestId = randomID + EXPECTED_REQUEST_ID;

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(uri)
                                         .headers(REQUEST_ID, longRequestId)
                                         .GET()
                                         .build();
        // when
        HttpResponse<Void> response = HttpEnv.getDftHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        // then
        assertEquals(200, response.statusCode());
        Optional<String> optionalHeader =  response.headers().firstValue(REQUEST_ID);
        assertTrue(optionalHeader.isPresent());
        String responseRequestId = optionalHeader.get();
        assertFalse(responseRequestId.contains(EXPECTED_REQUEST_ID));
        assertTrue(responseRequestId.startsWith(randomID));
    }
}
