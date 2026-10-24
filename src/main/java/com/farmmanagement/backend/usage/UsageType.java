package com.farmmanagement.backend.usage;

public enum UsageType {
    FERTILIZER("Phân bón"), PESTICIDE("Thuốc bảo vệ thực vật");

    private final String label;
    UsageType(String label) { this.label = label; }
    public String label() { return label; }
}
