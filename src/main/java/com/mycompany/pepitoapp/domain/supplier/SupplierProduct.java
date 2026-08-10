package com.mycompany.pepitoapp.domain.supplier;

public record SupplierProduct(long supplierId, String productId, String supplierSku,
        int unitsPerPack, String presentationName, Long lastPackPriceCents) { }
