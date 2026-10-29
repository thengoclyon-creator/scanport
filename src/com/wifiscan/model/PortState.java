package com.wifiscan.model;

/**
 * Trạng thái của một cổng mạng khi được quét.
 */
public enum PortState {
    OPEN("MỞ (Open)"),
    CLOSED("ĐÓNG (Closed)"),
    FILTERED("BỊ CHẶN (Filtered)");

    private final String displayName;

    PortState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
