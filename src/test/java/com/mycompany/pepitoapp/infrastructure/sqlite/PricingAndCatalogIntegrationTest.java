package com.mycompany.pepitoapp.infrastructure.sqlite;

import com.mycompany.pepitoapp.application.catalog.ProductQueryService;
import com.mycompany.pepitoapp.application.pricing.ScheduleSalePriceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PricingAndCatalogIntegrationTest {
    @TempDir java.nio.file.Path temp;

    @Test void futurePriceDoesNotChangeCurrentPriceBeforeEffectiveFrom() throws Exception {
        SQLiteTestDatabase db=new SQLiteTestDatabase(temp); db.addProduct("A","Producto","3.50","2.20"); db.migrate();
        Instant now=Instant.parse("2026-08-09T12:00:00Z"); var service=new ScheduleSalePriceService(new SQLitePricingRepository(db.provider),Clock.fixed(now,ZoneOffset.UTC));
        service.schedule("A",370,Instant.parse("2026-09-01T00:00:00Z"),"Incremento gradual",null);
        assertEquals(350,service.currentPrice("A"));
    }

    @Test void scheduledPriceBecomesEffectiveAtEffectiveFrom() throws Exception {
        SQLiteTestDatabase db=new SQLiteTestDatabase(temp); db.addProduct("A","Producto","3.50","2.20"); db.migrate();
        var service=new ScheduleSalePriceService(new SQLitePricingRepository(db.provider),Clock.systemUTC());
        service.schedule("A",370,Instant.parse("2026-09-01T00:00:00Z"),"Incremento gradual",null);
        assertEquals(370,service.priceAt("A",Instant.parse("2026-09-01T00:00:00Z")));
        assertEquals(370,service.priceAt("A",Instant.parse("2026-11-01T00:00:00Z")));
    }

    @Test void leadingZerosInBarcodeArePreservedAsString() throws Exception {
        SQLiteTestDatabase db=new SQLiteTestDatabase(temp); db.addProduct("0001234567890","Producto","1.50","1.00"); db.migrate();
        var service=new ProductQueryService(new SQLiteCatalogRepository(db.provider),Clock.systemUTC());
        var product=service.findByBarcode("0001234567890").orElseThrow();
        assertEquals("0001234567890",product.barcode());
    }
}
