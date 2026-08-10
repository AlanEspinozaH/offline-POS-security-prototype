package com.mycompany.pepitoapp.application.purchase;

import com.mycompany.pepitoapp.domain.purchase.Purchase;
import java.sql.SQLException;

public interface PurchaseRegistrationGateway {
    PurchaseRegistrationResult register(Purchase purchase) throws SQLException;
}
