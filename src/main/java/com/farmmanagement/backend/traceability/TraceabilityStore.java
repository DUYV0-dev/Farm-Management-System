package com.farmmanagement.backend.traceability;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class TraceabilityStore {
    private final Map<String, TraceabilityBatch> batches = new ConcurrentHashMap<>();

    public TraceabilityStore() {
        initSeedData();
    }

    private void initSeedData() {
        TraceabilityBatch b1 = new TraceabilityBatch();
        b1.setId("LOT-2026-ST25-01");
        b1.setProductName("Gạo Thơm Thượng Hạng ST25");
        b1.setVariety("ST25 Thuần Chủng F1 Sóc Trăng");
        b1.setSeason("Mùa Đông Xuân 2026");
        b1.setCropDuration("15/11/2025 -> 20/03/2026 (105 ngày)");
        b1.setFarmLocation("Cánh đồng Lúa Sector 1, H. Tháp Mười, Tỉnh Đồng Tháp");
        b1.setGpsCoordinates("10°34'18.2\"N 105°47'22.6\"E");
        b1.setPucCode("VN-DTP-0832-PUC");
        b1.setCertification("VietGAP");
        b1.setCertNumber("VietGAP-TT-26-01824");
        b1.setManager("Trần Văn Lượng (Quản lý vườn)");
        b1.setInspector("Lê Thị Mai (Kỹ thuật viên KCS)");
        b1.setProductImg("https://images.unsplash.com/photo-1586201375761-83865001e31c?auto=format&fit=crop&w=600&q=80");
        b1.setProductStory("Gạo ST25 canh tác tự nhiên, không tồn dư hóa chất, dẻo thơm hương lá dứa.");
        b1.setHarvestActualTime("20/03/2026 - 08:30");
        b1.setHarvestActualYield("15,500 kg (15.5 tấn)");
        b1.setHarvestGradeRatio("Loại 1: 98% | Loại 2: 2%");
        b1.setPackaging("Đóng túi hút chân không tráng bạc 5kg dán tem QR.");
        b1.setStorageCondition("Bảo quản nơi khô ráo, nhiệt độ 20-25°C. HSD: 12 tháng.");
        b1.setExpiryDate("20/03/2027");
        batches.put(b1.getId(), b1);

        TraceabilityBatch b2 = new TraceabilityBatch();
        b2.setId("LOT-2026-CHOC-02");
        b2.setProductName("Cà Chua Sô Cô La Isreal F1");
        b2.setVariety("Isreal Chocolate Tomato F1");
        b2.setSeason("Mùa Hè Thu 2026");
        b2.setCropDuration("01/06/2026 -> 24/09/2026 (115 ngày)");
        b2.setFarmLocation("Nhà màng Greenhouse A2, TP. Đà Lạt, Tỉnh Lâm Đồng");
        b2.setGpsCoordinates("11°56'24.8\"N 108°26'15.2\"E");
        b2.setPucCode("VN-LDO-0428-PUC");
        b2.setCertification("GlobalGAP");
        b2.setCertNumber("GGN-4059883726189");
        b2.setManager("Trần Văn Lượng (Quản lý nhà màng)");
        b2.setInspector("Hồng Thái Vinh (KCS)");
        b2.setProductImg("https://images.unsplash.com/photo-1592924357228-91a4daadcfea?auto=format&fit=crop&w=600&q=80");
        b2.setHarvestActualTime("24/09/2026 - 15:00");
        b2.setHarvestActualYield("1,850 kg");
        batches.put(b2.getId(), b2);

        TraceabilityBatch b3 = new TraceabilityBatch();
        b3.setId("LOT-2026-MELON-03");
        b3.setProductName("Dưa Lưới Huỳnh Long Ruột Cam");
        b3.setVariety("Dưa lưới Huỳnh Long F1 Nhật Bản");
        b3.setSeason("Mùa Hè Thu 2026");
        b3.setFarmLocation("Nhà kính Greenhouse C1, Xã Lạc Dương, Tỉnh Lâm Đồng");
        b3.setPucCode("VN-LDO-0915-PUC");
        b3.setCertification("VietGAP");
        b3.setHarvestActualYield("4,200 kg");
        batches.put(b3.getId(), b3);
    }

    public List<TraceabilityBatch> findAll(String search, String season, String certification) {
        return batches.values().stream()
                .filter(b -> {
                    if (search != null && !search.isBlank()) {
                        String s = search.toLowerCase();
                        boolean match = (b.getId() != null && b.getId().toLowerCase().contains(s)) ||
                                (b.getProductName() != null && b.getProductName().toLowerCase().contains(s)) ||
                                (b.getVariety() != null && b.getVariety().toLowerCase().contains(s)) ||
                                (b.getPucCode() != null && b.getPucCode().toLowerCase().contains(s));
                        if (!match) return false;
                    }
                    if (season != null && !season.isBlank()) {
                        if (!season.equalsIgnoreCase(b.getSeason())) return false;
                    }
                    if (certification != null && !certification.isBlank()) {
                        if (b.getCertification() == null || !b.getCertification().toLowerCase().contains(certification.toLowerCase())) return false;
                    }
                    return true;
                })
                .sorted(Comparator.comparing(TraceabilityBatch::getId))
                .collect(Collectors.toList());
    }

    public Optional<TraceabilityBatch> findById(String id) {
        return Optional.ofNullable(batches.get(id));
    }

    public TraceabilityBatch save(TraceabilityBatch batch) {
        if (batch.getId() == null || batch.getId().isBlank()) {
            batch.setId("LOT-2026-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        batches.put(batch.getId(), batch);
        return batch;
    }

    public boolean delete(String id) {
        return batches.remove(id) != null;
    }
}
