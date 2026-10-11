package com.farmmanagement.backend.production;

import java.time.Instant;

public record CropProduction(
    int productionId,
    int farmId,
    String cropName,
    String seasonName,
    String growthStage,
    Double expectedQuantity,
    Double actualQuantity,
    String unit,
    String status,
    Instant createdAt,
    Instant updatedAt
) {}
