package com.isofuture.uptime.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.isofuture.uptime.dto.CheckResultDto;
import com.isofuture.uptime.dto.CheckResultUpdateRequest;
import com.isofuture.uptime.dto.ExecuteCheckRequest;
import com.isofuture.uptime.entity.CheckResult;
import com.isofuture.uptime.entity.Ping;
import com.isofuture.uptime.repository.CheckResultRepository;
import com.isofuture.uptime.repository.PingRepository;

@Service
public class CheckService {

    private static final Logger log = LoggerFactory.getLogger(CheckService.class);
    private final PingRepository pingRepository;
    private final CheckResultRepository checkResultRepository;
    private final UserContext userContext;
    private final HttpClient httpClient;

    public CheckService(
        PingRepository pingRepository,
        CheckResultRepository checkResultRepository,
        UserContext userContext
    ) {
        this.pingRepository = pingRepository;
        this.checkResultRepository = checkResultRepository;
        this.userContext = userContext;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Transactional
    public CheckResultDto executeCheck(ExecuteCheckRequest request, boolean invokedByWorker) {
        log.debug("Executing check for ping ID: {} (invokedByWorker: {})", request.getPingId(), invokedByWorker);
        Ping ping = loadAccessiblePing(request.getPingId(), invokedByWorker);

        ping.setInProgress(true);
        ping.setUpdatedAt(Instant.now());
        // Don't save here - we'll save everything together in recordResult to avoid double saves

        Instant start = Instant.now();
        Integer httpCode = null;
        String error = null;

        log.debug("Checking URL: {}", ping.getUrl());
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ping.getUrl()))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();
            HttpResponse<Void> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
            httpCode = response.statusCode();
            log.debug("URL check completed: {} - HTTP {}", ping.getUrl(), httpCode);
        } catch (IOException | InterruptedException e) {
            error = e.getMessage();
            log.warn("URL check failed: {} - {}", ping.getUrl(), e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        double responseTime = Duration.between(start, Instant.now()).toMillis();
        Instant checkedAt = Instant.now();
        log.debug("URL check response time: {}ms for {}", responseTime, ping.getUrl());

        // Record result using the same ping entity instance
        return recordResult(ping, httpCode, error, responseTime, checkedAt, invokedByWorker);
    }

    @Transactional
    public CheckResultDto recordResult(CheckResultUpdateRequest request, boolean invokedByWorker) {
        log.debug("Recording check result for ping ID: {} (invokedByWorker: {})", request.getPingId(), invokedByWorker);
        Ping ping = loadAccessiblePing(request.getPingId(), invokedByWorker);
        return recordResult(ping, request.getHttpCode(), request.getErrorMessage(), 
            request.getResponseTimeMs(), request.getCheckedAt(), invokedByWorker);
    }

    private CheckResultDto recordResult(Ping ping, Integer httpCode, String errorMessage, 
            Double responseTimeMs, Instant checkedAt, boolean invokedByWorker) {
        log.debug("Recording check result for ping ID: {}", ping.getId());
        
        // Use the actual check time, or current time if not provided
        Instant checkTime = checkedAt != null ? checkedAt : Instant.now();
        Instant now = Instant.now();
        
        // Get frequency from the ping entity
        Integer frequencyMinutes = ping.getFrequencyMinutes();
        if (frequencyMinutes == null || frequencyMinutes <= 0) {
            log.warn("Invalid frequency minutes for ping ID {}: {}, using default of 5", ping.getId(), frequencyMinutes);
            frequencyMinutes = 5;
        }
        
        // Calculate next check time: check time + frequency minutes
        Instant nextCheckTime = checkTime.plus(frequencyMinutes, ChronoUnit.MINUTES);
        
        log.debug("Setting next check time for ping ID {}: check time={}, frequency={} minutes, next check={}, current time={}", 
            ping.getId(), checkTime, frequencyMinutes, nextCheckTime, now);
        
        // Update ping entity
        ping.setInProgress(false);
        ping.setNextCheckAt(nextCheckTime);
        ping.setUpdatedAt(now);
        
        // Create check result
        CheckResult result = new CheckResult();
        result.setPing(ping);
        result.setHttpCode(httpCode);
        result.setErrorMessage(errorMessage);
        result.setResponseTimeMs(responseTimeMs);
        result.setCheckedAt(checkTime);
        
        // Save ping entity first (this persists nextCheckAt)
        Ping savedPing = pingRepository.save(ping);
        log.debug("Saved ping ID {} with nextCheckAt: {}", savedPing.getId(), savedPing.getNextCheckAt());
        
        // Verify the saved value matches what we set
        if (savedPing.getNextCheckAt() == null || !savedPing.getNextCheckAt().equals(nextCheckTime)) {
            log.error("WARNING: nextCheckAt mismatch for ping ID {}: expected {}, got {}", 
                savedPing.getId(), nextCheckTime, savedPing.getNextCheckAt());
        }
        
        // Save check result
        CheckResult saved = checkResultRepository.save(result);
        
        log.info("Check result recorded: Ping ID {} - HTTP {} - Response time {}ms - Next check: {} (in {} minutes)", 
            ping.getId(), saved.getHttpCode() != null ? saved.getHttpCode() : "N/A", 
            saved.getResponseTimeMs() != null ? saved.getResponseTimeMs() : 0.0, 
            nextCheckTime, frequencyMinutes);

        CheckResultDto dto = new CheckResultDto();
        dto.setId(saved.getId());
        dto.setHttpCode(saved.getHttpCode());
        dto.setErrorMessage(saved.getErrorMessage());
        dto.setResponseTimeMs(saved.getResponseTimeMs());
        dto.setCheckedAt(saved.getCheckedAt());
        return dto;
    }

    private Ping loadAccessiblePing(Long id, boolean invokedByWorker) {
        log.trace("Loading ping ID: {} (invokedByWorker: {}, isAdmin: {})", id, invokedByWorker, userContext.isAdmin());
        if (userContext.isAdmin() || invokedByWorker) {
            return pingRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Ping not found: {}", id);
                    return new IllegalArgumentException("Ping not found");
                });
        }
        Long userId = userContext.getCurrentUser().getId();
        return pingRepository.findByIdAndOwnerId(id, userId)
            .orElseThrow(() -> {
                log.warn("Access denied: User {} attempted to access ping {}", userId, id);
                return new AccessDeniedException("Forbidden");
            });
    }
}

