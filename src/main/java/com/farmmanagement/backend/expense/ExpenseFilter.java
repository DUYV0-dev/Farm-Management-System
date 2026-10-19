package com.farmmanagement.backend.expense;

import java.time.LocalDate;

public record ExpenseFilter(Integer farmId, Integer seasonId, ExpenseCategory category,
                            LocalDate fromDate, LocalDate toDate) {
    public ExpenseFilter {
        if ((farmId != null && farmId <= 0) || (seasonId != null && seasonId <= 0)
                || (fromDate != null && toDate != null && fromDate.isAfter(toDate)))
            throw new IllegalArgumentException();
    }
}
