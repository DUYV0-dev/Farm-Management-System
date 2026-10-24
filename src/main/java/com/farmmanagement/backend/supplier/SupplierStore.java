package com.farmmanagement.backend.supplier;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SupplierStore {
    private final JdbcTemplate db;

    public SupplierStore(JdbcTemplate db) {
        this.db = db;
    }

    public record Supplier(
        int id,
        String name,
        String contactName,
        String email,
        String phone,
        String address,
        String taxCode,
        String notes,
        String status,
        String createdAt,
        String updatedAt
    ) {}

    public record PurchaseOrderItem(
        int id,
        int poId,
        int materialId,
        String materialName,
        String unit,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        BigDecimal receivedQuantity,
        String notes
    ) {}

    public record PurchaseOrder(
        int id,
        String poNumber,
        int supplierId,
        String supplierName,
        int farmId,
        String farmName,
        int warehouseId,
        String warehouseName,
        String orderDate,
        String expectedDeliveryDate,
        String actualDeliveryDate,
        String status,
        BigDecimal totalAmount,
        String notes,
        Integer createdBy,
        String createdAt,
        String updatedAt,
        List<PurchaseOrderItem> items
    ) {}

    private Supplier supplierRow(ResultSet r, int n) throws SQLException {
        return new Supplier(
            r.getInt("supplier_id"),
            r.getString("supplier_name"),
            r.getString("contact_name"),
            r.getString("email"),
            r.getString("phone"),
            r.getString("address"),
            r.getString("tax_code"),
            r.getString("notes"),
            r.getString("status"),
            r.getTimestamp("created_at") != null ? r.getTimestamp("created_at").toInstant().toString() : null,
            r.getTimestamp("updated_at") != null ? r.getTimestamp("updated_at").toInstant().toString() : null
        );
    }

    private PurchaseOrderItem itemRow(ResultSet r, int n) throws SQLException {
        return new PurchaseOrderItem(
            r.getInt("po_item_id"),
            r.getInt("po_id"),
            r.getInt("material_id"),
            r.getString("material_name"),
            r.getString("unit"),
            r.getBigDecimal("quantity"),
            r.getBigDecimal("unit_price"),
            r.getBigDecimal("total_price"),
            r.getBigDecimal("received_quantity"),
            r.getString("notes")
        );
    }

    private PurchaseOrder poSummaryRow(ResultSet r, int n) throws SQLException {
        return new PurchaseOrder(
            r.getInt("po_id"),
            r.getString("po_number"),
            r.getInt("supplier_id"),
            r.getString("supplier_name"),
            r.getInt("farm_id"),
            r.getString("farm_name"),
            r.getInt("warehouse_id"),
            r.getString("warehouse_name"),
            r.getDate("order_date") != null ? r.getDate("order_date").toString() : null,
            r.getDate("expected_delivery_date") != null ? r.getDate("expected_delivery_date").toString() : null,
            r.getDate("actual_delivery_date") != null ? r.getDate("actual_delivery_date").toString() : null,
            r.getString("status"),
            r.getBigDecimal("total_amount"),
            r.getString("notes"),
            r.getObject("created_by") != null ? r.getInt("created_by") : null,
            r.getTimestamp("created_at") != null ? r.getTimestamp("created_at").toInstant().toString() : null,
            r.getTimestamp("updated_at") != null ? r.getTimestamp("updated_at").toInstant().toString() : null,
            List.of()
        );
    }

    // ─── Supplier CRUD ────────────────────────────────────────────────────────
    public List<Supplier> listSuppliers(int offset, int limit, String search, String status) {
        StringBuilder sql = new StringBuilder("""
            SELECT * FROM public.supplier
            WHERE (strpos(lower(supplier_name), lower(?)) > 0
               OR strpos(lower(COALESCE(contact_name, '')), lower(?)) > 0
               OR strpos(lower(COALESCE(phone, '')), lower(?)) > 0)
        """);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ? ORDER BY supplier_id LIMIT ? OFFSET ?");
            return db.query(sql.toString(), this::supplierRow, search, search, search, status, limit, offset);
        }
        sql.append(" ORDER BY supplier_id LIMIT ? OFFSET ?");
        return db.query(sql.toString(), this::supplierRow, search, search, search, limit, offset);
    }

    public long countSuppliers(String search, String status) {
        StringBuilder sql = new StringBuilder("""
            SELECT count(*) FROM public.supplier
            WHERE (strpos(lower(supplier_name), lower(?)) > 0
               OR strpos(lower(COALESCE(contact_name, '')), lower(?)) > 0
               OR strpos(lower(COALESCE(phone, '')), lower(?)) > 0)
        """);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            return db.queryForObject(sql.toString(), Long.class, search, search, search, status);
        }
        return db.queryForObject(sql.toString(), Long.class, search, search, search);
    }

    public Optional<Supplier> supplier(int id) {
        return db.query("SELECT * FROM public.supplier WHERE supplier_id = ?", this::supplierRow, id)
            .stream().findFirst();
    }

    public int createSupplier(String name, String contactName, String email, String phone,
                               String address, String taxCode, String notes) {
        return db.queryForObject("""
            INSERT INTO public.supplier(supplier_name, contact_name, email, phone, address, tax_code, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            RETURNING supplier_id
        """, Integer.class, name, contactName, email, phone, address, taxCode, notes);
    }

    public void updateSupplier(int id, String name, String contactName, String email, String phone,
                               String address, String taxCode, String notes) {
        db.update("""
            UPDATE public.supplier
            SET supplier_name = ?, contact_name = ?, email = ?, phone = ?, address = ?,
                tax_code = ?, notes = ?, updated_at = CURRENT_TIMESTAMP
            WHERE supplier_id = ?
        """, name, contactName, email, phone, address, taxCode, notes, id);
    }

    public void supplierStatus(int id, String status) {
        db.update("UPDATE public.supplier SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE supplier_id = ?", status, id);
    }

    public boolean hasOpenOrders(int supplierId) {
        Integer count = db.queryForObject("""
            SELECT count(*) FROM public.purchase_order
            WHERE supplier_id = ? AND status IN ('DRAFT', 'ORDERED')
        """, Integer.class, supplierId);
        return count != null && count > 0;
    }

    // ─── Purchase Order CRUD ──────────────────────────────────────────────────
    public List<PurchaseOrder> listPurchaseOrders(int offset, int limit, Integer supplierId,
                                                  Integer farmId, Integer warehouseId,
                                                  String status, LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder("""
            SELECT po.*, s.supplier_name, f.farm_name, w.warehouse_name
            FROM public.purchase_order po
            JOIN public.supplier s USING(supplier_id)
            JOIN public.farm f USING(farm_id)
            JOIN public.warehouse w USING(warehouse_id)
            WHERE 1=1
        """);
        List<Object> args = new java.util.ArrayList<>();
        if (supplierId != null) { sql.append(" AND po.supplier_id = ?"); args.add(supplierId); }
        if (farmId != null) { sql.append(" AND po.farm_id = ?"); args.add(farmId); }
        if (warehouseId != null) { sql.append(" AND po.warehouse_id = ?"); args.add(warehouseId); }
        if (status != null && !status.isBlank()) { sql.append(" AND po.status = ?"); args.add(status); }
        if (fromDate != null) { sql.append(" AND po.order_date >= ?"); args.add(fromDate); }
        if (toDate != null) { sql.append(" AND po.order_date <= ?"); args.add(toDate); }

        sql.append(" ORDER BY po.po_id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);

        return db.query(sql.toString(), this::poSummaryRow, args.toArray());
    }

    public long countPurchaseOrders(Integer supplierId, Integer farmId, Integer warehouseId,
                                    String status, LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder("""
            SELECT count(*) FROM public.purchase_order po WHERE 1=1
        """);
        List<Object> args = new java.util.ArrayList<>();
        if (supplierId != null) { sql.append(" AND po.supplier_id = ?"); args.add(supplierId); }
        if (farmId != null) { sql.append(" AND po.farm_id = ?"); args.add(farmId); }
        if (warehouseId != null) { sql.append(" AND po.warehouse_id = ?"); args.add(warehouseId); }
        if (status != null && !status.isBlank()) { sql.append(" AND po.status = ?"); args.add(status); }
        if (fromDate != null) { sql.append(" AND po.order_date >= ?"); args.add(fromDate); }
        if (toDate != null) { sql.append(" AND po.order_date <= ?"); args.add(toDate); }

        return db.queryForObject(sql.toString(), Long.class, args.toArray());
    }

    public Optional<PurchaseOrder> purchaseOrder(int id) {
        String sql = """
            SELECT po.*, s.supplier_name, f.farm_name, w.warehouse_name
            FROM public.purchase_order po
            JOIN public.supplier s USING(supplier_id)
            JOIN public.farm f USING(farm_id)
            JOIN public.warehouse w USING(warehouse_id)
            WHERE po.po_id = ?
        """;
        var po = db.query(sql, this::poSummaryRow, id).stream().findFirst();
        if (po.isEmpty()) return Optional.empty();

        List<PurchaseOrderItem> items = purchaseOrderItems(id);
        var p = po.get();
        return Optional.of(new PurchaseOrder(
            p.id(), p.poNumber(), p.supplierId(), p.supplierName(), p.farmId(), p.farmName(),
            p.warehouseId(), p.warehouseName(), p.orderDate(), p.expectedDeliveryDate(),
            p.actualDeliveryDate(), p.status(), p.totalAmount(), p.notes(), p.createdBy(),
            p.createdAt(), p.updatedAt(), items
        ));
    }

    public List<PurchaseOrderItem> purchaseOrderItems(int poId) {
        String sql = """
            SELECT poi.*, m.material_name, m.unit
            FROM public.purchase_order_item poi
            JOIN public.material m USING(material_id)
            WHERE poi.po_id = ?
            ORDER BY poi.po_item_id ASC
        """;
        return db.query(sql, this::itemRow, poId);
    }

    public int createPurchaseOrder(String poNumber, int supplierId, int farmId, int warehouseId,
                                   LocalDate orderDate, LocalDate expectedDeliveryDate,
                                   BigDecimal totalAmount, String notes, int createdBy) {
        return db.queryForObject("""
            INSERT INTO public.purchase_order(po_number, supplier_id, farm_id, warehouse_id,
                                              order_date, expected_delivery_date, status,
                                              total_amount, notes, created_by)
            VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?)
            RETURNING po_id
        """, Integer.class, poNumber, supplierId, farmId, warehouseId, orderDate,
            expectedDeliveryDate, totalAmount, notes, createdBy);
    }

    public int createPurchaseOrderItem(int poId, int materialId, BigDecimal quantity,
                                       BigDecimal unitPrice, BigDecimal totalPrice, String notes) {
        return db.queryForObject("""
            INSERT INTO public.purchase_order_item(po_id, material_id, quantity, unit_price, total_price, received_quantity, notes)
            VALUES (?, ?, ?, ?, ?, 0, ?)
            RETURNING po_item_id
        """, Integer.class, poId, materialId, quantity, unitPrice, totalPrice, notes);
    }

    public void deletePurchaseOrderItems(int poId) {
        db.update("DELETE FROM public.purchase_order_item WHERE po_id = ?", poId);
    }

    public void updatePurchaseOrder(int id, LocalDate expectedDeliveryDate, BigDecimal totalAmount, String notes) {
        db.update("""
            UPDATE public.purchase_order
            SET expected_delivery_date = ?, total_amount = ?, notes = ?, updated_at = CURRENT_TIMESTAMP
            WHERE po_id = ?
        """, expectedDeliveryDate, totalAmount, notes, id);
    }

    public void updatePurchaseOrderStatus(int id, String status, LocalDate actualDeliveryDate) {
        db.update("""
            UPDATE public.purchase_order
            SET status = ?, actual_delivery_date = ?, updated_at = CURRENT_TIMESTAMP
            WHERE po_id = ?
        """, status, actualDeliveryDate, id);
    }

    public void updatePurchaseOrderItemReceived(int itemId, BigDecimal receivedQty) {
        db.update("""
            UPDATE public.purchase_order_item
            SET received_quantity = ?
            WHERE po_item_id = ?
        """, receivedQty, itemId);
    }

    // ─── Inventory Update on Receiving Materials ──────────────────────────────
    public void addInventoryStock(int warehouseId, int materialId, BigDecimal quantity) {
        db.update("""
            INSERT INTO public.warehouse_inventory(warehouse_id, material_id, quantity, last_updated)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (warehouse_id, material_id)
            DO UPDATE SET
                quantity = public.warehouse_inventory.quantity + EXCLUDED.quantity,
                last_updated = CURRENT_TIMESTAMP
        """, warehouseId, materialId, quantity);
    }

    public boolean isMaterialActive(int materialId) {
        List<String> list = db.query("SELECT status FROM public.material WHERE material_id = ?",
            (rs, i) -> rs.getString("status"), materialId);
        return !list.isEmpty() && "ACTIVE".equalsIgnoreCase(list.getFirst());
    }

    public boolean isWarehouseActive(int warehouseId) {
        List<String> list = db.query("SELECT status FROM public.warehouse WHERE warehouse_id = ?",
            (rs, i) -> rs.getString("status"), warehouseId);
        return !list.isEmpty() && "ACTIVE".equalsIgnoreCase(list.getFirst());
    }

    public void lockPurchaseOrder(int poId) {
        db.queryForObject("SELECT po_id FROM public.purchase_order WHERE po_id = ? FOR UPDATE", Integer.class, poId);
    }

    public void auditAction(int actor, String entityType, int entityId, String action, String requestId) {
        db.update("INSERT INTO public.audit_log(actor_user_id,event_at,entity_type,entity_key,action,request_id) VALUES (?,CURRENT_TIMESTAMP,?,?,?,?)",
            actor, entityType, Integer.toString(entityId), action, requestId);
    }
}
