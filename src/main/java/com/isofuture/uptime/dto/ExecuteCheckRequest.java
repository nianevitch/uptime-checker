package com.isofuture.uptime.dto;

import jakarta.validation.constraints.NotNull;

public class ExecuteCheckRequest {

    @NotNull
    private Long pingId;

    public Long getPingId() {
        return pingId;
    }

    public void setPingId(Long pingId) {
        this.pingId = pingId;
    }
}

