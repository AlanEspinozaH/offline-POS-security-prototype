package com.mycompany.pepitoapp.application.pricing;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

public final class PriceReviewService {
    private final PricingRepository repository;
    private final Clock clock;
    public PriceReviewService(PricingRepository repository, Clock clock) { this.repository=repository; this.clock=clock; }
    public List<PriceReviewRow> list() throws SQLException { return repository.listReviewRows(clock.instant()); }
    public void configurePolicy(String productId, int targetMarginBasisPoints, int minimumMarginBasisPoints, LocalDate lockedUntil) throws SQLException {
        repository.setPolicy(productId, targetMarginBasisPoints, minimumMarginBasisPoints, lockedUntil);
    }
}
