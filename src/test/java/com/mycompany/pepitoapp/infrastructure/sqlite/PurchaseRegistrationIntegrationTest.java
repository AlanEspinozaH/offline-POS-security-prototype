package com.mycompany.pepitoapp.infrastructure.sqlite;

import com.mycompany.pepitoapp.domain.purchase.Purchase;
import com.mycompany.pepitoapp.domain.purchase.PurchaseLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PurchaseRegistrationIntegrationTest {
    @TempDir java.nio.file.Path temp;

    @Test void registeringPurchaseUpdatesCostAndStockButNeverSalePrice() throws Exception {
        SQLiteTestDatabase db=new SQLiteTestDatabase(temp); db.addProduct("0775000000001","Coca-Cola","3.50","2.20"); db.migrate(); long supplier=db.addSupplier("ABC");
        var gateway=new SQLitePurchaseRegistrationGateway(db.provider,ZoneId.of("America/Lima"));
        var result=gateway.register(new Purchase(supplier,Instant.parse("2026-08-09T15:00:00Z"),null,null,List.of(new PurchaseLine("0775000000001",24,10,1,0,6000,0,"Caja"))));
        assertEquals(264,result.lines().getFirst().totalReceivedUnits());
        assertEquals(1,db.scalarLong("SELECT COUNT(*) FROM purchases")); assertEquals(1,db.scalarLong("SELECT COUNT(*) FROM product_cost_history"));
        assertEquals(264,db.scalarLong("SELECT stock_unidades FROM productos WHERE id_productos='0775000000001'"));
        assertEquals("3.50",db.scalarString("SELECT precio_unitario_venta FROM productos WHERE id_productos='0775000000001'"));
        assertEquals(350,db.scalarLong("SELECT price_cents FROM sale_price_history WHERE product_id='0775000000001'"));
    }

    @Test void historicalAverageIsWeightedByReceivedUnits() throws Exception {
        SQLiteTestDatabase db=new SQLiteTestDatabase(temp); db.addProduct("A","Producto","4.00","0"); db.migrate(); long supplier=db.addSupplier("ABC");
        var gateway=new SQLitePurchaseRegistrationGateway(db.provider);
        gateway.register(new Purchase(supplier,Instant.parse("2026-07-01T12:00:00Z"),null,null,List.of(new PurchaseLine("A",24,1,0,0,4800,0))));
        var second=gateway.register(new Purchase(supplier,Instant.parse("2026-08-01T12:00:00Z"),null,null,List.of(new PurchaseLine("A",24,1,0,0,5760,0))));
        assertEquals(0,new BigDecimal("220").compareTo(second.lines().getFirst().historicalWeightedCostCents()));
    }

    @Test void lockedPriceCreatesWarningAndProposalWithoutChangingPrice() throws Exception {
        SQLiteTestDatabase db=new SQLiteTestDatabase(temp); db.addProduct("A","Producto","3.50","2.20"); db.migrate(); long supplier=db.addSupplier("ABC");
        new SQLitePricingRepository(db.provider).setPolicy("A",3000,4000,LocalDate.of(2026,10,1));
        var result=new SQLitePurchaseRegistrationGateway(db.provider,ZoneId.of("America/Lima")).register(new Purchase(supplier,Instant.parse("2026-08-09T15:00:00Z"),null,null,List.of(new PurchaseLine("A",24,1,0,0,6000,0))));
        assertTrue(result.lines().getFirst().proposalCreated()); assertTrue(result.lines().getFirst().warning().contains("protegido hasta 2026-10-01"));
        assertEquals(1,db.scalarLong("SELECT COUNT(*) FROM sale_price_change_proposal WHERE status='PENDING'"));
        assertEquals(350,db.scalarLong("SELECT price_cents FROM sale_price_history WHERE product_id='A'"));
    }

    @Test void failingLineRollsBackWholePurchase() throws Exception {
        SQLiteTestDatabase db=new SQLiteTestDatabase(temp); db.addProduct("A","Producto","3.50","2.20"); db.migrate(); long supplier=db.addSupplier("ABC");
        var purchase=new Purchase(supplier,Instant.now(),null,null,List.of(new PurchaseLine("A",1,1,0,0,100,0),new PurchaseLine("MISSING",1,1,0,0,100,0)));
        assertThrows(java.sql.SQLException.class,()->new SQLitePurchaseRegistrationGateway(db.provider).register(purchase));
        assertEquals(0,db.scalarLong("SELECT COUNT(*) FROM purchases")); assertEquals(0,db.scalarLong("SELECT COUNT(*) FROM purchase_lines"));
        assertEquals(0,db.scalarLong("SELECT stock_unidades FROM productos WHERE id_productos='A'"));
    }
}
