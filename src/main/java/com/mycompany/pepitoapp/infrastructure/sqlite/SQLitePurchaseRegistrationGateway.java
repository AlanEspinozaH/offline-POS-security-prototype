package com.mycompany.pepitoapp.infrastructure.sqlite;

import com.mycompany.pepitoapp.application.purchase.PurchaseLineResult;
import com.mycompany.pepitoapp.application.purchase.PurchaseRegistrationGateway;
import com.mycompany.pepitoapp.application.purchase.PurchaseRegistrationResult;
import com.mycompany.pepitoapp.domain.pricing.PricingCalculator;
import com.mycompany.pepitoapp.domain.pricing.SalePricePolicy;
import com.mycompany.pepitoapp.domain.purchase.Purchase;
import com.mycompany.pepitoapp.domain.purchase.PurchaseLine;
import com.mycompany.pepitoapp.storage.ProductDatabaseProvider;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class SQLitePurchaseRegistrationGateway implements PurchaseRegistrationGateway {
    private final ProductDatabaseProvider databaseProvider;
    private final ZoneId businessZone;

    public SQLitePurchaseRegistrationGateway(ProductDatabaseProvider databaseProvider) {
        this(databaseProvider, ZoneId.systemDefault());
    }

    public SQLitePurchaseRegistrationGateway(ProductDatabaseProvider databaseProvider, ZoneId businessZone) {
        this.databaseProvider = databaseProvider;
        this.businessZone = businessZone;
    }

    @Override
    public PurchaseRegistrationResult register(Purchase purchase) throws SQLException {
        try (Connection connection = databaseProvider.getConnection()) {
            connection.setAutoCommit(false);
            try {
                validateSupplier(connection, purchase.supplierId());
                for (PurchaseLine line : purchase.lines()) validateUniqueProduct(connection, line.productId());
                long purchaseId = insertPurchase(connection, purchase);
                List<PurchaseLineResult> results = new ArrayList<>();
                for (PurchaseLine line : purchase.lines()) results.add(insertLineAndProjections(connection, purchaseId, purchase, line));
                connection.commit();
                return new PurchaseRegistrationResult(purchaseId, results);
            } catch (Exception ex) {
                connection.rollback();
                if (ex instanceof SQLException sqlException) throw sqlException;
                throw new SQLException("No se pudo confirmar la compra; se revirtieron todos los cambios", ex);
            }
        }
    }

    private PurchaseLineResult insertLineAndProjections(Connection connection, long purchaseId,
            Purchase purchase, PurchaseLine line) throws SQLException {
        BigDecimal previousCost = findLatestCost(connection, line.productId());
        long lineId = insertLine(connection, purchaseId, line);
        upsertCostSummary(connection, lineId, line, purchase.purchasedAt().toString());
        insertInventoryMovement(connection, purchaseId, lineId, purchase, line);
        updateLegacyStock(connection, line);
        rememberSupplierPresentation(connection, purchase.supplierId(), line, purchase.purchasedAt().toString());

        CostSummary summary = findCostSummary(connection, line.productId());
        long salePrice = findEffectiveSalePrice(connection, line.productId(), purchase.purchasedAt().toString());
        BigDecimal margin = salePrice > 0 ? PricingCalculator.grossMargin(salePrice, line.effectiveUnitCostCents()) : null;
        BigDecimal variation = PricingCalculator.costVariation(previousCost, line.effectiveUnitCostCents());
        Policy policy = findPolicy(connection, line.productId());
        boolean proposal = false;
        String warning = null;
        if (margin != null && margin.compareTo(BigDecimal.valueOf(policy.minimumMarginBasisPoints(), 4)) < 0) {
            LocalDate purchaseDate = purchase.purchasedAt().atZone(businessZone).toLocalDate();
            boolean protectedPrice = policy.lockedUntil() != null && purchaseDate.isBefore(policy.lockedUntil());
            warning = protectedPrice ? "El margen disminuyó. Precio protegido hasta " + policy.lockedUntil() : "Margen inferior a la política configurada";
            insertProposal(connection, purchaseId, line, salePrice, policy.targetMarginBasisPoints(), warning, purchase.purchasedAt().toString());
            proposal = true;
        }
        return new PurchaseLineResult(line.productId(), line.totalReceivedUnits(), line.netAmountCents(),
                line.effectiveUnitCostCents(), previousCost, summary.weightedCostCents(), salePrice,
                margin, variation, proposal, warning);
    }

    private long insertPurchase(Connection c, Purchase p) throws SQLException {
        String sql = "INSERT INTO purchases(supplier_id,purchased_at,reference_number,notes,status,created_at) VALUES(?,?,?,?, 'CONFIRMED', ?)";
        try (var s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            s.setLong(1, p.supplierId()); s.setString(2, p.purchasedAt().toString());
            s.setString(3, p.referenceNumber()); s.setString(4, p.notes()); s.setString(5, p.purchasedAt().toString());
            s.executeUpdate(); try (ResultSet keys = s.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("SQLite no devolvió el id de compra"); return keys.getLong(1); }
        }
    }

    private long insertLine(Connection c, long purchaseId, PurchaseLine line) throws SQLException {
        String sql = "INSERT INTO purchase_lines(purchase_id,product_id,units_per_pack,paid_packs,bonus_packs,bonus_units,pack_price_cents,gross_amount_cents,discount_cents,net_amount_cents,total_received_units) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        try (var s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            s.setLong(1, purchaseId); s.setString(2, line.productId()); s.setInt(3, line.unitsPerPack());
            s.setInt(4, line.paidPacks()); s.setInt(5, line.bonusPacks()); s.setInt(6, line.bonusUnits());
            s.setLong(7, line.packPriceCents()); s.setLong(8, line.grossAmountCents()); s.setLong(9, line.discountCents());
            s.setLong(10, line.netAmountCents()); s.setLong(11, line.totalReceivedUnits()); s.executeUpdate();
            try (ResultSet keys = s.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("SQLite no devolvió el id de línea"); return keys.getLong(1); }
        }
    }

    private void upsertCostSummary(Connection c, long lineId, PurchaseLine line, String at) throws SQLException {
        String sql = "INSERT INTO product_cost_summary(product_id,latest_purchase_line_id,latest_net_amount_cents,latest_received_units,cumulative_net_amount_cents,cumulative_received_units,updated_at) VALUES(?,?,?,?,?,?,?) "
                + "ON CONFLICT(product_id) DO UPDATE SET latest_purchase_line_id=excluded.latest_purchase_line_id,latest_net_amount_cents=excluded.latest_net_amount_cents,latest_received_units=excluded.latest_received_units,cumulative_net_amount_cents=product_cost_summary.cumulative_net_amount_cents+excluded.latest_net_amount_cents,cumulative_received_units=product_cost_summary.cumulative_received_units+excluded.latest_received_units,updated_at=excluded.updated_at";
        try (var s = c.prepareStatement(sql)) {
            s.setString(1, line.productId()); s.setLong(2, lineId); s.setLong(3, line.netAmountCents()); s.setLong(4, line.totalReceivedUnits());
            s.setLong(5, line.netAmountCents()); s.setLong(6, line.totalReceivedUnits()); s.setString(7, at); s.executeUpdate();
        }
    }

    private void insertInventoryMovement(Connection c, long purchaseId, long lineId, Purchase p, PurchaseLine line) throws SQLException {
        try (var s = c.prepareStatement("INSERT INTO inventory_movements(product_id,purchase_id,purchase_line_id,quantity_units,movement_type,occurred_at) VALUES(?,?,?,?,'PURCHASE_RECEIPT',?)")) {
            s.setString(1, line.productId()); s.setLong(2, purchaseId); s.setLong(3, lineId); s.setLong(4, line.totalReceivedUnits()); s.setString(5, p.purchasedAt().toString()); s.executeUpdate();
        }
    }

    private void updateLegacyStock(Connection c, PurchaseLine line) throws SQLException {
        String sql = "UPDATE productos SET stock_unidades = CAST(COALESCE(NULLIF(stock_unidades,''),'0') AS INTEGER) + ? WHERE id_productos=?";
        try (var s = c.prepareStatement(sql)) { s.setLong(1, line.totalReceivedUnits()); s.setString(2, line.productId()); if (s.executeUpdate() != 1) throw new SQLException("No se pudo actualizar un único producto: " + line.productId()); }
    }

    private void rememberSupplierPresentation(Connection c, long supplierId, PurchaseLine line, String at) throws SQLException {
        String sql = "INSERT INTO supplier_product(supplier_id,product_id,supplier_sku,units_per_pack,presentation_name,last_pack_price_cents,updated_at) VALUES(?,?,NULL,?,?,?,?) "
                + "ON CONFLICT(supplier_id,product_id) DO UPDATE SET units_per_pack=excluded.units_per_pack,presentation_name=excluded.presentation_name,last_pack_price_cents=excluded.last_pack_price_cents,updated_at=excluded.updated_at";
        try (var s = c.prepareStatement(sql)) {
            s.setLong(1, supplierId); s.setString(2, line.productId()); s.setInt(3, line.unitsPerPack());
            s.setString(4, line.presentationName()); s.setLong(5, line.packPriceCents()); s.setString(6, at); s.executeUpdate();
        }
    }

    private void insertProposal(Connection c, long purchaseId, PurchaseLine line, long salePrice,
            int targetMarginBps, String warning, String at) throws SQLException {
        BigDecimal theoretical = PricingCalculator.theoreticalPriceCents(line.effectiveUnitCostCents(), targetMarginBps);
        long suggested = SalePricePolicy.roundUpTo10Cents(theoretical);
        String sql = "INSERT INTO sale_price_change_proposal(product_id,purchase_id,current_price_cents,theoretical_price_cents,suggested_price_cents,status,warning,reason,created_at) VALUES(?,?,?,?,?,'PENDING',?,'Margen posterior a compra',?)";
        try (var s = c.prepareStatement(sql)) {
            s.setString(1, line.productId()); s.setLong(2, purchaseId); s.setLong(3, salePrice);
            s.setString(4, theoretical.toPlainString()); s.setLong(5, suggested); s.setString(6, warning); s.setString(7, at); s.executeUpdate();
        }
    }

    private void validateSupplier(Connection c, long id) throws SQLException {
        try (var s = c.prepareStatement("SELECT 1 FROM suppliers WHERE id=? AND active=1")) { s.setLong(1, id); try (var r=s.executeQuery()) { if (!r.next()) throw new SQLException("Proveedor inexistente o inactivo: " + id); } }
    }

    private void validateUniqueProduct(Connection c, String productId) throws SQLException {
        try (var s = c.prepareStatement("SELECT COUNT(*) FROM productos WHERE id_productos=?")) { s.setString(1, productId); try (var r=s.executeQuery()) { r.next(); int count=r.getInt(1); if (count != 1) throw new SQLException(count == 0 ? "Producto inexistente: " + productId : "Código de barras duplicado: " + productId); } }
    }

    private BigDecimal findLatestCost(Connection c, String productId) throws SQLException {
        try (var s=c.prepareStatement("SELECT latest_net_amount_cents,latest_received_units FROM product_cost_summary WHERE product_id=?")) { s.setString(1, productId); try (var r=s.executeQuery()) { return r.next() ? BigDecimal.valueOf(r.getLong(1)).divide(BigDecimal.valueOf(r.getLong(2)), PurchaseLine.COST_CONTEXT) : null; } }
    }

    private CostSummary findCostSummary(Connection c, String productId) throws SQLException {
        try (var s=c.prepareStatement("SELECT cumulative_net_amount_cents,cumulative_received_units FROM product_cost_summary WHERE product_id=?")) { s.setString(1, productId); try (var r=s.executeQuery()) { if (!r.next()) throw new SQLException("Proyección de costo no encontrada"); return new CostSummary(BigDecimal.valueOf(r.getLong(1)).divide(BigDecimal.valueOf(r.getLong(2)), PurchaseLine.COST_CONTEXT)); } }
    }

    private long findEffectiveSalePrice(Connection c, String productId, String at) throws SQLException {
        String sql="SELECT price_cents FROM sale_price_history WHERE product_id=? AND effective_from<=? AND (effective_to IS NULL OR effective_to>?) ORDER BY effective_from DESC,id DESC LIMIT 1";
        try (var s=c.prepareStatement(sql)) { s.setString(1, productId); s.setString(2, at); s.setString(3, at); try (var r=s.executeQuery()) { if (!r.next()) throw new SQLException("Producto sin precio de venta vigente: " + productId); return r.getLong(1); } }
    }

    private Policy findPolicy(Connection c, String productId) throws SQLException {
        try (var s=c.prepareStatement("SELECT target_margin_basis_points,minimum_margin_basis_points,locked_until FROM product_price_policy WHERE product_id=?")) { s.setString(1, productId); try (var r=s.executeQuery()) { if (!r.next()) return new Policy(3000,2000,null); String locked=r.getString(3); return new Policy(r.getInt(1),r.getInt(2),locked==null?null:LocalDate.parse(locked)); } }
    }

    private record CostSummary(BigDecimal weightedCostCents) { }
    private record Policy(int targetMarginBasisPoints, int minimumMarginBasisPoints, LocalDate lockedUntil) { }
}
