package com.farmmanagement.backend.inventory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryStore {
    private final JdbcTemplate db;
    public InventoryStore(JdbcTemplate db) { this.db = db; }

    public record MaterialCategory(int id, String name, String description, String status, String createdAt) {}
    public record Material(int id, int categoryId, String categoryName, String name, String unit, String manufacturer, String description, String status, String createdAt) {}
    public record Warehouse(int id, int farmId, String farmName, String name, String location, String capacityText, String status, String createdAt) {}
    public record WarehouseInventory(int id, int warehouseId, String warehouseName, int materialId, String materialName, String unit, BigDecimal quantity, String lastUpdated) {}

    private MaterialCategory categoryRow(ResultSet r, int n) throws SQLException {
        return new MaterialCategory(r.getInt("category_id"), r.getString("category_name"), r.getString("description"),
            r.getString("status"), r.getTimestamp("created_at").toInstant().toString());
    }

    private Material materialRow(ResultSet r, int n) throws SQLException {
        return new Material(r.getInt("material_id"), r.getInt("category_id"), r.getString("category_name"),
            r.getString("material_name"), r.getString("unit"), r.getString("manufacturer"), r.getString("description"),
            r.getString("status"), r.getTimestamp("created_at").toInstant().toString());
    }

    private Warehouse warehouseRow(ResultSet r, int n) throws SQLException {
        return new Warehouse(r.getInt("warehouse_id"), r.getInt("farm_id"), r.getString("farm_name"),
            r.getString("warehouse_name"), r.getString("location"), r.getString("capacity_text"),
            r.getString("status"), r.getTimestamp("created_at").toInstant().toString());
    }

    private WarehouseInventory inventoryRow(ResultSet r, int n) throws SQLException {
        return new WarehouseInventory(r.getInt("inventory_id"), r.getInt("warehouse_id"), r.getString("warehouse_name"),
            r.getInt("material_id"), r.getString("material_name"), r.getString("unit"),
            r.getBigDecimal("quantity"), r.getTimestamp("last_updated").toInstant().toString());
    }

    // -- Material Category CRUD --
    public List<MaterialCategory> listCategories(int offset, int limit, String search) {
        return db.query("SELECT * FROM public.material_category WHERE strpos(lower(category_name),lower(?))>0 ORDER BY category_id LIMIT ? OFFSET ?",
            this::categoryRow, search, limit, offset);
    }
    public long countCategories(String search) {
        return db.queryForObject("SELECT count(*) FROM public.material_category WHERE strpos(lower(category_name),lower(?))>0", Long.class, search);
    }
    public Optional<MaterialCategory> category(int id) {
        return db.query("SELECT * FROM public.material_category WHERE category_id=?", this::categoryRow, id).stream().findFirst();
    }
    public int createCategory(String name, String description) {
        return db.queryForObject("INSERT INTO public.material_category(category_name,description) VALUES (?,?) RETURNING category_id", Integer.class, name, description);
    }
    public void updateCategory(int id, String name, String description) {
        db.update("UPDATE public.material_category SET category_name=?,description=? WHERE category_id=?", name, description, id);
    }
    public void categoryStatus(int id, String status) {
        db.update("UPDATE public.material_category SET status=? WHERE category_id=?", status, id);
    }

    // -- Material CRUD --
    public List<Material> listMaterials(int offset, int limit, String search, Integer categoryId) {
        String q = "SELECT m.*, c.category_name FROM public.material m JOIN public.material_category c USING(category_id) WHERE (strpos(lower(m.material_name),lower(?))>0)";
        if (categoryId != null) {
            return db.query(q + " AND m.category_id=? ORDER BY m.material_id LIMIT ? OFFSET ?", this::materialRow, search, categoryId, limit, offset);
        }
        return db.query(q + " ORDER BY m.material_id LIMIT ? OFFSET ?", this::materialRow, search, limit, offset);
    }
    public long countMaterials(String search, Integer categoryId) {
        String q = "SELECT count(*) FROM public.material m WHERE (strpos(lower(m.material_name),lower(?))>0)";
        if (categoryId != null) {
            return db.queryForObject(q + " AND m.category_id=?", Long.class, search, categoryId);
        }
        return db.queryForObject(q, Long.class, search);
    }
    public Optional<Material> material(int id) {
        return db.query("SELECT m.*, c.category_name FROM public.material m JOIN public.material_category c USING(category_id) WHERE m.material_id=?", this::materialRow, id).stream().findFirst();
    }
    public int createMaterial(int categoryId, String name, String unit, String manufacturer, String description) {
        return db.queryForObject("INSERT INTO public.material(category_id,material_name,unit,manufacturer,description) VALUES (?,?,?,?,?) RETURNING material_id", Integer.class, categoryId, name, unit, manufacturer, description);
    }
    public void updateMaterial(int id, String name, String unit, String manufacturer, String description) {
        db.update("UPDATE public.material SET material_name=?,unit=?,manufacturer=?,description=? WHERE material_id=?", name, unit, manufacturer, description, id);
    }
    public void materialStatus(int id, String status) {
        db.update("UPDATE public.material SET status=? WHERE material_id=?", status, id);
    }

    // -- Warehouse CRUD --
    public List<Warehouse> listWarehouses(int offset, int limit, String search, Integer farmId) {
        String q = "SELECT w.*, f.farm_name FROM public.warehouse w JOIN public.farm f USING(farm_id) WHERE (strpos(lower(w.warehouse_name),lower(?))>0 OR strpos(lower(COALESCE(w.location,'')),lower(?))>0)";
        if (farmId != null) {
            return db.query(q + " AND w.farm_id=? ORDER BY w.warehouse_id LIMIT ? OFFSET ?", this::warehouseRow, search, search, farmId, limit, offset);
        }
        return db.query(q + " ORDER BY w.warehouse_id LIMIT ? OFFSET ?", this::warehouseRow, search, search, limit, offset);
    }
    public long countWarehouses(String search, Integer farmId) {
        String q = "SELECT count(*) FROM public.warehouse w WHERE (strpos(lower(w.warehouse_name),lower(?))>0 OR strpos(lower(COALESCE(w.location,'')),lower(?))>0)";
        if (farmId != null) {
            return db.queryForObject(q + " AND w.farm_id=?", Long.class, search, search, farmId);
        }
        return db.queryForObject(q, Long.class, search, search);
    }
    public Optional<Warehouse> warehouse(int id) {
        return db.query("SELECT w.*, f.farm_name FROM public.warehouse w JOIN public.farm f USING(farm_id) WHERE w.warehouse_id=?", this::warehouseRow, id).stream().findFirst();
    }
    public int createWarehouse(int farmId, String name, String location, String capacityText) {
        return db.queryForObject("INSERT INTO public.warehouse(farm_id,warehouse_name,location,capacity_text) VALUES (?,?,?,?) RETURNING warehouse_id", Integer.class, farmId, name, location, capacityText);
    }
    public void updateWarehouse(int id, String name, String location, String capacityText) {
        db.update("UPDATE public.warehouse SET warehouse_name=?,location=?,capacity_text=? WHERE warehouse_id=?", name, location, capacityText, id);
    }
    public void warehouseStatus(int id, String status) {
        db.update("UPDATE public.warehouse SET status=? WHERE warehouse_id=?", status, id);
    }

    // -- Warehouse Inventory CRUD --
    public List<WarehouseInventory> listInventory(int offset, int limit, String search, Integer warehouseId) {
        String q = "SELECT i.*, w.warehouse_name, m.material_name, m.unit FROM public.warehouse_inventory i JOIN public.warehouse w USING(warehouse_id) JOIN public.material m USING(material_id) WHERE (strpos(lower(m.material_name),lower(?))>0)";
        if (warehouseId != null) {
            return db.query(q + " AND i.warehouse_id=? ORDER BY i.inventory_id LIMIT ? OFFSET ?", this::inventoryRow, search, warehouseId, limit, offset);
        }
        return db.query(q + " ORDER BY i.inventory_id LIMIT ? OFFSET ?", this::inventoryRow, search, limit, offset);
    }
    public long countInventory(String search, Integer warehouseId) {
        String q = "SELECT count(*) FROM public.warehouse_inventory i JOIN public.material m USING(material_id) WHERE (strpos(lower(m.material_name),lower(?))>0)";
        if (warehouseId != null) {
            return db.queryForObject(q + " AND i.warehouse_id=?", Long.class, search, warehouseId);
        }
        return db.queryForObject(q, Long.class, search);
    }
    public Optional<WarehouseInventory> inventory(int id) {
        return db.query("SELECT i.*, w.warehouse_name, m.material_name, m.unit FROM public.warehouse_inventory i JOIN public.warehouse w USING(warehouse_id) JOIN public.material m USING(material_id) WHERE i.inventory_id=?", this::inventoryRow, id).stream().findFirst();
    }
    public Optional<WarehouseInventory> inventoryByWarehouseAndMaterial(int warehouseId, int materialId) {
        return db.query("SELECT i.*, w.warehouse_name, m.material_name, m.unit FROM public.warehouse_inventory i JOIN public.warehouse w USING(warehouse_id) JOIN public.material m USING(material_id) WHERE i.warehouse_id=? AND i.material_id=?", this::inventoryRow, warehouseId, materialId).stream().findFirst();
    }
    public int createInventory(int warehouseId, int materialId, BigDecimal quantity) {
        return db.queryForObject("INSERT INTO public.warehouse_inventory(warehouse_id,material_id,quantity) VALUES (?,?,?) RETURNING inventory_id", Integer.class, warehouseId, materialId, quantity);
    }
    public void updateInventory(int id, BigDecimal quantity) {
        db.update("UPDATE public.warehouse_inventory SET quantity=?, last_updated=CURRENT_TIMESTAMP WHERE inventory_id=?", quantity, id);
    }

    public int setInventory(int warehouseId, int materialId, BigDecimal quantity) {
        return db.queryForObject("""
            INSERT INTO public.warehouse_inventory(warehouse_id, material_id, quantity) VALUES (?,?,?)
            ON CONFLICT (warehouse_id, material_id) DO UPDATE
            SET quantity=EXCLUDED.quantity, last_updated=CURRENT_TIMESTAMP RETURNING inventory_id
            """, Integer.class, warehouseId, materialId, quantity);
    }

    public void auditAction(int actor, String entityType, int entityId, String action, String requestId) {
        db.update("INSERT INTO public.audit_log(actor_user_id,event_at,entity_type,entity_key,action,request_id) VALUES (?,CURRENT_TIMESTAMP,?,?,?,?)",
            actor, entityType, Integer.toString(entityId), action, requestId);
    }
}
