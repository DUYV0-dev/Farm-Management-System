package com.farmmanagement.backend.product;

import java.time.Instant;

public record HarvestedProduct(
    int productId,
    int productionId,
    String productName,
    Double quantity,
    String quality,
    String status,
    String storageLocation,
    Instant createdAt,
    Instant updatedAt
) {}
