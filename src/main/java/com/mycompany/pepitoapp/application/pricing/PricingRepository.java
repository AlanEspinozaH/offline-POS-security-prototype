package com.mycompany.pepitoapp.application.pricing;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface PricingRepository {
    long findEffectivePrice(String productId, Instant at) throws SQLException;
    void schedule(String productId, long priceCents, Instant effectiveFrom, String reason, String createdBy) throws SQLException;
    void setPolicy(String productId, int targetMarginBasisPoints, int minimumMarginBasisPoints, LocalDate lockedUntil) throws SQLException;
    List<PriceReviewRow> listReviewRows(Instant at) throws SQLException;
}
