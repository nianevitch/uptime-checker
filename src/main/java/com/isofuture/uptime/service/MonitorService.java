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
import com.isofuture.uptime.dto.MonitoredUrlRequest;
import com.isofuture.uptime.dto.MonitoredUrlResponse;
import com.isofuture.uptime.dto.PendingCheckResponse;
import com.isofuture.uptime.entity.MonitoredUrl;
import com.isofuture.uptime.entity.User;
import com.isofuture.uptime.mapper.MonitoredUrlMapper;
import com.isofuture.uptime.repository.CheckResultRepository;
import com.isofuture.uptime.repository.MonitoredUrlRepository;
import com.isofuture.uptime.repository.UserRepository;
import com.isofuture.uptime.security.SecurityUser;

@Service
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);
    private static final int DEFAULT_RECENT_RESULTS = 10;

    private final MonitoredUrlRepository monitoredUrlRepository;
    private final CheckResultRepository checkResultRepository;
    private final UserRepository userRepository;
    private final MonitoredUrlMapper mapper;
    private final UserContext userContext;

    public MonitorService(
        MonitoredUrlRepository monitoredUrlRepository,
        CheckResultRepository checkResultRepository,
        UserRepository userRepository,
        MonitoredUrlMapper mapper,
        UserContext userContext
    ) {
        this.monitoredUrlRepository = monitoredUrlRepository;
        this.checkResultRepository = checkResultRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.userContext = userContext;
    }

    @Transactional(readOnly = true)
    public List<MonitoredUrlResponse> listCurrentUserMonitors() {
        log.debug("Listing monitors for current user");
        List<MonitoredUrl> entities;
        if (userContext.isAdmin()) {
            log.debug("Admin user - listing all monitors");
            entities = monitoredUrlRepository.findAll();
        } else {
            SecurityUser currentUser = userContext.getCurrentUser();
            log.debug("Listing monitors for user: {} (ID: {})", currentUser.getUsername(), currentUser.getId());
            User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> {
                    log.error("User not found: {}", currentUser.getId());
                    return new IllegalStateException("User not found");
                });
            entities = monitoredUrlRepository.findByOwner(user);
        }

        List<MonitoredUrlResponse> responses = entities.stream()
            .map(entity -> toResponse(entity, DEFAULT_RECENT_RESULTS))
            .toList();
        log.debug("Found {} monitors", responses.size());
        return responses;
    }

    @Transactional
    public MonitoredUrlResponse createMonitor(MonitoredUrlRequest request) {
        SecurityUser currentUser = userContext.getCurrentUser();
        log.debug("Creating monitor: {} for user: {} (ID: {})", request.getUrl(), currentUser.getUsername(), currentUser.getId());
        User owner = userRepository.findById(currentUser.getId())
            .orElseThrow(() -> {
                log.error("User not found: {}", currentUser.getId());
                return new IllegalStateException("User not found");
            });

        MonitoredUrl entity = new MonitoredUrl();
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
            MonitoredUrl saved = monitoredUrlRepository.save(entity);
            log.info("Monitor created successfully: {} (ID: {}) for user: {}", saved.getUrl(), saved.getId(), owner.getEmail());
            return toResponse(saved, DEFAULT_RECENT_RESULTS);
        } catch (Exception e) {
            log.error("Failed to create monitor: {} - {}", request.getUrl(), e.getMessage(), e);
            throw new IllegalArgumentException("Unable to create monitor. It may already exist.", e);
        }
    }

    @Transactional
    public MonitoredUrlResponse updateMonitor(Long id, MonitoredUrlRequest request) {
        log.debug("Updating monitor: {}", id);
        MonitoredUrl entity = loadOwnedMonitor(id);

        entity.setLabel(request.getLabel());
        entity.setUrl(request.getUrl());
        entity.setFrequencyMinutes(request.getFrequencyMinutes());
        if (entity.getNextCheckAt() == null) {
            entity.setNextCheckAt(calculateNextCheck(request.getFrequencyMinutes()));
        }
        entity.setUpdatedAt(Instant.now());

        try {
            MonitoredUrlResponse response = toResponse(entity, DEFAULT_RECENT_RESULTS);
            log.info("Monitor updated successfully: {} (ID: {})", entity.getUrl(), id);
            return response;
        } catch (Exception e) {
            log.error("Failed to update monitor: {} - {}", id, e.getMessage(), e);
            throw new IllegalArgumentException("Unable to update monitor.", e);
        }
    }

    @Transactional
    public void deleteMonitor(Long id) {
        log.debug("Deleting monitor: {}", id);
        MonitoredUrl entity = loadOwnedMonitor(id);
        monitoredUrlRepository.delete(entity);
        log.info("Monitor deleted successfully: {} (ID: {})", entity.getUrl(), id);
    }

    @Transactional(readOnly = true)
    public List<PendingCheckResponse> getInProgressChecks(Integer limit) {
        log.debug("Getting in-progress checks (limit: {})", limit);
        Stream<MonitoredUrl> stream = monitoredUrlRepository.findByInProgressTrueOrderByUpdatedAtAsc().stream();
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
        List<MonitoredUrl> candidates = monitoredUrlRepository.findReadyForCheck(now);
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

    private MonitoredUrlResponse toResponse(MonitoredUrl entity, int recentLimit) {
        List<CheckResultDto> latest = checkResultRepository
            .findByMonitoredUrlOrderByCheckedAtDesc(entity)
            .stream()
            .limit(recentLimit)
            .map(mapper::toDto)
            .toList();
        return mapper.toResponse(entity, latest);
    }

    private MonitoredUrl loadOwnedMonitor(Long id) {
        log.trace("Loading monitor: {} (isAdmin: {})", id, userContext.isAdmin());
        if (userContext.isAdmin()) {
            return monitoredUrlRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Monitor not found: {}", id);
                    return new IllegalArgumentException("Monitor not found");
                });
        }

        SecurityUser currentUser = userContext.getCurrentUser();
        return monitoredUrlRepository.findByIdAndOwnerId(id, currentUser.getId())
            .orElseThrow(() -> {
                log.warn("Monitor not found or access denied: {} for user {}", id, currentUser.getId());
                return new IllegalArgumentException("Monitor not found");
            });
    }

    private Instant calculateNextCheck(int frequencyMinutes) {
        return Instant.now().plus(frequencyMinutes, ChronoUnit.MINUTES);
    }
}

