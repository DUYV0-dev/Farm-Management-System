package com.farmmanagement.backend.operations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.farmmanagement.backend.common.DomainException;

@Repository
public class OperationsStore {
    private final JdbcTemplate db;
    public OperationsStore(JdbcTemplate db) { this.db=db; }
    private static final String TASKS="""
        SELECT t.*,u.full_name AS assignee,COALESCE(s.season_name,c.crop_name) AS season,p.plot_name AS sector,
          t.start_date::text AS "startDate",t.due_date::text AS "dueDate"
        FROM public.farming_task t JOIN public.app_user u ON u.user_id=t.assigned_to
        JOIN public.crop_season s USING(season_id) JOIN public.plot p USING(plot_id) JOIN public.crop c USING(crop_id)
        """;
    private static final String TASK_WHERE=" WHERE (?='' OR t.status=?) AND (?::integer IS NULL OR t.season_id=?)";
    private static final String CALENDAR_WHERE=" WHERE t.start_date<=? AND t.due_date>=? AND (?='' OR t.status=?) AND (?::integer IS NULL OR t.season_id=?) AND (?::integer IS NULL OR t.assigned_to=?)";

    public List<Map<String,Object>> calendar(LocalDate from,LocalDate to,String status,Integer season,Integer assignee) {
        return db.queryForList(TASKS+CALENDAR_WHERE+" ORDER BY t.start_date,t.task_id LIMIT 2000",to,from,status,status,season,season,assignee,assignee);
    }
    public Long calendarCount(LocalDate from,LocalDate to,String status,Integer season,Integer assignee) {
        return db.queryForObject("SELECT count(*) FROM public.farming_task t"+CALENDAR_WHERE,Long.class,to,from,status,status,season,season,assignee,assignee);
    }

    private Map<String,Object> row(String sql,Object... args) {
        return db.queryForList(sql,args).stream().findFirst().orElseThrow(DomainException::missing);
    }
    public Integer currentFarm() {
        return db.queryForObject("SELECT public.current_farm_id()",Integer.class);
    }

    public int audit(int uid,String type,String entityKey,String action,String requestId) {
        return db.update("INSERT INTO public.audit_log(actor_user_id,event_at,entity_type,entity_key,action,request_id) VALUES (?,now(),?,?,?,?)",uid,type,entityKey,action,requestId);
    }

    public Long countEntities(String table) {
        if (!Set.of("plot","crop_season","material","warehouse","farming_task","harvest").contains(table)) throw new IllegalArgumentException();
        return db.queryForObject("SELECT count(*) FROM public."+table,Long.class);
    }

    public BigDecimal totalExpenses() {
        return db.queryForObject("SELECT COALESCE(sum(amount),0) FROM public.farm_expense",BigDecimal.class);
    }

    public Long overdueTasks(LocalDate today) {
        return db.queryForObject("SELECT count(*) FROM public.farming_task WHERE status IN ('TODO','IN_PROGRESS') AND due_date<?",Long.class,today);
    }

    public List<Map<String,Object>> seasons() {
        return db.queryForList("SELECT s.season_id AS id,COALESCE(s.season_name,c.crop_name)||' / '||p.plot_name AS name,s.status FROM public.crop_season s JOIN public.plot p USING(plot_id) JOIN public.crop c USING(crop_id) ORDER BY s.season_id DESC");
    }

    public List<Map<String,Object>> staff(int farm) {
        return db.queryForList("SELECT u.user_id AS id,u.full_name AS name FROM public.farm_member m JOIN public.app_user u USING(user_id) WHERE m.farm_id=? AND m.status='ACTIVE' AND u.status='ACTIVE' ORDER BY u.full_name",farm);
    }

    public List<Map<String,Object>> tasks(String status,Integer seasonId,int size,int offset) {
        return db.queryForList(TASKS+TASK_WHERE+" ORDER BY t.due_date,t.task_id LIMIT ? OFFSET ?",status,status,seasonId,seasonId,size,offset);
    }

    public Long taskCount(String status,Integer seasonId) {
        return db.queryForObject("SELECT count(*) FROM public.farming_task t"+TASK_WHERE,Long.class,status,status,seasonId,seasonId);
    }

    public Integer createTask(int farm,int season,String title,int assignee,String category,LocalDate start,LocalDate due,String description,int uid) {
        return db.queryForObject("INSERT INTO public.farming_task(farm_id,season_id,title,assigned_to,category,start_date,due_date,description,created_by) VALUES (?,?,?,?,?,?,?,?,?) RETURNING task_id",Integer.class,farm,season,title,assignee,category,start,due,description,uid);
    }

