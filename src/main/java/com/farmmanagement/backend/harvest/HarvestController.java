package com.farmmanagement.backend.harvest;

import com.farmmanagement.backend.common.*;
import com.farmmanagement.backend.auth.AuthController;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
public class HarvestController {
    private final HarvestService service;
    public HarvestController(HarvestService service) { this.service=service; }
    private int actor(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }
    private int integer(JsonNode b,String field) {
        var v=b.get(field);if(v==null || !v.isIntegralNumber() || !v.canConvertToInt() || v.asInt()<=0) throw new IllegalArgumentException();return v.asInt();
    }
    private LocalDate date(String text) { try { return LocalDate.parse(text); } catch(RuntimeException e) { throw new IllegalArgumentException(); } }
    @GetMapping("/harvest-farms")
    public Object farms(@AuthenticationPrincipal Jwt jwt,HttpServletRequest r) { AuthController.noQuery(r);return Api.success(service.farms(actor(jwt)),r); }
    @GetMapping("/harvest-units")
    public Object units(HttpServletRequest r) { AuthController.noQuery(r);return Api.success(service.units(),r); }
    @GetMapping("/farms/{farmId}/harvest-seasons")
    public Object seasons(@AuthenticationPrincipal Jwt jwt,@PathVariable int farmId,HttpServletRequest r) { AuthController.noQuery(r);return Api.success(service.seasons(actor(jwt),farmId),r); }
    @PutMapping(value="/farms/{farmId}/harvest-seasons/{id}",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Object configure(@AuthenticationPrincipal Jwt jwt,@PathVariable int farmId,@PathVariable int id,@RequestBody JsonNode b,HttpServletRequest r) {
        AuthController.noQuery(r);Input.fields(b,"name","startDate","endDate","unitId");
        return Api.success(service.configure(actor(jwt),farmId,id,Input.text(b,"name",100),date(Input.text(b,"startDate",10)),date(Input.text(b,"endDate",10)),integer(b,"unitId"),Api.requestId(r)),r);
    }
    @PostMapping(value="/farms/{farmId}/harvests",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt,@PathVariable int farmId,@RequestBody JsonNode b,HttpServletRequest r) {
        AuthController.noQuery(r);
        if(!b.isObject() || !b.has("seasonId") || !b.has("harvestDate") || !b.has("quantity")) throw new IllegalArgumentException();
        b.propertyNames().forEach(n->{if(!Set.of("seasonId","harvestDate","quantity","quality").contains(n)) throw new IllegalArgumentException();});
        var q=b.get("quantity");if(!q.isNumber() && !q.isTextual()) throw new IllegalArgumentException();
        BigDecimal quantity;try { quantity=new BigDecimal(q.asText()); } catch(RuntimeException e) { throw new IllegalArgumentException(); }
        String quality=null;
        if(b.has("quality") && !b.get("quality").isNull()) { if(!b.get("quality").isTextual()) throw new IllegalArgumentException();quality=b.get("quality").asText().trim();if(quality.isEmpty()) quality=null; }
        var h=service.create(actor(jwt),farmId,integer(b,"seasonId"),date(Input.text(b,"harvestDate",10)),quantity,quality,Api.requestId(r));
        return ResponseEntity.created(URI.create("/api/v1/farms/"+farmId+"/harvests/"+h.id())).body(Api.success(h,r));
    }
    @GetMapping("/farms/{farmId}/harvests")
    public Object history(@AuthenticationPrincipal Jwt jwt,@PathVariable int farmId,
        @RequestParam(required=false) Integer seasonId,@RequestParam(required=false) String fromDate,@RequestParam(required=false) String toDate,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,HttpServletRequest r) {
        if(!Set.of("seasonId","fromDate","toDate","page","size").containsAll(r.getParameterMap().keySet()) || r.getParameterMap().values().stream().anyMatch(v->v.length!=1)) throw new IllegalArgumentException();
        return Api.success(service.history(actor(jwt),farmId,seasonId,fromDate==null?null:date(fromDate),toDate==null?null:date(toDate),page,size),r);
    }
    @GetMapping("/farms/{farmId}/harvests/{id}")
    public Object get(@AuthenticationPrincipal Jwt jwt,@PathVariable int farmId,@PathVariable int id,HttpServletRequest r) { AuthController.noQuery(r);return Api.success(service.get(actor(jwt),farmId,id),r); }
}
