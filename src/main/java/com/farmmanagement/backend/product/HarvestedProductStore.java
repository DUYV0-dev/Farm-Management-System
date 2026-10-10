package com.farmmanagement.backend.product;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class HarvestedProductStore {
    private final JdbcTemplate db;

    public HarvestedProductStore(JdbcTemplate db) {
        this.db = db;
    }

    private HarvestedProduct row(ResultSet r, int i) throws SQLException {
        return new HarvestedProduct(
            r.getInt("product_id"),
            r.getInt("production_id"),
            r.getString("product_name"),
            r.getDouble("quantity"),
            r.getString("quality"),
            r.getString("status"),
            r.getString("storage_location"),
            r.getTimestamp("created_at") != null ? r.getTimestamp("created_at").toInstant() : null,
            r.getTimestamp("updated_at") != null ? r.getTimestamp("updated_at").toInstant() : null
        );
    }

    public HarvestedProduct create(HarvestedProduct p) {
        String sql = "INSERT INTO public.harvested_product(production_id, product_name, quantity, quality, status, storage_location) VALUES (?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"product_id"});
            ps.setInt(1, p.productionId());
            ps.setString(2, p.productName());
            ps.setObject(3, p.quantity());
            ps.setString(4, p.quality());
            ps.setString(5, p.status());
            ps.setString(6, p.storageLocation());
            return ps;
        }, keyHolder);
        
        int id = keyHolder.getKey().intValue();
        return byId(id).orElseThrow();
    }

    public Optional<HarvestedProduct> byId(int id) {
        return db.query("SELECT * FROM public.harvested_product WHERE product_id=?", this::row, id).stream().findFirst();
    }

    public List<HarvestedProduct> byProductionId(int productionId) {
        return db.query("SELECT * FROM public.harvested_product WHERE production_id=? ORDER BY created_at DESC", this::row, productionId);
    }

    public void update(int id, HarvestedProduct p) {
        String sql = "UPDATE public.harvested_product SET product_name=?, quantity=?, quality=?, status=?, storage_location=?, updated_at=CURRENT_TIMESTAMP WHERE product_id=?";
        db.update(sql, p.productName(), p.quantity(), p.quality(), p.status(), p.storageLocation(), id);
    }

    public void delete(int id) {
        db.update("DELETE FROM public.harvested_product WHERE product_id=?", id);
    }
}
