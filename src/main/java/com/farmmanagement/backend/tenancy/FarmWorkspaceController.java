package com.farmmanagement.backend.tenancy;

import com.farmmanagement.backend.auth.AuthController;
import com.farmmanagement.backend.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
public class FarmWorkspaceController {
    private final FarmWorkspaceService service;
    private final FarmWorkspaceStore store;
    public FarmWorkspaceController(FarmWorkspaceService service,FarmWorkspaceStore store) { this.service=service;this.store=store; }
    @PostMapping(value="/auth/register",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> register(@RequestBody JsonNode body,HttpServletRequest request) {
        AuthController.noQuery(request);
        Input.fields(body,"username","password","fullName","email","farmName","address","areaHa");
        return ResponseEntity.status(201).body(Api.success(service.register(Input.text(body,"username",50),Input.password(body,"password"),
            Input.text(body,"fullName",100),Input.email(body),Input.text(body,"farmName",100),Input.text(body,"address",255),Input.text(body,"areaHa",20),Api.requestId(request)),request));
    }
    @GetMapping("/workspace")
    public Map<String,Object> workspace(@AuthenticationPrincipal Jwt jwt,HttpServletRequest request) {
        AuthController.noQuery(request); return Api.success(store.memberships(Integer.parseInt(jwt.getSubject())),request);
    }
    @GetMapping("/workspace/{farm}/staff")
    public Map<String,Object> staff(@AuthenticationPrincipal Jwt jwt,@PathVariable int farm,HttpServletRequest request) {
        AuthController.noQuery(request); return Api.success(service.staff(Integer.parseInt(jwt.getSubject()),farm),request);
    }
    @PostMapping(value="/workspace/{farm}/staff",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> add(@AuthenticationPrincipal Jwt jwt,@PathVariable int farm,@RequestBody JsonNode body,HttpServletRequest request) {
        AuthController.noQuery(request); Input.fields(body,"username","password","fullName","email","job");
        return ResponseEntity.status(201).body(Api.success(service.add(Integer.parseInt(jwt.getSubject()),farm,Input.text(body,"username",50),
            Input.password(body,"password"),Input.text(body,"fullName",100),Input.email(body),Input.text(body,"job",50),Api.requestId(request)),request));
    }
    @PutMapping(value="/workspace/{farm}/staff/{target}",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> change(@AuthenticationPrincipal Jwt jwt,@PathVariable int farm,@PathVariable int target,@RequestBody JsonNode body,HttpServletRequest request) {
        AuthController.noQuery(request); Input.fields(body,"job","status");
        service.change(Integer.parseInt(jwt.getSubject()),farm,target,Input.text(body,"job",50),Input.text(body,"status",10),Api.requestId(request));
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/workspace/{farm}/complete")
    public ResponseEntity<Void> complete(@AuthenticationPrincipal Jwt jwt,@PathVariable int farm,HttpServletRequest request,@RequestBody(required=false) String body) {
        AuthController.noQuery(request); if(body!=null && !body.isBlank()) throw new IllegalArgumentException();
        service.complete(Integer.parseInt(jwt.getSubject()),farm,Api.requestId(request)); return ResponseEntity.noContent().build();
    }
}
