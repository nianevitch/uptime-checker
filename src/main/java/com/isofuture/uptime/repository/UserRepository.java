package com.isofuture.uptime.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.isofuture.uptime.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmailIgnoreCase(String email);
    
    @Query("SELECT u FROM UserEntity u WHERE u.deletedAt IS NULL")
    List<UserEntity> findAllActive();
    
    @Query("SELECT u FROM UserEntity u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<UserEntity> findActiveById(@Param("id") Long id);
    
    @Query("SELECT u FROM UserEntity u WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<UserEntity> findActiveByEmailIgnoreCase(@Param("email") String email);
}

