package com.mycompany.pepitoapp.storage;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Resolves and opens the local products SQLite database.
 */
public class ProductDatabaseProvider {

    private static final String ENVIRONMENT_VARIABLE = "PEPITO_PRODUCTOS_DB";
    private static final Path DEFAULT_DATABASE_PATH = Path.of("local-data", "productos2.db");
    private final Path configuredDatabasePath;

    public ProductDatabaseProvider() {
        this.configuredDatabasePath = null;
    }

    public ProductDatabaseProvider(Path databasePath) {
        this.configuredDatabasePath = databasePath.toAbsolutePath().normalize();
    }

    public Connection getConnection() throws SQLException {
        Path databasePath = resolveDatabasePath();
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    public Path resolveDatabasePath() throws SQLException {
        String configuredPath = System.getenv(ENVIRONMENT_VARIABLE);
        Path databasePath;
        try {
            if (configuredDatabasePath != null) {
                databasePath = configuredDatabasePath;
            } else if (configuredPath != null && !configuredPath.isBlank()) {
                databasePath = Path.of(configuredPath.trim());
            } else {
                databasePath = Path.of(System.getProperty("user.dir")).resolve(DEFAULT_DATABASE_PATH);
            }
        } catch (InvalidPathException ex) {
            throw new SQLException("La ruta configurada en " + ENVIRONMENT_VARIABLE + " no es valida: " + configuredPath, ex);
        }

        Path normalizedPath = databasePath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedPath)) {
            throw new SQLException("No se encontro la base de productos en " + normalizedPath
                    + ". Copia productos2.db en local-data/productos2.db dentro de la raiz del proyecto "
                    + "o define PEPITO_PRODUCTOS_DB con la ruta completa del archivo productos2.db.");
        }
        return normalizedPath;
    }
}
