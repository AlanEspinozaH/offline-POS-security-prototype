package com.mycompany.pepitoapp.security.crypto;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import java.util.Base64;

/**
 * Utility to derive fixed-length keys using Argon2id.
 */
public class Argon2KeyDeriver {

    private final int iterations;
    private final int memoryKb;
    private final int parallelism;
    private final int hashLength;

    public Argon2KeyDeriver() {
        this(3, 65536, 1, 32);
    }

    public Argon2KeyDeriver(int iterations, int memoryKb, int parallelism, int hashLength) {
        this.iterations = iterations;
        this.memoryKb = memoryKb;
        this.parallelism = parallelism;
        this.hashLength = hashLength;
    }

    /**
     * Derive a key with Argon2id using the provided salt.
     *
     * @param passphrase human provided secret
     * @param salt random salt
     * @return derived key bytes of length hashLength
     */
    public byte[] deriveKey(char[] passphrase, byte[] salt) {
        Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id, hashLength, salt.length);
        try {
            String encoded = argon2.hash(iterations, memoryKb, parallelism, passphrase, salt);
            String[] parts = encoded.split("\\$");
            if (parts.length < 6) {
                throw new IllegalStateException("Invalid Argon2 hash format");
            }
            return Base64.getDecoder().decode(parts[5]);
        } finally {
            argon2.wipeArray(passphrase);
        }
    }
}
