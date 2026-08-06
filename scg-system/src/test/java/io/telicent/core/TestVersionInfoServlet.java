package io.telicent.core;

import com.fasterxml.jackson.databind.JsonNode;
import io.telicent.LibTestsSCG;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import io.telicent.smart.caches.configuration.auth.AuthConstants;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.http.HttpEnv;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestVersionInfoServlet {

    private static final String DATASET_NAME = "/ds";
    private FusekiServer server;

    @AfterEach
    void cleanup() {
        Configurator.reset();
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void versionInfoEndpoint_whenServerRunning_returnsVersionInformation() throws IOException, InterruptedException {
        startServer();

        HttpResponse<String> response = getVersionInfo();
        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        JsonNode scgSystem = body.path("scg-system");

        assertEquals(200, response.statusCode());
        assertTrue(body.has("scg-system"));
        assertFalse(body.has("scg-server"));
        assertEquals("scg-system", scgSystem.path("artifactId").textValue());
        assertEquals("io.telicent.smart-caches.graph", scgSystem.path("groupId").textValue());
        assertEquals("Telicent Smart Cache Graph - System", scgSystem.path("name").textValue());
        assertFalse(scgSystem.path("version").textValue().isBlank());
        assertFalse(scgSystem.path("revision").textValue().isBlank());
        assertFalse(scgSystem.path("timestamp").textValue().isBlank());
        assertFalse(scgSystem.path("buildEnv").textValue().isBlank());
    }

    private void startServer() throws IOException {
        Properties properties = new Properties();
        properties.put(AuthConstants.ENV_JWKS_URL, AuthConstants.AUTH_DISABLED);
        Configurator.setSingleSource(new PropertiesSource(properties));
        LibTestsSCG.disableInitialCompaction();
        server = SmartCacheGraph.serverBuilder()
                                .port(0)
                                .add(DATASET_NAME, DatasetGraphFactory.createTxnMem())
                                .build()
                                .start();
    }

    private HttpResponse<String> getVersionInfo() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(URI.create(server.serverURL() + "version-info"))
                                         .GET()
                                         .build();
        return HttpEnv.getDftHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
