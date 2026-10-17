package com.farmmanagement.backend.inventory;

import com.farmmanagement.backend.common.Input;
import com.farmmanagement.backend.auth.AuthController;
import com.farmmanagement.backend.common.Api;
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
public class InventoryController {
    private final InventoryService service;
    public InventoryController(InventoryService service) { this.service = service; }

    private int actor(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }
    private void id(int id) { if (id <= 0) throw new IllegalArgumentException(); }

    private String optText(JsonNode body, String name, int max) {
        JsonNode v = body.get(name);
        if (v == null || v.isNull()) return null;
        if (!v.isTextual()) throw new IllegalArgumentException();
        String s = v.asText().trim();
        if (s.length() > max) throw new IllegalArgumentException();
        return s.isEmpty() ? null : s;
    }

    private int reqInt(JsonNode body, String name) {
        JsonNode v = body.get(name);
        if (v == null || !v.isIntegralNumber() || !v.canConvertToInt()) throw new IllegalArgumentException();
        int val = v.asInt();
        if (val <= 0) throw new IllegalArgumentException();
        return val;
    }

    // ─── Material Categories ─────────────────────────────────────────────────
    @GetMapping("/material-categories")
    public Map<String, Object> categories(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(defaultValue = "") String search,
                                          HttpServletRequest request) {
        if (!Set.of("page", "size", "search").containsAll(request.getParameterMap().keySet())
                || request.getParameterMap().values().stream().anyMatch(v -> v.length != 1))
            throw new IllegalArgumentException();
        return Api.success(service.listCategories(page, size, search.trim()), request);
    }

    @GetMapping("/material-categories/{id}")
    public Map<String, Object> category(@PathVariable int id, HttpServletRequest request) {
        id(id); AuthController.noQuery(request);
        return Api.success(service.getCategory(id), request);
    }