    public int createdEvent(int task,int uid) {
        return db.update("INSERT INTO public.task_event(task_id,actor_id,note) VALUES (?,?,'Tạo công việc')",task,uid);
    }

    public Map<String,Object> task(int id) {
        return row(TASKS+" WHERE t.task_id=?",id);
    }

    public Map<String,Object> lockTask(int id) {
        return row("SELECT * FROM public.farming_task WHERE task_id=? FOR UPDATE",id);
    }

    public int updateProgress(String status,int progress,int id,int version) {
        return db.update("UPDATE public.farming_task SET status=?,progress=?,version=version+1,updated_at=now() WHERE task_id=? AND version=?",status,progress,id,version);
    }

    public int taskEvent(int id,int uid,String note) {
        return db.update("INSERT INTO public.task_event(task_id,actor_id,note) VALUES (?,?,?)",id,uid,note);
    }

    public Map<String,Object> requireTask(int id) {
        return row("SELECT task_id FROM public.farming_task WHERE task_id=?",id);
    }

    public List<Map<String,Object>> history(int id) {
        return db.queryForList("SELECT e.note,e.event_at::text AS time,u.full_name AS actor FROM public.task_event e JOIN public.app_user u ON u.user_id=e.actor_id WHERE e.task_id=? ORDER BY e.event_id DESC",id);
    }

    public Map<String,Object> lockTaskStatus(int id) {
        return row("SELECT task_id,status FROM public.farming_task WHERE task_id=? FOR UPDATE",id);
    }

    public int assign(String title,int assignee,LocalDate start,LocalDate due,int id,int version) {
        return db.update("UPDATE public.farming_task SET title=?,assigned_to=?,start_date=?,due_date=?,version=version+1,updated_at=now() WHERE task_id=? AND version=?",title,assignee,start,due,id,version);
    }

    public List<Map<String,Object>> taskNotifications(LocalDate today,LocalDate until) {
        return db.queryForList("SELECT 'task-'||task_id AS id,title,due_date::text AS date,CASE WHEN due_date<? THEN 'OVERDUE' ELSE 'UPCOMING' END AS category FROM public.farming_task WHERE status IN ('TODO','IN_PROGRESS') AND due_date<=? ORDER BY due_date LIMIT 100",today,until);
    }

    public List<Map<String,Object>> stockNotifications() {
        return db.queryForList("SELECT 'stock-'||i.inventory_id AS id,m.material_name||' / '||w.warehouse_name AS title,i.quantity::text||' '||m.unit AS date,'LOW_STOCK' AS category FROM public.warehouse_inventory i JOIN public.material m USING(material_id) JOIN public.warehouse w USING(warehouse_id) WHERE i.quantity<=10 ORDER BY i.quantity LIMIT 100");
    }

    public List<String> readNotifications(int farm,int uid) {
        return db.queryForList("SELECT notification_key FROM public.notification_read WHERE farm_id=? AND user_id=?",String.class,farm,uid);
    }

    public int markRead(int farm,int uid,String key) {
        return db.update("INSERT INTO public.notification_read(farm_id,user_id,notification_key) VALUES (?,?,?) ON CONFLICT DO NOTHING",farm,uid,key);
    }

    public List<Map<String,Object>> lots(String search) {
        return db.queryForList("SELECT h.harvest_id AS id,'HARVEST-'||h.harvest_id AS code,c.crop_name AS product,s.season_name AS season,h.harvest_date::text AS date,h.quantity,u.unit_name AS unit,h.quality FROM public.harvest h JOIN public.crop_season s USING(season_id) JOIN public.plot p USING(plot_id) JOIN public.crop c USING(crop_id) JOIN public.unit u ON u.unit_id=s.yield_unit_id WHERE c.crop_name ILIKE ? OR ('HARVEST-'||h.harvest_id) ILIKE ? ORDER BY h.harvest_id DESC LIMIT 100",search,search);
    }

    public Map<String,Object> harvest(int id) {
        return row("SELECT h.*,s.season_name,s.season_id,p.plot_name,c.crop_name,f.farm_name FROM public.harvest h JOIN public.crop_season s USING(season_id) JOIN public.plot p USING(plot_id) JOIN public.crop c USING(crop_id) JOIN public.farm f USING(farm_id) WHERE h.harvest_id=?",id);
    }

    public List<Map<String,Object>> seasonTasks(Object season) {
        return db.queryForList(TASKS+" WHERE t.season_id=? ORDER BY t.start_date",season);
    }

