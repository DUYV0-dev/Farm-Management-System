package com.farmmanagement.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service=service; }
    static void noQuery(HttpServletRequest request) {
        if(request.getQueryString()!=null) throw new IllegalArgumentException("Query parameters are not supported");
    }
    @PostMapping(value="/login",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Object> login(@RequestBody JsonNode body,HttpServletRequest request) {
        noQuery(request);
        if(!body.isObject() || body.size()!=2 || !body.has("username") || !body.has("password")
            || !body.get("username").isTextual() || !body.get("password").isTextual()) throw new IllegalArgumentException();
        String username=body.get("username").asText().trim(), password=body.get("password").asText();
        if(!username.matches("[a-z0-9._-]{3,50}") || password.isEmpty() || password.length()>128) throw new IllegalArgumentException();
        return Api.success(service.login(username,password),request);
    }
    @GetMapping("/me")
    public Map<String,Object> me(@AuthenticationPrincipal Jwt jwt,HttpServletRequest request) {
        noQuery(request); return Api.success(service.me(Integer.parseInt(jwt.getSubject())),request);
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt,HttpServletRequest request,@RequestBody(required=false) String body) {
        noQuery(request); if(body!=null && !body.isBlank()) throw new IllegalArgumentException();
        service.logout(jwt);return ResponseEntity.noContent().build();
    }
    @PostMapping(value="/password",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> password(@AuthenticationPrincipal Jwt jwt,@RequestBody JsonNode body,HttpServletRequest request) {
        noQuery(request); Input.fields(body,"currentPassword","newPassword");
        service.changePassword(Integer.parseInt(jwt.getSubject()),Input.password(body,"currentPassword"),Input.password(body,"newPassword"));
        return ResponseEntity.noContent().build();
    }
}