    @PostMapping(value = "/material-categories", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createCategory(@AuthenticationPrincipal Jwt jwt,
                                            @RequestBody JsonNode body, HttpServletRequest request) {
        AuthController.noQuery(request);
        Input.fields(body, "name", "description");
        String name = Input.text(body, "name", 100);
        var category = service.createCategory(actor(jwt), name, optText(body, "description", 1000), Api.requestId(request));
        return ResponseEntity.created(URI.create("/api/v1/material-categories/" + category.id())).body(Api.success(category, request));
    }

    @PutMapping(value = "/material-categories/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> updateCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                                              @RequestBody JsonNode body, HttpServletRequest request) {
        id(id); AuthController.noQuery(request);
        Input.fields(body, "name", "description");
        String name = Input.text(body, "name", 100);
        return Api.success(service.updateCategory(actor(jwt), id, name, optText(body, "description", 1000), Api.requestId(request)), request);
    }

    @PutMapping(value = "/material-categories/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> categoryStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                                              @RequestBody JsonNode body, HttpServletRequest request) {
        id(id); AuthController.noQuery(request); Input.fields(body, "status");
        return Api.success(service.categoryStatus(actor(jwt), id, Input.text(body, "status", 10), Api.requestId(request)), request);
    }

    // ─── Materials ───────────────────────────────────────────────────────────
    @GetMapping("/materials")
    public Map<String, Object> materials(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         @RequestParam(defaultValue = "") String search,
                                         @RequestParam(required = false) Integer categoryId,
                                         HttpServletRequest request) {
        if (!Set.of("page", "size", "search", "categoryId").containsAll(request.getParameterMap().keySet())
                || request.getParameterMap().values().stream().anyMatch(v -> v.length != 1))
            throw new IllegalArgumentException();
        return Api.success(service.listMaterials(page, size, search.trim(), categoryId), request);
    }

    @GetMapping("/materials/{id}")
    public Map<String, Object> material(@PathVariable int id, HttpServletRequest request) {
        id(id); AuthController.noQuery(request);
        return Api.success(service.getMaterial(id), request);
    }

    @PostMapping(value = "/materials", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createMaterial(@AuthenticationPrincipal Jwt jwt,
                                            @RequestBody JsonNode body, HttpServletRequest request) {
        AuthController.noQuery(request);
        Input.fields(body, "categoryId", "name", "unit", "manufacturer", "description");
        int categoryId = reqInt(body, "categoryId");
        String name = Input.text(body, "name", 150);
        String unit = Input.text(body, "unit", 50);
        var material = service.createMaterial(actor(jwt), categoryId, name, unit, optText(body, "manufacturer", 150), optText(body, "description", 1000), Api.requestId(request));
        return ResponseEntity.created(URI.create("/api/v1/materials/" + material.id())).body(Api.success(material, request));
    }

    @PutMapping(value = "/materials/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> updateMaterial(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                                              @RequestBody JsonNode body, HttpServletRequest request) {
        id(id); AuthController.noQuery(request);
        Input.fields(body, "name", "unit", "manufacturer", "description");
        String name = Input.text(body, "name", 150);
        String unit = Input.text(body, "unit", 50);
        return Api.success(service.updateMaterial(actor(jwt), id, name, unit, optText(body, "manufacturer", 150), optText(body, "description", 1000), Api.requestId(request)), request);
    }

    @PutMapping(value = "/materials/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> materialStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                                              @RequestBody JsonNode body, HttpServletRequest request) {
        id(id); AuthController.noQuery(request); Input.fields(body, "status");
        return Api.success(service.materialStatus(actor(jwt), id, Input.text(body, "status", 10), Api.requestId(request)), request);
    }

    // ─── Warehouses ──────────────────────────────────────────────────────────
    @GetMapping("/warehouses")
    public Map<String, Object> warehouses(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(defaultValue = "") String search,
                                          @RequestParam(required = false) Integer farmId,
                                          HttpServletRequest request) {
        if (!Set.of("page", "size", "search", "farmId").containsAll(request.getParameterMap().keySet())
                || request.getParameterMap().values().stream().anyMatch(v -> v.length != 1))
            throw new IllegalArgumentException();
        return Api.success(service.listWarehouses(page, size, search.trim(), farmId), request);
    }

    @GetMapping("/warehouses/{id}")
    public Map<String, Object> warehouse(@PathVariable int id, HttpServletRequest request) {
        id(id); AuthController.noQuery(request);
        return Api.success(service.getWarehouse(id), request);
    }

    @PostMapping(value = "/warehouses", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createWarehouse(@AuthenticationPrincipal Jwt jwt,
                                             @RequestBody JsonNode body, HttpServletRequest request) {
        AuthController.noQuery(request);
        Input.fields(body, "farmId", "name", "location", "capacityText");
        int farmId = reqInt(body, "farmId");
        String name = Input.text(body, "name", 100);
        var warehouse = service.createWarehouse(actor(jwt), farmId, name, optText(body, "location", 255), optText(body, "capacityText", 100), Api.requestId(request));
        return ResponseEntity.created(URI.create("/api/v1/warehouses/" + warehouse.id())).body(Api.success(warehouse, request));
    }

    @PutMapping(value = "/warehouses/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> updateWarehouse(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                                               @RequestBody JsonNode body, HttpServletRequest request) {
        id(id); AuthController.noQuery(request);
        Input.fields(body, "name", "location", "capacityText");
        String name = Input.text(body, "name", 100);
        return Api.success(service.updateWarehouse(actor(jwt), id, name, optText(body, "location", 255), optText(body, "capacityText", 100), Api.requestId(request)), request);
    }

    @PutMapping(value = "/warehouses/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> warehouseStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                                               @RequestBody JsonNode body, HttpServletRequest request) {
        id(id); AuthController.noQuery(request); Input.fields(body, "status");
        return Api.success(service.warehouseStatus(actor(jwt), id, Input.text(body, "status", 10), Api.requestId(request)), request);
    }

    // ─── Warehouse Inventory ─────────────────────────────────────────────────
    @GetMapping("/inventory")
    public Map<String, Object> inventory(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         @RequestParam(defaultValue = "") String search,
                                         @RequestParam(required = false) Integer warehouseId,
                                         HttpServletRequest request) {
        if (!Set.of("page", "size", "search", "warehouseId").containsAll(request.getParameterMap().keySet())
                || request.getParameterMap().values().stream().anyMatch(v -> v.length != 1))
            throw new IllegalArgumentException();
        return Api.success(service.listInventory(page, size, search.trim(), warehouseId), request);
    }

    @GetMapping("/inventory/{id}")
    public Map<String, Object> inventoryItem(@PathVariable int id, HttpServletRequest request) {
        id(id); AuthController.noQuery(request);
        return Api.success(service.getInventory(id), request);
    }

    @PostMapping(value = "/inventory", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> adjustInventory(@AuthenticationPrincipal Jwt jwt,
                                               @RequestBody JsonNode body, HttpServletRequest request) {
        AuthController.noQuery(request);
        Input.fields(body, "warehouseId", "materialId", "quantity");
        int warehouseId = reqInt(body, "warehouseId");
        int materialId = reqInt(body, "materialId");
        String qty = Input.text(body, "quantity", 50);
        return Api.success(service.adjustInventory(actor(jwt), warehouseId, materialId, qty, Api.requestId(request)), request);
    }
}
