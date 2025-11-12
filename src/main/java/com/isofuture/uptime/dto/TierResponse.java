package com.isofuture.uptime.dto;

import java.time.Instant;

import com.isofuture.uptime.entity.Tier;

public class TierResponse {

    private Long id;
    private String name;
    private Instant createdAt;
    private Instant deletedAt;

    public TierResponse() {
    }

    public TierResponse(Tier entity) {
        this.id = entity.getId();
        this.name = entity.getName();
        this.createdAt = entity.getCreatedAt();
        this.deletedAt = entity.getDeletedAt();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}


