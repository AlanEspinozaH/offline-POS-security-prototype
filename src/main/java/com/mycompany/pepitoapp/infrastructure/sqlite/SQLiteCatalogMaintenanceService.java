package com.mycompany.pepitoapp.infrastructure.sqlite;

import com.mycompany.pepitoapp.application.catalog.CatalogMaintenanceService;
import com.mycompany.pepitoapp.domain.pricing.SalePricePolicy;
import com.mycompany.pepitoapp.storage.ProductDatabaseProvider;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

public final class SQLiteCatalogMaintenanceService implements CatalogMaintenanceService {
    private final ProductDatabaseProvider databaseProvider;
    public SQLiteCatalogMaintenanceService(ProductDatabaseProvider databaseProvider) { this.databaseProvider=databaseProvider; }

    @Override public void createProduct(String barcode, String name, long salePriceCents) throws SQLException {
        validateBarcode(barcode); SalePricePolicy.requireValidSalePrice(salePriceCents);
        if(name==null || name.isBlank()) throw new IllegalArgumentException("El nombre es obligatorio");
        try(Connection c=databaseProvider.getConnection()) {
            c.setAutoCommit(false);
            try {
                ensureBarcodeFree(c,barcode);
                try(var s=c.prepareStatement("INSERT INTO productos(id_productos,nombre,precio_unitario_venta,tipo,stock_unidades,fecha_de_caducidad,precio_unitario_costo) VALUES(?,?,?,'X','0','X','0')")) {
                    s.setString(1,barcode); s.setString(2,name.trim()); s.setString(3,java.math.BigDecimal.valueOf(salePriceCents,2).toPlainString()); s.executeUpdate();
                }
                String now=Instant.now().toString();
                try(var s=c.prepareStatement("INSERT INTO sale_price_history(product_id,price_cents,effective_from,effective_to,reason,created_at) VALUES(?,?,?,NULL,'Alta rápida durante recepción',?)")) {
                    s.setString(1,barcode); s.setLong(2,salePriceCents); s.setString(3,now); s.setString(4,now); s.executeUpdate();
                }
                c.commit();
            } catch(Exception ex) { c.rollback(); if(ex instanceof SQLException sql) throw sql; throw new SQLException("No se pudo crear el producto",ex); }
        }
    }

    @Override public void associateBarcode(String barcode, String existingProductId) throws SQLException {
        validateBarcode(barcode); validateBarcode(existingProductId);
        try(Connection c=databaseProvider.getConnection()) {
            ensureBarcodeFree(c,barcode);
            try(var count=c.prepareStatement("SELECT COUNT(*) FROM productos WHERE id_productos=?")) {
                count.setString(1,existingProductId); try(var r=count.executeQuery()) { r.next(); if(r.getInt(1)!=1) throw new SQLException("El producto destino debe existir y ser único"); }
            }
            try(var s=c.prepareStatement("INSERT INTO product_barcode_alias(barcode,product_id,created_at) VALUES(?,?,?)")) {
                s.setString(1,barcode); s.setString(2,existingProductId); s.setString(3,Instant.now().toString()); s.executeUpdate();
            }
        }
    }

    private void ensureBarcodeFree(Connection c,String barcode) throws SQLException {
        try(var s=c.prepareStatement("SELECT (SELECT COUNT(*) FROM productos WHERE id_productos=?)+(SELECT COUNT(*) FROM product_barcode_alias WHERE barcode=?)")) {
            s.setString(1,barcode); s.setString(2,barcode); try(var r=s.executeQuery()) { r.next(); if(r.getInt(1)>0) throw new SQLException("El código ya está registrado"); }
        }
    }
    private void validateBarcode(String barcode) { if(barcode==null || barcode.isBlank()) throw new IllegalArgumentException("El código de barras es obligatorio"); }
}
