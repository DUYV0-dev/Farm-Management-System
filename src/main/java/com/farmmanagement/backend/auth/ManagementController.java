package com.farmmanagement.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
public class ManagementController {
    private final ManagementService service;
    public ManagementController(ManagementService service) { this.service=service; }
    private int actor(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }
    private void id(int id) { if (id<=0) throw new IllegalArgumentException(); }
    private String roleName(JsonNode body) {
        String name=Input.text(body,"name",50);
        if (!name.matches("[A-Z][A-Z0-9_]{1,49}")) throw new IllegalArgumentException();
        return name;
    }
    @GetMapping("/users")
    public Map<String,Object> users(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
                                    @RequestParam(defaultValue="") String search,HttpServletRequest request) {
        if (!Set.of("page","size","search").containsAll(request.getParameterMap().keySet())
                || request.getParameterMap().values().stream().anyMatch(v->v.length!=1)) throw new IllegalArgumentException();
        return Api.success(service.list(page,size,search.trim()),request);
    }
    @GetMapping("/users/{id}")
    public Map<String,Object> user(@PathVariable int id,HttpServletRequest request) {
        id(id); AuthController.noQuery(request); return Api.success(service.get(id),request);
    }
    @PostMapping(value="/users",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt,@RequestBody JsonNode body,HttpServletRequest request) {
        AuthController.noQuery(request); Input.fields(body,"username","password","fullName","email");
        String username=Input.text(body,"username",50);
        if (!username.matches("[a-z0-9._-]{3,50}")) throw new IllegalArgumentException();
        var user=service.create(actor(jwt),username,Input.password(body,"password"),Input.text(body,"fullName",100),Input.email(body),Api.requestId(request));
        return ResponseEntity.created(URI.create("/api/v1/users/"+user.get("id"))).body(Api.success(user,request));
    }
    @PutMapping(value="/users/{id}",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Object> update(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@RequestBody JsonNode body,HttpServletRequest request) {
        id(id); AuthController.noQuery(request); Input.fields(body,"fullName","email");
        return Api.success(service.update(actor(jwt),id,Input.text(body,"fullName",100),Input.email(body),Api.requestId(request)),request);
    }
    @PutMapping(value="/users/{id}/status",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Object> status(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@RequestBody JsonNode body,HttpServletRequest request) {
        id(id); AuthController.noQuery(request); Input.fields(body,"status");
        return Api.success(service.status(actor(jwt),id,Input.text(body,"status",8),Api.requestId(request)),request);
    }
    @PutMapping(value="/users/{id}/roles",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Object> roles(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@RequestBody JsonNode body,HttpServletRequest request) {
        id(id); AuthController.noQuery(request); Input.fields(body,"roleIds");
        return Api.success(service.assign(actor(jwt),id,Input.ids(body,"roleIds"),Api.requestId(request)),request);
    }
    @PutMapping(value="/users/{id}/password",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> password(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@RequestBody JsonNode body,HttpServletRequest request) {
        id(id); AuthController.noQuery(request); Input.fields(body,"newPassword");
        service.resetPassword(actor(jwt),id,Input.password(body,"newPassword"),Api.requestId(request));
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/roles")
    public Map<String,Object> roles(HttpServletRequest request) {
        AuthController.noQuery(request); return Api.success(service.roles(),request);
    }
    @GetMapping("/permissions")
    public Map<String,Object> permissions(HttpServletRequest request) {
        AuthController.noQuery(request); return Api.success(service.permissions(),request);
    }
    @GetMapping("/roles/{id}")
    public Map<String,Object> role(@PathVariable int id,HttpServletRequest request) {
        id(id); AuthController.noQuery(request); return Api.success(service.getRole(id),request);
    }
    @PostMapping(value="/roles",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createRole(@AuthenticationPrincipal Jwt jwt,@RequestBody JsonNode body,HttpServletRequest request) {
        AuthController.noQuery(request); Input.fields(body,"name","description");
        var role=service.createRole(actor(jwt),roleName(body),Input.text(body,"description",255),Api.requestId(request));
        return ResponseEntity.created(URI.create("/api/v1/roles/"+role.id())).body(Api.success(role,request));
    }
    @PutMapping(value="/roles/{id}",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Object> updateRole(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@RequestBody JsonNode body,HttpServletRequest request) {
        id(id); AuthController.noQuery(request); Input.fields(body,"name","description");
        return Api.success(service.updateRole(actor(jwt),id,roleName(body),Input.text(body,"description",255),Api.requestId(request)),request);
    }
    @PutMapping(value="/roles/{id}/permissions",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Object> updatePermissions(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@RequestBody JsonNode body,HttpServletRequest request) {
        id(id); AuthController.noQuery(request); Input.fields(body,"permissions");
        return Api.success(service.permissions(actor(jwt),id,Input.codes(body),Api.requestId(request)),request);
    }
}
