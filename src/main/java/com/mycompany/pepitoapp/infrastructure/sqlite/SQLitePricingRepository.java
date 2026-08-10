package com.mycompany.pepitoapp.infrastructure.sqlite;

import com.mycompany.pepitoapp.application.pricing.PriceReviewRow;
import com.mycompany.pepitoapp.application.pricing.PricingRepository;
import com.mycompany.pepitoapp.domain.pricing.PricingCalculator;
import com.mycompany.pepitoapp.domain.pricing.SalePricePolicy;
import com.mycompany.pepitoapp.domain.purchase.PurchaseLine;
import com.mycompany.pepitoapp.storage.ProductDatabaseProvider;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class SQLitePricingRepository implements PricingRepository {
    private final ProductDatabaseProvider databaseProvider;
    public SQLitePricingRepository(ProductDatabaseProvider databaseProvider) { this.databaseProvider=databaseProvider; }

    @Override public long findEffectivePrice(String productId, Instant at) throws SQLException {
        String sql="SELECT price_cents FROM sale_price_history WHERE product_id=? AND effective_from<=? AND (effective_to IS NULL OR effective_to>?) ORDER BY effective_from DESC,id DESC LIMIT 1";
        try (var c=databaseProvider.getConnection(); var s=c.prepareStatement(sql)) {
            s.setString(1,productId); s.setString(2,at.toString()); s.setString(3,at.toString());
            try(var r=s.executeQuery()) { if(!r.next()) throw new SQLException("No existe precio vigente para " + productId); return r.getLong(1); }
        }
    }

    @Override public void schedule(String productId, long priceCents, Instant effectiveFrom, String reason, String createdBy) throws SQLException {
        SalePricePolicy.requireValidSalePrice(priceCents);
        String sql="INSERT INTO sale_price_history(product_id,price_cents,effective_from,effective_to,reason,created_at,created_by) VALUES(?,?,?,NULL,?,?,?)";
        try(var c=databaseProvider.getConnection(); var s=c.prepareStatement(sql)) {
            s.setString(1,productId); s.setLong(2,priceCents); s.setString(3,effectiveFrom.toString());
            s.setString(4,reason); s.setString(5,Instant.now().toString()); s.setString(6,createdBy); s.executeUpdate();
        }
    }

    @Override public void setPolicy(String productId, int target, int minimum, LocalDate lockedUntil) throws SQLException {
        if(target<=0 || target>=10000 || minimum<0 || minimum>=10000) throw new IllegalArgumentException("Márgenes de política inválidos");
        String sql="INSERT INTO product_price_policy(product_id,target_margin_basis_points,minimum_margin_basis_points,locked_until,updated_at) VALUES(?,?,?,?,?) "
                + "ON CONFLICT(product_id) DO UPDATE SET target_margin_basis_points=excluded.target_margin_basis_points,minimum_margin_basis_points=excluded.minimum_margin_basis_points,locked_until=excluded.locked_until,updated_at=excluded.updated_at";
        try(var c=databaseProvider.getConnection(); var s=c.prepareStatement(sql)) {
            s.setString(1,productId); s.setInt(2,target); s.setInt(3,minimum); s.setString(4,lockedUntil==null?null:lockedUntil.toString()); s.setString(5,Instant.now().toString()); s.executeUpdate();
        }
    }

    @Override public List<PriceReviewRow> listReviewRows(Instant at) throws SQLException {
        String sql="SELECT p.id_productos,p.nombre,"
                + "(SELECT price_cents FROM sale_price_history h WHERE h.product_id=p.id_productos AND h.effective_from<=? AND (h.effective_to IS NULL OR h.effective_to>?) ORDER BY h.effective_from DESC,h.id DESC LIMIT 1) current_price,"
                + "cs.latest_net_amount_cents,cs.latest_received_units,cs.cumulative_net_amount_cents,cs.cumulative_received_units,"
                + "(SELECT ph.net_amount_cents FROM product_cost_history ph WHERE ph.product_id=p.id_productos AND ph.purchase_line_id<>cs.latest_purchase_line_id ORDER BY ph.purchase_line_id DESC LIMIT 1) previous_net,"
                + "(SELECT ph.total_received_units FROM product_cost_history ph WHERE ph.product_id=p.id_productos AND ph.purchase_line_id<>cs.latest_purchase_line_id ORDER BY ph.purchase_line_id DESC LIMIT 1) previous_units,"
                + "COALESCE(pp.target_margin_basis_points,3000),COALESCE(pp.minimum_margin_basis_points,2000),pp.locked_until,"
                + "COALESCE((SELECT pr.status FROM sale_price_change_proposal pr WHERE pr.product_id=p.id_productos ORDER BY pr.id DESC LIMIT 1),'ESTABLE') "
                + "FROM productos p JOIN product_cost_summary cs ON cs.product_id=p.id_productos LEFT JOIN product_price_policy pp ON pp.product_id=p.id_productos "
                + "WHERE (SELECT COUNT(*) FROM productos px WHERE px.id_productos=p.id_productos)=1 ORDER BY p.nombre";
        try(Connection c=databaseProvider.getConnection(); var s=c.prepareStatement(sql)) {
            s.setString(1,at.toString()); s.setString(2,at.toString());
            try(var r=s.executeQuery()) {
                List<PriceReviewRow> rows=new ArrayList<>();
                while(r.next()) {
                    long sale=r.getLong(3);
                    BigDecimal latest=ratio(r.getLong(4),r.getLong(5));
                    BigDecimal average=ratio(r.getLong(6),r.getLong(7));
                    BigDecimal previous=r.getObject(8)==null?null:ratio(r.getLong(8),r.getLong(9));
                    int target=r.getInt(10); int minimum=r.getInt(11);
                    BigDecimal margin=sale>0?PricingCalculator.grossMargin(sale,latest):null;
                    BigDecimal variation=PricingCalculator.costVariation(previous,latest);
                    BigDecimal theoretical=PricingCalculator.theoreticalPriceCents(latest,target);
                    String lock=r.getString(12);
                    String status=r.getString(13);
                    if(margin!=null && margin.compareTo(BigDecimal.valueOf(minimum,4))<0 && "ESTABLE".equals(status)) status="REVISAR";
                    rows.add(new PriceReviewRow(r.getString(1),r.getString(2),sale,previous,latest,average,margin,variation,target,minimum,theoretical,SalePricePolicy.roundUpTo10Cents(theoretical),lock==null?null:LocalDate.parse(lock),status));
                }
                return rows;
            }
        }
    }

    private BigDecimal ratio(long cents,long units) { return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(units), PurchaseLine.COST_CONTEXT); }
}
