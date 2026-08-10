package com.mycompany.pepitoapp.infrastructure.sqlite;

import com.mycompany.pepitoapp.application.supplier.SupplierService;
import com.mycompany.pepitoapp.domain.supplier.Supplier;
import com.mycompany.pepitoapp.domain.supplier.SupplierProduct;
import com.mycompany.pepitoapp.storage.ProductDatabaseProvider;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SQLiteSupplierService implements SupplierService {
    private final ProductDatabaseProvider databaseProvider;
    public SQLiteSupplierService(ProductDatabaseProvider databaseProvider) { this.databaseProvider = databaseProvider; }

    @Override public List<Supplier> listActive() throws SQLException {
        try (var connection = databaseProvider.getConnection(); var statement = connection.prepareStatement("SELECT id,name,tax_id FROM suppliers WHERE active=1 ORDER BY name"); var result = statement.executeQuery()) {
            List<Supplier> suppliers = new ArrayList<>();
            while (result.next()) suppliers.add(new Supplier(result.getLong(1), result.getString(2), result.getString(3)));
            return suppliers;
        }
    }

    @Override public Supplier create(String name, String taxId) throws SQLException {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("El nombre del proveedor es obligatorio");
        String sql = "INSERT INTO suppliers(name,tax_id,created_at) VALUES(?,?,?)";
        try (var connection = databaseProvider.getConnection(); var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name.trim());
            statement.setString(2, taxId == null || taxId.isBlank() ? null : taxId.trim());
            statement.setString(3, Instant.now().toString());
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) { keys.next(); return new Supplier(keys.getLong(1), name.trim(), taxId); }
        }
    }

    @Override public Optional<SupplierProduct> findPresentation(long supplierId, String productId) throws SQLException {
        String sql = "SELECT supplier_sku,units_per_pack,presentation_name,last_pack_price_cents FROM supplier_product WHERE supplier_id=? AND product_id=?";
        try (var connection = databaseProvider.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, supplierId); statement.setString(2, productId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                Long lastPrice = result.getObject(4) == null ? null : result.getLong(4);
                return Optional.of(new SupplierProduct(supplierId, productId, result.getString(1), result.getInt(2), result.getString(3), lastPrice));
            }
        }
    }
}
