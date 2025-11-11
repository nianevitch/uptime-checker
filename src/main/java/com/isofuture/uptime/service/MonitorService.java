package com.isofuture.uptime.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.isofuture.uptime.dto.CheckResultDto;
import com.isofuture.uptime.dto.MonitoredUrlRequest;
import com.isofuture.uptime.dto.MonitoredUrlResponse;
import com.isofuture.uptime.dto.PendingCheckResponse;
import com.isofuture.uptime.entity.MonitoredUrlEntity;
import com.isofuture.uptime.entity.UserEntity;
import com.isofuture.uptime.mapper.MonitoredUrlMapper;
import com.isofuture.uptime.repository.CheckResultRepository;
import com.isofuture.uptime.repository.MonitoredUrlRepository;
import com.isofuture.uptime.repository.UserRepository;
import com.isofuture.uptime.security.SecurityUser;

@Service
public class MonitorService {

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
        List<MonitoredUrlEntity> entities;
        if (userContext.isAdmin()) {
            entities = monitoredUrlRepository.findAll();
        } else {
            SecurityUser currentUser = userContext.getCurrentUser();
            UserEntity user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));
            entities = monitoredUrlRepository.findByOwner(user);
        }

        return entities.stream()
            .map(entity -> toResponse(entity, DEFAULT_RECENT_RESULTS))
            .toList();
    }

    @Transactional
    public MonitoredUrlResponse createMonitor(MonitoredUrlRequest request) {
        SecurityUser currentUser = userContext.getCurrentUser();
        UserEntity owner = userRepository.findById(currentUser.getId())
            .orElseThrow(() -> new IllegalStateException("User not found"));

        MonitoredUrlEntity entity = new MonitoredUrlEntity();
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
            MonitoredUrlEntity saved = monitoredUrlRepository.save(entity);
            return toResponse(saved, DEFAULT_RECENT_RESULTS);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to create monitor. It may already exist.", e);
        }
    }

    @Transactional
    public MonitoredUrlResponse updateMonitor(Long id, MonitoredUrlRequest request) {
        MonitoredUrlEntity entity = loadOwnedMonitor(id);

        entity.setLabel(request.getLabel());
        entity.setUrl(request.getUrl());
        entity.setFrequencyMinutes(request.getFrequencyMinutes());
        if (entity.getNextCheckAt() == null) {
            entity.setNextCheckAt(calculateNextCheck(request.getFrequencyMinutes()));
        }
        entity.setUpdatedAt(Instant.now());

        try {
            return toResponse(entity, DEFAULT_RECENT_RESULTS);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to update monitor.", e);
        }
    }

    @Transactional
    public void deleteMonitor(Long id) {
        MonitoredUrlEntity entity = loadOwnedMonitor(id);
        monitoredUrlRepository.delete(entity);
    }

    @Transactional
    public List<PendingCheckResponse> claimPendingChecks(int limit) {
        Instant now = Instant.now();
        List<MonitoredUrlEntity> candidates = monitoredUrlRepository.findReadyForCheck(now);
        return candidates.stream()
            .limit(limit)
            .map(entity -> {
                entity.setInProgress(true);
                entity.setUpdatedAt(now);
                return new PendingCheckResponse(entity.getId(), entity.getUrl(), entity.getLabel());
            })
            .collect(Collectors.toList());
    }

    private MonitoredUrlResponse toResponse(MonitoredUrlEntity entity, int recentLimit) {
        List<CheckResultDto> latest = checkResultRepository
            .findByMonitoredUrlOrderByCheckedAtDesc(entity)
            .stream()
            .limit(recentLimit)
            .map(mapper::toDto)
            .toList();
        return mapper.toResponse(entity, latest);
    }

    private MonitoredUrlEntity loadOwnedMonitor(Long id) {
        if (userContext.isAdmin()) {
            return monitoredUrlRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Monitor not found"));
        }

        SecurityUser currentUser = userContext.getCurrentUser();
        return monitoredUrlRepository.findByIdAndOwnerId(id, currentUser.getId())
            .orElseThrow(() -> new IllegalArgumentException("Monitor not found"));
    }

    private Instant calculateNextCheck(int frequencyMinutes) {
        return Instant.now().plus(frequencyMinutes, ChronoUnit.MINUTES);
    }
}

