package com.farmmanagement.backend.notification;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationStore store;

    public NotificationController(NotificationStore store) {
        this.store = store;
    }

    @GetMapping
    public ResponseEntity<List<Notification>> getNotifications(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(store.findAll(category, status, priority, search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Notification> getNotificationById(@PathVariable String id) {
        return store.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Notification> createNotification(@RequestBody Notification notification) {
        if (notification.getStatus() == null) notification.setStatus("unread");
        if (notification.getTimestamp() == null) notification.setTimestamp(new Date().toString());
        return ResponseEntity.ok(store.save(notification));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Notification> updateStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        if (newStatus == null) return ResponseEntity.badRequest().build();

        return store.findById(id).map(n -> {
            n.setStatus(newStatus);
            return ResponseEntity.ok(store.save(n));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/mark-all-read")
    public ResponseEntity<Void> markAllRead() {
        store.markAllAsRead();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable String id) {
        if (store.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
