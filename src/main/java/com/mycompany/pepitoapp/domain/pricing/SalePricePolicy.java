package com.mycompany.pepitoapp.domain.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class SalePricePolicy {
    private SalePricePolicy() { }
    public static boolean isValidSalePrice(long priceCents) { return priceCents >= 0 && priceCents % 10 == 0; }
    public static void requireValidSalePrice(long priceCents) {
        if (!isValidSalePrice(priceCents)) throw new IllegalArgumentException("El precio de venta debe ser múltiplo de S/ 0.10");
    }
    public static long roundUpTo10Cents(BigDecimal theoreticalCents) {
        if (theoreticalCents.signum() < 0) throw new IllegalArgumentException("El precio no puede ser negativo");
        return theoreticalCents.divide(BigDecimal.TEN, 0, RoundingMode.CEILING).longValueExact() * 10L;
    }
    public static long roundUpTo10Cents(long cents) { return roundUpTo10Cents(BigDecimal.valueOf(cents)); }
}
