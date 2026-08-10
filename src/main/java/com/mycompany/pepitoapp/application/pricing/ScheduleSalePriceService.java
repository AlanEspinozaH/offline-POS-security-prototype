package com.mycompany.pepitoapp.application.pricing;

import com.mycompany.pepitoapp.domain.pricing.SalePricePolicy;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;

public final class ScheduleSalePriceService {
    private final PricingRepository repository;
    private final Clock clock;
    public ScheduleSalePriceService(PricingRepository repository, Clock clock) { this.repository=repository; this.clock=clock; }
    public void schedule(String productId, long priceCents, Instant effectiveFrom, String reason, String createdBy) throws SQLException {
        if (productId == null || productId.isBlank()) throw new IllegalArgumentException("El producto es obligatorio");
        SalePricePolicy.requireValidSalePrice(priceCents);
        if (effectiveFrom == null) throw new IllegalArgumentException("La fecha de vigencia es obligatoria");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("El motivo es obligatorio");
        repository.schedule(productId, priceCents, effectiveFrom, reason.trim(), createdBy);
    }
    public long currentPrice(String productId) throws SQLException { return repository.findEffectivePrice(productId, clock.instant()); }
    public long priceAt(String productId, Instant at) throws SQLException { return repository.findEffectivePrice(productId, at); }
}
