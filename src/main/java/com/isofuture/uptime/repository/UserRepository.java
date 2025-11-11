package com.isofuture.uptime.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.isofuture.uptime.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmailIgnoreCase(String email);
}

