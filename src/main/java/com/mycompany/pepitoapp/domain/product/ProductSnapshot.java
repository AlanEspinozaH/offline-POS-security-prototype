package com.mycompany.pepitoapp.domain.product;

import java.math.BigDecimal;

public record ProductSnapshot(String barcode, String name, String type, int stockUnits,
        long currentSalePriceCents, BigDecimal latestUnitCostCents) { }
