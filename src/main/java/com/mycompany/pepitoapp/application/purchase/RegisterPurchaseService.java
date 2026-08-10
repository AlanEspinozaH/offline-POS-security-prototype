package com.mycompany.pepitoapp.application.purchase;

import com.mycompany.pepitoapp.domain.purchase.Purchase;
import java.sql.SQLException;

public final class RegisterPurchaseService {
    private final PurchaseRegistrationGateway gateway;
    public RegisterPurchaseService(PurchaseRegistrationGateway gateway) { this.gateway = gateway; }
    public PurchaseRegistrationResult register(Purchase purchase) throws SQLException { return gateway.register(purchase); }
}
