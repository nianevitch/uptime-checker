package com.isofuture.uptime.security;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.isofuture.uptime.entity.User;

public class SecurityUser implements UserDetails {

	private static final long serialVersionUID = -4625746023998045208L;
	private final Long id;
    private final String email;
    private final String passwordHash;
    private final Set<String> roles;

    public SecurityUser(User entity) {
        this.id = entity.getId();
        this.email = entity.getEmail();
        this.passwordHash = entity.getPasswordHash();
        this.roles = entity.getRoles() != null 
            ? entity.getRoles().stream()
                .map(role -> "ROLE_" + role.getName().toUpperCase())
                .collect(Collectors.toUnmodifiableSet())
            : Set.of();
    }

    public Long getId() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
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

