package com.farmmanagement.backend.inventory;

import com.farmmanagement.backend.common.DomainException;


import java.math.BigDecimal;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InventoryService {
    private final InventoryStore store;
    public InventoryService(InventoryStore store) { this.store = store; }

    private InventoryStore.MaterialCategory category(int id) { return store.category(id).orElseThrow(DomainException::missing); }
    private InventoryStore.Material material(int id) { return store.material(id).orElseThrow(DomainException::missing); }
    private InventoryStore.Warehouse warehouse(int id) { return store.warehouse(id).orElseThrow(DomainException::missing); }
    private InventoryStore.WarehouseInventory inventory(int id) { return store.inventory(id).orElseThrow(DomainException::missing); }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            BigDecimal d = new BigDecimal(value);
            if (d.compareTo(BigDecimal.ZERO) < 0 || d.compareTo(new BigDecimal("999999999.999")) > 0
                || d.stripTrailingZeros().scale() > 3) throw new IllegalArgumentException();
            return d;
        } catch (NumberFormatException e) { throw new IllegalArgumentException(); }
    }

    // ── Material Category ────────────────────────────────────────────────────
    @PreAuthorize("hasAuthority('materials:read')")
    @Transactional(readOnly = true)
    public Map<String, Object> listCategories(int page, int size, String search) {
        if (page < 0 || page > 100000 || size < 1 || size > 100 || search.length() > 100) throw new IllegalArgumentException();
        return Map.of("items", store.listCategories(page * size, size, search), "page", page, "size", size, "total", store.countCategories(search));
    }

    @PreAuthorize("hasAuthority('materials:read')")
    @Transactional(readOnly = true)
    public InventoryStore.MaterialCategory getCategory(int id) { return category(id); }

    @PreAuthorize("hasAuthority('materials:create')")
    public InventoryStore.MaterialCategory createCategory(int actor, String name, String description, String requestId) {
        int id = store.createCategory(name, description);
        store.auditAction(actor, "MATERIAL_CATEGORY", id, "CREATE", requestId);
        return category(id);
    }

    @PreAuthorize("hasAuthority('materials:update')")
    public InventoryStore.MaterialCategory updateCategory(int actor, int id, String name, String description, String requestId) {
        category(id);
        store.updateCategory(id, name, description);
        store.auditAction(actor, "MATERIAL_CATEGORY", id, "UPDATE", requestId);
        return category(id);
    }

    @PreAuthorize("hasAuthority('materials:status')")
    public InventoryStore.MaterialCategory categoryStatus(int actor, int id, String status, String requestId) {
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) throw new IllegalArgumentException();
        category(id);
        store.categoryStatus(id, status);
        store.auditAction(actor, "MATERIAL_CATEGORY", id, "UPDATE_STATUS", requestId);
        return category(id);
    }

    // ── Material ─────────────────────────────────────────────────────────────
    @PreAuthorize("hasAuthority('materials:read')")
    @Transactional(readOnly = true)
    public Map<String, Object> listMaterials(int page, int size, String search, Integer categoryId) {
        if (categoryId != null && categoryId <= 0) throw new IllegalArgumentException();
        if (page < 0 || page > 100000 || size < 1 || size > 100 || search.length() > 100) throw new IllegalArgumentException();
        return Map.of("items", store.listMaterials(page * size, size, search, categoryId), "page", page, "size", size, "total", store.countMaterials(search, categoryId));
    }

    @PreAuthorize("hasAuthority('materials:read')")
    @Transactional(readOnly = true)
    public InventoryStore.Material getMaterial(int id) { return material(id); }

    @PreAuthorize("hasAuthority('materials:create')")
    public InventoryStore.Material createMaterial(int actor, int categoryId, String name, String unit, String manufacturer, String description, String requestId) {
        int id = store.createMaterial(categoryId, name, unit, manufacturer, description);
        store.auditAction(actor, "MATERIAL", id, "CREATE", requestId);
        return material(id);
    }

    @PreAuthorize("hasAuthority('materials:update')")
    public InventoryStore.Material updateMaterial(int actor, int id, String name, String unit, String manufacturer, String description, String requestId) {
        material(id);
        store.updateMaterial(id, name, unit, manufacturer, description);
        store.auditAction(actor, "MATERIAL", id, "UPDATE", requestId);
        return material(id);
    }

    @PreAuthorize("hasAuthority('materials:status')")
    public InventoryStore.Material materialStatus(int actor, int id, String status, String requestId) {
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) throw new IllegalArgumentException();
        material(id);
        store.materialStatus(id, status);
        store.auditAction(actor, "MATERIAL", id, "UPDATE_STATUS", requestId);
        return material(id);
    }

    // ── Warehouse ────────────────────────────────────────────────────────────
    @PreAuthorize("hasAuthority('warehouses:read')")
    @Transactional(readOnly = true)
    public Map<String, Object> listWarehouses(int page, int size, String search, Integer farmId) {
        if (farmId != null && farmId <= 0) throw new IllegalArgumentException();
        if (page < 0 || page > 100000 || size < 1 || size > 100 || search.length() > 100) throw new IllegalArgumentException();
        return Map.of("items", store.listWarehouses(page * size, size, search, farmId), "page", page, "size", size, "total", store.countWarehouses(search, farmId));
    }

    @PreAuthorize("hasAuthority('warehouses:read')")
    @Transactional(readOnly = true)
    public InventoryStore.Warehouse getWarehouse(int id) { return warehouse(id); }

    @PreAuthorize("hasAuthority('warehouses:create')")
    public InventoryStore.Warehouse createWarehouse(int actor, int farmId, String name, String location, String capacityText, String requestId) {
        int id = store.createWarehouse(farmId, name, location, capacityText);
        store.auditAction(actor, "WAREHOUSE", id, "CREATE", requestId);
        return warehouse(id);
    }

    @PreAuthorize("hasAuthority('warehouses:update')")
    public InventoryStore.Warehouse updateWarehouse(int actor, int id, String name, String location, String capacityText, String requestId) {
        warehouse(id);
        store.updateWarehouse(id, name, location, capacityText);
        store.auditAction(actor, "WAREHOUSE", id, "UPDATE", requestId);
        return warehouse(id);
    }

    @PreAuthorize("hasAuthority('warehouses:status')")
    public InventoryStore.Warehouse warehouseStatus(int actor, int id, String status, String requestId) {
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) throw new IllegalArgumentException();
        warehouse(id);
        store.warehouseStatus(id, status);
        store.auditAction(actor, "WAREHOUSE", id, "UPDATE_STATUS", requestId);
        return warehouse(id);
    }

    // ── Warehouse Inventory ──────────────────────────────────────────────────
    @PreAuthorize("hasAuthority('warehouses:read')")
    @Transactional(readOnly = true)
    public Map<String, Object> listInventory(int page, int size, String search, Integer warehouseId) {
        if (warehouseId != null && warehouseId <= 0) throw new IllegalArgumentException();
        if (page < 0 || page > 100000 || size < 1 || size > 100 || search.length() > 100) throw new IllegalArgumentException();
        return Map.of("items", store.listInventory(page * size, size, search, warehouseId), "page", page, "size", size, "total", store.countInventory(search, warehouseId));
    }

    @PreAuthorize("hasAuthority('warehouses:read')")
    @Transactional(readOnly = true)
    public InventoryStore.WarehouseInventory getInventory(int id) { return inventory(id); }

    @PreAuthorize("hasAuthority('inventory:manage')")
    public InventoryStore.WarehouseInventory adjustInventory(int actor, int warehouseId, int materialId, String quantityStr, String requestId) {
        BigDecimal qty = parseDecimal(quantityStr);
        if (qty == null) throw new IllegalArgumentException();
        
        warehouse(warehouseId);
        material(materialId);
        int invId = store.setInventory(warehouseId, materialId, qty);
        store.auditAction(actor, "WAREHOUSE_INVENTORY", invId, "SET_QUANTITY", requestId);
        return inventory(invId);
    }
}
