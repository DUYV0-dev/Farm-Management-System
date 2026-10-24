package com.farmmanagement.backend.usage;

import java.time.LocalDate;

public record UsageFilter(Integer farmId, Integer seasonId, Integer materialId,
                          UsageType type, LocalDate fromDate, LocalDate toDate) {
    public UsageFilter {
        if ((farmId != null && farmId <= 0) || (seasonId != null && seasonId <= 0)
                || (materialId != null && materialId <= 0)
                || (fromDate != null && (fromDate.getYear() < 1 || fromDate.getYear() > 9999))
                || (toDate != null && (toDate.getYear() < 1 || toDate.getYear() > 9999))
                || (fromDate != null && toDate != null && fromDate.isAfter(toDate)))
            throw new IllegalArgumentException();
    }
}
