package io.telicent.backup.utils;


import org.apache.commons.io.IOUtils;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.PublicKeyDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.jcajce.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Date;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

public class EncryptionUtils {

    private static final Logger LOG = LoggerFactory.getLogger(EncryptionUtils.class);


    private final int compressionAlgorithm;
    private final int symmetricKeyAlgorithm;
    private final boolean armor;
    private final boolean withIntegrityCheck;
    private final int bufferSize;

    // decryption vars
    private final char[] passkey;
    private final PGPSecretKeyRingCollection pgpSecretKeyRingCollection;

    /**
     * Fully configurable constructor
     */
    public EncryptionUtils(
            InputStream privateKeyIn,
            String passkey,
            int compressionAlgorithm,
            int symmetricKeyAlgorithm,
            boolean armor,
            boolean withIntegrityCheck,
            int bufferSize) throws IOException, PGPException {
        this.passkey = passkey.toCharArray();
        this.pgpSecretKeyRingCollection = new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(privateKeyIn)
                , new JcaKeyFingerprintCalculator());
        this.compressionAlgorithm = compressionAlgorithm;
        this.symmetricKeyAlgorithm = symmetricKeyAlgorithm;
        this.armor = armor;
        this.withIntegrityCheck = withIntegrityCheck;
        this.bufferSize = bufferSize;
    }

    /**
     * Constructor with default values
     */
    public EncryptionUtils(InputStream privateKeyIn, String passkey) throws IOException, PGPException {
        this(privateKeyIn,
                passkey,
                CompressionAlgorithmTags.ZIP,
                SymmetricKeyAlgorithmTags.AES_128,
                true,
                true,
                1 << 16);
    }

    /**
     * Creates an encrypted copy of the file at the inputFilePath at the outputFilePath using the provided public key
     *
     * @param inputFilePath  the Path of the file to encrypt
     * @param outputFilePath the Path of the encrypted file to create
     * @param publicKey      the URL of the public key to use
     * @return the file path of the encrypted file
     * @throws IOException  if there is a problem reading the files
     * @throws PGPException if there is a problem with the encryption
     */
    public Path encryptFile(Path inputFilePath, Path outputFilePath, URL publicKey) throws IOException, PGPException {
        LOG.info("Encrypting input file {} to output file {}.", inputFilePath, outputFilePath);
        try (OutputStream fos = Files.newOutputStream(outputFilePath);
             InputStream clearIn = Files.newInputStream(inputFilePath);
             InputStream publicKeyIn = publicKey.openStream()) {
            encrypt(fos, clearIn, inputFilePath.toFile().length(), publicKeyIn);
        }
        LOG.info("Successfully encrypted input file {} to {}.", inputFilePath, outputFilePath);
        return outputFilePath;
    }

    /**
     * Creates a decrypted copy of the file at the inputFilePath at the outputFilePath
     *
     * @param inputFilePath  the Path of the file to decrypt
     * @param outputFilePath the Path of the decrypted file to create
     * @return the file path of the decrypted file
     * @throws IOException  if there is a problem reading the files
     * @throws PGPException if there is a problem with the decryption
     */
    public Path decryptFile(Path inputFilePath, Path outputFilePath) throws IOException, PGPException {
        LOG.info("Decrypting input file {} to output file {}.", inputFilePath, outputFilePath);
        try (OutputStream fos = Files.newOutputStream(outputFilePath)) {
            decrypt(Files.newInputStream(inputFilePath), fos);
        }
        LOG.info("Successfully decrypted input file {} to {}.", inputFilePath, outputFilePath);
        return outputFilePath;
    }

    /**
     * Encrypts {@code clearIn} (up to {@code length} bytes) to {@code encryptOut} using
     * the provided public key stream.
     * <p>
     * Each PGP wrapping layer (compression, encryption, optional ASCII armor) must be
     * closed for its trailing packet to be written — closure is part of the encryption
     * protocol, not just resource hygiene. Using nested try-with-resources guarantees
     * closure in the correct reverse-construction order on every exit path (including
     * when {@code copyAsLiteralData} throws).
     * <p>
     * The original implementation closed these streams manually, which leaked them on a
     * thrown {@link PGPException} and meant a partially-written output file could be
     * left behind looking valid. This version closes them under all conditions.
     * <p>
     * NB: closing the outermost PGP stream cascades down to {@code encryptOut}. The
     * caller's try-with-resources on {@code encryptOut} is a defence-in-depth no-op
     * (double-close on a closed {@link OutputStream} is harmless).
     */
    private void encrypt(OutputStream encryptOut, InputStream clearIn, long length, InputStream publicKeyIn)
            throws IOException, PGPException {
        final PGPEncryptedDataGenerator pgpEncryptedDataGenerator = new PGPEncryptedDataGenerator(
                // This bit here configures the encrypted data generator
                new JcePGPDataEncryptorBuilder(symmetricKeyAlgorithm)
                        .setWithIntegrityPacket(withIntegrityCheck)
                        .setSecureRandom(new SecureRandom())
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
        );
        pgpEncryptedDataGenerator.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(
                getPublicKey(publicKeyIn)));

        // PGPCompressedDataGenerator doesn't implement AutoCloseable in BouncyCastle
        // 1.84, so it can't go in try-with-resources. We close it in an inner finally,
        // which guarantees its trailing compression packet is written before the cipher
        // stream's close() writes the encryption trailer. The stream wrappers ARE
        // AutoCloseable and go in the outer try-with-resources, which closes them in
        // LIFO order: cipherOutStream → outerStream (→ encryptOut, transitively).
        final PGPCompressedDataGenerator compressedDataGenerator =
                new PGPCompressedDataGenerator(compressionAlgorithm);
        try (OutputStream outerStream = armor ? new ArmoredOutputStream(encryptOut) : encryptOut;
             OutputStream cipherOutStream = pgpEncryptedDataGenerator.open(outerStream, new byte[bufferSize])) {
            try {
                OutputStream literalOut = compressedDataGenerator.open(cipherOutStream);
                copyAsLiteralData(literalOut, clearIn, length, bufferSize);
            } finally {
                // MUST run before cipherOutStream / outerStream close — otherwise the
                // output isn't valid PGP. Throws IOException, which propagates.
                compressedDataGenerator.close();
            }
        }
    }


    private void decrypt(InputStream encryptedIn, OutputStream clearOut)
            throws PGPException, IOException {
        // Removing armour and returning the underlying binary encrypted stream
        encryptedIn = PGPUtil.getDecoderStream(encryptedIn);
        final JcaPGPObjectFactory pgpObjectFactory = new JcaPGPObjectFactory(encryptedIn);

        final Object obj = pgpObjectFactory.nextObject();
        //The first object might be a marker packet
        final PGPEncryptedDataList pgpEncryptedDataList = (obj instanceof PGPEncryptedDataList)
                ? (PGPEncryptedDataList) obj : (PGPEncryptedDataList) pgpObjectFactory.nextObject();

        PGPPrivateKey pgpPrivateKey = null;
        PGPPublicKeyEncryptedData publicKeyEncryptedData = null;

        final Iterator<PGPEncryptedData> encryptedDataItr = pgpEncryptedDataList.getEncryptedDataObjects();
        while (pgpPrivateKey == null && encryptedDataItr.hasNext()) {
            publicKeyEncryptedData = (PGPPublicKeyEncryptedData) encryptedDataItr.next();
            pgpPrivateKey = findSecretKey(publicKeyEncryptedData.getKeyIdentifier().getKeyId());
        }

        if (Objects.isNull(publicKeyEncryptedData)) {
            throw new PGPException("Could not generate PGPPublicKeyEncryptedData object");
        }

        if (pgpPrivateKey == null) {
            throw new PGPException("Could Not Extract private key");
        }
        doDecrypt(clearOut, pgpPrivateKey, publicKeyEncryptedData);
    }

    /**
     * Decrypts the public Key encrypted data using the provided private key and writes it to the output stream
     *
     * @param clearOut               the output stream to which data is to be written
     * @param pgpPrivateKey          the private key instance
     * @param publicKeyEncryptedData the public key encrypted data instance
     * @throws IOException  for IO related error
     * @throws PGPException for pgp related errors
     */
    private static void doDecrypt(OutputStream clearOut, PGPPrivateKey pgpPrivateKey, PGPPublicKeyEncryptedData publicKeyEncryptedData) throws IOException, PGPException {
        final PublicKeyDataDecryptorFactory decryptorFactory = new JcePublicKeyDataDecryptorFactoryBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(pgpPrivateKey);
        final InputStream decryptedCompressedIn = publicKeyEncryptedData.getDataStream(decryptorFactory);

        final JcaPGPObjectFactory decCompObjFac = new JcaPGPObjectFactory(decryptedCompressedIn);
        final PGPCompressedData pgpCompressedData = (PGPCompressedData) decCompObjFac.nextObject();

        final InputStream compressedDataStream = new BufferedInputStream(pgpCompressedData.getDataStream());
        final JcaPGPObjectFactory pgpCompObjFac = new JcaPGPObjectFactory(compressedDataStream);

        final Object message = pgpCompObjFac.nextObject();

        if (message instanceof PGPLiteralData pgpLiteralData) {
            final InputStream decDataStream = pgpLiteralData.getInputStream();
            IOUtils.copy(decDataStream, clearOut);
            clearOut.close();
        } else if (message instanceof PGPOnePassSignatureList) {
            throw new PGPException("Encrypted message contains a signed message not literal data");
        } else {
            throw new PGPException("Message is not a simple encrypted file - Type Unknown");
        }
        // Performing Integrity check
        if (publicKeyEncryptedData.isIntegrityProtected()) {
            if (!publicKeyEncryptedData.verify()) {
                throw new PGPException("Message failed integrity check");
            }
        }
    }

    /**
     * Copies "length" amount of data from the input stream and writes it pgp literal data to the provided output stream
     *
     * @param outputStream the output stream to which data is to be written
     * @param in           the input stream from which data is to be read
     * @param length       the length of data to be read
     * @param bufferSize   the buffer size, as it uses buffer to speed up copying
     * @throws IOException for IO related errors
     */
    private static void copyAsLiteralData(OutputStream outputStream, InputStream in, long length, int bufferSize) throws IOException {
        final PGPLiteralDataGenerator lData = new PGPLiteralDataGenerator();
        final byte[] buff = new byte[bufferSize];
        try (OutputStream pOut = lData.open(outputStream, PGPLiteralData.BINARY, PGPLiteralData.CONSOLE, Date.from(LocalDateTime.now().toInstant(ZoneOffset.UTC)), new byte[bufferSize])) {
            int len;
            long totalBytesWritten = 0L;
            // The previous condition `totalBytesWritten <= length` was checked BEFORE the
            // read, so the final iteration could write up to `bufferSize - 1` bytes past
            // `length`. With the default 64KiB buffer that's a meaningful overshoot.
            // We now check that more bytes are wanted before reading, and cap the write
            // to the remaining-bytes budget on the final iteration.
            while (totalBytesWritten < length && (len = in.read(buff)) > 0) {
                long remaining = length - totalBytesWritten;
                int toWrite = (int) Math.min(len, remaining);
                pOut.write(buff, 0, toWrite);
                totalBytesWritten += toWrite;
                if (toWrite < len) {
                    // We've hit the declared length but the input still had more bytes
                    // available — stop here so we don't write past the contract.
                    break;
                }
            }
        } finally {
            // Clearing buffer
            Arrays.fill(buff, (byte) 0);
        }
    }

    /**
     * Gets the public key from the key input stream
     *
     * @param keyInputStream the key input stream
     * @return a PGPPublic key instance
     * @throws IOException  for IO related errors
     * @throws PGPException PGPException for pgp related errors
     */
    private static PGPPublicKey getPublicKey(InputStream keyInputStream) throws IOException, PGPException {
        final PGPPublicKeyRingCollection pgpPublicKeyRings = new PGPPublicKeyRingCollection(
                PGPUtil.getDecoderStream(keyInputStream), new JcaKeyFingerprintCalculator());
        final Iterator<PGPPublicKeyRing> keyRingIterator = pgpPublicKeyRings.getKeyRings();
        while (keyRingIterator.hasNext()) {
            PGPPublicKeyRing pgpPublicKeyRing = keyRingIterator.next();
            Optional<PGPPublicKey> pgpPublicKey = extractPGPKeyFromRing(pgpPublicKeyRing);
            if (pgpPublicKey.isPresent()) {
                return pgpPublicKey.get();
            }
        }
        throw new PGPException("Invalid public key");
    }

    private static Optional<PGPPublicKey> extractPGPKeyFromRing(PGPPublicKeyRing pgpPublicKeyRing) {
        for (PGPPublicKey publicKey : pgpPublicKeyRing) {
            if (publicKey.isEncryptionKey()) {
                return Optional.of(publicKey);
            }
        }
        return Optional.empty();
    }

    private PGPPrivateKey findSecretKey(long keyID) throws PGPException {
        final PGPSecretKey pgpSecretKey = pgpSecretKeyRingCollection.getSecretKey(keyID);
        return pgpSecretKey == null ? null : pgpSecretKey.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(passkey));
    }

}
