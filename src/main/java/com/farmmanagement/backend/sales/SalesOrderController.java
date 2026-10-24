package com.farmmanagement.backend.sales;

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
@RequestMapping("/api/v1/sales-orders")
public class SalesOrderController {
    private final SalesOrderService service;

    public SalesOrderController(SalesOrderService service) {
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

    private Integer optInt(JsonNode body, String name) {
        JsonNode v = body.get(name);
        if (v == null || v.isNull()) return null;
        if (!v.isIntegralNumber() || !v.canConvertToInt() || v.asInt() <= 0) {
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

    private List<SalesOrderService.ItemInput> parseItems(JsonNode body) {
        JsonNode itemsNode = body.get("items");
        if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty() || itemsNode.size() > 200) {
            throw new IllegalArgumentException("Đơn bán hàng cần danh sách nông sản hợp lệ.");
        }
        List<SalesOrderService.ItemInput> items = new ArrayList<>();
        for (JsonNode item : itemsNode) {
            if (!item.isObject()) throw new IllegalArgumentException();
            int productId = reqInt(item, "productId");
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
            items.add(new SalesOrderService.ItemInput(productId, quantity, unitPrice, notes));
        }
        return items;
    }

    @GetMapping
    public Map<String, Object> listSalesOrders(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) Integer customerId,
                                               @RequestParam(required = false) Integer farmId,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String paymentStatus,
                                               @RequestParam(required = false) String fromDate,
                                               @RequestParam(required = false) String toDate,
                                               HttpServletRequest request) {
        if (!Set.of("page", "size", "customerId", "farmId", "status", "paymentStatus", "fromDate", "toDate")
            .containsAll(request.getParameterMap().keySet())
            || request.getParameterMap().values().stream().anyMatch(v -> v.length != 1)) {
            throw new IllegalArgumentException();
        }
        LocalDate from = fromDate != null && !fromDate.isBlank() ? LocalDate.parse(fromDate) : null;
        LocalDate to = toDate != null && !toDate.isBlank() ? LocalDate.parse(toDate) : null;
        return Api.success(service.listSalesOrders(page, size, customerId, farmId, status, paymentStatus, from, to), request);
    }

    @GetMapping("/{id}")
    public Map<String, Object> getSalesOrder(@PathVariable int id, HttpServletRequest request) {
        checkId(id);
        AuthController.noQuery(request);
        return Api.success(service.getSalesOrder(id), request);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSalesOrder(@AuthenticationPrincipal Jwt jwt,
                                              @RequestBody JsonNode body,
                                              HttpServletRequest request) {
        AuthController.noQuery(request);
        int customerId = reqInt(body, "customerId");
        Integer farmId = optInt(body, "farmId");
        String soNumber = optText(body, "soNumber", 50);
        LocalDate orderDate = optDate(body, "orderDate");
        LocalDate deliveryDate = optDate(body, "deliveryDate");
        String status = optText(body, "status", 20);
        String paymentStatus = optText(body, "paymentStatus", 20);
        String deliveryAddress = optText(body, "deliveryAddress", 255);
        String notes = optText(body, "notes", 1000);
        List<SalesOrderService.ItemInput> items = parseItems(body);

        var created = service.createSalesOrder(actor(jwt), soNumber, customerId, farmId,
            orderDate, deliveryDate, status, paymentStatus, deliveryAddress, notes, items, Api.requestId(request));

        return ResponseEntity.created(URI.create("/api/v1/sales-orders/" + created.id()))
            .body(Api.success(created, request));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> updateSalesOrder(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable int id,
                                                @RequestBody JsonNode body,
                                                HttpServletRequest request) {
        checkId(id);
        AuthController.noQuery(request);
        LocalDate deliveryDate = optDate(body, "deliveryDate");
        String paymentStatus = optText(body, "paymentStatus", 20);
        String deliveryAddress = optText(body, "deliveryAddress", 255);
        String notes = optText(body, "notes", 1000);
        List<SalesOrderService.ItemInput> items = body.has("items") ? parseItems(body) : null;

        var updated = service.updateSalesOrder(actor(jwt), id, deliveryDate, paymentStatus,
            deliveryAddress, notes, items, Api.requestId(request));
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
        var updated = service.changeSalesOrderStatus(actor(jwt), id, status, Api.requestId(request));
        return Api.success(updated, request);
    }
}
