package com.mycompany.pepitoapp.security.ledger;

/**
 * Immutable view of a ledger entry persisted in SQLite.
 */
public record LedgerEntry(
        long id,
        long createdAt,
        String saleJson,
        String previousHash,
        String currentHash,
        String signature,
        String keyId,
        boolean checkpoint
) {}
