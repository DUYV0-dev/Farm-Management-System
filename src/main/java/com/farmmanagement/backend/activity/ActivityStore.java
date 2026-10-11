package com.farmmanagement.backend.activity;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class ActivityStore {
    private final Map<String, FarmingActivity> activities = new ConcurrentHashMap<>();

    public ActivityStore() {
        initSeedData();
    }

    private void initSeedData() {
        FarmingActivity act1 = new FarmingActivity(
                "ACT-101",
                "Bón phân đợt 1 cho lúa Đông Xuân Sector 1",
                "Mùa Đông Xuân 2026",
                "Bón phân",
                "Nguyễn Văn Hùng",
                "Cánh đồng Lúa Sector 1",
                "High",
                "2026-10-01",
                "2026-10-04",
                "completed",
                100,
                "Bón thúc đợt 1 phân NPK 20-20-15 tạo đà đẻ nhánh khỏe cho lúa vụ Đông Xuân."
        );
        Map<String, Object> comp1 = new HashMap<>();
        comp1.put("actualDate", "2026-10-03 16:30");
        comp1.put("outputQty", 250);
        comp1.put("outputUnit", "kg phân NPK");
        comp1.put("inspector", "Trần Văn Lượng (Quản lý vườn)");
        comp1.put("quality", "Xuất sắc (5★)");
        comp1.put("notes", "Đã bón phân đồng đều trên diện tích 2.5 ha, lúa sinh trưởng rất tốt.");
        act1.setCompletionRecord(comp1);

        List<Map<String, Object>> hist1 = new ArrayList<>();
        hist1.add(Map.of("time", "2026-10-01 07:00", "type", "create", "desc", "Khởi tạo hoạt động canh tác cho Mùa Đông Xuân 2026"));
        hist1.add(Map.of("time", "2026-10-03 16:30", "type", "completion", "desc", "Ghi nhận HOÀN THÀNH 100% bởi Trần Văn Lượng"));
        act1.setHistory(hist1);

        activities.put(act1.getId(), act1);

        FarmingActivity act2 = new FarmingActivity(
                "ACT-102",
                "Phun thuốc sinh học phòng trừ sâu cuốn lá Greenhouse A2",
                "Mùa Đông Xuân 2026",
                "Phun thuốc",
                "Lê Thị Mai",
                "Nhà màng Greenhouse A2",
                "Critical",
                "2026-10-04",
                "2026-10-07",
                "in_progress",
                65,
                "Sử dụng chế phẩm sinh học BT phun phòng ngừa sâu cuốn lá giai đoạn phát triển mầm."
        );
        activities.put(act2.getId(), act2);
    }

    public List<FarmingActivity> findAll(String season, String category, String status, String search) {
        return activities.values().stream()
                .filter(a -> season == null || season.isBlank() || season.equalsIgnoreCase("ALL") || a.getSeason().equalsIgnoreCase(season))
                .filter(a -> category == null || category.isBlank() || a.getCategory().equalsIgnoreCase(category))
                .filter(a -> status == null || status.isBlank() || a.getStatus().equalsIgnoreCase(status))
                .filter(a -> search == null || search.isBlank() ||
                        a.getTitle().toLowerCase().contains(search.toLowerCase()) ||
                        a.getAssignee().toLowerCase().contains(search.toLowerCase()) ||
                        a.getSector().toLowerCase().contains(search.toLowerCase()))
                .collect(Collectors.toList());
    }

    public Optional<FarmingActivity> findById(String id) {
        return Optional.ofNullable(activities.get(id));
    }

    public FarmingActivity save(FarmingActivity activity) {
        if (activity.getId() == null || activity.getId().isBlank()) {
            activity.setId("ACT-" + System.currentTimeMillis() % 10000);
        }
        activities.put(activity.getId(), activity);
        return activity;
    }

    public boolean delete(String id) {
        return activities.remove(id) != null;
    }
}
