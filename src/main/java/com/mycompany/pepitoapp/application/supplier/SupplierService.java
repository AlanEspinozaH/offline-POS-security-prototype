package com.mycompany.pepitoapp.application.supplier;

import com.mycompany.pepitoapp.domain.supplier.Supplier;
import com.mycompany.pepitoapp.domain.supplier.SupplierProduct;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface SupplierService {
    List<Supplier> listActive() throws SQLException;
    Supplier create(String name, String taxId) throws SQLException;
    Optional<SupplierProduct> findPresentation(long supplierId, String productId) throws SQLException;
}
