package com.farmmanagement.backend.activity;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/activities")
public class FarmingActivityController {

    private final ActivityStore store;

    public FarmingActivityController(ActivityStore store) {
        this.store = store;
    }

    @GetMapping
    public ResponseEntity<List<FarmingActivity>> getAllActivities(
            @RequestParam(required = false) String season,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(store.findAll(season, category, status, search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FarmingActivity> getActivityById(@PathVariable String id) {
        return store.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<FarmingActivity> createActivity(@RequestBody FarmingActivity activity) {
        if (activity.getHistory() == null) {
            activity.setHistory(new ArrayList<>());
        }
        activity.getHistory().add(Map.of(
                "time", new Date().toString(),
                "type", "create",
                "desc", "Hoạt động được khởi tạo qua API REST"
        ));
        return ResponseEntity.ok(store.save(activity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FarmingActivity> updateActivity(@PathVariable String id, @RequestBody FarmingActivity updated) {
        return store.findById(id).map(existing -> {
            existing.setTitle(updated.getTitle());
            existing.setSeason(updated.getSeason());
            existing.setCategory(updated.getCategory());
            existing.setAssignee(updated.getAssignee());
            existing.setSector(updated.getSector());
            existing.setPriority(updated.getPriority());
            existing.setStartDate(updated.getStartDate());
            existing.setDueDate(updated.getDueDate());
            existing.setDescription(updated.getDescription());
            return ResponseEntity.ok(store.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<FarmingActivity> updateStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        if (newStatus == null) return ResponseEntity.badRequest().build();

        return store.findById(id).map(act -> {
            String oldStatus = act.getStatus();
            act.setStatus(newStatus);
            if ("completed".equalsIgnoreCase(newStatus)) {
                act.setProgress(100);
            }
            act.getHistory().add(0, Map.of(
                    "time", new Date().toString(),
                    "type", "status",
                    "desc", "Cập nhật trạng thái từ " + oldStatus + " sang " + newStatus
            ));
            return ResponseEntity.ok(store.save(act));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/progress")
    public ResponseEntity<FarmingActivity> updateProgress(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Integer newProgress = (Integer) body.get("progress");
        String note = (String) body.get("note");
        if (newProgress == null) return ResponseEntity.badRequest().build();

        return store.findById(id).map(act -> {
            int old = act.getProgress();
            act.setProgress(newProgress);
            if (newProgress == 100) {
                act.setStatus("completed");
            } else if (newProgress > 0 && "todo".equalsIgnoreCase(act.getStatus())) {
                act.setStatus("in_progress");
            }
            act.getHistory().add(0, Map.of(
                    "time", new Date().toString(),
                    "type", "progress",
                    "desc", "Cập nhật tiến độ: " + old + "% -> " + newProgress + "%" + (note != null ? " (" + note + ")" : "")
            ));
            return ResponseEntity.ok(store.save(act));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/completion")
    public ResponseEntity<FarmingActivity> recordCompletion(@PathVariable String id, @RequestBody Map<String, Object> completionRecord) {
        return store.findById(id).map(act -> {
            act.setStatus("completed");
            act.setProgress(100);
            act.setCompletionRecord(completionRecord);
            act.getHistory().add(0, Map.of(
                    "time", new Date().toString(),
                    "type", "completion",
                    "desc", "Ghi nhận HOÀN THÀNH (100%) bởi " + completionRecord.getOrDefault("inspector", "Cán bộ nghiệm thu")
            ));
            return ResponseEntity.ok(store.save(act));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<FarmingActivity> addNote(@PathVariable String id, @RequestBody Map<String, Object> noteData) {
        return store.findById(id).map(act -> {
            if (act.getNotes() == null) act.setNotes(new ArrayList<>());
            act.getNotes().add(0, noteData);
            act.getHistory().add(0, Map.of(
                    "time", new Date().toString(),
                    "type", "note",
                    "desc", "Thêm ghi chú nhật ký: " + noteData.getOrDefault("category", "Ghi chú")
            ));
            return ResponseEntity.ok(store.save(act));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteActivity(@PathVariable String id) {
        if (store.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
