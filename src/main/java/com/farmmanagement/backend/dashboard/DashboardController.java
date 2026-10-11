package com.farmmanagement.backend.dashboard;

import static com.farmmanagement.backend.dashboard.ReportModels.*;
import com.farmmanagement.backend.common.*;
import com.farmmanagement.backend.auth.AuthController;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
public class DashboardController {
    private final DashboardService service;
    private final ReportExportService export;
    public DashboardController(DashboardService service,ReportExportService export) { this.service=service;this.export=export; }
    private int actor(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }
    private static DomainException invalid() { return new DomainException(400,"INVALID_FILTER","Bộ lọc không hợp lệ hoặc không được hỗ trợ."); }
    private LocalDate date(String s) { try { if(s==null || !s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid();var d=LocalDate.parse(s);if(d.getYear()<1) throw invalid();return d; } catch(RuntimeException e) { throw invalid(); } }
    private Integer id(String s) { if(s==null) return null;try { int v=Integer.parseInt(s);if(v<=0) throw invalid();return v; } catch(NumberFormatException e) { throw invalid(); } }
    private void query(HttpServletRequest r,String... keys) {
        if(!Set.of(keys).containsAll(r.getParameterMap().keySet()) || r.getParameterMap().values().stream().anyMatch(v->v.length!=1)) throw invalid();
    }
    private Filters filters(HttpServletRequest r) {
        query(r,"fromDate","toDate","plotId","cropId","seasonId");
        return new Filters(date(r.getParameter("fromDate")),date(r.getParameter("toDate")),id(r.getParameter("plotId")),id(r.getParameter("cropId")),id(r.getParameter("seasonId")));
    }
    @GetMapping("/dashboard-farms")
    public Object farms(@AuthenticationPrincipal Jwt jwt,HttpServletRequest r) { AuthController.noQuery(r);return Api.success(service.farms(actor(jwt)),r); }
    @GetMapping("/farms/{farmId}/dashboard/production")
    public Object production(@AuthenticationPrincipal Jwt jwt,@PathVariable int farmId,HttpServletRequest r) { return Api.success(service.production(actor(jwt),farmId,filters(r)),r); }
    @GetMapping("/farms/{farmId}/statistics/seasons")
    public Object seasons(@AuthenticationPrincipal Jwt jwt,@PathVariable int farmId,HttpServletRequest r) { return Api.success(service.seasons(actor(jwt),farmId,filters(r)),r); }
    @GetMapping("/farms/{farmId}/statistics/tasks")
    public Object tasks(@AuthenticationPrincipal Jwt jwt,@PathVariable int farmId,HttpServletRequest r) { return Api.success(service.tasks(actor(jwt),farmId,filters(r)),r); }
    @GetMapping("/farms/{farmId}/statistics/inventory")
    public Object inventory(@AuthenticationPrincipal Jwt jwt,@PathVariable int farmId,HttpServletRequest r) {
        query(r,"fromDate","toDate","warehouseId","materialId");
        return Api.success(service.inventory(actor(jwt),farmId,date(r.getParameter("fromDate")),date(r.getParameter("toDate")),id(r.getParameter("warehouseId")),id(r.getParameter("materialId"))),r);
    }
    @GetMapping("/farms/{farmId}/statistics/expenses")
    public Object expenses(@AuthenticationPrincipal Jwt jwt,@PathVariable int farmId,HttpServletRequest r) {
        query(r,"fromDate","toDate","warehouseId","materialId");
        return Api.success(service.expenses(actor(jwt),farmId,date(r.getParameter("fromDate")),date(r.getParameter("toDate")),id(r.getParameter("warehouseId")),id(r.getParameter("materialId"))),r);
    }
    @PostMapping(value="/farms/{farmId}/reports/production",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt,@PathVariable int farmId,@RequestBody JsonNode b,HttpServletRequest r) {
        AuthController.noQuery(r);
        if(!b.isObject() || !b.has("fromDate") || !b.has("toDate")) throw invalid();
        b.propertyNames().forEach(n->{if(!Set.of("fromDate","toDate","plotId","cropId","seasonId").contains(n)) throw invalid();});
        for(String key:List.of("plotId","cropId","seasonId")) if(b.has(key) && !b.get(key).isNull() && (!b.get(key).isIntegralNumber() || !b.get(key).canConvertToInt())) throw invalid();
        var f=new Filters(date(Input.text(b,"fromDate",10)),date(Input.text(b,"toDate",10)),b.hasNonNull("plotId")?b.get("plotId").asInt():null,b.hasNonNull("cropId")?b.get("cropId").asInt():null,b.hasNonNull("seasonId")?b.get("seasonId").asInt():null);
        var s=service.create(actor(jwt),farmId,f);
        return ResponseEntity.created(URI.create("/api/v1/farms/"+farmId+"/reports/"+s.reportId())).body(Api.success(s,r));
    }
    @GetMapping("/farms/{farmId}/reports/{reportId}")
    public Object report(@AuthenticationPrincipal Jwt jwt,@PathVariable int farmId,@PathVariable UUID reportId,HttpServletRequest r) {
        query(r,"page","size");int page=r.getParameter("page")==null?0:parse(r.getParameter("page")),size=r.getParameter("size")==null?20:parse(r.getParameter("size"));
        if(page<0 || page>100000 || size<1 || size>50) throw invalid();
        var s=service.snapshot(actor(jwt),farmId,reportId);int start=Math.min(page*size,s.details().size()),end=Math.min(start+size,s.details().size());
        var data=new LinkedHashMap<String,Object>();data.put("reportId",s.reportId());data.put("farmName",s.farmName());data.put("generatedAt",s.generatedAt());data.put("expiresAt",s.expiresAt());data.put("appliedFilters",s.appliedFilters());data.put("metricVersion",s.metricVersion());data.put("timezone",s.timezone());data.put("summary",s.summary());data.put("items",s.details().subList(start,end));data.put("total",s.details().size());data.put("page",page);data.put("size",size);
        return Api.success(data,r);
    }
    private int parse(String s) { try { return Integer.parseInt(s); } catch(RuntimeException e) { throw invalid(); } }
    @GetMapping("/farms/{farmId}/reports/{reportId}/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt,@PathVariable int farmId,@PathVariable UUID reportId,@RequestParam String format,HttpServletRequest r) {
        query(r,"format");if(!Set.of("pdf","xlsx").contains(format)) throw invalid();
        byte[] data=export.export(actor(jwt),farmId,reportId,format);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=production-"+reportId+"."+format).header(HttpHeaders.CACHE_CONTROL,"no-store").contentType(MediaType.parseMediaType(format.equals("pdf")?"application/pdf":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(data);
    }
}
