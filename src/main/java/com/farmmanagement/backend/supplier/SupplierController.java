package com.farmmanagement.backend.supplier;

import com.farmmanagement.backend.auth.AuthController;
import com.farmmanagement.backend.common.Api;
import com.farmmanagement.backend.common.Input;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {
    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    private int actor(Jwt jwt) {
        return Integer.parseInt(jwt.getSubject());
    }

    private void checkId(int id) {
        if (id <= 0) throw new IllegalArgumentException();
    }

    private String optText(JsonNode body, String name, int max) {
        JsonNode v = body.get(name);
        if (v == null || v.isNull()) return null;
        if (!v.isTextual()) throw new IllegalArgumentException();
        String s = v.asText().trim();
        if (s.length() > max) throw new IllegalArgumentException();
        return s.isEmpty() ? null : s;
    }

    @GetMapping
    public Map<String, Object> listSuppliers(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(defaultValue = "") String search,
                                             @RequestParam(required = false) String status,
                                             HttpServletRequest request) {
        if (!Set.of("page", "size", "search", "status").containsAll(request.getParameterMap().keySet())
            || request.getParameterMap().values().stream().anyMatch(v -> v.length != 1)) {
            throw new IllegalArgumentException();
        }
        return Api.success(service.listSuppliers(page, size, search.trim(), status), request);
    }

    @GetMapping("/{id}")
    public Map<String, Object> getSupplier(@PathVariable int id, HttpServletRequest request) {
        checkId(id);
        AuthController.noQuery(request);
        return Api.success(service.getSupplier(id), request);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSupplier(@AuthenticationPrincipal Jwt jwt,
                                            @RequestBody JsonNode body,
                                            HttpServletRequest request) {
        AuthController.noQuery(request);
        String name = Input.text(body, "name", 150);
        String contactName = optText(body, "contactName", 100);
        String email = optText(body, "email", 254);
        String phone = optText(body, "phone", 20);
        String address = optText(body, "address", 255);
        String taxCode = optText(body, "taxCode", 50);
        String notes = optText(body, "notes", 1000);

        var created = service.createSupplier(actor(jwt), name, contactName, email, phone,
            address, taxCode, notes, Api.requestId(request));
        return ResponseEntity.created(URI.create("/api/v1/suppliers/" + created.id()))
            .body(Api.success(created, request));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> updateSupplier(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable int id,
                                              @RequestBody JsonNode body,
                                              HttpServletRequest request) {
        checkId(id);
        AuthController.noQuery(request);
        String name = Input.text(body, "name", 150);
        String contactName = optText(body, "contactName", 100);
        String email = optText(body, "email", 254);
        String phone = optText(body, "phone", 20);
        String address = optText(body, "address", 255);
        String taxCode = optText(body, "taxCode", 50);
        String notes = optText(body, "notes", 1000);

        var updated = service.updateSupplier(actor(jwt), id, name, contactName, email, phone,
            address, taxCode, notes, Api.requestId(request));
        return Api.success(updated, request);
    }

    @PutMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> changeStatus(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable int id,
                                            @RequestBody JsonNode body,
                                            HttpServletRequest request) {
        checkId(id);
        AuthController.noQuery(request);
        String status = Input.text(body, "status", 10).toUpperCase(Locale.ROOT);
        var updated = service.changeSupplierStatus(actor(jwt), id, status, Api.requestId(request));
        return Api.success(updated, request);
    }
}
