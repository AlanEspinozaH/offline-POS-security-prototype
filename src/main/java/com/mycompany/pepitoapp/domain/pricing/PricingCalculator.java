package com.mycompany.pepitoapp.domain.pricing;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public final class PricingCalculator {
    public static final MathContext CONTEXT = new MathContext(16, RoundingMode.HALF_UP);
    private PricingCalculator() { }
    public static BigDecimal grossMargin(long salePriceCents, BigDecimal unitCostCents) {
        if (salePriceCents <= 0) throw new IllegalArgumentException("El precio de venta debe ser positivo");
        return BigDecimal.valueOf(salePriceCents).subtract(unitCostCents).divide(BigDecimal.valueOf(salePriceCents), CONTEXT);
    }
    public static BigDecimal costVariation(BigDecimal previousCostCents, BigDecimal latestCostCents) {
        if (previousCostCents == null || previousCostCents.signum() == 0) return null;
        return latestCostCents.subtract(previousCostCents).divide(previousCostCents, CONTEXT);
    }
    public static BigDecimal theoreticalPriceCents(BigDecimal unitCostCents, int targetMarginBasisPoints) {
        if (targetMarginBasisPoints <= 0 || targetMarginBasisPoints >= 10_000) throw new IllegalArgumentException("El margen objetivo debe estar entre 0% y 100%");
        return unitCostCents.divide(BigDecimal.ONE.subtract(BigDecimal.valueOf(targetMarginBasisPoints, 4)), CONTEXT);
    }
}
