package com.farmmanagement.backend.expense;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FarmExpense(int id, int farmId, String farmName, Integer seasonId,
                          String cropName, String plotName, ExpenseCategory category,
                          @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount, LocalDate expenseDate, String description,
                          Integer createdBy, Instant createdAt) {}
