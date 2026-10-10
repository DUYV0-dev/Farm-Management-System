package com.farmmanagement.backend.product;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class HarvestedProductController {
    private final HarvestedProductService service;

    public HarvestedProductController(HarvestedProductService service) {
        this.service = service;
    }

    private Map<String, Object> response(Object data) {
        return Map.of("data", data, "requestId", UUID.randomUUID().toString());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createProduct(@RequestBody HarvestedProduct product) {
        var created = service.createProduct(product);
        return ResponseEntity.ok(response(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getProduct(@PathVariable int id) {
        return ResponseEntity.ok(response(service.getProduct(id)));
    }

    @GetMapping("/production/{productionId}")
    public ResponseEntity<Map<String, Object>> getProductsByProduction(@PathVariable int productionId) {
        return ResponseEntity.ok(response(service.getProductsByProduction(productionId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateProduct(@PathVariable int id, @RequestBody HarvestedProduct product) {
        return ResponseEntity.ok(response(service.updateProduct(id, product)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable int id) {
        service.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
