package com.isofuture.uptime.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.isofuture.uptime.entity.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByNameIgnoreCase(String name);
}

