package com.mycompany.pepitoapp.security.crypto;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;
import java.util.Properties;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Minimal local keystore that encrypts Ed25519 private keys with AES-GCM using
 * a key derived from Argon2id.
 */
public class KeyStoreManager {

    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final int SALT_LENGTH = 16;

    private final Argon2KeyDeriver keyDeriver;
    private final Path keyDirectory;
    private final SecureRandom secureRandom = new SecureRandom();

    public KeyStoreManager() {
        this(new Argon2KeyDeriver(), Path.of(System.getProperty("user.home"), ".pepitoapp", "keystore"));
    }

    public KeyStoreManager(Argon2KeyDeriver keyDeriver, Path keyDirectory) {
        this.keyDeriver = Objects.requireNonNull(keyDeriver, "keyDeriver");
        this.keyDirectory = Objects.requireNonNull(keyDirectory, "keyDirectory");
    }

    public KeyPair loadOrCreateKeyPair(String keyId, char[] passphrase) throws Exception {
        Files.createDirectories(keyDirectory);
        Path keyFile = keyDirectory.resolve(keyId + ".properties");
        if (Files.exists(keyFile)) {
            return readKey(keyFile, passphrase);
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = generator.generateKeyPair();
        writeKey(keyFile, keyPair, passphrase);
        return keyPair;
    }

    private KeyPair readKey(Path keyFile, char[] passphrase) throws Exception {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(keyFile)) {
            props.load(in);
        }
        byte[] salt = Base64.getDecoder().decode(props.getProperty("salt"));
        byte[] iv = Base64.getDecoder().decode(props.getProperty("iv"));
        byte[] cipherText = Base64.getDecoder().decode(props.getProperty("cipher"));
        byte[] publicKeyBytes = Base64.getDecoder().decode(props.getProperty("public"));

        SecretKey derived = deriveAesKey(passphrase, salt);
        Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
        cipher.init(Cipher.DECRYPT_MODE, derived, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] privateKeyBytes = cipher.doFinal(cipherText);

        KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
        PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
        PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        return new KeyPair(publicKey, privateKey);
    }

    private void writeKey(Path keyFile, KeyPair keyPair, char[] passphrase) throws Exception {
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(salt);
        secureRandom.nextBytes(iv);
        SecretKey derived = deriveAesKey(passphrase, salt);

        Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
        cipher.init(Cipher.ENCRYPT_MODE, derived, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] cipherText = cipher.doFinal(keyPair.getPrivate().getEncoded());

        Properties props = new Properties();
        props.setProperty("salt", Base64.getEncoder().encodeToString(salt));
        props.setProperty("iv", Base64.getEncoder().encodeToString(iv));
        props.setProperty("cipher", Base64.getEncoder().encodeToString(cipherText));
        props.setProperty("public", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));

        try (OutputStream out = Files.newOutputStream(keyFile)) {
            props.store(out, "PepitoApp Ed25519 key");
        }
    }

    private SecretKey deriveAesKey(char[] passphrase, byte[] salt) {
        byte[] keyBytes = keyDeriver.deriveKey(passphrase, salt);
        return new SecretKeySpec(keyBytes, "AES");
    }
}
