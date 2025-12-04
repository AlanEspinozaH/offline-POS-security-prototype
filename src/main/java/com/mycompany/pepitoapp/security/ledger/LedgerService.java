package com.mycompany.pepitoapp.security.ledger;

import com.mycompany.pepitoapp.security.crypto.CryptoService;
import com.mycompany.pepitoapp.security.storage.SecureDatabaseProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Manages the hash-chain ledger and verification routines.
 */
public class LedgerService {

    private final SecureDatabaseProvider databaseProvider;

    public LedgerService() {
        this(new SecureDatabaseProvider());
    }

    public LedgerService(SecureDatabaseProvider databaseProvider) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
    }

    public boolean appendSignedEntry(String saleJson, String keyId, char[] passphrase, CryptoService cryptoService) {
        Objects.requireNonNull(saleJson, "saleJson");
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(passphrase, "passphrase");
        Objects.requireNonNull(cryptoService, "cryptoService");
        try (Connection connection = databaseProvider.getConnection(passphrase)) {
            connection.setAutoCommit(false);
            String prevHash = findLastHash(connection);
            String currentHash = hash(prevHash + saleJson);
            String signature = cryptoService.sign(currentHash.getBytes(StandardCharsets.UTF_8), keyId, passphrase);
            insertEntry(connection, saleJson, prevHash, currentHash, signature, keyId);
            connection.commit();
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    public VerificationResult verifyChain(char[] passphrase, CryptoService cryptoService) {
        List<String> errors = new ArrayList<>();
        try (Connection connection = databaseProvider.getConnection(passphrase)) {
            List<LedgerEntry> entries = loadEntries(connection);
            String expectedPrev = hash("GENESIS");
            for (LedgerEntry entry : entries) {
                if (!entry.previousHash().equals(expectedPrev)) {
                    errors.add("Prev hash mismatch at id " + entry.id());
                }
                String recomputedHash = hash(entry.previousHash() + entry.saleJson());
                if (!recomputedHash.equals(entry.currentHash())) {
                    errors.add("Hash mismatch at id " + entry.id());
                }
                if (cryptoService != null) {
                    boolean validSignature = cryptoService.verify(
                            entry.currentHash().getBytes(StandardCharsets.UTF_8),
                            entry.signature(),
                            entry.keyId(),
                            passphrase
                    );
                    if (!validSignature) {
                        errors.add("Firma inválida en la entrada " + entry.id());
                    }
                }
                expectedPrev = entry.currentHash();
            }
        } catch (Exception ex) {
            errors.add("Verification failed: " + ex.getMessage());
        }
        return new VerificationResult(errors.isEmpty(), errors);
    }

    private List<LedgerEntry> loadEntries(Connection connection) throws SQLException {
        String query = "SELECT id, created_at, sale_json, previous_hash, current_hash, signature, key_id, checkpoint FROM ledger ORDER BY id ASC";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            ResultSet rs = statement.executeQuery();
            List<LedgerEntry> entries = new ArrayList<>();
            while (rs.next()) {
                entries.add(new LedgerEntry(
                        rs.getLong("id"),
                        rs.getLong("created_at"),
                        rs.getString("sale_json"),
                        rs.getString("previous_hash"),
                        rs.getString("current_hash"),
                        rs.getString("signature"),
                        rs.getString("key_id"),
                        rs.getInt("checkpoint") == 1
                ));
            }
            return entries;
        }
    }

    private void insertEntry(Connection connection, String saleJson, String prevHash, String currentHash, String signature, String keyId) throws SQLException {
        String insert = "INSERT INTO ledger(created_at, sale_json, previous_hash, current_hash, signature, key_id, checkpoint) VALUES(?,?,?,?,?,?,0)";
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setLong(1, Instant.now().getEpochSecond());
            statement.setString(2, saleJson);
            statement.setString(3, prevHash);
            statement.setString(4, currentHash);
            statement.setString(5, signature);
            statement.setString(6, keyId);
            statement.executeUpdate();
        }
    }

    private String findLastHash(Connection connection) throws SQLException {
        String query = "SELECT current_hash FROM ledger ORDER BY id DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return rs.getString(1);
            }
            return hash("GENESIS");
        }
    }

    private String hash(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] out = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(out);
    }
}
