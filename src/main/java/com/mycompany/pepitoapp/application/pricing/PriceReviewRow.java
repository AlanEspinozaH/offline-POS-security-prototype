package com.mycompany.pepitoapp.application.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceReviewRow(String productId, String productName, long currentPriceCents,
        BigDecimal previousCostCents, BigDecimal latestCostCents, BigDecimal historicalWeightedCostCents,
        BigDecimal grossMargin, BigDecimal costVariation, int targetMarginBasisPoints, int minimumMarginBasisPoints,
        BigDecimal theoreticalPriceCents, long suggestedPriceCents, LocalDate lockedUntil, String status) { }
