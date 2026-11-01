package com.farmmanagement.backend.portal;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StorefrontStore {
    private final JdbcTemplate db;
    public StorefrontStore(JdbcTemplate db) { this.db=db; }
    public List<Map<String,Object>> catalog() {
        return db.queryForList("SELECT * FROM public.store_catalog() ORDER BY farm_id,id LIMIT 1000");
    }
    public UUID checkout(String payload) {
        return db.queryForObject("SELECT public.store_checkout(?::jsonb)",UUID.class,payload);
    }
    public String receipt(UUID id) {
        return db.queryForObject("SELECT public.store_receipt(?)::text",String.class,id);
    }
}
