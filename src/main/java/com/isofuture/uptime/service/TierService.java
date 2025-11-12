package com.isofuture.uptime.service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.isofuture.uptime.dto.TierRequest;
import com.isofuture.uptime.dto.TierResponse;
import com.isofuture.uptime.dto.TierUpdateRequest;
import com.isofuture.uptime.entity.Tier;
import com.isofuture.uptime.exception.ResourceNotFoundException;
import com.isofuture.uptime.repository.TierRepository;

@Service
public class TierService {

    private static final Logger log = LoggerFactory.getLogger(TierService.class);
    private final TierRepository tierRepository;
    private final UserContext userContext;

    public TierService(TierRepository tierRepository, UserContext userContext) {
        this.tierRepository = tierRepository;
        this.userContext = userContext;
    }

    @Transactional(readOnly = true)
    public List<TierResponse> listAll() {
        log.debug("Listing all tiers");
        if (!userContext.isAdmin()) {
            log.warn("Non-admin user attempted to list all tiers");
            throw new AccessDeniedException("Only admins can list all tiers");
        }
        List<TierResponse> tiers = tierRepository.findAllActive().stream()
            .map(TierResponse::new)
            .collect(Collectors.toList());
        log.debug("Found {} active tiers", tiers.size());
        return tiers;
    }

    @Transactional(readOnly = true)
    public TierResponse getById(Long id) {
        log.debug("Getting tier by ID: {}", id);
        if (!userContext.isAdmin()) {
            log.warn("Non-admin user attempted to get tier: {}", id);
            throw new AccessDeniedException("Only admins can view tiers");
        }
        Tier entity = tierRepository.findActiveById(id)
            .orElseThrow(() -> {
                log.warn("Tier not found: {}", id);
                return new ResourceNotFoundException("Tier not found");
            });
        log.debug("Tier found: {} ({})", entity.getName(), id);
        return new TierResponse(entity);
    }

    @Transactional
    public TierResponse create(TierRequest request) {
        log.debug("Creating tier: {}", request.getName());
        if (!userContext.isAdmin()) {
            log.warn("Non-admin user attempted to create tier: {}", request.getName());
            throw new AccessDeniedException("Only admins can create tiers");
        }

        tierRepository.findActiveByNameIgnoreCase(request.getName()).ifPresent(existing -> {
            log.warn("Attempted to create tier with existing name: {}", request.getName());
            throw new IllegalArgumentException("Tier name already exists");
        });

        Tier entity = new Tier();
        entity.setName(request.getName());
        entity.setCreatedAt(Instant.now());

        Tier saved = tierRepository.save(entity);
        log.info("Tier created successfully: {} (ID: {})", saved.getName(), saved.getId());
        return new TierResponse(saved);
    }

    @Transactional
    public TierResponse update(Long id, TierUpdateRequest request) {
        log.debug("Updating tier: {}", id);
        if (!userContext.isAdmin()) {
            log.warn("Non-admin user attempted to update tier: {}", id);
            throw new AccessDeniedException("Only admins can update tiers");
        }

        Tier entity = tierRepository.findActiveById(id)
            .orElseThrow(() -> {
                log.warn("Tier not found for update: {}", id);
                return new ResourceNotFoundException("Tier not found");
            });

        if (request.getName() != null && !request.getName().equals(entity.getName())) {
            log.debug("Updating name from {} to {}", entity.getName(), request.getName());
            tierRepository.findActiveByNameIgnoreCase(request.getName()).ifPresent(existing -> {
                log.warn("Attempted to update tier {} with existing name: {}", id, request.getName());
                throw new IllegalArgumentException("Tier name already exists");
            });
            entity.setName(request.getName());
        }

        Tier saved = tierRepository.save(entity);
        log.info("Tier updated successfully: {} (ID: {})", saved.getName(), saved.getId());
        return new TierResponse(saved);
    }

    /**
     * Soft deletes a tier by setting the deletedAt timestamp.
     * 
     * IMPORTANT: Tier records are NEVER physically deleted from the database.
     * This method only sets the deletedAt timestamp, making the tier invisible
     * to all active tier queries (which filter by deletedAt IS NULL).
     * 
     * After soft delete:
     * - Tier will not appear in listAll() (uses findAllActive())
     * - Tier will not appear in getById() (uses findActiveById())
     * - Tier will not appear in findActiveByNameIgnoreCase()
     * - Tier record remains in database for audit/history purposes
     * - Users with this tier will still have the relationship, but the tier
     *   will not be accessible through normal tier queries
     * 
     * @param id Tier ID to soft delete
     * @throws AccessDeniedException if current user is not an admin
     * @throws ResourceNotFoundException if tier not found
     */
    @Transactional
    public void softDelete(Long id) {
        log.debug("Soft deleting tier: {}", id);
        if (!userContext.isAdmin()) {
            log.warn("Non-admin user attempted to delete tier: {}", id);
            throw new AccessDeniedException("Only admins can delete tiers");
        }

        Tier entity = tierRepository.findActiveById(id)
            .orElseThrow(() -> {
                log.warn("Tier not found for deletion: {}", id);
                return new ResourceNotFoundException("Tier not found");
            });

        // SOFT DELETE: Set deletedAt timestamp - record is NEVER physically deleted
        entity.setDeletedAt(Instant.now());
        tierRepository.save(entity);
        log.info("Tier soft deleted successfully: {} (ID: {})", entity.getName(), id);
        
        // Note: The tier record remains in the database with deletedAt set.
        // All active tier queries filter by deletedAt IS NULL, so this tier
        // will be effectively invisible to the application.
    }
}


