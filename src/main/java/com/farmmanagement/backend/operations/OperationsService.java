package com.farmmanagement.backend.operations;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import com.farmmanagement.backend.common.*;
import tools.jackson.databind.JsonNode;

@Service
@Transactional
public class OperationsService {
    private final OperationsStore store;
    private final com.farmmanagement.backend.usage.MaterialUsageService usage;
    private static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    public OperationsService(OperationsStore store,com.farmmanagement.backend.usage.MaterialUsageService usage) { this.store=store;this.usage=usage; }
    private int farm() { return store.currentFarm(); }
    private boolean has(String permission) { return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(a->a.getAuthority().equals(permission)); }
    private int id(JsonNode b,String field) {
        var value=b.get(field);
        if(value==null || !value.isIntegralNumber() || !value.canConvertToInt() || value.asInt()<=0) throw new IllegalArgumentException();
        return value.asInt();
    }
    private void fields(JsonNode b,String... names) { Input.fields(b,names); }
    private void audit(int uid,String type,int id,String action,String requestId) {
        store.audit(uid,type,Integer.toString(id),action,requestId);
    }
    @PreAuthorize("hasAuthority('farms:read')")
    public Object overview(String requestId) {
        var data=new LinkedHashMap<String,Object>();data.put("today",LocalDate.now(ZONE));data.put("timezone",ZONE.toString());
        for(var item:List.of(new String[]{"plots","plots:read","plot"},new String[]{"seasons","crops:read","crop_season"},
                new String[]{"materials","materials:read","material"},new String[]{"warehouses","warehouses:read","warehouse"},
                new String[]{"tasks","tasks:read","farming_task"},new String[]{"harvests","harvests:read","harvest"})) {
            if(has(item[1])) data.put(item[0],store.countEntities(item[2]));
        }
        if(has("expenses:read")) data.put("expenses",store.totalExpenses());
        if(has("tasks:read")) data.put("overdue",store.overdueTasks(LocalDate.now(ZONE)));
        return data;
    }
    @PreAuthorize("hasAnyAuthority('tasks:read','inventory:manage','traceability:read','crops:read')")
    public Object options(String requestId) {
        return Map.of("seasons",store.seasons(),
            "staff",store.staff(farm()));
    }

    @PreAuthorize("hasAuthority('tasks:read')")
    public Object tasks(int page,int size,
            String status,Integer seasonId,String requestId) {
        if(page<0 || page>100000 || size<1 || size>100 || !Set.of("","TODO","IN_PROGRESS","COMPLETED","CANCELLED").contains(status)) throw new IllegalArgumentException();
        return Map.of("items",store.tasks(status,seasonId,size,page*size),
            "total",store.taskCount(status,seasonId),"page",page,"size",size);
    }
    @PreAuthorize("hasAuthority('tasks:create')")
    public Object createTask(int actorId,JsonNode b,String requestId) {
        fields(b,"title","seasonId","assignedTo","category","startDate","dueDate","description");
        int season=id(b,"seasonId"),assignee=id(b,"assignedTo");
        LocalDate start=LocalDate.parse(Input.text(b,"startDate",10)),due=LocalDate.parse(Input.text(b,"dueDate",10));
        if(due.isBefore(start)) throw new IllegalArgumentException();
        String category=Input.text(b,"category",30);
        if(!Set.of("OTHER","FERTILIZER","PESTICIDE","IRRIGATION","HARVEST","SOIL").contains(category)) throw new IllegalArgumentException();
        String description=b.path("description").asText("");if(!b.path("description").isTextual() || description.length()>2000) throw new IllegalArgumentException();
        int uid=actorId;
        int task=store.createTask(farm(),season,Input.text(b,"title",200),assignee,category,start,due,description,uid);
        store.createdEvent(task,uid);audit(uid,"FARMING_TASK",task,"CREATE",requestId);
        return store.task(task);
    }

