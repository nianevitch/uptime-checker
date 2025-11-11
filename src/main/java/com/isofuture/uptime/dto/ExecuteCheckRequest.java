package com.isofuture.uptime.dto;

import jakarta.validation.constraints.NotNull;

public class ExecuteCheckRequest {

    @NotNull
    private Long monitorId;

    public Long getMonitorId() {
        return monitorId;
    }

    public void setMonitorId(Long monitorId) {
        this.monitorId = monitorId;
    }
}

