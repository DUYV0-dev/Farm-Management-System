package com.farmmanagement.backend.portal;

import com.farmmanagement.backend.auth.AuthController;
import com.farmmanagement.backend.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/account")
public class AccountPortalController {
    private final AccountPortalService service;
    public AccountPortalController(AccountPortalService service) {this.service=service;}
    private int uid(Jwt jwt) {return Integer.parseInt(jwt.getSubject());}
    static String optional(JsonNode b,String key,int max) {
        var v=b.get(key);if(v==null || !v.isTextual() || v.asText().trim().length()>max) throw new IllegalArgumentException();return v.asText().trim();
    }
    static int positive(JsonNode b,String key) {
        var v=b.get(key);if(v==null || !v.isIntegralNumber() || !v.canConvertToInt() || v.asInt()<=0) throw new IllegalArgumentException();return v.asInt();
    }
    @GetMapping public Map<String,Object> overview(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,HttpServletRequest r) {
        if(r.getParameterMap().keySet().stream().anyMatch(k->!Set.of("from","to").contains(k))) throw new IllegalArgumentException();
        return Api.success(service.overview(uid(jwt),from==null?LocalDate.now().withDayOfYear(1):from,to==null?LocalDate.now():to),r);
    }
    @PutMapping("/profile") public Map<String,Object> profile(@AuthenticationPrincipal Jwt jwt,@RequestBody JsonNode b,HttpServletRequest r) {
        AuthController.noQuery(r);Input.fields(b,"fullName","email","phone","bio");return Api.success(service.profile(uid(jwt),Input.text(b,"fullName",100),Input.email(b),optional(b,"phone",20),optional(b,"bio",500),Api.requestId(r)),r);
    }
    @PostMapping("/farms") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Map<String,Object> farm(@AuthenticationPrincipal Jwt jwt,@RequestBody JsonNode b,HttpServletRequest r) {
        AuthController.noQuery(r);Input.fields(b,"name","address","area");return Api.success(service.createFarm(uid(jwt),Input.text(b,"name",100),Input.text(b,"address",255),new BigDecimal(Input.text(b,"area",20)),Api.requestId(r)),r);
    }
    @PutMapping("/farms/{farm}/listings/{product}") public Map<String,Object> publish(@AuthenticationPrincipal Jwt jwt,@PathVariable int farm,@PathVariable int product,@RequestBody JsonNode b,HttpServletRequest r) {
        AuthController.noQuery(r);Input.fields(b,"name","description","price","published","version");
        if(!b.path("published").isBoolean() || !b.path("version").isIntegralNumber() || !b.path("version").canConvertToInt()) throw new IllegalArgumentException();
        return Api.success(service.publish(uid(jwt),farm,product,Input.text(b,"name",150),optional(b,"description",2000),new BigDecimal(Input.text(b,"price",20)),b.path("published").asBoolean(),b.path("version").asInt(),Api.requestId(r)),r);
    }
    @GetMapping("/farms/{farm}/orders/{id}") public Map<String,Object> order(@AuthenticationPrincipal Jwt jwt,@PathVariable int farm,@PathVariable UUID id,HttpServletRequest r) {
        AuthController.noQuery(r);return Api.success(service.order(uid(jwt),farm,id),r);
    }
    @PostMapping("/farms/{farm}/orders/{id}/decision") public Map<String,Object> decide(@AuthenticationPrincipal Jwt jwt,@PathVariable int farm,@PathVariable UUID id,@RequestBody JsonNode b,HttpServletRequest r) {
        AuthController.noQuery(r);Input.fields(b,"accept");if(!b.path("accept").isBoolean()) throw new IllegalArgumentException();return Api.success(service.decide(uid(jwt),farm,id,b.path("accept").asBoolean(),Api.requestId(r)),r);
    }
}
