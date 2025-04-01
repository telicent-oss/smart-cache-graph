package io.telicent.access;

import io.telicent.LibTestsSCG;
import io.telicent.jena.abac.fuseki.SysFusekiABAC;
import io.telicent.smart.cache.configuration.Configurator;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static io.telicent.LibTestsSCG.tokenHeader;
import static io.telicent.LibTestsSCG.tokenHeaderValue;
import static io.telicent.core.SmartCacheGraph.construct;

public class TestAccessBase {

    private static final Path DIR = Path.of("src/test/files");

    private HttpClient httpClient;

    protected static final String SERVICE_NAME_1 = "ds1";
    protected static final String SERVICE_NAME_2 = "ds2";

    protected FusekiServer server;

    @BeforeEach
    void setUp() throws Exception {
        FusekiLogging.setLogging();
        SysFusekiABAC.init();
        LibTestsSCG.setupAuthentication();
        LibTestsSCG.disableInitialCompaction();
        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void clearDown() throws Exception {
        LibTestsSCG.teardownAuthentication();
        if (null != server) {
            server.stop();
        }
        Configurator.reset();
    }

    @AfterAll
    static void afterAll() throws Exception {
        FileUtils.deleteDirectory(new File("labels"));
    }

    protected void startServer() {
        final List<String> arguments = List.of("--conf", DIR + "/access-query-test-config.ttl");
        server = construct(arguments.toArray(new String[0])).start();
    }

    /**
     * Calls the upload endpoint passing in two distinct datasets for two different service endpoints
     */
    protected void loadData() {
        LibTestsSCG.uploadFile(server.serverURL() + SERVICE_NAME_1 + "/upload", DIR + "/access-query-data-labelled-ds1.trig");
        LibTestsSCG.uploadFile(server.serverURL() + SERVICE_NAME_2 + "/upload", DIR + "/access-query-data-labelled-ds2.trig");
    }

    protected String callServiceEndpoint(final String requestBody, final String user, final String serviceName, final String endpoint) throws Exception {
        return callServiceEndpoint(requestBody, user, serviceName, endpoint, "");
    }

    protected String callServiceEndpoint(final String requestBody, final String user, final String serviceName, final String endpoint, final String queryParams) throws Exception {
        final String accessQueryUrl = server.serverURL() + serviceName + endpoint + queryParams;
        final String bearerToken = LibTestsSCG.tokenForUser(user);
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(accessQueryUrl))
                .header("Content-type", "application/json")
                .header(tokenHeader(), tokenHeaderValue(bearerToken))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
