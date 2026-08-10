package com.mycompany.pepitoapp.application.catalog;

import com.mycompany.pepitoapp.domain.product.ProductSnapshot;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Optional;

public final class ProductQueryService {
    private final CatalogRepository repository;
    private final Clock clock;

    public ProductQueryService(CatalogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Optional<ProductSnapshot> findByBarcode(String barcode) throws SQLException {
        if (barcode == null || barcode.isBlank()) return Optional.empty();
        return repository.findByBarcode(barcode.trim(), clock.instant());
    }
}
