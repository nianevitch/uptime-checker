package com.isofuture.uptime.dto;

import java.util.Set;

import jakarta.validation.constraints.Email;

public class UserUpdateRequest {

    @Email
    private String email;

    private Set<String> roles;

    private Set<String> tiers;

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
}

