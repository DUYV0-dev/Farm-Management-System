package com.farmmanagement.backend.supplier;

import com.farmmanagement.backend.common.DomainException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SupplierService {
    private final SupplierStore store;

    public SupplierService(SupplierStore store) {
        this.store = store;
    }

    public record ItemInput(int materialId, BigDecimal quantity, BigDecimal unitPrice, String notes) {}

    private SupplierStore.Supplier supplier(int id) {
        return store.supplier(id).orElseThrow(DomainException::missing);
    }

    private SupplierStore.PurchaseOrder purchaseOrder(int id) {
        return store.purchaseOrder(id).orElseThrow(DomainException::missing);
    }

    // ─── Supplier Management ──────────────────────────────────────────────────
    @PreAuthorize("hasAuthority('suppliers:read')")
    @Transactional(readOnly = true)
    public Map<String, Object> listSuppliers(int page, int size, String search, String status) {
        if (page < 0 || page > 100000 || size < 1 || size > 100 || search.length() > 100) {
            throw new IllegalArgumentException();
        }
        if (status != null && !status.isBlank() && !Set.of("ACTIVE", "INACTIVE").contains(status)) {
            throw new IllegalArgumentException();
        }
        return Map.of(
            "items", store.listSuppliers(page * size, size, search.trim(), status),
            "page", page,
            "size", size,
            "total", store.countSuppliers(search.trim(), status)
        );
    }

    @PreAuthorize("hasAuthority('suppliers:read')")
    @Transactional(readOnly = true)
    public SupplierStore.Supplier getSupplier(int id) {
        return supplier(id);
    }

    @PreAuthorize("hasAuthority('suppliers:create')")
    public SupplierStore.Supplier createSupplier(int actor, String name, String contactName,
                                                 String email, String phone, String address,
                                                 String taxCode, String notes, String requestId) {
        validateSupplierFields(name, email, phone);
        int id = store.createSupplier(name.trim(), contactName, email, phone, address, taxCode, notes);
        store.auditAction(actor, "SUPPLIER", id, "CREATE", requestId);
        return supplier(id);
    }

    @PreAuthorize("hasAuthority('suppliers:update')")
    public SupplierStore.Supplier updateSupplier(int actor, int id, String name, String contactName,
                                                 String email, String phone, String address,
                                                 String taxCode, String notes, String requestId) {
        supplier(id);
        validateSupplierFields(name, email, phone);
        store.updateSupplier(id, name.trim(), contactName, email, phone, address, taxCode, notes);
        store.auditAction(actor, "SUPPLIER", id, "UPDATE", requestId);
        return supplier(id);
    }

    @PreAuthorize("hasAuthority('suppliers:status')")
    public SupplierStore.Supplier changeSupplierStatus(int actor, int id, String status, String requestId) {
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) throw new IllegalArgumentException();
        var s = supplier(id);
        if ("INACTIVE".equals(status) && store.hasOpenOrders(id)) {
            throw DomainException.conflict("Nhà cung cấp đang có đơn mua hàng chưa hoàn tất. Không thể ngừng hoạt động.");
        }
        store.supplierStatus(id, status);
        store.auditAction(actor, "SUPPLIER", id, "STATUS_" + status, requestId);
        return supplier(id);
    }

    private void validateSupplierFields(String name, String email, String phone) {
        if (name == null || name.isBlank() || name.length() > 150) throw new IllegalArgumentException();
        if (email != null && !email.isBlank()) {
            String e = email.trim().toLowerCase(Locale.ROOT);
            if (!e.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || e.length() > 254) throw new IllegalArgumentException();
        }
        if (phone != null && !phone.isBlank()) {
            String p = phone.trim();
            if (!p.matches("^[0-9+\\-() ]{6,20}$")) throw new IllegalArgumentException();
        }
    }

    // ─── Purchase Order Management ────────────────────────────────────────────
    @PreAuthorize("hasAuthority('purchases:read')")
    @Transactional(readOnly = true)
    public Map<String, Object> listPurchaseOrders(int page, int size, Integer supplierId,
                                                  Integer farmId, Integer warehouseId,
                                                  String status, LocalDate fromDate, LocalDate toDate) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) throw new IllegalArgumentException();
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) throw new IllegalArgumentException();
        if (status != null && !status.isBlank() && !Set.of("DRAFT", "ORDERED", "RECEIVED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException();
        }
        return Map.of(
            "items", store.listPurchaseOrders(page * size, size, supplierId, farmId, warehouseId, status, fromDate, toDate),
            "page", page,
            "size", size,
            "total", store.countPurchaseOrders(supplierId, farmId, warehouseId, status, fromDate, toDate)
        );
    }

    @PreAuthorize("hasAuthority('purchases:read')")
    @Transactional(readOnly = true)
    public SupplierStore.PurchaseOrder getPurchaseOrder(int id) {
        return purchaseOrder(id);
    }

    @PreAuthorize("hasAuthority('purchases:create')")
    public SupplierStore.PurchaseOrder createPurchaseOrder(int actor, String customPoNumber,
                                                           int supplierId, int farmId, int warehouseId,
                                                           LocalDate orderDate, LocalDate expectedDeliveryDate,
                                                           String notes, List<ItemInput> items, String requestId) {
        var sup = supplier(supplierId);
        if (!"ACTIVE".equals(sup.status())) {
            throw DomainException.conflict("Nhà cung cấp đã ngừng hoạt động.");
        }
        if (!store.isWarehouseActive(warehouseId)) {
            throw DomainException.conflict("Kho lưu trữ không tồn tại hoặc đã ngừng hoạt động.");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Đơn mua hàng phải có ít nhất 1 vật tư.");
        }
        LocalDate date = orderDate != null ? orderDate : LocalDate.now();
        String poNumber = (customPoNumber != null && !customPoNumber.isBlank())
            ? customPoNumber.trim()
            : generatePoNumber(date);

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (var item : items) {
            validateItem(item);
            BigDecimal lineTotal = item.quantity().multiply(item.unitPrice()).setScale(2, RoundingMode.HALF_UP);
            totalAmount = totalAmount.add(lineTotal);
        }

        int poId = store.createPurchaseOrder(poNumber, supplierId, farmId, warehouseId,
            date, expectedDeliveryDate, totalAmount, notes, actor);

        for (var item : items) {
            BigDecimal lineTotal = item.quantity().multiply(item.unitPrice()).setScale(2, RoundingMode.HALF_UP);
            store.createPurchaseOrderItem(poId, item.materialId(), item.quantity(), item.unitPrice(), lineTotal, item.notes());
        }

        store.auditAction(actor, "PURCHASE_ORDER", poId, "CREATE", requestId);
        return purchaseOrder(poId);
    }

    @PreAuthorize("hasAuthority('purchases:update')")
    public SupplierStore.PurchaseOrder updatePurchaseOrder(int actor, int id, LocalDate expectedDeliveryDate,
                                                           String notes, List<ItemInput> items, String requestId) {
        store.lockPurchaseOrder(id);
        var po = purchaseOrder(id);
        if (!"DRAFT".equals(po.status())) {
            throw DomainException.conflict("Chỉ có thể chỉnh sửa đơn mua hàng ở trạng thái DRAFT.");
        }

        BigDecimal totalAmount = po.totalAmount();
        if (items != null && !items.isEmpty()) {
            store.deletePurchaseOrderItems(id);
            totalAmount = BigDecimal.ZERO;
            for (var item : items) {
                validateItem(item);
                BigDecimal lineTotal = item.quantity().multiply(item.unitPrice()).setScale(2, RoundingMode.HALF_UP);
                totalAmount = totalAmount.add(lineTotal);
                store.createPurchaseOrderItem(id, item.materialId(), item.quantity(), item.unitPrice(), lineTotal, item.notes());
            }
        }

        store.updatePurchaseOrder(id, expectedDeliveryDate, totalAmount, notes);
        store.auditAction(actor, "PURCHASE_ORDER", id, "UPDATE", requestId);
        return purchaseOrder(id);
    }

    @PreAuthorize("hasAuthority('purchases:update') or hasAuthority('purchases:cancel')")
    public SupplierStore.PurchaseOrder changePurchaseOrderStatus(int actor, int id, String newStatus, String requestId) {
        if (!Set.of("ORDERED", "CANCELLED").contains(newStatus)) {
            throw new IllegalArgumentException();
        }
        store.lockPurchaseOrder(id);
        var po = purchaseOrder(id);
        if ("RECEIVED".equals(po.status())) {
            throw DomainException.conflict("Đơn mua hàng đã nhận vật tư vào kho, không thể thay đổi trạng thái.");
        }
        if ("CANCELLED".equals(po.status())) {
            throw DomainException.conflict("Đơn mua hàng đã bị hủy.");
        }

        if ("ORDERED".equals(newStatus)) {
            if (!"DRAFT".equals(po.status())) {
                throw DomainException.conflict("Chỉ đơn DRAFT mới có thể chuyển sang ORDERED.");
            }
        }

        store.updatePurchaseOrderStatus(id, newStatus, null);
        store.auditAction(actor, "PURCHASE_ORDER", id, "STATUS_" + newStatus, requestId);
        return purchaseOrder(id);
    }

    // ─── Update Inventory After Receiving Materials ───────────────────────────
    @PreAuthorize("hasAuthority('purchases:receive')")
    public SupplierStore.PurchaseOrder receiveMaterials(int actor, int poId, String requestId) {
        store.lockPurchaseOrder(poId);
        var po = purchaseOrder(poId);

        if ("RECEIVED".equals(po.status())) {
            throw DomainException.conflict("Đơn mua hàng này đã được nhập kho trước đó.");
        }
        if ("CANCELLED".equals(po.status())) {
            throw DomainException.conflict("Đơn mua hàng đã bị hủy, không thể nhập kho.");
        }
        if (!store.isWarehouseActive(po.warehouseId())) {
            throw DomainException.conflict("Kho nhận hàng đã ngừng hoạt động.");
        }

        List<SupplierStore.PurchaseOrderItem> items = po.items();
        if (items.isEmpty()) {
            throw DomainException.conflict("Đơn mua hàng không có vật tư nào để nhập kho.");
        }

        for (var item : items) {
            if (!store.isMaterialActive(item.materialId())) {
                throw DomainException.conflict("Vật tư '" + item.materialName() + "' đã ngừng hoạt động, không thể nhập kho.");
            }
            // Auto update stock in warehouse_inventory
            store.addInventoryStock(po.warehouseId(), item.materialId(), item.quantity());
            store.updatePurchaseOrderItemReceived(item.id(), item.quantity());
        }

        LocalDate now = LocalDate.now();
        store.updatePurchaseOrderStatus(poId, "RECEIVED", now);

        store.auditAction(actor, "PURCHASE_ORDER", poId, "RECEIVE", requestId);
        store.auditAction(actor, "INVENTORY", po.warehouseId(), "RECEIVE_MATERIALS_FROM_PO_" + poId, requestId);

        return purchaseOrder(poId);
    }

    private void validateItem(ItemInput item) {
        if (item.materialId() <= 0) throw new IllegalArgumentException("Mã vật tư không hợp lệ.");
        if (!store.isMaterialActive(item.materialId())) {
            throw DomainException.conflict("Vật tư (ID " + item.materialId() + ") không tồn tại hoặc đã ngừng hoạt động.");
        }
        if (item.quantity() == null || item.quantity().compareTo(BigDecimal.ZERO) <= 0
            || item.quantity().compareTo(new BigDecimal("999999999.999")) > 0
            || item.quantity().stripTrailingZeros().scale() > 3) {
            throw new IllegalArgumentException("Số lượng vật tư không hợp lệ (phải > 0, tối đa 3 chữ số thập phân).");
        }
        if (item.unitPrice() == null || item.unitPrice().compareTo(BigDecimal.ZERO) < 0
            || item.unitPrice().compareTo(new BigDecimal("999999999999.99")) > 0
            || item.unitPrice().stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Đơn giá vật tư không hợp lệ (phải >= 0, tối đa 2 chữ số thập phân).");
        }
    }

    private String generatePoNumber(LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase(Locale.ROOT);
        return "PO-" + dateStr + "-" + rand;
    }
}
