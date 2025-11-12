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
        log.debug("URL check response time: {}ms for {}", responseTime, ping.getUrl());

        CheckResultUpdateRequest updateRequest = new CheckResultUpdateRequest();
        updateRequest.setPingId(ping.getId());
        updateRequest.setHttpCode(httpCode);
        updateRequest.setErrorMessage(error);
        updateRequest.setResponseTimeMs(responseTime);
        updateRequest.setCheckedAt(Instant.now());

        return recordResult(updateRequest, invokedByWorker);
    }

    @Transactional
    public CheckResultDto recordResult(CheckResultUpdateRequest request, boolean invokedByWorker) {
        log.debug("Recording check result for ping ID: {} (invokedByWorker: {})", request.getPingId(), invokedByWorker);
        Ping ping = loadAccessiblePing(request.getPingId(), invokedByWorker);

        CheckResult result = new CheckResult();
        result.setPing(ping);
        result.setHttpCode(request.getHttpCode());
        result.setErrorMessage(request.getErrorMessage());
        result.setResponseTimeMs(request.getResponseTimeMs());
        result.setCheckedAt(request.getCheckedAt() != null ? request.getCheckedAt() : Instant.now());

        ping.setInProgress(false);
        ping.setNextCheckAt(Instant.now().plus(ping.getFrequencyMinutes(), ChronoUnit.MINUTES));
        ping.setUpdatedAt(Instant.now());

        CheckResult saved = checkResultRepository.save(result);
        log.info("Check result recorded: Ping ID {} - HTTP {} - Response time {}ms", 
            ping.getId(), saved.getHttpCode(), saved.getResponseTimeMs());

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

