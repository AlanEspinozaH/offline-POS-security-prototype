package com.mycompany.pepitoapp.domain.purchase;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

public record PurchaseLine(String productId, int unitsPerPack, int paidPacks, int bonusPacks,
        int bonusUnits, long packPriceCents, long discountCents, String presentationName) {
    public static final MathContext COST_CONTEXT = new MathContext(16, RoundingMode.HALF_UP);

    public PurchaseLine {
        Objects.requireNonNull(productId, "productId");
        if (productId.isBlank()) throw new IllegalArgumentException("El producto es obligatorio");
        if (unitsPerPack <= 0) throw new IllegalArgumentException("Las unidades por paquete deben ser positivas");
        if (paidPacks < 0 || bonusPacks < 0 || bonusUnits < 0) throw new IllegalArgumentException("Las cantidades no pueden ser negativas");
        if (packPriceCents < 0 || discountCents < 0) throw new IllegalArgumentException("Los importes no pueden ser negativos");
        if (totalReceivedUnits(unitsPerPack, paidPacks, bonusPacks, bonusUnits) <= 0) throw new IllegalArgumentException("La línea debe recibir al menos una unidad");
        if (discountCents > Math.multiplyExact((long) paidPacks, packPriceCents)) throw new IllegalArgumentException("El descuento no puede superar el importe bruto");
        presentationName = presentationName == null || presentationName.isBlank() ? "Paquete" : presentationName.trim();
    }

    public PurchaseLine(String productId, int unitsPerPack, int paidPacks, int bonusPacks,
            int bonusUnits, long packPriceCents, long discountCents) {
        this(productId, unitsPerPack, paidPacks, bonusPacks, bonusUnits, packPriceCents, discountCents, "Paquete");
    }

    public long paidUnits() { return Math.multiplyExact((long) paidPacks, unitsPerPack); }
    public long bonusPackUnits() { return Math.multiplyExact((long) bonusPacks, unitsPerPack); }
    public long totalReceivedUnits() { return totalReceivedUnits(unitsPerPack, paidPacks, bonusPacks, bonusUnits); }
    public long grossAmountCents() { return Math.multiplyExact((long) paidPacks, packPriceCents); }
    public long netAmountCents() { return grossAmountCents() - discountCents; }
    public BigDecimal effectiveUnitCostCents() { return BigDecimal.valueOf(netAmountCents()).divide(BigDecimal.valueOf(totalReceivedUnits()), COST_CONTEXT); }
    public BigDecimal effectiveUnitCostSoles() { return effectiveUnitCostCents().movePointLeft(2); }

    private static long totalReceivedUnits(int unitsPerPack, int paidPacks, int bonusPacks, int bonusUnits) {
        return Math.addExact(Math.multiplyExact(Math.addExact((long) paidPacks, bonusPacks), unitsPerPack), bonusUnits);
    }
}
