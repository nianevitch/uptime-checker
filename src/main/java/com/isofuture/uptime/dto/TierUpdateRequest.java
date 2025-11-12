package com.isofuture.uptime.dto;

import jakarta.validation.constraints.Size;

public class TierUpdateRequest {

    @Size(min = 1, max = 100)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}


