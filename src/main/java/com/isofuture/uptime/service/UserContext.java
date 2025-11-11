package com.isofuture.uptime.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.isofuture.uptime.security.SecurityUser;

@Component
public class UserContext {

    public SecurityUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityUser securityUser)) {
            throw new IllegalStateException("No authenticated user");
        }
        return securityUser;
    }

    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        String expectation = "ROLE_" + role.toUpperCase();
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(expectation::equals);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean isWorker() {
        return hasRole("WORKER");
    }
}

