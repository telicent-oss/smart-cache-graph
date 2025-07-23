package io.telicent.backup.services;

import io.telicent.backup.utils.RSAKeyPairGenerator;
import io.telicent.core.MainSmartCacheGraph;
import io.telicent.labels.services.TestLabelsQuery;
import io.telicent.smart.cache.configuration.Configurator;
import org.apache.jena.fuseki.main.FusekiServer;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Security;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SystemStubsExtension.class)
public class TestBackupAndRestoreWithEncryption {

    @SystemStub
    protected static EnvironmentVariables ENV;

    protected static FusekiServer SERVER;

    protected static String BASE_URI;

    protected final static URL CONFIG_URL = TestLabelsQuery.class.getClassLoader().getResource("config-labels-query-test.ttl");

    private final static URL DATA1_URL = TestLabelsQuery.class.getClassLoader().getResource("test-data-labelled-1.trig");

    private final static URL DATA2_URL = TestLabelsQuery.class.getClassLoader().getResource("test-data-labelled-2.trig");

    private final static String DATASET1_NAME = "securedDataset1";

    private final static String DATASET2_NAME = "securedDataset2";

    static {
        // Add Bouncy castle to JVM
        if (Objects.isNull(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME))) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @BeforeAll
    public static void beforeAll() throws Exception {
        Configurator.reset();
        final Path privateKeyPath = Paths.get("private.asc");
        final Path publicKeyPath = Paths.get("public.asc");
        final URL privateKeyUrl = privateKeyPath.toUri().toURL();
        final URL publicKeyUrl = publicKeyPath.toUri().toURL();
        String passkey = "dummy";
        try (FileOutputStream privateOut = new FileOutputStream(privateKeyPath.toString());
             FileOutputStream publicOut = new FileOutputStream(publicKeyPath.toString())) {
            RSAKeyPairGenerator.generateAndExportKeyRing(privateOut, publicOut, "test@telicent.io", passkey.toCharArray(), true);
        }
        ENV.set("ENABLE_BACKUPS",true);
        ENV.set("JWKS_URL", "disabled");
        ENV.set("BACKUPS_PRIVATE_KEY_URL", privateKeyUrl);
        ENV.set("BACKUPS_PUBLIC_KEY_URL", publicKeyUrl);
        ENV.set("BACKUPS_PASSKEY", passkey);
        SERVER = MainSmartCacheGraph.buildAndRun("--config", CONFIG_URL.getPath());
        BASE_URI = "http://localhost:" + SERVER.getHttpPort();
        uploadData(DATA1_URL, DATASET1_NAME);
        uploadData(DATA2_URL, DATASET2_NAME);
    }

    @AfterAll
    public static void afterAll() throws IOException {
        SERVER.stop();
        FileUtils.deleteDirectory(new File("target/labels1"));
        FileUtils.deleteDirectory(new File("target/labels2"));
        FileUtils.delete(new File("public.asc"));
        FileUtils.delete(new File("private.asc"));
    }

    @Test
    public void test_backup_and_restore() throws Exception {
        callAndAssert("create/");
        callAndAssert("restore/1");
    }

    private static void callAndAssert(String action) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(new URI(BASE_URI + "/$/backups/" + action))
                .headers("accept", "*/*")
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
        }
    }

    private static void uploadData(URL dataUrl, String datasetName) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(new URI(BASE_URI + "/" + datasetName + "/upload"))
                .headers("Security-Label", "!", "Content-Type", "application/trig")
                .POST(HttpRequest.BodyPublishers.ofFile(Paths.get(dataUrl.toURI()))).build();
        try (final HttpClient client = HttpClient.newHttpClient()) {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
        }
    }

}
