package com.farmmanagement.backend.sales;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SalesOrderStore {
    private final JdbcTemplate db;

    public SalesOrderStore(JdbcTemplate db) {
        this.db = db;
    }

    public record SalesOrderItem(
        int id,
        int soId,
        int productId,
        String productName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        String notes
    ) {}

    public record SalesOrder(
        int id,
        String soNumber,
        int customerId,
        String customerName,
        Integer farmId,
        String farmName,
        String orderDate,
        String deliveryDate,
        String status,
        String paymentStatus,
        BigDecimal totalAmount,
        String deliveryAddress,
        String notes,
        Integer createdBy,
        String createdAt,
        String updatedAt,
        List<SalesOrderItem> items
    ) {}

    public record ProductStock(
        int productId,
        String productName,
        BigDecimal quantity,
        String status
    ) {}

    private SalesOrderItem itemRow(ResultSet r, int n) throws SQLException {
        return new SalesOrderItem(
            r.getInt("so_item_id"),
            r.getInt("so_id"),
            r.getInt("product_id"),
            r.getString("product_name"),
            r.getBigDecimal("quantity"),
            r.getBigDecimal("unit_price"),
            r.getBigDecimal("total_price"),
            r.getString("notes")
        );
    }

    private SalesOrder soSummaryRow(ResultSet r, int n) throws SQLException {
        return new SalesOrder(
            r.getInt("so_id"),
            r.getString("so_number"),
            r.getInt("customer_id"),
            r.getString("customer_name"),
            r.getObject("farm_id") != null ? r.getInt("farm_id") : null,
            r.getString("farm_name"),
            r.getDate("order_date") != null ? r.getDate("order_date").toString() : null,
            r.getDate("delivery_date") != null ? r.getDate("delivery_date").toString() : null,
            r.getString("status"),
            r.getString("payment_status"),
            r.getBigDecimal("total_amount"),
            r.getString("delivery_address"),
            r.getString("notes"),
            r.getObject("created_by") != null ? r.getInt("created_by") : null,
            r.getTimestamp("created_at") != null ? r.getTimestamp("created_at").toInstant().toString() : null,
            r.getTimestamp("updated_at") != null ? r.getTimestamp("updated_at").toInstant().toString() : null,
            List.of()
        );
    }

    public List<SalesOrder> listSalesOrders(int offset, int limit, Integer customerId, Integer farmId,
                                            String status, String paymentStatus,
                                            LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder("""
            SELECT so.*, c.customer_name, f.farm_name
            FROM public.sales_order so
            JOIN public.customer c USING(customer_id)
            LEFT JOIN public.farm f USING(farm_id)
            WHERE 1=1
        """);
        List<Object> args = new java.util.ArrayList<>();
        if (customerId != null) { sql.append(" AND so.customer_id = ?"); args.add(customerId); }
        if (farmId != null) { sql.append(" AND so.farm_id = ?"); args.add(farmId); }
        if (status != null && !status.isBlank()) { sql.append(" AND so.status = ?"); args.add(status); }
        if (paymentStatus != null && !paymentStatus.isBlank()) { sql.append(" AND so.payment_status = ?"); args.add(paymentStatus); }
        if (fromDate != null) { sql.append(" AND so.order_date >= ?"); args.add(fromDate); }
        if (toDate != null) { sql.append(" AND so.order_date <= ?"); args.add(toDate); }

        sql.append(" ORDER BY so.so_id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);

        return db.query(sql.toString(), this::soSummaryRow, args.toArray());
    }

    public long countSalesOrders(Integer customerId, Integer farmId, String status,
                                 String paymentStatus, LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder("""
            SELECT count(*) FROM public.sales_order so WHERE 1=1
        """);
        List<Object> args = new java.util.ArrayList<>();
        if (customerId != null) { sql.append(" AND so.customer_id = ?"); args.add(customerId); }
        if (farmId != null) { sql.append(" AND so.farm_id = ?"); args.add(farmId); }
        if (status != null && !status.isBlank()) { sql.append(" AND so.status = ?"); args.add(status); }
        if (paymentStatus != null && !paymentStatus.isBlank()) { sql.append(" AND so.payment_status = ?"); args.add(paymentStatus); }
        if (fromDate != null) { sql.append(" AND so.order_date >= ?"); args.add(fromDate); }
        if (toDate != null) { sql.append(" AND so.order_date <= ?"); args.add(toDate); }

        return db.queryForObject(sql.toString(), Long.class, args.toArray());
    }

    public Optional<SalesOrder> salesOrder(int id) {
        String sql = """
            SELECT so.*, c.customer_name, f.farm_name
            FROM public.sales_order so
            JOIN public.customer c USING(customer_id)
            LEFT JOIN public.farm f USING(farm_id)
            WHERE so.so_id = ?
        """;
        var so = db.query(sql, this::soSummaryRow, id).stream().findFirst();
        if (so.isEmpty()) return Optional.empty();

        List<SalesOrderItem> items = salesOrderItems(id);
        var s = so.get();
        return Optional.of(new SalesOrder(
            s.id(), s.soNumber(), s.customerId(), s.customerName(), s.farmId(), s.farmName(),
            s.orderDate(), s.deliveryDate(), s.status(), s.paymentStatus(), s.totalAmount(),
            s.deliveryAddress(), s.notes(), s.createdBy(), s.createdAt(), s.updatedAt(), items
        ));
    }

    public List<SalesOrderItem> salesOrderItems(int soId) {
        String sql = """
            SELECT soi.*, p.product_name
            FROM public.sales_order_item soi
            JOIN public.harvested_product p USING(product_id)
            WHERE soi.so_id = ?
            ORDER BY soi.so_item_id ASC
        """;
        return db.query(sql, this::itemRow, soId);
    }

    public int createSalesOrder(String soNumber, int customerId, Integer farmId,
                                LocalDate orderDate, LocalDate deliveryDate, String status,
                                String paymentStatus, BigDecimal totalAmount,
                                String deliveryAddress, String notes, int createdBy) {
        return db.queryForObject("""
            INSERT INTO public.sales_order(so_number, customer_id, farm_id, order_date,
                                           delivery_date, status, payment_status,
                                           total_amount, delivery_address, notes, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING so_id
        """, Integer.class, soNumber, customerId, farmId, orderDate, deliveryDate,
            status, paymentStatus, totalAmount, deliveryAddress, notes, createdBy);
    }

    public int createSalesOrderItem(int soId, int productId, BigDecimal quantity,
                                    BigDecimal unitPrice, BigDecimal totalPrice, String notes) {
        return db.queryForObject("""
            INSERT INTO public.sales_order_item(so_id, product_id, quantity, unit_price, total_price, notes)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING so_item_id
        """, Integer.class, soId, productId, quantity, unitPrice, totalPrice, notes);
    }

    public void deleteSalesOrderItems(int soId) {
        db.update("DELETE FROM public.sales_order_item WHERE so_id = ?", soId);
    }

    public void updateSalesOrder(int id, LocalDate deliveryDate, String paymentStatus,
                                 BigDecimal totalAmount, String deliveryAddress, String notes) {
        db.update("""
            UPDATE public.sales_order
            SET delivery_date = ?, payment_status = ?, total_amount = ?,
                delivery_address = ?, notes = ?, updated_at = CURRENT_TIMESTAMP
            WHERE so_id = ?
        """, deliveryDate, paymentStatus, totalAmount, deliveryAddress, notes, id);
    }

    public void updateSalesOrderStatus(int id, String status) {
        db.update("""
            UPDATE public.sales_order
            SET status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE so_id = ?
        """, status, id);
    }

    // ─── Product Stock / Availability Management ──────────────────────────────
    public Optional<ProductStock> lockProductForUpdate(int productId) {
        String sql = """
            SELECT product_id, product_name, COALESCE(quantity, 0) as quantity, status
            FROM public.harvested_product
            WHERE product_id = ?
            FOR UPDATE
        """;
        return db.query(sql, (rs, i) -> new ProductStock(
            rs.getInt("product_id"),
            rs.getString("product_name"),
            rs.getBigDecimal("quantity"),
            rs.getString("status")
        ), productId).stream().findFirst();
    }

    public void deductProductStock(int productId, BigDecimal quantity) {
        db.update("""
            UPDATE public.harvested_product
            SET quantity = quantity - ?, updated_at = CURRENT_TIMESTAMP
            WHERE product_id = ?
        """, quantity, productId);
    }

    public void restoreProductStock(int productId, BigDecimal quantity) {
        db.update("""
            UPDATE public.harvested_product
            SET quantity = quantity + ?, updated_at = CURRENT_TIMESTAMP
            WHERE product_id = ?
        """, quantity, productId);
    }

    public void lockSalesOrder(int soId) {
        db.queryForObject("SELECT so_id FROM public.sales_order WHERE so_id = ? FOR UPDATE", Integer.class, soId);
    }

    public void auditAction(int actor, String entityType, int entityId, String action, String requestId) {
        db.update("INSERT INTO public.audit_log(actor_user_id,event_at,entity_type,entity_key,action,request_id) VALUES (?,CURRENT_TIMESTAMP,?,?,?,?)",
            actor, entityType, Integer.toString(entityId), action, requestId);
    }
}
