package com.farmmanagement.backend.dashboard;

import static com.farmmanagement.backend.dashboard.ReportModels.*;
import com.farmmanagement.backend.common.DomainException;
import com.farmmanagement.backend.harvest.HarvestStore;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class DashboardService {
    public static final int MAX_ROWS=9999;
    private final DashboardStore store;
    private final HarvestStore farms;
    private final ObjectMapper mapper;
    public DashboardService(DashboardStore store,HarvestStore farms,ObjectMapper mapper) { this.store=store;this.farms=farms;this.mapper=mapper; }
    private void scope(int actor,int farm) { if(farm<=0 || !farms.allowed(actor,farm,false)) throw DomainException.missing(); }
    private void filters(int actor,int farm,Filters f) {
        scope(actor,farm);
        if(f.plotId()!=null && !store.filterBelongs(farm,"plot",f.plotId()) || f.cropId()!=null && !store.filterBelongs(farm,"crop",f.cropId()) || f.seasonId()!=null && !store.filterBelongs(farm,"season",f.seasonId())) throw DomainException.missing();
    }
    private Map<String,Object> metadata(int farm,Object filters,String status) {
        var m=new LinkedHashMap<String,Object>();m.put("farmId",farm);m.put("generatedAt",Instant.now().toString());m.put("timezone",ZONE);m.put("metricVersion",VERSION);m.put("appliedFilters",filters);m.put("sourceStatus",status);return m;
    }
    @PreAuthorize("hasAuthority('dashboard:read')")
    public List<Map<String,Object>> farms(int actor) { return farms.farms(actor); }
    @PreAuthorize("hasAuthority('dashboard:read') and hasAuthority('harvests:read')")
    public Map<String,Object> production(int actor,int farm,Filters f) {
        filters(actor,farm,f);var m=metadata(farm,f,"AVAILABLE");
        m.put("unitGroups",store.production(farm,f,"unit"));m.put("byMonth",store.production(farm,f,"month"));m.put("byCrop",store.production(farm,f,"crop"));m.put("rule","Thu hoạch CONFIRMED trong kỳ; không cộng các đơn vị khác nhau.");return m;
    }
    @PreAuthorize("hasAuthority('dashboard:read') and hasAuthority('crops:read') and hasAuthority('harvests:read')")
    public Map<String,Object> seasons(int actor,int farm,Filters f) {
        filters(actor,farm,f);var rows=store.seasons(farm,f,MAX_ROWS+1);limit(rows.size());var m=metadata(farm,f,"AVAILABLE");
        var counts=new LinkedHashMap<String,Long>();for(String status:List.of("PLANNED","IN_PROGRESS","COMPLETED")) counts.put(status,rows.stream().filter(r->status.equals(r.status())).count());
        m.put("total",rows.size());m.put("byStatus",counts);m.put("items",rows);m.put("unconfiguredSeasons",store.unconfiguredSeasons(farm));m.put("rule","Vụ xác nhận giao kỳ; trạng thái hiện tại. Sản lượng lũy kế tính đến toDate, tỷ lệ có thể vượt 100%. Vụ chưa có ngày không nằm trong số đếm giao kỳ.");return m;
    }
    @PreAuthorize("hasAuthority('dashboard:read')")
    public Map<String,Object> tasks(int actor,int farm,Filters f) {
        filters(actor,farm,f);var m=metadata(farm,f,"UNAVAILABLE");m.put("reason","Chưa có nguồn FARMING_TASK xác nhận, liên kết mùa vụ và trang trại. Hoạt động demo trong bộ nhớ không được dùng cho KPI.");return m;
    }
    @PreAuthorize("hasAuthority('dashboard:read') and hasAuthority('warehouses:read')")
    public Map<String,Object> inventory(int actor,int farm,LocalDate from,LocalDate to,Integer warehouse,Integer material) {
        scope(actor,farm);new Filters(from,to,null,null,null);
        if(warehouse!=null && (warehouse<=0 || !store.warehouseBelongs(farm,warehouse))) throw DomainException.missing();
        if(material!=null && material<=0) throw new IllegalArgumentException();
        var f=new LinkedHashMap<String,Object>();f.put("fromDate",from);f.put("toDate",to);f.put("warehouseId",warehouse);f.put("materialId",material);
        var m=metadata(farm,f,"PARTIAL");m.put("currentItems",store.inventory(farm,warehouse,material));m.put("historicalStatus","UNAVAILABLE");m.put("lowStockStatus","UNAVAILABLE");m.put("reason","Chỉ có tồn kho hiện tại; chưa có sổ giao dịch IN/OUT xác nhận và ngưỡng tồn thấp. Không suy tồn đầu/cuối kỳ từ số dư hiện tại.");return m;
    }
    @PreAuthorize("hasAuthority('expenses:read')")
    public Map<String,Object> expenses(int actor,int farm,LocalDate from,LocalDate to,Integer warehouse,Integer material) {
        scope(actor,farm);new Filters(from,to,null,null,null);
        if(warehouse!=null && (warehouse<=0 || !store.warehouseBelongs(farm,warehouse))) throw DomainException.missing();
        if(material!=null && material<=0) throw new IllegalArgumentException();
        var f=new LinkedHashMap<String,Object>();f.put("fromDate",from);f.put("toDate",to);f.put("warehouseId",warehouse);f.put("materialId",material);
        var m=metadata(farm,f,"UNAVAILABLE");m.put("currency","VND");m.put("purchaseValue",null);m.put("productionExpense",null);m.put("reason","Chưa có đơn mua vật tư xác nhận. Giá trị đơn mua không đồng nghĩa tổng chi phí sản xuất hoặc tiền đã thanh toán.");return m;
    }
    static void limit(int rows) { if(rows>MAX_ROWS) throw new DomainException(413,"REPORT_TOO_LARGE","Dữ liệu vượt 9.999 dòng; hãy thu hẹp khoảng ngày hoặc bộ lọc."); }
    @PreAuthorize("hasAuthority('reports:read') and hasAuthority('harvests:read') and hasAuthority('crops:read')")
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public Snapshot create(int actor,int farm,Filters f) {
        filters(actor,farm,f);var details=store.details(farm,f,MAX_ROWS+1);var summary=store.reportSeasons(farm,f,MAX_ROWS+1);limit(details.size());limit(summary.size());
        var now=Instant.now();var snapshot=new Snapshot(UUID.randomUUID(),farm,store.farmName(farm),actor,now,now.plus(Duration.ofHours(24)),f,VERSION,ZONE,details,summary);
        store.save(snapshot,mapper.writeValueAsString(snapshot));return snapshot;
    }
    @PreAuthorize("hasAuthority('reports:read') and hasAuthority('harvests:read') and hasAuthority('crops:read')")
    public Snapshot snapshot(int actor,int farm,UUID id) {
        scope(actor,farm);var s=mapper.readValue(store.snapshot(actor,farm,id).orElseThrow(DomainException::missing),Snapshot.class);
        if(!s.expiresAt().isAfter(Instant.now())) throw new DomainException(410,"REPORT_EXPIRED","Báo cáo đã hết hạn; hãy tạo báo cáo mới.");return s;
    }
}
