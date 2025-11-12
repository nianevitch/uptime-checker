package com.isofuture.uptime.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.isofuture.uptime.dto.CheckResultDto;
import com.isofuture.uptime.dto.PingRequest;
import com.isofuture.uptime.dto.PingResponse;
import com.isofuture.uptime.dto.PendingCheckResponse;
import com.isofuture.uptime.entity.Ping;
import com.isofuture.uptime.entity.User;
import com.isofuture.uptime.mapper.PingMapper;
import com.isofuture.uptime.repository.CheckResultRepository;
import com.isofuture.uptime.repository.PingRepository;
import com.isofuture.uptime.repository.UserRepository;
import com.isofuture.uptime.security.SecurityUser;

@Service
public class PingService {

    private static final Logger log = LoggerFactory.getLogger(PingService.class);
    private static final int DEFAULT_RECENT_RESULTS = 10;

    private final PingRepository pingRepository;
    private final CheckResultRepository checkResultRepository;
    private final UserRepository userRepository;
    private final PingMapper mapper;
    private final UserContext userContext;

    public PingService(
        PingRepository pingRepository,
        CheckResultRepository checkResultRepository,
        UserRepository userRepository,
        PingMapper mapper,
        UserContext userContext
    ) {
        this.pingRepository = pingRepository;
        this.checkResultRepository = checkResultRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.userContext = userContext;
    }

    @Transactional(readOnly = true)
    public List<PingResponse> listCurrentUserPings() {
        log.debug("Listing pings for current user");
        List<Ping> entities;
        if (userContext.isAdmin()) {
            log.debug("Admin user - listing all pings");
            entities = pingRepository.findAll();
        } else {
            SecurityUser currentUser = userContext.getCurrentUser();
            log.debug("Listing pings for user: {} (ID: {})", currentUser.getUsername(), currentUser.getId());
            User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> {
                    log.error("User not found: {}", currentUser.getId());
                    return new IllegalStateException("User not found");
                });
            entities = pingRepository.findByOwner(user);
        }

        List<PingResponse> responses = entities.stream()
            .map(entity -> toResponse(entity, DEFAULT_RECENT_RESULTS))
            .toList();
        log.debug("Found {} pings", responses.size());
        return responses;
    }

    @Transactional
    public PingResponse createPing(PingRequest request) {
        SecurityUser currentUser = userContext.getCurrentUser();
        log.debug("Creating ping: {} for user: {} (ID: {})", request.getUrl(), currentUser.getUsername(), currentUser.getId());
        User owner = userRepository.findById(currentUser.getId())
            .orElseThrow(() -> {
                log.error("User not found: {}", currentUser.getId());
                return new IllegalStateException("User not found");
            });

        Ping entity = new Ping();
        entity.setOwner(owner);
        entity.setLabel(request.getLabel());
        entity.setUrl(request.getUrl());
        entity.setFrequencyMinutes(request.getFrequencyMinutes());
        entity.setNextCheckAt(calculateNextCheck(request.getFrequencyMinutes()));
        entity.setInProgress(false);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        try {
            Ping saved = pingRepository.save(entity);
            log.info("Ping created successfully: {} (ID: {}) for user: {}", saved.getUrl(), saved.getId(), owner.getEmail());
            return toResponse(saved, DEFAULT_RECENT_RESULTS);
        } catch (Exception e) {
            log.error("Failed to create ping: {} - {}", request.getUrl(), e.getMessage(), e);
            throw new IllegalArgumentException("Unable to create ping. It may already exist.", e);
        }
    }

    @Transactional
    public PingResponse updatePing(Long id, PingRequest request) {
        log.debug("Updating ping: {}", id);
        Ping entity = loadOwnedPing(id);

        entity.setLabel(request.getLabel());
        entity.setUrl(request.getUrl());
        entity.setFrequencyMinutes(request.getFrequencyMinutes());
        // Only update nextCheckAt if it's null (don't overwrite existing scheduled checks)
        if (entity.getNextCheckAt() == null) {
            entity.setNextCheckAt(calculateNextCheck(request.getFrequencyMinutes()));
        }
        entity.setUpdatedAt(Instant.now());

        try {
            // Save the entity to persist changes
            Ping saved = pingRepository.save(entity);
            PingResponse response = toResponse(saved, DEFAULT_RECENT_RESULTS);
            log.info("Ping updated successfully: {} (ID: {})", saved.getUrl(), id);
            return response;
        } catch (Exception e) {
            log.error("Failed to update ping: {} - {}", id, e.getMessage(), e);
            throw new IllegalArgumentException("Unable to update ping.", e);
        }
    }

    @Transactional
    public void deletePing(Long id) {
        log.debug("Deleting ping: {}", id);
        Ping entity = loadOwnedPing(id);
        pingRepository.delete(entity);
        log.info("Ping deleted successfully: {} (ID: {})", entity.getUrl(), id);
    }

    @Transactional(readOnly = true)
    public List<PendingCheckResponse> getInProgressChecks(Integer limit) {
        log.debug("Getting in-progress checks (limit: {})", limit);
        Stream<Ping> stream = pingRepository.findByInProgressTrueOrderByUpdatedAtAsc().stream();
        if (limit != null) {
            stream = stream.limit(limit);
        }
        List<PendingCheckResponse> pending = stream
            .map(entity -> new PendingCheckResponse(entity.getId(), entity.getUrl(), entity.getLabel()))
            .collect(Collectors.toList());
        log.debug("Found {} in-progress checks", pending.size());
        return pending;
    }

    @Transactional
    public List<PendingCheckResponse> fetchNextChecks(int limit) {
        log.debug("Fetching next checks (limit: {})", limit);
        Instant now = Instant.now();
        List<Ping> candidates = pingRepository.findReadyForCheck(now);
        log.debug("Found {} candidates for next check", candidates.size());
        List<PendingCheckResponse> next = candidates.stream()
            .limit(limit)
            .map(entity -> {
                entity.setInProgress(true);
                entity.setUpdatedAt(now);
                PendingCheckResponse response = new PendingCheckResponse(entity.getId(), entity.getUrl(), entity.getLabel());
                return response;
            })
            .collect(Collectors.toList());
        log.info("Fetched {} next checks", next.size());
        return next;
    }

    private PingResponse toResponse(Ping entity, int recentLimit) {
        List<CheckResultDto> latest = checkResultRepository
            .findByPingOrderByCheckedAtDesc(entity)
            .stream()
            .limit(recentLimit)
            .map(mapper::toDto)
            .toList();
        return mapper.toResponse(entity, latest);
    }

    private Ping loadOwnedPing(Long id) {
        log.trace("Loading ping: {} (isAdmin: {})", id, userContext.isAdmin());
        if (userContext.isAdmin()) {
            return pingRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Ping not found: {}", id);
                    return new IllegalArgumentException("Ping not found");
                });
        }

        SecurityUser currentUser = userContext.getCurrentUser();
        return pingRepository.findByIdAndOwnerId(id, currentUser.getId())
            .orElseThrow(() -> {
                log.warn("Ping not found or access denied: {} for user {}", id, currentUser.getId());
                return new IllegalArgumentException("Ping not found");
            });
    }

    private Instant calculateNextCheck(int frequencyMinutes) {
        return Instant.now().plus(frequencyMinutes, ChronoUnit.MINUTES);
    }
}

