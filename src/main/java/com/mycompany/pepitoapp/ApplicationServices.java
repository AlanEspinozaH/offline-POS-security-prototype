package com.mycompany.pepitoapp;

import com.mycompany.pepitoapp.application.catalog.CatalogMaintenanceService;
import com.mycompany.pepitoapp.application.catalog.ProductQueryService;
import com.mycompany.pepitoapp.application.pricing.PriceReviewService;
import com.mycompany.pepitoapp.application.pricing.ScheduleSalePriceService;
import com.mycompany.pepitoapp.application.purchase.AddPurchaseLineService;
import com.mycompany.pepitoapp.application.purchase.RegisterPurchaseService;
import com.mycompany.pepitoapp.application.supplier.SupplierService;
import com.mycompany.pepitoapp.infrastructure.sqlite.SQLiteCatalogMaintenanceService;
import com.mycompany.pepitoapp.infrastructure.sqlite.SQLiteCatalogRepository;
import com.mycompany.pepitoapp.infrastructure.sqlite.SQLiteMigrationRunner;
import com.mycompany.pepitoapp.infrastructure.sqlite.SQLitePricingRepository;
import com.mycompany.pepitoapp.infrastructure.sqlite.SQLitePurchaseRegistrationGateway;
import com.mycompany.pepitoapp.infrastructure.sqlite.SQLiteSupplierService;
import com.mycompany.pepitoapp.storage.ProductDatabaseProvider;
import java.sql.SQLException;
import java.time.Clock;

public final class ApplicationServices {
    private static ApplicationServices instance;
    private final ProductQueryService productQueryService;
    private final CatalogMaintenanceService catalogMaintenanceService;
    private final SupplierService supplierService;
    private final AddPurchaseLineService addPurchaseLineService;
    private final RegisterPurchaseService registerPurchaseService;
    private final PriceReviewService priceReviewService;
    private final ScheduleSalePriceService scheduleSalePriceService;

    private ApplicationServices(ProductDatabaseProvider provider, Clock clock) throws SQLException {
        new SQLiteMigrationRunner(provider).migrate();
        var pricingRepository=new SQLitePricingRepository(provider);
        productQueryService=new ProductQueryService(new SQLiteCatalogRepository(provider),clock);
        catalogMaintenanceService=new SQLiteCatalogMaintenanceService(provider);
        supplierService=new SQLiteSupplierService(provider);
        addPurchaseLineService=new AddPurchaseLineService();
        registerPurchaseService=new RegisterPurchaseService(new SQLitePurchaseRegistrationGateway(provider));
        priceReviewService=new PriceReviewService(pricingRepository,clock);
        scheduleSalePriceService=new ScheduleSalePriceService(pricingRepository,clock);
    }

    public static synchronized void initialize() throws SQLException { if(instance==null) instance=new ApplicationServices(new ProductDatabaseProvider(),Clock.systemDefaultZone()); }
    public static ApplicationServices get() { if(instance==null) throw new IllegalStateException("Servicios no inicializados"); return instance; }
    public ProductQueryService productQuery() { return productQueryService; }
    public CatalogMaintenanceService catalogMaintenance() { return catalogMaintenanceService; }
    public SupplierService suppliers() { return supplierService; }
    public AddPurchaseLineService addPurchaseLine() { return addPurchaseLineService; }
    public RegisterPurchaseService registerPurchase() { return registerPurchaseService; }
    public PriceReviewService priceReview() { return priceReviewService; }
    public ScheduleSalePriceService scheduleSalePrice() { return scheduleSalePriceService; }
}
