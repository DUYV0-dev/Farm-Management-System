package com.farmmanagement.backend.supplier;

import com.farmmanagement.backend.auth.AuthController;
import com.farmmanagement.backend.common.Api;
import com.farmmanagement.backend.common.Input;
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
@RequestMapping("/api/v1/purchase-orders")
public class PurchaseOrderController {
    private final SupplierService service;

    public PurchaseOrderController(SupplierService service) {
        this.service = service;
    }

    private int actor(Jwt jwt) {
        return Integer.parseInt(jwt.getSubject());
    }

    private void checkId(int id) {
        if (id <= 0) throw new IllegalArgumentException();
    }

    private int reqInt(JsonNode body, String name) {
        JsonNode v = body.get(name);
        if (v == null || !v.isIntegralNumber() || !v.canConvertToInt() || v.asInt() <= 0) {
            throw new IllegalArgumentException();
        }
        return v.asInt();
    }

    private String optText(JsonNode body, String name, int max) {
        JsonNode v = body.get(name);
        if (v == null || v.isNull()) return null;
        if (!v.isTextual()) throw new IllegalArgumentException();
        String s = v.asText().trim();
        if (s.length() > max) throw new IllegalArgumentException();
        return s.isEmpty() ? null : s;
    }

    private LocalDate optDate(JsonNode body, String name) {
        JsonNode v = body.get(name);
        if (v == null || v.isNull() || v.asText().isBlank()) return null;
        try {
            return LocalDate.parse(v.asText().trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException();
        }
    }

    private List<SupplierService.ItemInput> parseItems(JsonNode body) {
        JsonNode itemsNode = body.get("items");
        if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty() || itemsNode.size() > 200) {
            throw new IllegalArgumentException("Đơn mua hàng cần danh sách vật tư hợp lệ.");
        }
        List<SupplierService.ItemInput> items = new ArrayList<>();
        for (JsonNode item : itemsNode) {
            if (!item.isObject()) throw new IllegalArgumentException();
            int materialId = reqInt(item, "materialId");
            JsonNode qNode = item.get("quantity");
            if (qNode == null) throw new IllegalArgumentException();
            BigDecimal quantity;
            try {
                quantity = new BigDecimal(qNode.asText());
            } catch (RuntimeException e) {
                throw new IllegalArgumentException();
            }

            JsonNode pNode = item.get("unitPrice");
            if (pNode == null) throw new IllegalArgumentException();
            BigDecimal unitPrice;
            try {
                unitPrice = new BigDecimal(pNode.asText());
            } catch (RuntimeException e) {
                throw new IllegalArgumentException();
            }

            String notes = optText(item, "notes", 500);
            items.add(new SupplierService.ItemInput(materialId, quantity, unitPrice, notes));
        }
        return items;
    }

    @GetMapping
    public Map<String, Object> listPurchaseOrders(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) Integer supplierId,
                                                  @RequestParam(required = false) Integer farmId,
                                                  @RequestParam(required = false) Integer warehouseId,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String fromDate,
                                                  @RequestParam(required = false) String toDate,
                                                  HttpServletRequest request) {
        if (!Set.of("page", "size", "supplierId", "farmId", "warehouseId", "status", "fromDate", "toDate")
            .containsAll(request.getParameterMap().keySet())
            || request.getParameterMap().values().stream().anyMatch(v -> v.length != 1)) {
            throw new IllegalArgumentException();
        }
        LocalDate from = fromDate != null && !fromDate.isBlank() ? LocalDate.parse(fromDate) : null;
        LocalDate to = toDate != null && !toDate.isBlank() ? LocalDate.parse(toDate) : null;
        return Api.success(service.listPurchaseOrders(page, size, supplierId, farmId, warehouseId, status, from, to), request);
    }

    @GetMapping("/{id}")
    public Map<String, Object> getPurchaseOrder(@PathVariable int id, HttpServletRequest request) {
        checkId(id);
        AuthController.noQuery(request);
        return Api.success(service.getPurchaseOrder(id), request);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createPurchaseOrder(@AuthenticationPrincipal Jwt jwt,
                                                 @RequestBody JsonNode body,
                                                 HttpServletRequest request) {
        AuthController.noQuery(request);
        int supplierId = reqInt(body, "supplierId");
        int farmId = reqInt(body, "farmId");
        int warehouseId = reqInt(body, "warehouseId");
        String poNumber = optText(body, "poNumber", 50);
        LocalDate orderDate = optDate(body, "orderDate");
        LocalDate expectedDeliveryDate = optDate(body, "expectedDeliveryDate");
        String notes = optText(body, "notes", 1000);
        List<SupplierService.ItemInput> items = parseItems(body);

        var created = service.createPurchaseOrder(actor(jwt), poNumber, supplierId, farmId, warehouseId,
            orderDate, expectedDeliveryDate, notes, items, Api.requestId(request));

        return ResponseEntity.created(URI.create("/api/v1/purchase-orders/" + created.id()))
            .body(Api.success(created, request));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> updatePurchaseOrder(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable int id,
                                                   @RequestBody JsonNode body,
                                                   HttpServletRequest request) {
        checkId(id);
        AuthController.noQuery(request);
        LocalDate expectedDeliveryDate = optDate(body, "expectedDeliveryDate");
        String notes = optText(body, "notes", 1000);
        List<SupplierService.ItemInput> items = body.has("items") ? parseItems(body) : null;

        var updated = service.updatePurchaseOrder(actor(jwt), id, expectedDeliveryDate, notes, items, Api.requestId(request));
        return Api.success(updated, request);
    }

    @PutMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> changeStatus(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable int id,
                                            @RequestBody JsonNode body,
                                            HttpServletRequest request) {
        checkId(id);
        AuthController.noQuery(request);
        String status = Input.text(body, "status", 20).toUpperCase(Locale.ROOT);
        var updated = service.changePurchaseOrderStatus(actor(jwt), id, status, Api.requestId(request));
        return Api.success(updated, request);
    }

    @PostMapping("/{id}/receive")
    public Map<String, Object> receiveMaterials(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable int id,
                                                HttpServletRequest request) {
        checkId(id);
        AuthController.noQuery(request);
        var updated = service.receiveMaterials(actor(jwt), id, Api.requestId(request));
        return Api.success(updated, request);
    }
}
