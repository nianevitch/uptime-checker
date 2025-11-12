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
import com.isofuture.uptime.entity.MonitoredUrl;
import com.isofuture.uptime.repository.CheckResultRepository;
import com.isofuture.uptime.repository.MonitoredUrlRepository;

@Service
public class CheckService {

    private static final Logger log = LoggerFactory.getLogger(CheckService.class);
    private final MonitoredUrlRepository monitoredUrlRepository;
    private final CheckResultRepository checkResultRepository;
    private final UserContext userContext;
    private final HttpClient httpClient;

    public CheckService(
        MonitoredUrlRepository monitoredUrlRepository,
        CheckResultRepository checkResultRepository,
        UserContext userContext
    ) {
        this.monitoredUrlRepository = monitoredUrlRepository;
        this.checkResultRepository = checkResultRepository;
        this.userContext = userContext;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Transactional
    public CheckResultDto executeCheck(ExecuteCheckRequest request, boolean invokedByWorker) {
        log.debug("Executing check for monitor ID: {} (invokedByWorker: {})", request.getMonitorId(), invokedByWorker);
        MonitoredUrl monitor = loadAccessibleMonitor(request.getMonitorId(), invokedByWorker);

        monitor.setInProgress(true);
        monitor.setUpdatedAt(Instant.now());

        Instant start = Instant.now();
        Integer httpCode = null;
        String error = null;

        log.debug("Checking URL: {}", monitor.getUrl());
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(monitor.getUrl()))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();
            HttpResponse<Void> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
            httpCode = response.statusCode();
            log.debug("URL check completed: {} - HTTP {}", monitor.getUrl(), httpCode);
        } catch (IOException | InterruptedException e) {
            error = e.getMessage();
            log.warn("URL check failed: {} - {}", monitor.getUrl(), e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        double responseTime = Duration.between(start, Instant.now()).toMillis();
        log.debug("URL check response time: {}ms for {}", responseTime, monitor.getUrl());

        CheckResultUpdateRequest updateRequest = new CheckResultUpdateRequest();
        updateRequest.setMonitorId(monitor.getId());
        updateRequest.setHttpCode(httpCode);
        updateRequest.setErrorMessage(error);
        updateRequest.setResponseTimeMs(responseTime);
        updateRequest.setCheckedAt(Instant.now());

        return recordResult(updateRequest, invokedByWorker);
    }

    @Transactional
    public CheckResultDto recordResult(CheckResultUpdateRequest request, boolean invokedByWorker) {
        log.debug("Recording check result for monitor ID: {} (invokedByWorker: {})", request.getMonitorId(), invokedByWorker);
        MonitoredUrl monitor = loadAccessibleMonitor(request.getMonitorId(), invokedByWorker);

        CheckResult result = new CheckResult();
        result.setMonitoredUrl(monitor);
        result.setHttpCode(request.getHttpCode());
        result.setErrorMessage(request.getErrorMessage());
        result.setResponseTimeMs(request.getResponseTimeMs());
        result.setCheckedAt(request.getCheckedAt() != null ? request.getCheckedAt() : Instant.now());

        monitor.setInProgress(false);
        monitor.setNextCheckAt(Instant.now().plus(monitor.getFrequencyMinutes(), ChronoUnit.MINUTES));
        monitor.setUpdatedAt(Instant.now());

        CheckResult saved = checkResultRepository.save(result);
        log.info("Check result recorded: Monitor ID {} - HTTP {} - Response time {}ms", 
            monitor.getId(), saved.getHttpCode(), saved.getResponseTimeMs());

        CheckResultDto dto = new CheckResultDto();
        dto.setId(saved.getId());
        dto.setHttpCode(saved.getHttpCode());
        dto.setErrorMessage(saved.getErrorMessage());
        dto.setResponseTimeMs(saved.getResponseTimeMs());
        dto.setCheckedAt(saved.getCheckedAt());
        return dto;
    }

    private MonitoredUrl loadAccessibleMonitor(Long id, boolean invokedByWorker) {
        log.trace("Loading monitor ID: {} (invokedByWorker: {}, isAdmin: {})", id, invokedByWorker, userContext.isAdmin());
        if (userContext.isAdmin() || invokedByWorker) {
            return monitoredUrlRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Monitor not found: {}", id);
                    return new IllegalArgumentException("Monitor not found");
                });
        }
        Long userId = userContext.getCurrentUser().getId();
        return monitoredUrlRepository.findByIdAndOwnerId(id, userId)
            .orElseThrow(() -> {
                log.warn("Access denied: User {} attempted to access monitor {}", userId, id);
                return new AccessDeniedException("Forbidden");
            });
    }
}

