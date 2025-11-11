package com.isofuture.uptime.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.isofuture.uptime.entity.RoleEntity;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {
    Optional<RoleEntity> findByNameIgnoreCase(String name);
}

