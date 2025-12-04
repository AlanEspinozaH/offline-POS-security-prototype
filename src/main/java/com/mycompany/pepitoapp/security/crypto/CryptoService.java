package com.mycompany.pepitoapp.security.crypto;

import java.security.KeyPair;
import java.security.Signature;
import java.util.Base64;
import java.util.Objects;

/**
 * Provides signing and verification with Ed25519 using keys protected by the
 * local KeyStoreManager.
 */
public class CryptoService {

    private final KeyStoreManager keyStoreManager;

    public CryptoService() {
        this(new KeyStoreManager());
    }

    public CryptoService(KeyStoreManager keyStoreManager) {
        this.keyStoreManager = Objects.requireNonNull(keyStoreManager, "keyStoreManager");
    }

    public String sign(byte[] payload, String keyId, char[] passphrase) throws Exception {
        KeyPair keyPair = keyStoreManager.loadOrCreateKeyPair(keyId, passphrase);
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(keyPair.getPrivate());
        signature.update(payload);
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    public boolean verify(byte[] payload, String signatureBase64, String keyId, char[] passphrase) throws Exception {
        KeyPair keyPair = keyStoreManager.loadOrCreateKeyPair(keyId, passphrase);
        Signature signature = Signature.getInstance("Ed25519");
        signature.initVerify(keyPair.getPublic());
        signature.update(payload);
        byte[] provided = Base64.getDecoder().decode(signatureBase64);
        return signature.verify(provided);
    }
}
