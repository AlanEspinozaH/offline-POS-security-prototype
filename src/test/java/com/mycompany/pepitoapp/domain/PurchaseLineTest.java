package com.mycompany.pepitoapp.domain;

import com.mycompany.pepitoapp.domain.pricing.PricingCalculator;
import com.mycompany.pepitoapp.domain.pricing.SalePricePolicy;
import com.mycompany.pepitoapp.domain.purchase.PurchaseLine;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PurchaseLineTest {
    @Test void tenPaidBoxesAndOneBonusBoxPreserveExactEffectiveCost() {
        PurchaseLine line=new PurchaseLine("7750000000001",24,10,1,0,6000,0);
        assertEquals(240,line.paidUnits()); assertEquals(24,line.bonusPackUnits());
        assertEquals(264,line.totalReceivedUnits()); assertEquals(60000,line.grossAmountCents()); assertEquals(60000,line.netAmountCents());
        assertEquals(0,new BigDecimal("227.2727272727273").compareTo(line.effectiveUnitCostCents()));
    }

    @Test void threeBoxesAt5760Produce240PerUnit() {
        PurchaseLine line=new PurchaseLine("A",24,3,0,0,5760,0);
        assertEquals(72,line.totalReceivedUnits()); assertEquals(17280,line.netAmountCents());
        assertEquals(0,new BigDecimal("240").compareTo(line.effectiveUnitCostCents()));
    }

    @Test void monetaryDiscountReducesNetAndUnitCost() {
        PurchaseLine line=new PurchaseLine("A",10,2,0,0,5000,1000);
        assertEquals(10000,line.grossAmountCents()); assertEquals(9000,line.netAmountCents());
        assertEquals(0,new BigDecimal("450").compareTo(line.effectiveUnitCostCents()));
    }

    @Test void bonusUnitsWithoutBonusPackIncreaseReceivedQuantity() {
        PurchaseLine line=new PurchaseLine("A",12,2,0,3,2400,0);
        assertEquals(27,line.totalReceivedUnits()); assertEquals(0,new BigDecimal("177.7777777777778").compareTo(line.effectiveUnitCostCents()));
    }

    @Test void costVariationSupportsPositiveChanges() {
        assertEquals(0,new BigDecimal("0.06818181818181818").compareTo(PricingCalculator.costVariation(new BigDecimal("220"),new BigDecimal("235"))));
    }

    @Test void costVariationSupportsNegativeChanges() {
        assertEquals(0,new BigDecimal("-0.06382978723404255").compareTo(PricingCalculator.costVariation(new BigDecimal("235"),new BigDecimal("220"))));
    }

    @Test void salePrice350CentsIsValid() {
        assertTrue(SalePricePolicy.isValidSalePrice(350));
    }

    @Test void salePrice353CentsIsInvalid() {
        assertFalse(SalePricePolicy.isValidSalePrice(353));
    }

    @Test void roundUpToTenCentsUsesCommercialCeiling() {
        assertEquals(360,SalePricePolicy.roundUpTo10Cents(360));
        assertEquals(370,SalePricePolicy.roundUpTo10Cents(361));
        assertEquals(370,SalePricePolicy.roundUpTo10Cents(369));
        assertEquals(370,SalePricePolicy.roundUpTo10Cents(370));
    }
}
