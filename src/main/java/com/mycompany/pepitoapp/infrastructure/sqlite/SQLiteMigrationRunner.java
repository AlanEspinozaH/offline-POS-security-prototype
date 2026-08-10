package com.mycompany.pepitoapp.infrastructure.sqlite;

import com.mycompany.pepitoapp.storage.ProductDatabaseProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

public final class SQLiteMigrationRunner {
    private static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, "/db/migration/V001__purchase_pricing.sql")
    );
    private final ProductDatabaseProvider databaseProvider;

    public SQLiteMigrationRunner(ProductDatabaseProvider databaseProvider) {
        this.databaseProvider = databaseProvider;
    }

    public void migrate() throws SQLException {
        try (Connection connection = databaseProvider.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY, resource TEXT NOT NULL, applied_at TEXT NOT NULL)");
                }
                for (Migration migration : MIGRATIONS) {
                    if (!isApplied(connection, migration.version())) apply(connection, migration);
                }
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                if (ex instanceof SQLException sqlException) throw sqlException;
                throw new SQLException("No se pudo aplicar la migración SQLite", ex);
            }
        }
    }

    private boolean isApplied(Connection connection, int version) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT 1 FROM schema_migrations WHERE version = ?")) {
            statement.setInt(1, version);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    private void apply(Connection connection, Migration migration) throws SQLException, IOException {
        String sql = load(migration.resource());
        try (Statement statement = connection.createStatement()) {
            for (String command : sql.split(";")) {
                if (!command.isBlank()) statement.execute(command);
            }
        }
        try (var statement = connection.prepareStatement("INSERT INTO schema_migrations(version, resource, applied_at) VALUES(?,?,?)")) {
            statement.setInt(1, migration.version());
            statement.setString(2, migration.resource());
            statement.setString(3, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    private String load(String resource) throws IOException {
        try (InputStream input = SQLiteMigrationRunner.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Recurso de migración no encontrado: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record Migration(int version, String resource) { }
}
