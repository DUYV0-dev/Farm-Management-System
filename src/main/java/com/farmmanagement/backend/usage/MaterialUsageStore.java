package com.farmmanagement.backend.usage;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MaterialUsageStore {
    private final JdbcTemplate db;
    public MaterialUsageStore(JdbcTemplate db) { this.db = db; }

    private static final String FROM = """
        FROM public.material_usage u JOIN public.crop_season s ON s.season_id=u.season_id
        JOIN public.plot p ON p.plot_id=s.plot_id JOIN public.farm f ON f.farm_id=p.farm_id
        JOIN public.crop c ON c.crop_id=s.crop_id
        """;
    private static final String SELECT = "SELECT u.*,p.farm_id,f.farm_name,p.plot_name,c.crop_name " + FROM;

    private MaterialUsage row(ResultSet r, int n) throws SQLException {
        return new MaterialUsage(r.getInt("usage_id"), r.getInt("season_id"), r.getInt("farm_id"),
            r.getString("farm_name"), r.getString("plot_name"), r.getString("crop_name"),
            r.getInt("material_id"), r.getString("material_name"), UsageType.valueOf(r.getString("usage_type")),
            r.getBigDecimal("quantity"), r.getString("unit"), r.getDate("application_date").toLocalDate(),
            r.getString("notes"), r.getObject("created_by", Integer.class), r.getTimestamp("created_at").toInstant());
    }

    // Coordinate usage writes; the insert trigger also locks the referenced rows.
    public void lockManagement() { db.execute("SELECT pg_advisory_xact_lock(740207)"); }

    public record SeasonOption(int id, int farmId, String farmName, String plotName, String cropName, String status, boolean available) {}
    public record MaterialOption(int id, String name, String unit, String categoryName, boolean available) {}
    private volatile Boolean warehouseSchema;
    private boolean warehouseSchema() {
        if(warehouseSchema==null) warehouseSchema=Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='material' AND column_name='category_id')",Boolean.class));
        return warehouseSchema;
    }
    private String materialSelect() {
        return warehouseSchema()
            ? "SELECT m.material_id,m.material_name,m.unit,c.category_name,(m.status='ACTIVE' AND c.status='ACTIVE') AS available FROM public.material m JOIN public.material_category c USING(category_id)"
            : "SELECT m.material_id,m.material_name,u.unit_code AS unit,m.category AS category_name,(m.status='ACTIVE') AS available FROM public.material m JOIN public.unit u USING(unit_id)";
    }

    public List<SeasonOption> seasons() {
        return db.query("""
            SELECT s.season_id,p.farm_id,f.farm_name,p.plot_name,c.crop_name,s.status,
                (s.status IN ('IN_PROGRESS','COMPLETED') AND p.status='ACTIVE'
                    AND f.status='ACTIVE' AND c.status='ACTIVE') AS available
            FROM public.crop_season s JOIN public.plot p USING(plot_id)
            JOIN public.farm f USING(farm_id) JOIN public.crop c USING(crop_id)
            ORDER BY s.season_id DESC
            """, (r,n) -> new SeasonOption(r.getInt("season_id"), r.getInt("farm_id"),
                r.getString("farm_name"), r.getString("plot_name"), r.getString("crop_name"), r.getString("status"), r.getBoolean("available")));
    }

    public List<MaterialOption> materials() {
        return db.query(materialSelect()+" ORDER BY m.material_name,m.material_id", (r,n) -> new MaterialOption(r.getInt("material_id"), r.getString("material_name"),
                r.getString("unit"), r.getString("category_name"), r.getBoolean("available")));
    }

    public boolean seasonExists(int id) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM public.crop_season WHERE season_id=?)", Boolean.class, id));
    }

    public boolean seasonAvailable(int id) {
        return Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM public.crop_season s JOIN public.plot p USING(plot_id)
            JOIN public.farm f USING(farm_id) JOIN public.crop c USING(crop_id)
            WHERE s.season_id=? AND s.status IN ('IN_PROGRESS','COMPLETED')
                AND p.status='ACTIVE' AND f.status='ACTIVE' AND c.status='ACTIVE')
            """, Boolean.class, id));
    }

    public Optional<MaterialOption> material(int id) {
        return db.query(materialSelect()+" WHERE m.material_id=?", (r,n) -> new MaterialOption(r.getInt("material_id"), r.getString("material_name"),
                r.getString("unit"), r.getString("category_name"), r.getBoolean("available")), id).stream().findFirst();
    }

    public boolean materialAvailable(int id) {
        return material(id).map(MaterialOption::available).orElse(false);
    }

    public int create(int seasonId, MaterialOption material, UsageType type, BigDecimal quantity,
                      LocalDate date, String notes, int actor) {
        return db.queryForObject("""
            INSERT INTO public.material_usage(season_id,material_id,material_name,usage_type,quantity,unit,application_date,notes,created_by)
            VALUES (?,?,?,?,?,?,?,?,?) RETURNING usage_id
            """, Integer.class, seasonId, material.id(), material.name(), type.name(), quantity,
            material.unit(), java.sql.Date.valueOf(date), notes, actor);
    }

    public Optional<MaterialUsage> byId(int id) {
        return db.query(SELECT + " WHERE u.usage_id=?", this::row, id).stream().findFirst();
    }

    private record Query(String where, List<Object> args) {}
    private Query filter(UsageFilter filter) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (filter.farmId() != null) { where.append(" AND p.farm_id=?"); args.add(filter.farmId()); }
        if (filter.seasonId() != null) { where.append(" AND u.season_id=?"); args.add(filter.seasonId()); }
        if (filter.materialId() != null) { where.append(" AND u.material_id=?"); args.add(filter.materialId()); }
        if (filter.type() != null) { where.append(" AND u.usage_type=?"); args.add(filter.type().name()); }
        if (filter.fromDate() != null) { where.append(" AND u.application_date>=?"); args.add(java.sql.Date.valueOf(filter.fromDate())); }
        if (filter.toDate() != null) { where.append(" AND u.application_date<=?"); args.add(java.sql.Date.valueOf(filter.toDate())); }
        return new Query(where.toString(), args);
    }

    public List<MaterialUsage> history(UsageFilter filter, int offset, int limit) {
        Query q = filter(filter);
        List<Object> args = new ArrayList<>(q.args()); args.add(limit); args.add(offset);
        return db.query(SELECT + q.where() + " ORDER BY u.application_date DESC,u.usage_id DESC LIMIT ? OFFSET ?",
            this::row, args.toArray());
    }

    public long count(UsageFilter filter) {
        Query q = filter(filter);
        return db.queryForObject("SELECT count(*) " + FROM + q.where(), Long.class, q.args().toArray());
    }

    public record UsageTotal(UsageType type, int materialId, String materialName, String unit, long count, BigDecimal quantity) {}
    // Quantities with different units or materials must never be added together.
    public List<UsageTotal> totals(UsageFilter filter) {
        Query q = filter(filter);
        return db.query("SELECT u.usage_type,u.material_id,u.material_name,u.unit,count(*) AS count,sum(u.quantity) AS quantity "
            + FROM + q.where() + " GROUP BY u.usage_type,u.material_id,u.material_name,u.unit ORDER BY u.usage_type,u.material_name,u.unit",
            (r,n) -> new UsageTotal(UsageType.valueOf(r.getString("usage_type")), r.getInt("material_id"),
                r.getString("material_name"), r.getString("unit"), r.getLong("count"), r.getBigDecimal("quantity")), q.args().toArray());
    }
}
