package com.farmmanagement.backend.expense;

public enum ExpenseCategory {
    SEEDS("Giống cây trồng"),
    FERTILIZER("Phân bón"),
    PESTICIDES("Thuốc bảo vệ thực vật"),
    LABOR("Nhân công"),
    EQUIPMENT("Máy móc và thiết bị"),
    IRRIGATION("Tưới tiêu"),
    TRANSPORT("Vận chuyển"),
    OTHER("Chi phí khác");

    private final String label;

    ExpenseCategory(String label) { this.label = label; }
    public String label() { return label; }
}
