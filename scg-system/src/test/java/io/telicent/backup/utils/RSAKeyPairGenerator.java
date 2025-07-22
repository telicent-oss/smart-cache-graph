package io.telicent.backup.utils;

import org.bouncycastle.bcpg.*;
import org.bouncycastle.bcpg.sig.Features;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.PBESecretKeyEncryptor;
import org.bouncycastle.openpgp.operator.PGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * A simple utility class that generates an RSA key ring.
 * Basically a copy of <a href="https://github.com/bcgit/bc-java/blob/main/pg/src/main/java/org/bouncycastle/openpgp/examples/RSAKeyPairGenerator.java">org.bouncycastle.openpgp.examples.RSAKeyPairGenerator</a>
 * <p>
 * usage: RSAKeyPairGenerator [-a] identity passPhrase
 * <p>
 * Where identity is the name to be associated with the public key. The keys are placed
 * in the files pub.[asc|bpg] and secret.[asc|bpg].
 */
public class RSAKeyPairGenerator
{

    private static final int SIG_HASH = HashAlgorithmTags.SHA512;
    private static final int[] HASH_PREFERENCES = new int[]{
            HashAlgorithmTags.SHA512, HashAlgorithmTags.SHA384, HashAlgorithmTags.SHA256, HashAlgorithmTags.SHA224
    };
    private static final int[] SYM_PREFERENCES = new int[]{
            SymmetricKeyAlgorithmTags.AES_256, SymmetricKeyAlgorithmTags.AES_192, SymmetricKeyAlgorithmTags.AES_128
    };
    private static final int[] COMP_PREFERENCES = new int[]{
            CompressionAlgorithmTags.ZLIB, CompressionAlgorithmTags.BZIP2, CompressionAlgorithmTags.ZLIB, CompressionAlgorithmTags.UNCOMPRESSED
    };

    public static void generateAndExportKeyRing(
            OutputStream secretOut,
            OutputStream publicOut,
            String identity,
            char[] passPhrase,
            boolean armor)
            throws IOException, NoSuchProviderException, PGPException, NoSuchAlgorithmException
    {
        if (armor)
        {
            secretOut = new ArmoredOutputStream(secretOut);
        }

        final PGPDigestCalculator sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1);
        final KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");

        final PGPContentSignerBuilder contentSignerBuilder = new JcaPGPContentSignerBuilder(PublicKeyAlgorithmTags.RSA_GENERAL, SIG_HASH).setProvider("BC");
        final PBESecretKeyEncryptor secretKeyEncryptor = new JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, sha1Calc).setProvider("BC")
                .build(passPhrase);

        final Date now = new Date();

        kpg.initialize(3072);
        final KeyPair primaryKP = kpg.generateKeyPair();
        final PGPKeyPair primaryKey = new JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, primaryKP, now);
        final PGPSignatureSubpacketGenerator primarySubpackets = new PGPSignatureSubpacketGenerator();
        primarySubpackets.setKeyFlags(true, KeyFlags.CERTIFY_OTHER);
        primarySubpackets.setPreferredHashAlgorithms(false, HASH_PREFERENCES);
        primarySubpackets.setPreferredSymmetricAlgorithms(false, SYM_PREFERENCES);
        primarySubpackets.setPreferredCompressionAlgorithms(false, COMP_PREFERENCES);
        primarySubpackets.setFeature(false, Features.FEATURE_MODIFICATION_DETECTION);
        primarySubpackets.setIssuerFingerprint(false, primaryKey.getPublicKey());

        kpg.initialize(3072);
        final KeyPair signingKP = kpg.generateKeyPair();
        final PGPKeyPair signingKey = new JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, signingKP, now);
        final PGPSignatureSubpacketGenerator signingKeySubpacket = new PGPSignatureSubpacketGenerator();
        signingKeySubpacket.setKeyFlags(true, KeyFlags.SIGN_DATA);
        signingKeySubpacket.setIssuerFingerprint(false, primaryKey.getPublicKey());

        kpg.initialize(3072);
        final KeyPair encryptionKP = kpg.generateKeyPair();
        final PGPKeyPair encryptionKey = new JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, encryptionKP, now);
        final PGPSignatureSubpacketGenerator encryptionKeySubpackets = new PGPSignatureSubpacketGenerator();
        encryptionKeySubpackets.setKeyFlags(true, KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE);
        encryptionKeySubpackets.setIssuerFingerprint(false, primaryKey.getPublicKey());

        final PGPKeyRingGenerator gen = new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION, primaryKey, identity,
                sha1Calc, primarySubpackets.generate(), null, contentSignerBuilder, secretKeyEncryptor);
        gen.addSubKey(signingKey, signingKeySubpacket.generate(), null, contentSignerBuilder);
        gen.addSubKey(encryptionKey, encryptionKeySubpackets.generate(), null);

        final PGPSecretKeyRing secretKeys = gen.generateSecretKeyRing();
        secretKeys.encode(secretOut);

        secretOut.close();

        if (armor)
        {
            publicOut = new ArmoredOutputStream(publicOut);
        }

        final List<PGPPublicKey> publicKeyList = new ArrayList<PGPPublicKey>();
        final Iterator<PGPPublicKey> it = secretKeys.getPublicKeys();
        while (it.hasNext())
        {
            publicKeyList.add(it.next());
        }

        final PGPPublicKeyRing publicKeys = new PGPPublicKeyRing(publicKeyList);

        publicKeys.encode(publicOut);

        publicOut.close();
    }

}
