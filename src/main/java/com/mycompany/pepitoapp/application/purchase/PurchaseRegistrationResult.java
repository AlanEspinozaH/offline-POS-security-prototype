package com.mycompany.pepitoapp.application.purchase;

import java.util.List;

public record PurchaseRegistrationResult(long purchaseId, List<PurchaseLineResult> lines) {
    public PurchaseRegistrationResult { lines = List.copyOf(lines); }
}
