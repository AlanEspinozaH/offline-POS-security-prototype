package com.mycompany.pepitoapp.application.purchase;

import java.math.BigDecimal;

public record PurchaseLineResult(String productId, long totalReceivedUnits, long netAmountCents,
        BigDecimal effectiveUnitCostCents, BigDecimal previousUnitCostCents,
        BigDecimal historicalWeightedCostCents, long currentSalePriceCents,
        BigDecimal grossMargin, BigDecimal costVariation, boolean proposalCreated, String warning) { }
