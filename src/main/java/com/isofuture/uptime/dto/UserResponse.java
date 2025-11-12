package com.isofuture.uptime.dto;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import com.isofuture.uptime.entity.UserEntity;

public class UserResponse {

    private Long id;
    private String email;
    private Set<String> roles;
    private Instant createdAt;
    private Instant deletedAt;

    public UserResponse() {
    }

    public UserResponse(UserEntity entity) {
        this.id = entity.getId();
        this.email = entity.getEmail();
        this.roles = entity.getRoles().stream()
            .map(role -> role.getName())
            .collect(Collectors.toSet());
        this.createdAt = entity.getCreatedAt();
        this.deletedAt = entity.getDeletedAt();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
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

