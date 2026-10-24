package com.farmmanagement.backend.expense;

import com.farmmanagement.backend.common.DomainException;
import com.farmmanagement.backend.farm.FarmStore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FarmExpenseService {
    private final FarmExpenseStore store;
    private final FarmStore farms;
    public FarmExpenseService(FarmExpenseStore store, FarmStore farms) { this.store = store; this.farms = farms; }

    public record Category(String code, String name) {}

    @PreAuthorize("hasAnyAuthority('expenses:read','expenses:create')")
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, Object> options() {
        return Map.of("farms", store.farms(), "seasons", store.seasons(), "categories",
            Arrays.stream(ExpenseCategory.values()).map(c -> new Category(c.name(), c.label())).toList());
    }

    @PreAuthorize("hasAuthority('expenses:read')")
    @Transactional(readOnly = true)
    public List<Category> categories() {
        return Arrays.stream(ExpenseCategory.values()).map(c -> new Category(c.name(), c.label())).toList();
    }

    @PreAuthorize("hasAuthority('expenses:create')")
    public FarmExpense recordExpense(int actor, int farmId, Integer seasonId, ExpenseCategory category,
                                     BigDecimal amount, LocalDate date, String description, String requestId) {
        if (farmId <= 0 || (seasonId != null && seasonId <= 0) || category == null || amount == null
                || amount.signum() <= 0 || amount.compareTo(new BigDecimal("9999999999999999.99")) > 0
                || amount.stripTrailingZeros().scale() > 2 || date == null || date.getYear() < 1 || date.getYear() > 9999)
            throw new IllegalArgumentException();
        if (description != null) {
            description = description.trim();
            if (description.length() > 1000) throw new IllegalArgumentException();
            if (description.isEmpty()) description = null;
        }
        farms.farm(farmId).orElseThrow(DomainException::missing);
        if (seasonId != null) {
            farms.season(seasonId).orElseThrow(DomainException::missing);
            if (!store.seasonBelongsToFarm(seasonId, farmId))
                throw DomainException.conflict("Mùa vụ không thuộc trang trại đã chọn.");
        }
        int id = store.create(farmId, seasonId, category, amount.setScale(2, RoundingMode.UNNECESSARY), date, description, actor);
        farms.auditAction(actor, "FARM_EXPENSE", id, "CREATE", requestId);
        return store.byId(id).orElseThrow(DomainException::missing);
    }

    @PreAuthorize("hasAuthority('expenses:read')")
    @Transactional(readOnly = true)
    public FarmExpense getExpense(int id) {
        if (id <= 0) throw new IllegalArgumentException();
        return store.byId(id).orElseThrow(DomainException::missing);
    }

    @PreAuthorize("hasAuthority('expenses:read')")
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, Object> history(int page, int size, ExpenseFilter filter) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) throw new IllegalArgumentException();
        var totals = store.totals(filter);
        return Map.of("items", store.history(filter, page * size, size), "page", page, "size", size,
            "total", totals.count(), "totalAmount", totals.totalAmount().toPlainString(), "currency", "VND");
    }

    @PreAuthorize("hasAuthority('expenses:read')")
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, Object> summary(ExpenseFilter filter) {
        var totals = store.totals(filter);
        return Map.of("count", totals.count(), "totalAmount", totals.totalAmount().toPlainString(),
            "currency", "VND", "byCategory", store.totalsByCategory(filter));
    }
}
