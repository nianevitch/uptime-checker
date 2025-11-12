package com.isofuture.uptime.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.isofuture.uptime.entity.Tier;

/**
 * TierRepository - Repository for Tier
 * 
 * IMPORTANT POLICY: Tier records are NEVER physically deleted from the database.
 * All tier deletion operations MUST use soft delete by setting the deletedAt timestamp.
 * 
 * The service layer (TierService.softDelete()) ensures this policy is followed.
 * Hard delete methods from JpaRepository (delete(), deleteById(), deleteAll()) 
 * should NEVER be called directly in production code.
 * 
 * All query methods filter by deletedAt IS NULL to exclude soft-deleted tiers
 * from normal operations. Soft-deleted tiers are effectively invisible to the application.
 */
public interface TierRepository extends JpaRepository<Tier, Long> {
    
    /**
     * Finds a tier by name (case-insensitive).
     * 
     * WARNING: This method does NOT filter by deletedAt - it can return deleted tiers.
     * For active tiers only, use findActiveByNameIgnoreCase() instead.
     * 
     * @deprecated This method can return deleted tiers. Use findActiveByNameIgnoreCase() for active tiers.
     * This method is only kept for backward compatibility and should not be used in new code.
     */
    @Deprecated
    Optional<Tier> findByNameIgnoreCase(String name);
    
    /**
     * Finds all active (non-deleted) tiers.
     * Only returns tiers where deletedAt IS NULL.
     * 
     * @return List of active tiers (deletedAt IS NULL)
     */
    @Query("SELECT t FROM Tier t WHERE t.deletedAt IS NULL ORDER BY t.name")
    List<Tier> findAllActive();
    
    /**
     * Finds an active tier by ID.
     * Only returns tier if deletedAt IS NULL.
     * 
     * @param id Tier ID
     * @return Optional containing the active tier, or empty if not found or deleted
     */
    @Query("SELECT t FROM Tier t WHERE t.id = :id AND t.deletedAt IS NULL")
    Optional<Tier> findActiveById(@Param("id") Long id);
    
    /**
     * Finds an active tier by name (case-insensitive).
     * Only returns tier if deletedAt IS NULL.
     * 
     * @param name Tier name (case-insensitive)
     * @return Optional containing the active tier, or empty if not found or deleted
     */
    @Query("SELECT t FROM Tier t WHERE LOWER(t.name) = LOWER(:name) AND t.deletedAt IS NULL")
    Optional<Tier> findActiveByNameIgnoreCase(@Param("name") String name);
    
    /**
     * Finds a tier by ID including deleted tiers.
     * This method is provided for cases where you need to check if a tier exists
     * regardless of deletion status (e.g., to verify soft delete was applied).
     * For normal operations, use findActiveById() instead.
     * 
     * @param id Tier ID
     * @return Optional containing the tier (including deleted), or empty if not found
     */
    @Query("SELECT t FROM Tier t WHERE t.id = :id")
    Optional<Tier> findByIdIncludingDeleted(@Param("id") Long id);
    
    /**
     * Finds all tiers including deleted tiers.
     * This method is provided for migration/maintenance purposes where you need
     * to update all tiers regardless of deletion status (e.g., fixing NULL created_at).
     * For normal operations, use findAllActive() instead.
     * 
     * @return List of all tiers (including deleted)
     */
    @Query("SELECT t FROM Tier t")
    List<Tier> findAllIncludingDeleted();
}

