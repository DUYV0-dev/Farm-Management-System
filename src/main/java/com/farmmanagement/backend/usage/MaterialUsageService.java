package com.farmmanagement.backend.usage;

import com.farmmanagement.backend.common.DomainException;
import com.farmmanagement.backend.farm.FarmStore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MaterialUsageService {
    private final MaterialUsageStore store;
    private final FarmStore farms;
    public MaterialUsageService(MaterialUsageStore store, FarmStore farms) { this.store = store; this.farms = farms; }

    public record TypeOption(String code, String name) {}

    @PreAuthorize("hasAnyAuthority('usage:read','usage:create')")
    @Transactional(readOnly = true)
    public Map<String,Object> options() {
        return Map.of("types", Arrays.stream(UsageType.values()).map(t -> new TypeOption(t.name(), t.label())).toList(),
            "seasons", store.seasons(), "materials", store.materials());
    }

    @PreAuthorize("hasAuthority('usage:create')")
    public MaterialUsage record(int actor, int seasonId, int materialId, UsageType type,
                                BigDecimal quantity, LocalDate date, String notes, String requestId) {
        if (actor <= 0 || seasonId <= 0 || materialId <= 0 || type == null || quantity == null
                || quantity.signum() <= 0 || quantity.compareTo(new BigDecimal("999999999999.999")) > 0
                || quantity.stripTrailingZeros().scale() > 3 || date == null || date.getYear() < 1
                || date.isAfter(LocalDate.now(ZoneId.of("Asia/Bangkok"))))
            throw new DomainException(400, "INVALID_INPUT", "Số lượng phải lớn hơn 0, tối đa 3 chữ số thập phân; ngày sử dụng không được ở tương lai.");
        if (notes != null) {
            notes = notes.trim();
            if (notes.length() > 1000) throw new IllegalArgumentException();
            if (notes.isEmpty()) notes = null;
        }
        store.lockManagement();
        if (!store.seasonExists(seasonId)) throw DomainException.missing();
        if (!store.seasonAvailable(seasonId))
            throw DomainException.conflict("Chọn mùa vụ đang thực hiện hoặc đã hoàn thành, có trang trại, khu đất và cây trồng đang hoạt động.");
        var material = store.material(materialId).orElseThrow(DomainException::missing);
        if (!store.materialAvailable(materialId))
            throw DomainException.conflict("Vật tư đã ngừng hoạt động hoặc không có đơn vị tính hợp lệ.");
        int id = store.create(seasonId, material, type, quantity, date, notes, actor);
        farms.auditAction(actor, "MATERIAL_USAGE", id, "CREATE", requestId);
        return store.byId(id).orElseThrow(DomainException::missing);
    }

    @PreAuthorize("hasAuthority('usage:read')")
    @Transactional(readOnly = true)
    public MaterialUsage get(int id) {
        if (id <= 0) throw new IllegalArgumentException();
        return store.byId(id).orElseThrow(DomainException::missing);
    }

    @PreAuthorize("hasAuthority('usage:read')")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String,Object> history(int page, int size, UsageFilter filter) {
        if (page < 0 || page > 100000 || size < 1 || size > 100 || filter == null) throw new IllegalArgumentException();
        return Map.of("items", store.history(filter, page * size, size), "page", page, "size", size, "total", store.count(filter));
    }

    @PreAuthorize("hasAuthority('usage:read')")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String,Object> summary(UsageFilter filter) {
        if (filter == null) throw new IllegalArgumentException();
        return Map.of("count", store.count(filter), "byMaterial", store.totals(filter));
    }
}
