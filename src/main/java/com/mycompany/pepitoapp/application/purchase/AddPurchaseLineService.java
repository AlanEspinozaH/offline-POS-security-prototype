package com.mycompany.pepitoapp.application.purchase;

import com.mycompany.pepitoapp.domain.purchase.PurchaseLine;
import java.math.BigDecimal;

public final class AddPurchaseLineService {
    public PurchaseLine create(String productId, int unitsPerPack, int paidPacks, int bonusPacks,
            int bonusUnits, long packPriceCents, long discountCents, String presentationName) {
        return new PurchaseLine(productId, unitsPerPack, paidPacks, bonusPacks, bonusUnits,
                packPriceCents, discountCents, presentationName);
    }

    public LinePreview preview(PurchaseLine line) {
        return new LinePreview(line.grossAmountCents(), line.netAmountCents(),
                line.totalReceivedUnits(), line.effectiveUnitCostCents());
    }

    public record LinePreview(long grossAmountCents, long netAmountCents,
            long totalReceivedUnits, BigDecimal effectiveUnitCostCents) { }
}
