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

    private static final Logger LOG = LoggerFactory.getLogger("EncryptionUtils");


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
        LOG.info("Encrypting input file {} to output file {}.", inputFilePath.toString(), outputFilePath.toString());
        try (OutputStream fos = Files.newOutputStream(outputFilePath)) {
            encrypt(fos, Files.newInputStream(inputFilePath), inputFilePath.toFile().length(),
                    publicKey.openStream());
        }
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
        LOG.info("Decrypting input file {} to output file {}.", inputFilePath.toString(), outputFilePath.toString());
        try (OutputStream fos = Files.newOutputStream(outputFilePath)) {
            decrypt(Files.newInputStream(inputFilePath), fos);
        }
        return outputFilePath;
    }

    private void encrypt(OutputStream encryptOut, InputStream clearIn, long length, InputStream publicKeyIn)
            throws IOException, PGPException {
        final PGPCompressedDataGenerator compressedDataGenerator = new PGPCompressedDataGenerator(compressionAlgorithm);
        final PGPEncryptedDataGenerator pgpEncryptedDataGenerator = new PGPEncryptedDataGenerator(
                // This bit here configures the encrypted data generator
                new JcePGPDataEncryptorBuilder(symmetricKeyAlgorithm)
                        .setWithIntegrityPacket(withIntegrityCheck)
                        .setSecureRandom(new SecureRandom())
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
        );
        // Adding public key
        pgpEncryptedDataGenerator.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(
                getPublicKey(publicKeyIn)));
        if (armor) {
            encryptOut = new ArmoredOutputStream(encryptOut);
        }
        final OutputStream cipherOutStream = pgpEncryptedDataGenerator.open(encryptOut, new byte[bufferSize]);
        copyAsLiteralData(compressedDataGenerator.open(cipherOutStream), clearIn, length, bufferSize);
        // Closing all output streams in sequence
        compressedDataGenerator.close();
        cipherOutStream.close();
        encryptOut.close();
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
            pgpPrivateKey = findSecretKey(publicKeyEncryptedData.getKeyID());
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
        try (in; OutputStream pOut = lData.open(outputStream, PGPLiteralData.BINARY, PGPLiteralData.CONSOLE, Date.from(LocalDateTime.now().toInstant(ZoneOffset.UTC)), new byte[bufferSize])) {
            int len;
            long totalBytesWritten = 0L;
            while (totalBytesWritten <= length && (len = in.read(buff)) > 0) {
                pOut.write(buff, 0, len);
                totalBytesWritten += len;
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
