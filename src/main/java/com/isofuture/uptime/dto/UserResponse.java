package com.isofuture.uptime.dto;

import java.time.Instant;
import java.util.Set;
import java.util.HashSet;

import com.isofuture.uptime.entity.User;
import com.isofuture.uptime.entity.Role;
import com.isofuture.uptime.entity.Tier;

public class UserResponse {

    private Long id;
    private String email;
    private Set<String> roles;
    private Set<String> tiers;
    private Instant createdAt;
    private Instant deletedAt;

    public UserResponse() {
    }

    public UserResponse(User entity) {
        this.id = entity.getId();
        this.email = entity.getEmail();
        try {
            Set<Role> entityRoles = entity.getRoles();
            if (entityRoles != null && !entityRoles.isEmpty()) {
                // Copy to a new HashSet to avoid Hibernate collection access issues
                this.roles = new HashSet<>();
                // Use toArray to avoid iterator issues with Hibernate collections
                Role[] rolesArray = entityRoles.toArray(new Role[0]);
                for (Role role : rolesArray) {
                    if (role != null && role.getName() != null) {
                        this.roles.add(role.getName());
                    }
                }
            } else {
                this.roles = java.util.Collections.emptySet();
            }
        } catch (Exception e) {
            // Fallback: initialize empty set if there's any issue accessing roles
            this.roles = java.util.Collections.emptySet();
        }
        try {
            Set<Tier> entityTiers = entity.getTiers();
            if (entityTiers != null && !entityTiers.isEmpty()) {
                // Copy to a new HashSet to avoid Hibernate collection access issues
                this.tiers = new HashSet<>();
                // Use toArray to avoid iterator issues with Hibernate collections
                Tier[] tiersArray = entityTiers.toArray(new Tier[0]);
                for (Tier tier : tiersArray) {
                    if (tier != null && tier.getName() != null) {
                        this.tiers.add(tier.getName());
                    }
                }
            } else {
                this.tiers = java.util.Collections.emptySet();
            }
        } catch (Exception e) {
            // Fallback: initialize empty set if there's any issue accessing tiers
            this.tiers = java.util.Collections.emptySet();
        }
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

    public Set<String> getTiers() {
        return tiers;
    }

    public void setTiers(Set<String> tiers) {
        this.tiers = tiers;
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

