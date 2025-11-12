package com.isofuture.uptime;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.isofuture.uptime.entity.Role;
import com.isofuture.uptime.entity.User;
import com.isofuture.uptime.security.SecurityUser;

/**
 * Base test class with common test utilities
 */
public abstract class BaseTest {

    protected User createUser(Long id, String email, String passwordHash, String... roles) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setCreatedAt(Instant.now());
        
        Set<Role> roleEntities = Set.of(roles).stream()
            .map(roleName -> {
                Role role = new Role();
                role.setName(roleName);
                return role;
            })
            .collect(Collectors.toSet());
        user.setRoles(roleEntities);
        
        return user;
    }

    protected SecurityUser createSecurityUser(Long id, String email, String... roles) {
        User user = createUser(id, email, "hashed", roles);
        return new SecurityUser(user);
    }

    protected SecurityUser createAdminUser(Long id, String email) {
        return createSecurityUser(id, email, "admin", "user");
    }

    protected SecurityUser createRegularUser(Long id, String email) {
        return createSecurityUser(id, email, "user");
    }
}




