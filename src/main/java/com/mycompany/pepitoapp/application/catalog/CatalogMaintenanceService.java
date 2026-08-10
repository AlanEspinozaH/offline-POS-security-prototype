package com.mycompany.pepitoapp.application.catalog;

import java.sql.SQLException;

public interface CatalogMaintenanceService {
    void createProduct(String barcode, String name, long salePriceCents) throws SQLException;
    void associateBarcode(String barcode, String existingProductId) throws SQLException;
}
