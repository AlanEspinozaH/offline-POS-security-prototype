package com.mycompany.pepitoapp.infrastructure.sqlite;

import com.mycompany.pepitoapp.application.catalog.CatalogRepository;
import com.mycompany.pepitoapp.domain.product.ProductSnapshot;
import com.mycompany.pepitoapp.storage.ProductDatabaseProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public final class SQLiteCatalogRepository implements CatalogRepository {
    private final ProductDatabaseProvider databaseProvider;

    public SQLiteCatalogRepository(ProductDatabaseProvider databaseProvider) { this.databaseProvider = databaseProvider; }

    @Override
    public Optional<ProductSnapshot> findByBarcode(String barcode, Instant at) throws SQLException {
        String sql = "SELECT p.id_productos, p.nombre, p.tipo, p.stock_unidades, "
                + "COALESCE((SELECT sph.price_cents FROM sale_price_history sph WHERE sph.product_id=p.id_productos "
                + "AND sph.effective_from<=? AND (sph.effective_to IS NULL OR sph.effective_to>?) "
                + "ORDER BY sph.effective_from DESC, sph.id DESC LIMIT 1), "
                + "CAST(ROUND(CAST(p.precio_unitario_venta AS REAL)*100.0) AS INTEGER)) AS price_cents, "
                + "cs.latest_net_amount_cents,cs.latest_received_units,p.precio_unitario_costo "
                + "FROM productos p LEFT JOIN product_cost_summary cs ON cs.product_id=p.id_productos "
                + "WHERE p.id_productos=COALESCE((SELECT product_id FROM product_barcode_alias WHERE barcode=?),?) ORDER BY p.rowid LIMIT 2";
        try (Connection connection = databaseProvider.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, at.toString());
            statement.setString(2, at.toString());
            statement.setString(3, barcode);
            statement.setString(4, barcode);
            try (var result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                BigDecimal latestCost = result.getObject(6) == null
                        ? parseMoney(result.getString(8)).movePointRight(2)
                        : BigDecimal.valueOf(result.getLong(6)).divide(BigDecimal.valueOf(result.getLong(7)), com.mycompany.pepitoapp.domain.purchase.PurchaseLine.COST_CONTEXT);
                ProductSnapshot product = new ProductSnapshot(result.getString(1), result.getString(2), result.getString(3),
                        parseInteger(result.getString(4)), result.getLong(5), latestCost);
                if (result.next()) throw new SQLException("El código de barras está duplicado en el catálogo: " + barcode);
                return Optional.of(product);
            }
        }
    }

    private int parseInteger(String value) {
        try { return new BigDecimal(value == null ? "0" : value).setScale(0, RoundingMode.DOWN).intValueExact(); }
        catch (ArithmeticException | NumberFormatException ex) { return 0; }
    }

    private BigDecimal parseMoney(String value) {
        try { return new BigDecimal(value == null || value.isBlank() ? "0" : value); }
        catch (NumberFormatException ex) { return BigDecimal.ZERO; }
    }
}
