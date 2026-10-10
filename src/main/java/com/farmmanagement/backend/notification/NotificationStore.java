package com.farmmanagement.backend.notification;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class NotificationStore {
    private final Map<String, Notification> notifications = new ConcurrentHashMap<>();

    public NotificationStore() {
        initSeedData();
    }

    private void initSeedData() {
        Notification n0 = new Notification(
                "NOTIF-100",
                "🔴 SỤT ÁP NƯỚC HỆ THỐNG TƯỚI TỰ ĐỘNG - SECTOR 4",
                "Phát hiện sụt áp lực nước nghiêm trọng (dưới 1.2 bar) tại đường ống tưới chính Sector 4. Nguyên nhân dự kiến do tắc nghẽn bộ lọc đĩa 120 mesh và hư hỏng 2 van xả cặn tự động. Yêu cầu kỹ sư Võ Hà Duy tiến hành súc rửa màng lọc, thay thế 2 van xả 34mm và kiểm tra lại áp suất toàn hệ thống trước 14:00 chiều nay để tránh ảnh hưởng đến đợt tưới tiêu vụ lúa Đông Xuân 2026.",
                "Inventory Alert",
                "critical",
                "unread",
                "5 phút trước",
                new Date().toString()
        );
        notifications.put(n0.getId(), n0);

        Notification n1 = new Notification(
                "NOTIF-101",
                "CẢNH BÁO VẬT TƯ: NPK 16-16-8 DƯỚI NGƯỠNG TỐI THIỂU",
                "Số lượng phân NPK 16-16-8 trong kho chỉ còn 45kg (ngưỡng tối thiểu: 100kg). Cần đặt mua bổ sung.",
                "Inventory Alert",
                "critical",
                "unread",
                "15 phút trước",
                new Date().toString()
        );
        notifications.put(n1.getId(), n1);

        Notification n2 = new Notification(
                "NOTIF-102",
                "NHẮC NHỞ CÔNG VIỆC: Quá hạn kiểm tra hệ thống tưới",
                "Công việc 'Bảo trì hệ thống tưới nhỏ giọt Control Unit A' chưa hoàn thành theo kế hoạch.",
                "Task Reminder",
                "warning",
                "unread",
                "1 giờ trước",
                new Date().toString()
        );
        notifications.put(n2.getId(), n2);

        Notification n3 = new Notification(
                "NOTIF-103",
                "NHẮC NHỞ MÙA VỤ: Đợt bón thúc 2 vụ Đông Xuân 2026",
                "Theo lịch mùa vụ Đông Xuân 2026, Cánh đồng Lúa Sector 1 đến giai đoạn đẻ nhánh cần bón thúc đợt 2.",
                "Crop Season",
                "info",
                "unread",
                "3 giờ trước",
                new Date().toString()
        );
        notifications.put(n3.getId(), n3);
    }

    public List<Notification> findAll(String category, String status, String priority, String search) {
        return notifications.values().stream()
                .filter(n -> category == null || category.isBlank() || n.getCategory().equalsIgnoreCase(category))
                .filter(n -> status == null || status.isBlank() || n.getStatus().equalsIgnoreCase(status))
                .filter(n -> priority == null || priority.isBlank() || n.getPriority().equalsIgnoreCase(priority))
                .filter(n -> search == null || search.isBlank() ||
                        n.getTitle().toLowerCase().contains(search.toLowerCase()) ||
                        n.getDesc().toLowerCase().contains(search.toLowerCase()))
                .collect(Collectors.toList());
    }

    public Optional<Notification> findById(String id) {
        return Optional.ofNullable(notifications.get(id));
    }

    public Notification save(Notification notification) {
        if (notification.getId() == null || notification.getId().isBlank()) {
            notification.setId("NOTIF-" + System.currentTimeMillis() % 10000);
        }
        notifications.put(notification.getId(), notification);
        return notification;
    }

    public void markAllAsRead() {
        notifications.values().forEach(n -> {
            if ("unread".equalsIgnoreCase(n.getStatus())) {
                n.setStatus("read");
            }
        });
    }

    public boolean delete(String id) {
        return notifications.remove(id) != null;
    }
}
