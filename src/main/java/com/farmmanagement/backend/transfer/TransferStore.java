package com.farmmanagement.backend.transfer;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TransferStore {
    private final JdbcTemplate db;
    public TransferStore(JdbcTemplate db) { this.db=db; }
    public int currentFarm() {
        String value=db.queryForObject("SELECT current_setting('app.farm_id',true)",String.class);
        if(value==null || value.isBlank()) throw new org.springframework.security.access.AccessDeniedException("Select a farm");
        return Integer.parseInt(value);
    }
    private String select(TransferSchema.Dataset schema) {
        var columns=new ArrayList<String>();columns.add("t."+schema.idColumn()+" AS \"sourceId\"");
        schema.fields().forEach(f -> columns.add("t."+f.column()+" AS \""+f.name()+"\""));
        return "SELECT "+String.join(",",columns)+" FROM public."+schema.table()+" t WHERE "+schema.scope();
    }
    public List<Map<String,String>> rows(TransferSchema.Dataset schema,int farm,Integer id) {
        String sql=select(schema)+(id==null?"":" AND t."+schema.idColumn()+"=?")+" ORDER BY t."+schema.idColumn()+" LIMIT "+(TransferSchema.MAX_ROWS+1);
        Object[] params=id==null?new Object[]{farm}:new Object[]{farm,id};
        return db.query(sql,(r,n)->{
            var row=new LinkedHashMap<String,String>();
            for(String field:schema.columns()) row.put(field,r.getString(field));return row;
        },params);
    }
    /** Explicit tenant filtering supplements RLS, including when verifying under an admin connection. */
    public boolean reference(String dataset,int id,int farm,boolean active) {
        String predicate=switch(dataset) {
            case "plots" -> "SELECT 1 FROM public.plot p JOIN public.farm f USING(farm_id) WHERE p.plot_id=? AND p.farm_id=?"+(active?" AND p.status='ACTIVE' AND f.status='ACTIVE'":"");
            case "crops" -> "SELECT 1 FROM public.crop WHERE crop_id=? AND (tenant_farm_id=? OR tenant_farm_id IS NULL)"+(active?" AND status='ACTIVE'":"");
            case "material-categories" -> "SELECT 1 FROM public.material_category WHERE category_id=? AND (tenant_farm_id=? OR tenant_farm_id IS NULL)"+(active?" AND status='ACTIVE'":"");
            case "materials" -> "SELECT 1 FROM public.material m JOIN public.material_category c USING(category_id) WHERE m.material_id=? AND (m.tenant_farm_id=? OR m.tenant_farm_id IS NULL)"+(active?" AND m.status='ACTIVE' AND c.status='ACTIVE'":"");
            case "warehouses" -> "SELECT 1 FROM public.warehouse w JOIN public.farm f USING(farm_id) WHERE w.warehouse_id=? AND w.farm_id=?"+(active?" AND w.status='ACTIVE' AND f.status='ACTIVE'":"");
            case "seasons" -> "SELECT 1 FROM public.crop_season s JOIN public.plot p USING(plot_id) JOIN public.farm f USING(farm_id) JOIN public.crop c USING(crop_id) WHERE s.season_id=? AND p.farm_id=?"+(active?" AND s.status IN ('IN_PROGRESS','COMPLETED') AND p.status='ACTIVE' AND f.status='ACTIVE' AND c.status='ACTIVE'":"");
            default -> throw new IllegalArgumentException();
        };
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS("+predicate+")",Boolean.class,id,farm));
    }
    public boolean duplicateName(TransferSchema.Dataset schema,int farm,String name) {
        String column=schema.fields().stream().filter(f -> f.name().equals("name")).findFirst().orElseThrow().column();
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM public."+schema.table()+" t WHERE "+schema.scope()+" AND lower(btrim(t."+column+"))=lower(btrim(?)))",Boolean.class,farm,name));
    }
    public BigDecimal freePlotArea(int farm) {
        return db.queryForObject("SELECT f.area_ha-COALESCE((SELECT sum(p.area_ha) FROM public.plot p WHERE p.farm_id=f.farm_id),0) FROM public.farm f WHERE f.farm_id=?",BigDecimal.class,farm);
    }
    public boolean duplicateValues(TransferSchema.Dataset schema,int farm,Map<String,String> row) {
        var predicates=new ArrayList<String>();var params=new ArrayList<Object>();params.add(farm);
        for(var field:schema.fields()) {
            String type=switch(field.type()) { case INTEGER -> "integer";case DECIMAL -> "numeric";case DATE -> "date";default -> "text"; };
            predicates.add("t."+field.column()+" IS NOT DISTINCT FROM CAST(? AS "+type+")");params.add(row.get(field.name()));
        }
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM public."+schema.table()+" t WHERE "+schema.scope()+" AND "+String.join(" AND ",predicates)+")",Boolean.class,params.toArray()));
    }
    public void lockImport(int farm) { db.queryForObject("SELECT pg_advisory_xact_lock(740208,?)",Object.class,farm); }
}
