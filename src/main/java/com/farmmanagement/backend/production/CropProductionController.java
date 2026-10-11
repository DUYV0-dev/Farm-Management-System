package com.farmmanagement.backend.production;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/productions")
public class CropProductionController {
    private final CropProductionService service;

    public CropProductionController(CropProductionService service) {
        this.service = service;
    }

    private Map<String, Object> response(Object data) {
        return Map.of("data", data, "requestId", UUID.randomUUID().toString());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createProduction(@RequestBody CropProduction production) {
        var created = service.createProduction(production);
        return ResponseEntity.ok(response(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getProduction(@PathVariable int id) {
        return ResponseEntity.ok(response(service.getProduction(id)));
    }

    @GetMapping("/farm/{farmId}")
    public ResponseEntity<Map<String, Object>> getProductionsByFarm(@PathVariable int farmId) {
        return ResponseEntity.ok(response(service.getProductionsByFarm(farmId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateProduction(@PathVariable int id, @RequestBody CropProduction production) {
        return ResponseEntity.ok(response(service.updateProduction(id, production)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduction(@PathVariable int id) {
        service.deleteProduction(id);
        return ResponseEntity.noContent().build();
    }
}
