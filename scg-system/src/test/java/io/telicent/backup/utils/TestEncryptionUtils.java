package io.telicent.backup.utils;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Security;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestEncryptionUtils {

    @TempDir
    Path tempDir;

    private final URL zippedBackupUrl = loadResource("/backup.zip");
    private final URL encryptedBackupUrl = loadResource("/backup.enc");
    private final URL privateKeyUrl = loadResource("/private.pgp");
    private final URL publicKeyUrl = loadResource("/public.pgp");
    private static final String passkey = "dummy";

    static {
        // Add Bouncy castle to JVM
        if (Objects.isNull(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME))) {
            Security.addProvider(new BouncyCastleProvider());
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

    @Test
    public void testEncrypt() throws URISyntaxException, PGPException, IOException {
        final EncryptionUtils encryptionUtils = new EncryptionUtils(privateKeyUrl.openStream(), passkey);
        final Path encryptedFile = encryptionUtils.encryptFile(Paths.get(zippedBackupUrl.toURI()), tempDir.resolve("output.enc"), publicKeyUrl);
        assertNotNull(encryptedFile);
    }

    @Test
    public void testDecrypt() throws URISyntaxException, PGPException, IOException {
        final EncryptionUtils encryptionUtils = new EncryptionUtils(privateKeyUrl.openStream(), passkey);
        final Path decryptedFile = encryptionUtils.decryptFile(Paths.get(encryptedBackupUrl.toURI()), tempDir.resolve("output.zip"));
        assertNotNull(decryptedFile);
    }

    private static URL loadResource(String resourcePath) {
        return Optional.ofNullable(TestEncryptionUtils.class.getResource(resourcePath))
                .orElseThrow(() -> new IllegalArgumentException(String.format("Resource %s not found", resourcePath)));
    }

}
