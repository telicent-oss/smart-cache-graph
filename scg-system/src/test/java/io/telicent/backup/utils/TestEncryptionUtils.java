package io.telicent.backup.utils;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestEncryptionUtils {

    @TempDir
    Path tempDir;

    private final URL zippedBackupUrl = loadResource("/backup.zip");
    private static URL privateKeyUrl;
    private static URL publicKeyUrl;
    private static final String passkey = "dummy";

    static {
        // Add Bouncy castle to JVM
        if (Objects.isNull(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME))) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @BeforeAll
    public static void setUp() throws NoSuchAlgorithmException, NoSuchProviderException, IOException, PGPException {
        final Path privateKeyPath = Paths.get("private.asc");
        final Path publicKeyPath = Paths.get("public.asc");
        privateKeyUrl = privateKeyPath.toUri().toURL();
        publicKeyUrl = publicKeyPath.toUri().toURL();
        try (FileOutputStream privateOut = new FileOutputStream(privateKeyPath.toString());
             FileOutputStream publicOut = new FileOutputStream(publicKeyPath.toString())) {
            RSAKeyPairGenerator.generateAndExportKeyRing(privateOut, publicOut, "test@telicent.io", passkey.toCharArray(), true);
        }
    }

    @AfterEach
    public void cleanup() throws IOException {
        try (Stream<Path> stream = Files.walk(tempDir)) {
            stream.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @AfterAll
    public static void tearDown() throws URISyntaxException, IOException {
        Files.deleteIfExists(Path.of(privateKeyUrl.toURI()));
        Files.deleteIfExists(Path.of(publicKeyUrl.toURI()));
    }

    @Test
    public void testEncryptAndDecrypt() throws URISyntaxException, PGPException, IOException {
        final EncryptionUtils encryptionUtils = new EncryptionUtils(privateKeyUrl.openStream(), passkey);
        final Path encryptedFile = encryptionUtils.encryptFile(Paths.get(zippedBackupUrl.toURI()), tempDir.resolve("output.enc"), publicKeyUrl);
        assertNotNull(encryptedFile);
        final Path decryptedFile = encryptionUtils.decryptFile(encryptedFile, tempDir.resolve("output.zip"));
        assertNotNull(decryptedFile);
    }

    private static URL loadResource(String resourcePath) {
        return Optional.ofNullable(TestEncryptionUtils.class.getResource(resourcePath))
                .orElseThrow(() -> new IllegalArgumentException(String.format("Resource %s not found", resourcePath)));
    }

}
