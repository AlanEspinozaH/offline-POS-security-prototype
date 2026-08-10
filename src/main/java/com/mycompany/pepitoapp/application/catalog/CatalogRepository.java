package com.mycompany.pepitoapp.application.catalog;

import com.mycompany.pepitoapp.domain.product.ProductSnapshot;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public interface CatalogRepository {
    Optional<ProductSnapshot> findByBarcode(String barcode, Instant at) throws SQLException;
}
