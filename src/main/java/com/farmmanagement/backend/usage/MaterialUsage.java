package com.farmmanagement.backend.usage;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record MaterialUsage(int id, int seasonId, int farmId, String farmName,
                            String plotName, String cropName, int materialId,
                            String materialName, UsageType type, BigDecimal quantity,
                            String unit, LocalDate applicationDate, String notes,
                            Integer createdBy, Instant createdAt) {}
