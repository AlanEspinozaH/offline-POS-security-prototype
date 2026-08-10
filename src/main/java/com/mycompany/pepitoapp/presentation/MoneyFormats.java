package com.mycompany.pepitoapp.presentation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyFormats {
    private MoneyFormats() { }
    public static long parseSolesToCents(String text) {
        if(text==null || text.isBlank()) return 0;
        String normalized=text.trim().replace("S/","").replace(",",".").trim();
        return new BigDecimal(normalized).movePointRight(2).setScale(0,RoundingMode.UNNECESSARY).longValueExact();
    }
    public static String salePrice(long cents) { return "S/ " + BigDecimal.valueOf(cents,2).stripTrailingZeros().toPlainString(); }
    public static String preciseCost(BigDecimal cents) { return cents==null?"—":"S/ " + cents.movePointLeft(2).setScale(6,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
    public static String percent(BigDecimal ratio) { return ratio==null?"—":ratio.movePointRight(2).setScale(2,RoundingMode.HALF_UP).toPlainString()+" %"; }
}