    public List<Map<String,Object>> seasonUsage(Object season) {
        return db.queryForList("SELECT material_name,usage_type,quantity,unit,application_date::text AS date,notes FROM public.material_usage WHERE season_id=? ORDER BY application_date",season);
    }

    public List<Map<String,Object>> harvestProducts(int id) {
        return db.queryForList("SELECT product_id,product_name,quantity,quality,status,storage_location FROM public.harvested_product WHERE harvest_id=?",id);
    }

    public List<Map<String,Object>> products(int size,int offset) {
        return db.queryForList("SELECT p.*,c.crop_name,c.season_name,c.unit FROM public.harvested_product p JOIN public.crop_production c USING(production_id) ORDER BY p.product_id DESC LIMIT ? OFFSET ?",size,offset);
    }

    public Long productCount() {
        return db.queryForObject("SELECT count(*) FROM public.harvested_product",Long.class);
    }

    public int updateStorage(String location,String status,int id) {
        return db.update("UPDATE public.harvested_product SET storage_location=?,status=?,updated_at=now() WHERE product_id=?",location,status,id);
    }

    public Map<String,Object> product(int id) {
        return row("SELECT * FROM public.harvested_product WHERE product_id=?",id);
    }

    public List<Map<String,Object>> growth() {
        return db.queryForList("SELECT g.*,COALESCE(s.season_name,c.crop_name) AS season,s.expected_yield FROM public.crop_growth g JOIN public.crop_season s USING(season_id) JOIN public.crop c USING(crop_id) ORDER BY g.observed_date DESC,g.growth_id DESC LIMIT 100");
    }

    public Map<String,Object> lockSeason(int season) {
        return row("SELECT season_id FROM public.crop_season WHERE season_id=? AND status IN ('PLANNED','IN_PROGRESS') FOR UPDATE",season);
    }

    public int updateExpectedYield(BigDecimal expected,int season) {
        return db.update("UPDATE public.crop_season SET expected_yield=? WHERE season_id=?",expected,season);
    }

    public Integer recordGrowth(int season,String stage,LocalDate date,String notes,int uid) {
        return db.queryForObject("INSERT INTO public.crop_growth(season_id,stage,observed_date,notes,created_by) VALUES (?,?,?,?,?) RETURNING growth_id",Integer.class,season,stage,date,notes,uid);
    }

    public List<Map<String,Object>> movements() {
        return db.queryForList("SELECT e.*,m.material_name,w.warehouse_name FROM public.inventory_movement e JOIN public.material m USING(material_id) JOIN public.warehouse w USING(warehouse_id) ORDER BY movement_id DESC LIMIT 100");
    }

    public void lockStock() {
        db.execute("SELECT pg_advisory_xact_lock(740207)");
    }

    public int claimStockRequest(int farm,UUID key,String payload,int uid) {
        return db.update("INSERT INTO public.stock_request(farm_id,request_key,payload,created_by) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",farm,key,payload,uid);
    }

    public Map<String,Object> stockRequest(int farm,UUID key) {
        return row("SELECT payload,created_by FROM public.stock_request WHERE farm_id=? AND request_key=?",farm,key);
    }

    public Map<String,Object> requireWarehouse(int warehouse) {
        return row("SELECT warehouse_id FROM public.warehouse WHERE warehouse_id=? AND status='ACTIVE'",warehouse);
    }

    public int usageRequest(UUID key,int id) {
        return db.update("UPDATE public.material_usage SET request_key=? WHERE usage_id=?",key,id);
    }

    public Map<String,Object> requireMaterial(int material) {
        return row("SELECT material_id FROM public.material WHERE material_id=? AND status='ACTIVE'",material);
    }

    public String stockReason(String reason) {
        return db.queryForObject("SELECT set_config('app.stock_reason',?,true)",String.class,reason);
    }

    public int ensureInventory(int warehouse,int material) {
        return db.update("INSERT INTO public.warehouse_inventory(warehouse_id,material_id,quantity) VALUES (?,?,0) ON CONFLICT(warehouse_id,material_id) DO NOTHING",warehouse,material);
    }

    public Map<String,Object> lockInventory(int warehouse,int material) {
        return row("SELECT inventory_id,quantity FROM public.warehouse_inventory WHERE warehouse_id=? AND material_id=? FOR UPDATE",warehouse,material);
    }

    public int updateInventory(BigDecimal next,Object inventoryId) {
        return db.update("UPDATE public.warehouse_inventory SET quantity=?,last_updated=now() WHERE inventory_id=?",next,inventoryId);
    }
}
