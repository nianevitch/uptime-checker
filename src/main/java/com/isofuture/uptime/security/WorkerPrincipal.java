package com.isofuture.uptime.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;

public class WorkerPrincipal implements org.springframework.security.core.userdetails.UserDetails {

    private final String name;

    public WorkerPrincipal(String name) {
        this.name = name;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return name;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}