    @PreAuthorize("hasAuthority('tasks:read')")
    @Transactional(readOnly=true)
    public Map<String,Object> calendar(LocalDate from,LocalDate to,String status,Integer season,Integer assignee) {
        if(from==null || to==null || to.isBefore(from) || from.plusDays(62).isBefore(to)
                || !Set.of("","TODO","IN_PROGRESS","COMPLETED","CANCELLED").contains(status)
                || (season!=null && season<=0) || (assignee!=null && assignee<=0)) throw new IllegalArgumentException();
        long total=store.calendarCount(from,to,status,season,assignee);
        return Map.of("items",store.calendar(from,to,status,season,assignee),"total",total,"truncated",total>2000);
    }
    @PreAuthorize("hasAuthority('tasks:update')")
    public Object progress(int actorId,int id,JsonNode b,String requestId) {
        fields(b,"status","progress","version","note");String status=Input.text(b,"status",20),note=Input.text(b,"note",2000);
        if(!Set.of("TODO","IN_PROGRESS","COMPLETED","CANCELLED").contains(status) || !b.path("progress").isIntegralNumber() || !b.path("version").isIntegralNumber()) throw new IllegalArgumentException();
        int progress=b.path("progress").asInt(-1),version=b.path("version").asInt(-1),uid=actorId;
        if(progress<0 || progress>100 || version<0 || status.equals("COMPLETED")!=(progress==100)) throw new IllegalArgumentException();
        var task=store.lockTask(id);
        if(!has("tasks:create") && !task.get("assigned_to").equals(uid)) throw new org.springframework.security.access.AccessDeniedException("Assigned tasks only");
        if(Set.of("COMPLETED","CANCELLED").contains(task.get("status"))) throw DomainException.conflict("Công việc đã đóng. Lịch sử được giữ nguyên.");
        if(store.updateProgress(status,progress,id,version)!=1)
            throw DomainException.conflict("Công việc đã được cập nhật. Tải lại dữ liệu trước khi lưu.");
        store.taskEvent(id,uid,status+" / "+progress+"%: "+note);audit(uid,"FARMING_TASK",id,"PROGRESS",requestId);
        return store.task(id);
    }
    @PreAuthorize("hasAuthority('tasks:read')")
    public Object history(int id,String requestId) {
        store.requireTask(id);
        return store.history(id);
    }
    @PreAuthorize("hasAuthority('tasks:create')")
    public Object assign(int actorId,int id,JsonNode b,String requestId) {
        fields(b,"title","assignedTo","startDate","dueDate","version");
        int assignee=id(b,"assignedTo");String title=Input.text(b,"title",200);
        LocalDate start=LocalDate.parse(Input.text(b,"startDate",10)),due=LocalDate.parse(Input.text(b,"dueDate",10));
        if(due.isBefore(start) || !b.path("version").isIntegralNumber() || b.path("version").asInt(-1)<0) throw new IllegalArgumentException();
        var task=store.lockTaskStatus(id);
        if(Set.of("COMPLETED","CANCELLED").contains(task.get("status"))) throw DomainException.conflict("Công việc đã đóng.");
        if(store.assign(title,assignee,start,due,id,b.path("version").asInt())!=1)
            throw DomainException.conflict("Công việc đã thay đổi. Tải lại trước khi lưu.");
        store.taskEvent(id,actorId,"Phân công #"+assignee+"; lịch "+start+" - "+due);
        audit(actorId,"FARMING_TASK",id,"ASSIGN",requestId);return store.task(id);
    }
    @PreAuthorize("hasAuthority('notifications:read')")
    public Object notifications(int actorId,String requestId) {
        LocalDate today=LocalDate.now(ZONE);
        var items=new ArrayList<Map<String,Object>>();
        if(has("tasks:read")) items.addAll(store.taskNotifications(today,today.plusDays(3)));
        if(has("warehouses:read")) items.addAll(store.stockNotifications());
        var read=new HashSet<>(store.readNotifications(farm(),actorId));
        for(var item:items) item.put("read",read.contains(item.get("id")));
        return items;
    }
    @PreAuthorize("hasAuthority('notifications:read')")
    public Object markRead(int actorId,String key,String requestId) {
        if(!key.matches("(task|stock)-[1-9][0-9]{0,9}")) throw new IllegalArgumentException();
        store.markRead(farm(),actorId,key);
        return Map.of("read",true);
    }
    @PreAuthorize("hasAuthority('traceability:read')")
    public Object lots(String search,String requestId) {
        if(search.length()>100) throw new IllegalArgumentException();
        return store.lots("%"+search+"%");
    }
    @PreAuthorize("hasAuthority('traceability:read')")
    public Object trace(int id,String requestId) {
        var harvest=store.harvest(id);
        Object season=harvest.get("season_id");
        return Map.of("harvest",harvest,
            "activities",store.seasonTasks(season),
            "usage",store.seasonUsage(season),
            "products",store.harvestProducts(id));
    }
    @PreAuthorize("hasAnyAuthority('crops:read','sales:read')")
    public Object products(int page,int size,String requestId) {
        if(page<0 || page>100000 || size<1 || size>100) throw new IllegalArgumentException();
        return Map.of("items",store.products(size,page*size),
            "total",store.productCount(),"page",page,"size",size);
    }
    @PreAuthorize("hasAuthority('crops:update')")
    public Object productStorage(int id,int actorId,JsonNode b,String requestId) {
        fields(b,"storageLocation","status");String location=Input.text(b,"storageLocation",200),status=Input.text(b,"status",30);
        if(!Set.of("AVAILABLE","QUARANTINED","EXPIRED").contains(status)) throw new IllegalArgumentException();
        if(store.updateStorage(location,status,id)!=1) throw DomainException.missing();
        audit(actorId,"HARVESTED_PRODUCT",id,"STORAGE",requestId);
        return store.product(id);
    }
    @PreAuthorize("hasAuthority('crops:read')")
    public Object growth(String requestId) {
        return store.growth();
    }
    @PreAuthorize("hasAuthority('crops:update')")
    public Object growth(int actorId,JsonNode b,String requestId) {
        fields(b,"seasonId","stage","observedDate","notes","expectedYield");int season=id(b,"seasonId");
        String stage=Input.text(b,"stage",100),notes=Input.text(b,"notes",2000);LocalDate date=LocalDate.parse(Input.text(b,"observedDate",10));
        BigDecimal expected=new BigDecimal(Input.text(b,"expectedYield",24));
        if(date.isAfter(LocalDate.now(ZONE)) || expected.signum()<0 || expected.stripTrailingZeros().scale()>3 || expected.compareTo(new BigDecimal("9999999999999.999"))>0) throw new IllegalArgumentException();
        store.lockSeason(season);
        store.updateExpectedYield(expected,season);
        int id=store.recordGrowth(season,stage,date,notes,actorId);
        audit(actorId,"CROP_GROWTH",id,"CREATE",requestId);return Map.of("id",id);
    }
    @PreAuthorize("hasAuthority('warehouses:read')")
    public Object movements(String requestId) {
        return store.movements();
    }
    @PreAuthorize("hasAuthority('usage:create')")
    public Object consume(int actorId,JsonNode b,String requestId) {
        fields(b,"warehouseId","materialId","seasonId","type","quantity","applicationDate","notes","requestKey");
        int warehouse=id(b,"warehouseId"),material=id(b,"materialId"),season=id(b,"seasonId"),uid=actorId,farm=farm();
        UUID key=UUID.fromString(Input.text(b,"requestKey",36));
        BigDecimal quantity=new BigDecimal(Input.text(b,"quantity",24));
        var type=com.farmmanagement.backend.usage.UsageType.valueOf(Input.text(b,"type",20));
        LocalDate date=LocalDate.parse(Input.text(b,"applicationDate",10));
        String notes=b.path("notes").isNull()?null:b.path("notes").asText();
        if(notes!=null && notes.length()>1000) throw new IllegalArgumentException();
        String payload="USAGE:"+warehouse+":"+material+":"+season+":"+type+":"+quantity.stripTrailingZeros().toPlainString()+":"+date+":"+notes;
        store.lockStock();
        if(store.claimStockRequest(farm,key,payload,uid)==0) {
            var previous=store.stockRequest(farm,key);
            if(!payload.equals(previous.get("payload")) || !previous.get("created_by").equals(uid)) throw DomainException.conflict("Mã thao tác đã được dùng cho nội dung khác.");
            return Map.of("replayed",true);
        }
        store.requireWarehouse(warehouse);
        var record=usage.recordFromWarehouse(uid,season,material,type,quantity,date,notes,warehouse,requestId);
        store.usageRequest(key,record.id());
        return record;
    }
    @PreAuthorize("hasAuthority('inventory:manage')")
    public Object move(int actorId,JsonNode b,String requestId) {
        fields(b,"warehouseId","materialId","quantity","direction","reason","requestKey");
        int warehouse=id(b,"warehouseId"),material=id(b,"materialId"),uid=actorId,farm=farm();
        String direction=Input.text(b,"direction",3),reason=Input.text(b,"reason",150);
        if(!Set.of("IN","OUT").contains(direction)) throw new IllegalArgumentException();
        UUID key=UUID.fromString(Input.text(b,"requestKey",36));BigDecimal quantity=new BigDecimal(Input.text(b,"quantity",24));
        if(quantity.signum()<=0 || quantity.stripTrailingZeros().scale()>3 || quantity.compareTo(new BigDecimal("999999999.999"))>0) throw new IllegalArgumentException();
        String payload=warehouse+":"+material+":"+quantity.stripTrailingZeros().toPlainString()+":"+direction+":"+reason;
        store.lockStock();
        if(store.claimStockRequest(farm,key,payload,uid)==0) {
            var previous=store.stockRequest(farm,key);
            if(!payload.equals(previous.get("payload")) || !previous.get("created_by").equals(uid)) throw DomainException.conflict("Mã thao tác đã được dùng cho nội dung khác.");
            return Map.of("replayed",true);
        }
        store.requireWarehouse(warehouse);
        store.requireMaterial(material);
        store.stockReason(direction+": "+reason);
        store.ensureInventory(warehouse,material);
        var stock=store.lockInventory(warehouse,material);
        BigDecimal balance=(BigDecimal)stock.get("quantity"),next=direction.equals("IN")?balance.add(quantity):balance.subtract(quantity);
        if(next.signum()<0) throw DomainException.conflict("Số lượng xuất vượt tồn kho hiện tại.");
        store.updateInventory(next,stock.get("inventory_id"));
        audit(uid,"INVENTORY",((Number)stock.get("inventory_id")).intValue(),direction,requestId);
        return Map.of("balance",next,"replayed",false);
    }
}
