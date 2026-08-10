package com.mycompany.pepitoapp.infrastructure.sqlite;

import com.mycompany.pepitoapp.storage.ProductDatabaseProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;

final class SQLiteTestDatabase {
    final Path path;
    final ProductDatabaseProvider provider;

    SQLiteTestDatabase(Path directory) throws Exception {
        path=directory.resolve("test.db"); Files.createFile(path); provider=new ProductDatabaseProvider(path);
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+path); var s=c.createStatement()) {
            s.execute("CREATE TABLE productos(id_productos TEXT,nombre TEXT,precio_unitario_venta TEXT,tipo TEXT,stock_unidades TEXT,fecha_de_caducidad TEXT,precio_unitario_costo TEXT)");
        }
    }

    void addProduct(String barcode,String name,String salePrice,String cost) throws Exception {
        try(Connection c=provider.getConnection(); var s=c.prepareStatement("INSERT INTO productos VALUES(?,?,?,'X','0','X',?)")) {
            s.setString(1,barcode); s.setString(2,name); s.setString(3,salePrice); s.setString(4,cost); s.executeUpdate();
        }
    }

    void migrate() throws Exception { new SQLiteMigrationRunner(provider).migrate(); }

    long addSupplier(String name) throws Exception {
        try(Connection c=provider.getConnection(); var s=c.prepareStatement("INSERT INTO suppliers(name,active,created_at) VALUES(?,1,?)",java.sql.Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1,name); s.setString(2,Instant.now().toString()); s.executeUpdate(); try(var keys=s.getGeneratedKeys()) { keys.next(); return keys.getLong(1); }
        }
    }

    long scalarLong(String sql) throws Exception { try(Connection c=provider.getConnection(); var s=c.createStatement(); var r=s.executeQuery(sql)) { r.next(); return r.getLong(1); } }
    String scalarString(String sql) throws Exception { try(Connection c=provider.getConnection(); var s=c.createStatement(); var r=s.executeQuery(sql)) { r.next(); return r.getString(1); } }
}
