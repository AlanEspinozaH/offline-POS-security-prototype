package com.mycompany.pepitoapp.domain.purchase;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record Purchase(long supplierId, Instant purchasedAt, String referenceNumber, String notes, List<PurchaseLine> lines) {
    public Purchase {
        if (supplierId <= 0) throw new IllegalArgumentException("El proveedor es obligatorio");
        Objects.requireNonNull(purchasedAt, "purchasedAt");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (lines.isEmpty()) throw new IllegalArgumentException("La compra requiere al menos una línea");
        referenceNumber = normalize(referenceNumber);
        notes = normalize(notes);
    }
    private static String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
