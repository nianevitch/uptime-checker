package com.isofuture.uptime.service;

/**
 * RowStatus - Status values for cleansed CSV rows
 */
public enum RowStatus {
    PENDING("pending", "Pending processing"),
    PROCESSED("processed", "Processed"),
    DUPLICATE("duplicate", "Duplicate - needs merge");

    private final String value;
    private final String description;

    RowStatus(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static RowStatus fromString(String value) {
        if (value == null) {
            return PENDING;
        }
        for (RowStatus status : RowStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return PENDING;
    }
}

