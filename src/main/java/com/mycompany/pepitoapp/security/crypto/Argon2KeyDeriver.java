package com.mycompany.pepitoapp.security.crypto;

import de.mkammerer.argon2.Argon2Advanced;
import de.mkammerer.argon2.Argon2Factory;

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
     * @param passphrase human provided secret; the caller-owned array is not wiped
     * @param salt random salt
     * @return derived key bytes of length hashLength
     */
    public byte[] deriveKey(char[] passphrase, byte[] salt) {
        Argon2Advanced argon2 = Argon2Factory.createAdvanced(Argon2Factory.Argon2Types.ARGON2id, salt.length, hashLength);
        char[] workingCopy = passphrase.clone();
        try {
            return argon2.rawHash(iterations, memoryKb, parallelism, workingCopy, salt);
        } finally {
            argon2.wipeArray(workingCopy);
        }
    }
}
