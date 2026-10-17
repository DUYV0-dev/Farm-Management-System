package com.farmmanagement.backend.traceability;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/traceability")
public class TraceabilityController {

    private final TraceabilityStore store;

    public TraceabilityController(TraceabilityStore store) {
        this.store = store;
    }

    @GetMapping
    public ResponseEntity<List<TraceabilityBatch>> getAllBatches(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String season,
            @RequestParam(required = false) String certification) {
        return ResponseEntity.ok(store.findAll(search, season, certification));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TraceabilityBatch> getBatchById(@PathVariable String id) {
        return store.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<TraceabilityBatch> createBatch(@RequestBody TraceabilityBatch batch) {
        return ResponseEntity.ok(store.save(batch));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBatch(@PathVariable String id) {
        if (store.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
