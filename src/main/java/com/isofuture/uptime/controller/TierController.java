package com.isofuture.uptime.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.isofuture.uptime.dto.TierRequest;
import com.isofuture.uptime.dto.TierResponse;
import com.isofuture.uptime.dto.TierUpdateRequest;
import com.isofuture.uptime.service.TierService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tiers")
public class TierController {

    private static final Logger log = LoggerFactory.getLogger(TierController.class);
    private final TierService tierService;

    public TierController(TierService tierService) {
        this.tierService = tierService;
    }

    @GetMapping
    public ResponseEntity<List<TierResponse>> listAll() {
        log.debug("GET /api/tiers - Listing all tiers");
        List<TierResponse> tiers = tierService.listAll();
        log.info("GET /api/tiers - Found {} tiers", tiers.size());
        return ResponseEntity.ok(tiers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TierResponse> getById(@PathVariable("id") Long id) {
        log.debug("GET /api/tiers/{} - Getting tier by ID", id);
        TierResponse tier = tierService.getById(id);
        log.info("GET /api/tiers/{} - Tier found: {}", id, tier.getName());
        return ResponseEntity.ok(tier);
    }

    @PostMapping
    public ResponseEntity<TierResponse> create(@Valid @RequestBody TierRequest request) {
        log.debug("POST /api/tiers - Creating tier: {}", request.getName());
        TierResponse tier = tierService.create(request);
        log.info("POST /api/tiers - Tier created: {} (ID: {})", tier.getName(), tier.getId());
        return ResponseEntity.ok(tier);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TierResponse> update(
        @PathVariable("id") Long id,
        @Valid @RequestBody TierUpdateRequest request
    ) {
        log.debug("PUT /api/tiers/{} - Updating tier", id);
        TierResponse tier = tierService.update(id, request);
        log.info("PUT /api/tiers/{} - Tier updated: {}", id, tier.getName());
        return ResponseEntity.ok(tier);
    }

    /**
     * Soft deletes a tier.
     * 
     * IMPORTANT: Tier records are NEVER physically deleted from the database.
     * This endpoint performs a soft delete by setting the deletedAt timestamp.
     * The tier record remains in the database for audit/history purposes.
     * 
     * After deletion, the tier will be invisible to all normal operations
     * (listAll, getById) but the record persists in the database.
     * 
     * @param id Tier ID to delete
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        log.debug("DELETE /api/tiers/{} - Soft deleting tier", id);
        tierService.softDelete(id);
        log.info("DELETE /api/tiers/{} - Tier soft deleted successfully", id);
        return ResponseEntity.noContent().build();
    }
}


