package com.mycompany.pepitoapp.security.storage;

import com.mycompany.pepitoapp.security.crypto.Argon2KeyDeriver;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

/**
 * Provides a SQLite connection prepared for SQLCipher (when available) and
 * initializes the ledger schema.
 */
public class SecureDatabaseProvider {

    private final Argon2KeyDeriver keyDeriver;
    private final Path dbDirectory;
    private final Path dbFile;
    private final Path keyMetadataFile;

    public SecureDatabaseProvider() {
        this(new Argon2KeyDeriver(), Path.of(System.getProperty("user.home"), ".pepitoapp"));
    }

    public SecureDatabaseProvider(Argon2KeyDeriver keyDeriver, Path dbDirectory) {
        this.keyDeriver = Objects.requireNonNull(keyDeriver, "keyDeriver");
        this.dbDirectory = Objects.requireNonNull(dbDirectory, "dbDirectory");
        this.dbFile = dbDirectory.resolve("ledger.db");
        this.keyMetadataFile = dbDirectory.resolve("db_key.properties");
    }

    public Connection getConnection(char[] passphrase) throws SQLException, IOException {
        Files.createDirectories(dbDirectory);
        String url = "jdbc:sqlite:" + dbFile.toString();
        Connection connection = DriverManager.getConnection(url);
        try {
            applySqlCipherPragma(connection, passphrase);
            initializeSchema(connection);
        } catch (Exception ex) {
            throw new SQLException("Unable to initialize secure database", ex);
        }
        return connection;
    }

    private void initializeSchema(Connection connection) throws SQLException {
        String ddl = "CREATE TABLE IF NOT EXISTS ledger (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "created_at INTEGER NOT NULL," +
                "sale_json TEXT NOT NULL," +
                "previous_hash TEXT NOT NULL," +
                "current_hash TEXT NOT NULL," +
                "signature TEXT NOT NULL," +
                "key_id TEXT NOT NULL," +
                "checkpoint INTEGER NOT NULL DEFAULT 0" +
                ")";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(ddl);
        }
    }

    private void applySqlCipherPragma(Connection connection, char[] passphrase) throws Exception {
        byte[] salt = loadOrCreateSalt();
        byte[] key = keyDeriver.deriveKey(passphrase, salt);
        String hexKey = Base64.getEncoder().encodeToString(key);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA key = '" + hexKey + "'");
        } catch (SQLException ignored) {
            // When SQLCipher is not present this PRAGMA is ignored; keeping it lets the
            // same code work when the SQLCipher driver is configured.
        }
    }

    private byte[] loadOrCreateSalt() throws IOException {
        if (Files.exists(keyMetadataFile)) {
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(keyMetadataFile)) {
                props.load(in);
            }
            return Base64.getDecoder().decode(props.getProperty("salt"));
        }
        byte[] salt = UUID.randomUUID().toString().getBytes();
        Properties props = new Properties();
        props.setProperty("salt", Base64.getEncoder().encodeToString(salt));
        try (OutputStream out = Files.newOutputStream(keyMetadataFile)) {
            props.store(out, "PepitoApp SQLCipher salt");
        }
        return salt;
    }
}
