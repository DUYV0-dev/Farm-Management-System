package com.farmmanagement.backend.harvest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HarvestStore {
    private final JdbcTemplate db;
    public HarvestStore(JdbcTemplate db) { this.db=db; }
    public record Season(int id, int farmId, String plotName, String cropName, String name,
        String status, String recordStatus, LocalDate start, LocalDate end, Integer unitId, String unit) {}
    public record Harvest(int id, int seasonId, int farmId, String seasonName, String plotName,
        String cropName, LocalDate harvestDate, BigDecimal quantity, String unit, String quality,
        String recordStatus, int createdBy, String createdAt) {}
    // Shared with migration 09 and all existing season writes; prevents parent changes during harvest writes.
    public void lock() { db.execute("SELECT pg_advisory_xact_lock(740207)"); }
    public boolean allowed(int actor,int farmId,boolean write) {
        return Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM public.farm_member fm
            JOIN public.farm_member_role mr ON mr.farm_id=fm.farm_id AND mr.user_id=fm.user_id
            JOIN public.role r USING(role_id) JOIN public.app_user u ON u.user_id=fm.user_id
            WHERE fm.user_id=? AND fm.farm_id=? AND fm.status='ACTIVE' AND u.status='ACTIVE'
            AND r.scope='FARM' AND (r.role_name='MANAGER' OR (NOT ? AND r.role_name='OWNER')))
            """,Boolean.class,actor,farmId,write));
    }
    public List<Map<String,Object>> farms(int actor) {
        return db.query("""
            SELECT DISTINCT f.farm_id,f.farm_name FROM public.farm f
            JOIN public.farm_member fm USING(farm_id)
            JOIN public.farm_member_role mr ON mr.farm_id=fm.farm_id AND mr.user_id=fm.user_id
            JOIN public.role r USING(role_id)
            WHERE fm.user_id=? AND fm.status='ACTIVE' AND r.scope='FARM' AND r.role_name IN ('OWNER','MANAGER')
            ORDER BY f.farm_id
            """,(r,n)->Map.<String,Object>of("id",r.getInt(1),"name",r.getString(2)),actor);
    }
    private static final String SEASONS="""
        SELECT s.*,p.farm_id,p.plot_name,c.crop_name,u.unit_name
        FROM public.crop_season s JOIN public.plot p USING(plot_id) JOIN public.crop c USING(crop_id)
        LEFT JOIN public.unit u ON u.unit_id=s.yield_unit_id
        """;
    private Season seasonRow(java.sql.ResultSet r,int n) throws java.sql.SQLException {
        return new Season(r.getInt("season_id"),r.getInt("farm_id"),r.getString("plot_name"),
            r.getString("crop_name"),r.getString("season_name"),r.getString("status"),r.getString("record_status"),
            r.getObject("actual_start_date",LocalDate.class)!=null?r.getObject("actual_start_date",LocalDate.class):r.getObject("planned_start_date",LocalDate.class),
            r.getObject("actual_end_date",LocalDate.class)!=null?r.getObject("actual_end_date",LocalDate.class):r.getObject("planned_end_date",LocalDate.class),
            r.getObject("yield_unit_id",Integer.class),r.getString("unit_name"));
    }
    public Optional<Season> season(int farmId,int id) {
        return db.query(SEASONS+" WHERE p.farm_id=? AND s.season_id=?",this::seasonRow,farmId,id).stream().findFirst();
    }
    public List<Season> seasons(int farmId) { return db.query(SEASONS+" WHERE p.farm_id=? ORDER BY s.season_id DESC",this::seasonRow,farmId); }
    public List<Map<String,Object>> units() {
        return db.query("SELECT unit_id,unit_name FROM public.unit ORDER BY unit_id",(r,n)->Map.<String,Object>of("id",r.getInt(1),"name",r.getString(2)));
    }
    public boolean unitExists(int id) { return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM public.unit WHERE unit_id=?)",Boolean.class,id)); }
    public boolean hasHarvest(int seasonId) { return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM public.harvest WHERE season_id=?)",Boolean.class,seasonId)); }
    public void configure(int seasonId,String name,LocalDate start,LocalDate end,int unitId) {
        db.update("UPDATE public.crop_season SET season_name=?,planned_start_date=?,planned_end_date=?,yield_unit_id=? WHERE season_id=?",name,start,end,unitId,seasonId);
    }
    public int create(int actor,int seasonId,LocalDate date,BigDecimal quantity,String quality) {
        return db.queryForObject("""
            INSERT INTO public.harvest(season_id,created_by,harvest_date,quantity,quality,record_status)
            VALUES (?,?,?,?,?,'CONFIRMED') RETURNING harvest_id
            """,Integer.class,seasonId,actor,date,quantity,quality);
    }
    private static final String HARVESTS="""
        FROM public.harvest h JOIN public.crop_season s USING(season_id)
        JOIN public.plot p USING(plot_id) JOIN public.crop c USING(crop_id)
        LEFT JOIN public.unit u ON u.unit_id=s.yield_unit_id
        """;
    private Harvest row(java.sql.ResultSet r,int n) throws java.sql.SQLException {
        return new Harvest(r.getInt("harvest_id"),r.getInt("season_id"),r.getInt("farm_id"),r.getString("season_name"),
            r.getString("plot_name"),r.getString("crop_name"),r.getObject("harvest_date",LocalDate.class),r.getBigDecimal("quantity"),
            r.getString("unit_name"),r.getString("quality"),r.getString("record_status"),r.getInt("created_by"),r.getTimestamp("created_at").toInstant().toString());
    }
    private static final String SELECT="SELECT h.*,p.farm_id,s.season_name,p.plot_name,c.crop_name,u.unit_name ";
    public Optional<Harvest> get(int farmId,int id) {
        return db.query(SELECT+HARVESTS+" WHERE p.farm_id=? AND h.harvest_id=?",this::row,farmId,id).stream().findFirst();
    }
    public Map<String,Object> history(int farmId,Integer seasonId,LocalDate from,LocalDate to,int page,int size) {
        String where=" WHERE p.farm_id=?"; List<Object> args=new ArrayList<>();args.add(farmId);
        if(seasonId!=null) { where+=" AND h.season_id=?";args.add(seasonId); }
        if(from!=null) { where+=" AND h.harvest_date>=?";args.add(from); }
        if(to!=null) { where+=" AND h.harvest_date<=?";args.add(to); }
        long total=db.queryForObject("SELECT count(*) "+HARVESTS+where,Long.class,args.toArray());
        args.add(size);args.add(page*size);
        var rows=db.query(SELECT+HARVESTS+where+" ORDER BY h.harvest_date DESC,h.harvest_id DESC LIMIT ? OFFSET ?",this::row,args.toArray());
        return Map.of("items",rows,"page",page,"size",size,"total",total);
    }
    public void audit(int actor,String type,int id,String action,String requestId) {
        db.update("INSERT INTO public.audit_log(actor_user_id,event_at,entity_type,entity_key,action,request_id) VALUES (?,CURRENT_TIMESTAMP,?,?,?,?)",actor,type,Integer.toString(id),action,requestId);
    }
}
