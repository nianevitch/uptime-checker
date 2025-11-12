package com.isofuture.uptime.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.isofuture.uptime.entity.User;

/**
 * UserRepository - Repository for User
 * 
 * IMPORTANT POLICY: User records are NEVER physically deleted from the database.
 * All user deletion operations MUST use soft delete by setting the deletedAt timestamp.
 * 
 * The service layer (UserService.softDelete()) ensures this policy is followed.
 * Hard delete methods from JpaRepository (delete(), deleteById(), deleteAll()) 
 * should NEVER be called directly in production code.
 * 
 * All query methods filter by deletedAt IS NULL to exclude soft-deleted users
 * from normal operations. Soft-deleted users are effectively invisible to the application.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Finds a user by email (case-insensitive).
     * 
     * WARNING: This method does NOT filter by deletedAt - it can return deleted users.
     * For active users only, use findActiveByEmailIgnoreCase() instead.
     * 
     * @deprecated This method can return deleted users. Use findActiveByEmailIgnoreCase() for active users.
     * This method is only kept for backward compatibility and should not be used in new code.
     */
    @Deprecated
    Optional<User> findByEmailIgnoreCase(String email);
    
    /**
     * Finds all active (non-deleted) users.
     * Only returns users where deletedAt IS NULL.
     * 
     * @return List of active users (deletedAt IS NULL)
     */
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles WHERE u.deletedAt IS NULL")
    List<User> findAllActive();
    
    /**
     * Finds an active user by ID.
     * Only returns user if deletedAt IS NULL.
     * 
     * @param id User ID
     * @return Optional containing the active user, or empty if not found or deleted
     */
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<User> findActiveById(@Param("id") Long id);
    
    /**
     * Finds an active user by email (case-insensitive).
     * Only returns user if deletedAt IS NULL.
     * 
     * @param email User email (case-insensitive)
     * @return Optional containing the active user, or empty if not found or deleted
     */
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles WHERE LOWER(u.email) = LOWER(:email) AND u.deletedAt IS NULL")
    Optional<User> findActiveByEmailIgnoreCase(@Param("email") String email);
    
    /**
     * Finds a user by ID including deleted users.
     * This method is provided for cases where you need to check if a user exists
     * regardless of deletion status (e.g., to verify soft delete was applied).
     * For normal operations, use findActiveById() instead.
     * 
     * @param id User ID
     * @return Optional containing the user (including deleted), or empty if not found
     */
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles WHERE u.id = :id")
    Optional<User> findByIdIncludingDeleted(@Param("id") Long id);
}

