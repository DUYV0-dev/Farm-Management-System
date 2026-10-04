package com.farmmanagement.backend.farm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FarmStore {
    private final JdbcTemplate db;
    public FarmStore(JdbcTemplate db) { this.db = db; }

    public record Farm(int id, String name, String address, Double areaHa,
                       String description, String status, String createdAt) {}
                       
    public record Plot(int id, int farmId, String farmName, String name, Double areaHa,
                       String soilType, String location, String description, String status, String createdAt) {}
                       
    public record Crop(int id, String name, String scientificName, String category,
                       Integer growthDays, String description, String status, String createdAt) {}
                       
    public record Season(int id, int plotId, String plotName, String farmName, 
                         int cropId, String cropName, String status) {}

    private Farm farmRow(ResultSet r, int n) throws SQLException {
        return new Farm(r.getInt("farm_id"), r.getString("farm_name"), r.getString("address"),
            r.getDouble("area_ha"), r.getString("description"), r.getString("status"),
            r.getTimestamp("created_at").toInstant().toString());
    }

    private Plot plotRow(ResultSet r, int n) throws SQLException {
        return new Plot(r.getInt("plot_id"), r.getInt("farm_id"), r.getString("farm_name"),
            r.getString("plot_name"), r.getDouble("area_ha"), r.getString("soil_type"),
            r.getString("location"), r.getString("description"), r.getString("status"),
            r.getTimestamp("created_at").toInstant().toString());
    }

    private Crop cropRow(ResultSet r, int n) throws SQLException {
        int gDays = r.getInt("growth_days");
        Integer growthDays = r.wasNull() ? null : gDays;
        return new Crop(r.getInt("crop_id"), r.getString("crop_name"), r.getString("scientific_name"),
            r.getString("category"), growthDays, r.getString("description"),
            r.getString("status"), r.getTimestamp("created_at").toInstant().toString());
    }
    
    private Season seasonRow(ResultSet r, int n) throws SQLException {
        return new Season(r.getInt("season_id"), r.getInt("plot_id"), r.getString("plot_name"),
            r.getString("farm_name"), r.getInt("crop_id"), r.getString("crop_name"),
            r.getString("status"));
    }

    // ─── Farm CRUD ───────────────────────────────────────────────────────────
    public List<Farm> listFarms(int offset, int limit, String search) {
        return db.query("""
            SELECT * FROM public.farm
            WHERE strpos(lower(farm_name),lower(?))>0 OR strpos(lower(COALESCE(address,'')),lower(?))>0
            ORDER BY farm_id LIMIT ? OFFSET ?
            """, this::farmRow, search, search, limit, offset);
    }

    public long countFarms(String search) {
        return db.queryForObject("""
            SELECT count(*) FROM public.farm
            WHERE strpos(lower(farm_name),lower(?))>0 OR strpos(lower(COALESCE(address,'')),lower(?))>0
            """, Long.class, search, search);
    }

    public Optional<Farm> farm(int id) {
        return db.query("SELECT * FROM public.farm WHERE farm_id=?", this::farmRow, id).stream().findFirst();
    }

    public int createFarm(String name, String address, Double areaHa, String description) {
        return db.queryForObject("""
            INSERT INTO public.farm(farm_name,address,area_ha,description)
            VALUES (?,?,?,?) RETURNING farm_id
            """, Integer.class, name, address, areaHa, description);
    }

    public void updateFarm(int id, String name, String address, Double areaHa, String description) {
        db.update("""
            UPDATE public.farm SET farm_name=?,address=?,area_ha=?,description=? WHERE farm_id=?
            """, name, address, areaHa, description, id);
    }

    public void farmStatus(int id, String status) {
        db.update("UPDATE public.farm SET status=? WHERE farm_id=?", status, id);
    }

    // ─── Plot CRUD ───────────────────────────────────────────────────────────
    public List<Plot> listPlots(int offset, int limit, String search, Integer farmId) {
        if (farmId != null) {
            return db.query("""
                SELECT p.*,f.farm_name FROM public.plot p JOIN public.farm f USING(farm_id)
                WHERE p.farm_id=? AND (strpos(lower(p.plot_name),lower(?))>0 OR strpos(lower(COALESCE(p.soil_type,'')),lower(?))>0)
                ORDER BY p.plot_id LIMIT ? OFFSET ?
                """, this::plotRow, farmId, search, search, limit, offset);
        }
        return db.query("""
            SELECT p.*,f.farm_name FROM public.plot p JOIN public.farm f USING(farm_id)
            WHERE strpos(lower(p.plot_name),lower(?))>0 OR strpos(lower(COALESCE(p.soil_type,'')),lower(?))>0
            ORDER BY p.plot_id LIMIT ? OFFSET ?
            """, this::plotRow, search, search, limit, offset);
    }

    public long countPlots(String search, Integer farmId) {
        if (farmId != null) {
            return db.queryForObject("""
                SELECT count(*) FROM public.plot WHERE farm_id=?
                AND (strpos(lower(plot_name),lower(?))>0 OR strpos(lower(COALESCE(soil_type,'')),lower(?))>0)
                """, Long.class, farmId, search, search);
        }
        return db.queryForObject("""
            SELECT count(*) FROM public.plot
            WHERE strpos(lower(plot_name),lower(?))>0 OR strpos(lower(COALESCE(soil_type,'')),lower(?))>0
            """, Long.class, search, search);
    }

    public Optional<Plot> plot(int id) {
        return db.query("""
            SELECT p.*,f.farm_name FROM public.plot p JOIN public.farm f USING(farm_id) WHERE p.plot_id=?
            """, this::plotRow, id).stream().findFirst();
    }

    public int createPlot(int farmId, String name, Double areaHa, String soilType, String location, String description) {
        return db.queryForObject("""
            INSERT INTO public.plot(farm_id,plot_name,area_ha,soil_type,location,description)
            VALUES (?,?,?,?,?,?) RETURNING plot_id
            """, Integer.class, farmId, name, areaHa, soilType, location, description);
    }

    public void updatePlot(int id, String name, Double areaHa, String soilType, String location, String description) {
        db.update("""
            UPDATE public.plot SET plot_name=?,area_ha=?,soil_type=?,location=?,description=? WHERE plot_id=?
            """, name, areaHa, soilType, location, description, id);
    }

    public void plotStatus(int id, String status) {
        db.update("UPDATE public.plot SET status=? WHERE plot_id=?", status, id);
    }

    // ─── Crop Catalog CRUD ───────────────────────────────────────────────────
    public List<Crop> listCrops(int offset, int limit, String search) {
        return db.query("""
            SELECT * FROM public.crop
            WHERE strpos(lower(crop_name),lower(?))>0 OR strpos(lower(COALESCE(scientific_name,'')),lower(?))>0
            ORDER BY crop_id LIMIT ? OFFSET ?
            """, this::cropRow, search, search, limit, offset);
    }

    public long countCrops(String search) {
        return db.queryForObject("""
            SELECT count(*) FROM public.crop
            WHERE strpos(lower(crop_name),lower(?))>0 OR strpos(lower(COALESCE(scientific_name,'')),lower(?))>0
            """, Long.class, search, search);
    }

    public Optional<Crop> crop(int id) {
        return db.query("SELECT * FROM public.crop WHERE crop_id=?", this::cropRow, id).stream().findFirst();
    }

    public int createCrop(String name, String scientificName, String category, Integer growthDays, String description) {
        return db.queryForObject("""
            INSERT INTO public.crop(crop_name,scientific_name,category,growth_days,description)
            VALUES (?,?,?,?,?) RETURNING crop_id
            """, Integer.class, name, scientificName, category, growthDays, description);
    }

    public void updateCrop(int id, String name, String scientificName, String category, Integer growthDays, String description) {
        db.update("""
            UPDATE public.crop SET crop_name=?,scientific_name=?,category=?,growth_days=?,description=? WHERE crop_id=?
            """, name, scientificName, category, growthDays, description, id);
    }

    public void cropStatus(int id, String status) {
        db.update("UPDATE public.crop SET status=? WHERE crop_id=?", status, id);
    }

    // ─── Crop Season CRUD ────────────────────────────────────────────────────
    public List<Season> listSeasons(int offset, int limit, String search, Integer plotId) {
        if (plotId != null) {
            return db.query("""
                SELECT s.*, p.plot_name, f.farm_name, c.crop_name 
                FROM public.crop_season s JOIN public.plot p USING(plot_id) 
                JOIN public.farm f USING(farm_id) JOIN public.crop c USING(crop_id)
                WHERE s.plot_id=? AND strpos(lower(c.crop_name),lower(?))>0
                ORDER BY s.season_id DESC LIMIT ? OFFSET ?
                """, this::seasonRow, plotId, search, limit, offset);
        }
        return db.query("""
            SELECT s.*, p.plot_name, f.farm_name, c.crop_name 
            FROM public.crop_season s JOIN public.plot p USING(plot_id) 
            JOIN public.farm f USING(farm_id) JOIN public.crop c USING(crop_id)
            WHERE strpos(lower(c.crop_name),lower(?))>0 OR strpos(lower(p.plot_name),lower(?))>0
            ORDER BY s.season_id DESC LIMIT ? OFFSET ?
            """, this::seasonRow, search, search, limit, offset);
    }

    public long countSeasons(String search, Integer plotId) {
        if (plotId != null) {
            return db.queryForObject("""
                SELECT count(*) FROM public.crop_season s JOIN public.crop c USING(crop_id)
                WHERE s.plot_id=? AND strpos(lower(c.crop_name),lower(?))>0
                """, Long.class, plotId, search);
        }
        return db.queryForObject("""
            SELECT count(*) FROM public.crop_season s JOIN public.plot p USING(plot_id) JOIN public.crop c USING(crop_id)
            WHERE strpos(lower(c.crop_name),lower(?))>0 OR strpos(lower(p.plot_name),lower(?))>0
            """, Long.class, search, search);
    }
    
    public Optional<Season> season(int id) {
        return db.query("""
            SELECT s.*, p.plot_name, f.farm_name, c.crop_name 
            FROM public.crop_season s JOIN public.plot p USING(plot_id) 
            JOIN public.farm f USING(farm_id) JOIN public.crop c USING(crop_id)
            WHERE s.season_id=?
            """, this::seasonRow, id).stream().findFirst();
    }

    public int createSeason(int plotId, int cropId, String status) {
        return db.queryForObject("""
            INSERT INTO public.crop_season(plot_id,crop_id,status) VALUES (?,?,?) RETURNING season_id
            """, Integer.class, plotId, cropId, status);
    }
    
    public void updateSeasonStatus(int id, String status) {
        db.update("UPDATE public.crop_season SET status=? WHERE season_id=?", status, id);
    }

    // ─── Audit ───────────────────────────────────────────────────────────────
    public void auditAction(int actor, String entityType, int entityId, String action, String requestId) {
        db.update("""
            INSERT INTO public.audit_log(actor_user_id,event_at,entity_type,entity_key,action,request_id)
            VALUES (?,CURRENT_TIMESTAMP,?,?,?,?)
            """, actor, entityType, Integer.toString(entityId), action, requestId);
    }
}
