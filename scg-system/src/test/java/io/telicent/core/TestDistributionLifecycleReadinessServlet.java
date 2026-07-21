package io.telicent.core;

import io.telicent.LibTestsSCG;
import io.telicent.distribution.DistributionLifecycleStateFile;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDistributionLifecycleReadinessServlet {

    private static final String DATASET_NAME = "/ds";
    private FusekiServer server;

    @AfterEach
    void cleanup() {
        DistributionLifecycleReadiness.getInstance().reset();
        Configurator.reset();
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void readinessEndpoint_whenFilteringDisabled_returns200() throws IOException, InterruptedException {
        startServer(new Properties());

        HttpResponse<String> response = getReady();

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"ready\" : true"));
        assertTrue(response.body().contains("\"state\" : \"DISABLED\""));
    }

    @Test
    void readinessEndpoint_whenLifecycleStateUnavailable_returns503() throws IOException, InterruptedException {
        Properties properties = new Properties();
        properties.setProperty(FMod_DistributionLifecycle.ROUTE_TO_NAMED_GRAPHS, "true");
        properties.setProperty(FMod_DistributionLifecycle.DISTRIBUTION_LIFECYCLE_STATE_FILE,
                               "target/missing-readiness-state.json");
        startServer(properties);

        HttpResponse<String> response = getReady();

        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("\"state\" : \"EXTERNAL_ONLY\""));
        assertTrue(response.body().contains("still unavailable"));
    }

    @Test
    void readinessEndpoint_whenLifecycleStateAvailable_returns200() throws IOException, InterruptedException {
        Path stateFile = Files.createTempFile("scg-readiness-servlet-", ".json");
        Files.writeString(stateFile, """
                {
                  "application": "scg-test",
                  "distributions": {
                    "urn:distribution:one": "Active"
                  }
                }
                """, StandardCharsets.UTF_8);

        Properties properties = new Properties();
        properties.setProperty(FMod_DistributionLifecycle.ROUTE_TO_NAMED_GRAPHS, "true");
        properties.setProperty(FMod_DistributionLifecycle.DISTRIBUTION_LIFECYCLE_STATE_FILE, stateFile.toString());
        startServer(properties);

        HttpResponse<String> response = getReady();

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"ready\" : true"));
        assertTrue(response.body().contains("\"state\" : \"READY\""));
    }

    private void startServer(Properties properties) throws IOException {
        properties.put(AuthConstants.ENV_JWKS_URL, AuthConstants.AUTH_DISABLED);
        Configurator.setSingleSource(new PropertiesSource(properties));
        LibTestsSCG.disableInitialCompaction();
        server = SmartCacheGraph.serverBuilder()
                                .port(0)
                                .add(DATASET_NAME, DatasetGraphFactory.createTxnMem())
                                .build()
                                .start();
    }

    private HttpResponse<String> getReady() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(URI.create(server.serverURL() + "$/ready"))
                                         .GET()
                                         .build();
        return HttpEnv.getDftHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
