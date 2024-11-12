package io.telicent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.shaded.com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDatasetBackups {

    @Container
    public static TestDatasetBackupsContainer container = TestDatasetBackupsContainer.getInstance();

    @Test
    @Disabled
    @Order(1)
    public void isContainerRunning(){

        // service starts...
        assertTrue(container.isRunning());
    }

    @Test
    @Order(2)
    public void createBackupFolderUsingEnvironmentVariable() throws IOException, InterruptedException {

        // if... environment variable ENV_BACKUPS_DIR is defined
        container.stop();
        container.withEnv("ENV_BACKUPS_DIR", "testdir").start();

        // when... service is started

        // then... customized backups folder is created
        int result = container.execInContainer("test", "-d", "testdir").getExitCode();
        assertEquals(0,result);

    }

    @Test
    @Order(2)
    public void createDefaultBackupFolderOnStartup_1() throws IOException, InterruptedException {

        // if... ENV_BACKUPS_DIR is not defined
        int result = container.execInContainer("unset", "ENV_BACKUPS_DIR").getExitCode();
        assertEquals(0, 0);

        // when...  the service is started

        // then... the default backup folder [/backups] is created.
        result = container.execInContainer("test", "-d", "backups").getExitCode();
        assertEquals(0,result);

    }

    @Test
    @Order(2)
    public void createDefaultBackupFolderOnStartup_2() throws IOException, InterruptedException {

        // if... ENV_BACKUPS_DIR is not defined
        int result = container.execInContainer("export", "ENV_BACKUPS_DIR=").getExitCode();
        assertEquals(0, 0);

        // when...  the service is started

        // then... the default backup folder [/backups] is created.
        result = container.execInContainer("test", "-d", "backups").getExitCode();
        assertEquals(0,result);
    }

    @Test
    @Order(2)
    public void createDefaultBackupFolderOnStartup_3() throws IOException, InterruptedException {

        // if... ENV_BACKUPS_DIR is not defined
        int result = container.execInContainer("export", "ENV_BACKUPS_DIR=").getExitCode();
        assertEquals(0, 0);

        // when...  the service is started

        // then... the default backup folder [/backups] is created.
        result = container.execInContainer("test", "-d", "backups").getExitCode();
        assertEquals(0,result);
    }

    @Test
    @Order(2)
    public void createBackupFile() throws IOException, InterruptedException {

        // if... system is running

        // when...  dataset backup request is POSTed
        HttpResponse httpResponse = postDatasetBackupRequest("/$/backups", "ds");


        // then... a zipped/backup file is created in the default folder [/backup].
        int result = container.execInContainer("find", "backups", "-name", "*.nq.gz" ).getExitCode();
        assertEquals(0,result);

    }



    @BeforeAll
    public static void setup() {
        container.start();
    }

    @AfterAll
    public static void teardown() {
        container.stop();
    }

    private static HttpResponse<String> postDatasetBackupRequest(String endpoint, String dataset) {
        try {
            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI("http://localhost:" + container.getMappedPort(3030) + endpoint + "/" + dataset))
                        .POST(HttpRequest.BodyPublishers.noBody()).build();
                return client.send(request, HttpResponse.BodyHandlers.ofString());
            }
        } catch ( Exception e) { throw new RuntimeException(e); }
    }

}
