package io.telicent;

import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.http.HttpEnv;
import org.apache.jena.http.HttpLib;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.apache.jena.http.HttpLib.execute;
import static org.apache.jena.http.HttpLib.toRequestURI;
import static org.apache.jena.riot.web.HttpNames.METHOD_POST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDatasetBackups {

    @Container
    public static TestDatasetBackupsContainer container = TestDatasetBackupsContainer.getInstance();

    @Test
    @Order(1)
    public void isContainerRunning(){
        assertTrue(container.isRunning());
    }

    @Test
    @Order(2)
    public void createBackupFile() throws IOException, InterruptedException {

        int result = container.execInContainer("test", "-d", "backups").getExitCode();
        assertEquals(1,result);

        // send backup request
        result = container.execInContainer( "curl", "-X", "POST", "http://localhost:3030" + "/$/backups/ds1" ).getExitCode();

        result = container.execInContainer("test", "-d", "backups").getExitCode();
//        assertEquals(0,result);

    }

    @BeforeAll
    public static void setup() {
        container.start();
    }

    @AfterAll
    public static void teardown() {
        container.stop();
    }

//    public static HttpResponse<String> makePOSTCallWithPath(String path) throws URISyntaxException {
//        HttpClient client = HttpClient.newHttpClient();
//        HttpRequest request = HttpRequest.newBuilder()
//            .uri(new URI("" + path))
//            .POST(HttpRequest.BodyPublishers.noBody()).build();
//        return client.send();
//    }

}
