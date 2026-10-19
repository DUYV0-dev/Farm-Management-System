package com.farmmanagement.backend.dashboard;

import static com.farmmanagement.backend.dashboard.ReportModels.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardStore {
    private final JdbcTemplate db;
    public DashboardStore(JdbcTemplate db) { this.db=db; }
    public String farmName(int id) { return db.queryForObject("SELECT farm_name FROM public.farm WHERE farm_id=?",String.class,id); }
    public boolean filterBelongs(int farm,String type,int id) {
        String sql=switch(type) {
            case "plot" -> "SELECT EXISTS(SELECT 1 FROM public.plot WHERE farm_id=? AND plot_id=?)";
            case "season" -> "SELECT EXISTS(SELECT 1 FROM public.crop_season s JOIN public.plot p USING(plot_id) WHERE p.farm_id=? AND s.season_id=?)";
            case "crop" -> "SELECT EXISTS(SELECT 1 FROM public.crop WHERE crop_id=? AND ?>0)";
            default -> throw new IllegalArgumentException();
        };
        return Boolean.TRUE.equals(db.queryForObject(sql,Boolean.class,type.equals("crop")?id:farm,type.equals("crop")?farm:id));
    }
    private String scope(Filters f,List<Object> args) {
        String q="";
        if(f.plotId()!=null) { q+=" AND s.plot_id=?";args.add(f.plotId()); }
        if(f.cropId()!=null) { q+=" AND s.crop_id=?";args.add(f.cropId()); }
        if(f.seasonId()!=null) { q+=" AND s.season_id=?";args.add(f.seasonId()); }
        return q;
    }
    private static final String JOINS=" FROM public.harvest h JOIN public.crop_season s USING(season_id) JOIN public.plot p USING(plot_id) JOIN public.crop c USING(crop_id) JOIN public.unit u ON u.unit_id=s.yield_unit_id ";
    public List<ProductionRow> details(int farm,Filters f,int limit) {
        List<Object> args=new ArrayList<>(List.of(farm,f.fromDate(),f.toDate()));
        String where=" WHERE p.farm_id=? AND h.record_status='CONFIRMED' AND h.harvest_date BETWEEN ? AND ?"+scope(f,args);
        args.add(limit);
        return db.query("SELECT h.*,s.season_name,s.status AS season_status,s.plot_id,s.crop_id,p.plot_name,c.crop_name,u.unit_id,u.unit_name"+JOINS+where+" ORDER BY h.harvest_date,h.harvest_id LIMIT ?",(r,n)->new ProductionRow(r.getInt("harvest_id"),r.getInt("season_id"),r.getString("season_name"),r.getString("season_status"),r.getInt("plot_id"),r.getString("plot_name"),r.getInt("crop_id"),r.getString("crop_name"),r.getObject("harvest_date",LocalDate.class),r.getBigDecimal("quantity"),r.getInt("unit_id"),r.getString("unit_name"),r.getString("quality")),args.toArray());
    }
    public List<Map<String,Object>> production(int farm,Filters f,String group) {
        String keys=switch(group) {
            case "unit" -> "u.unit_id,u.unit_name";
            case "month" -> "to_char(h.harvest_date,'YYYY-MM'),u.unit_id,u.unit_name";
            case "crop" -> "s.crop_id,c.crop_name,u.unit_id,u.unit_name";
            default -> throw new IllegalArgumentException();
        };
        List<Object> args=new ArrayList<>(List.of(farm,f.fromDate(),f.toDate()));
        String where=" WHERE p.farm_id=? AND h.record_status='CONFIRMED' AND h.harvest_date BETWEEN ? AND ?"+scope(f,args);
        String select=switch(group) {
            case "month" -> "to_char(h.harvest_date,'YYYY-MM') AS period,";
            case "crop" -> "s.crop_id AS \"cropId\",c.crop_name AS \"cropName\",";
            default -> "";
        };
        return db.queryForList("SELECT "+select+"u.unit_id AS \"unitId\",u.unit_name AS unit,count(*) AS count,sum(h.quantity) AS quantity"+JOINS+where+" GROUP BY "+keys+" ORDER BY "+keys,args.toArray());
    }
    public List<SeasonRow> seasons(int farm,Filters f,int limit) {
        return seasons(farm,f,limit,false);
    }
    public List<SeasonRow> reportSeasons(int farm,Filters f,int limit) { return seasons(farm,f,limit,true); }
    private List<SeasonRow> seasons(int farm,Filters f,int limit,boolean report) {
        List<Object> args=new ArrayList<>(List.of(f.fromDate(),f.toDate(),f.toDate(),f.fromDate(),f.toDate(),farm,f.toDate(),f.fromDate()));
        String where=" WHERE p.farm_id=? AND "+(report?"(s.record_status='CONFIRMED' OR COALESCE(a.harvest_count,0)>0)":"s.record_status='CONFIRMED' AND s.status<>'CANCELLED'")+" AND COALESCE(s.actual_start_date,s.planned_start_date)<=? AND COALESCE(s.actual_end_date,s.planned_end_date)>=?"+scope(f,args);
        args.add(limit);
        return db.query("""
            SELECT s.*,p.plot_name,c.crop_name,u.unit_name,
            COALESCE(a.period_quantity,0) AS period_quantity,COALESCE(a.cumulative_quantity,0) AS cumulative_quantity,COALESCE(a.harvest_count,0) AS harvest_count
            FROM public.crop_season s JOIN public.plot p USING(plot_id) JOIN public.crop c USING(crop_id)
            LEFT JOIN public.unit u ON u.unit_id=s.yield_unit_id
            LEFT JOIN (SELECT season_id,sum(quantity) FILTER(WHERE harvest_date BETWEEN ? AND ?) AS period_quantity,
                sum(quantity) FILTER(WHERE harvest_date<=?) AS cumulative_quantity,
                count(*) FILTER(WHERE harvest_date BETWEEN ? AND ?) AS harvest_count
                FROM public.harvest WHERE record_status='CONFIRMED' GROUP BY season_id) a USING(season_id)
            """+where+" ORDER BY s.season_id LIMIT ?",(r,n)->{
                var expected=r.getBigDecimal("expected_yield");var actual=r.getBigDecimal("cumulative_quantity");
                var ratio=expected==null || expected.signum()==0?null:actual.multiply(BigDecimal.valueOf(100)).divide(expected,2,RoundingMode.HALF_UP);
                return new SeasonRow(r.getInt("season_id"),r.getString("season_name"),r.getString("status"),r.getString("crop_name"),r.getString("plot_name"),r.getString("unit_name"),expected,r.getBigDecimal("period_quantity"),actual,ratio,ratio==null?"Chưa có kế hoạch lớn hơn 0":null,r.getLong("harvest_count"));
            },args.toArray());
    }
    public long unconfiguredSeasons(int farm) {
        return db.queryForObject("SELECT count(*) FROM public.crop_season s JOIN public.plot p USING(plot_id) WHERE p.farm_id=? AND s.record_status='CONFIRMED' AND (COALESCE(s.actual_start_date,s.planned_start_date) IS NULL OR COALESCE(s.actual_end_date,s.planned_end_date) IS NULL)",Long.class,farm);
    }
    public List<Map<String,Object>> inventory(int farm,Integer warehouse,Integer material) {
        String where=" WHERE w.farm_id=?";List<Object> args=new ArrayList<>(List.of(farm));
        if(warehouse!=null) { where+=" AND w.warehouse_id=?";args.add(warehouse); }
        if(material!=null) { where+=" AND m.material_id=?";args.add(material); }
        return db.queryForList("SELECT w.warehouse_id AS \"warehouseId\",w.warehouse_name AS warehouse,m.material_id AS \"materialId\",m.material_name AS material,m.unit,i.quantity,i.last_updated::text AS \"lastUpdated\" FROM public.warehouse_inventory i JOIN public.warehouse w USING(warehouse_id) JOIN public.material m USING(material_id)"+where+" ORDER BY w.warehouse_id,m.material_id",args.toArray());
    }
    public boolean warehouseBelongs(int farm,int id) { return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM public.warehouse WHERE farm_id=? AND warehouse_id=?)",Boolean.class,farm,id)); }
    public void save(Snapshot s,String payload) {
        db.update("INSERT INTO public.production_report_snapshot(report_id,farm_id,user_id,generated_at,expires_at,payload) VALUES (?,?,?,?,?,?::jsonb)",s.reportId(),s.farmId(),s.userId(),java.sql.Timestamp.from(s.generatedAt()),java.sql.Timestamp.from(s.expiresAt()),payload);
    }
    public Optional<String> snapshot(int actor,int farm,UUID id) {
        return db.query("SELECT payload::text FROM public.production_report_snapshot WHERE report_id=? AND farm_id=? AND user_id=?",(r,n)->r.getString(1),id,farm,actor).stream().findFirst();
    }
}
