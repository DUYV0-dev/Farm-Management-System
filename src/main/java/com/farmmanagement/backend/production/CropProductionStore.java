package com.farmmanagement.backend.production;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.PreparedStatement;

@Repository
public class CropProductionStore {
    private final JdbcTemplate db;

    public CropProductionStore(JdbcTemplate db) {
        this.db = db;
    }

    private CropProduction row(ResultSet r, int i) throws SQLException {
        return new CropProduction(
            r.getInt("production_id"),
            r.getInt("farm_id"),
            r.getString("crop_name"),
            r.getString("season_name"),
            r.getString("growth_stage"),
            r.getDouble("expected_quantity"),
            r.getDouble("actual_quantity"),
            r.getString("unit"),
            r.getString("status"),
            r.getTimestamp("created_at") != null ? r.getTimestamp("created_at").toInstant() : null,
            r.getTimestamp("updated_at") != null ? r.getTimestamp("updated_at").toInstant() : null
        );
    }

    public CropProduction create(CropProduction p) {
        String sql = "INSERT INTO public.crop_production(farm_id, crop_name, season_name, growth_stage, expected_quantity, actual_quantity, unit, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"production_id"});
            ps.setInt(1, p.farmId());
            ps.setString(2, p.cropName());
            ps.setString(3, p.seasonName());
            ps.setString(4, p.growthStage());
            ps.setObject(5, p.expectedQuantity());
            ps.setObject(6, p.actualQuantity());
            ps.setString(7, p.unit());
            ps.setString(8, p.status());
            return ps;
        }, keyHolder);
        
        int id = keyHolder.getKey().intValue();
        return byId(id).orElseThrow();
    }

    public Optional<CropProduction> byId(int id) {
        return db.query("SELECT * FROM public.crop_production WHERE production_id=?", this::row, id).stream().findFirst();
    }

    public List<CropProduction> byFarmId(int farmId) {
        return db.query("SELECT * FROM public.crop_production WHERE farm_id=? ORDER BY created_at DESC", this::row, farmId);
    }

    public void update(int id, CropProduction p) {
        String sql = "UPDATE public.crop_production SET crop_name=?, season_name=?, growth_stage=?, expected_quantity=?, actual_quantity=?, unit=?, status=?, updated_at=CURRENT_TIMESTAMP WHERE production_id=?";
        db.update(sql, p.cropName(), p.seasonName(), p.growthStage(), p.expectedQuantity(), p.actualQuantity(), p.unit(), p.status(), id);
    }

    public void delete(int id) {
        db.update("DELETE FROM public.crop_production WHERE production_id=?", id);
    }
}
