package com.isofuture.uptime.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.isofuture.uptime.dto.CheckResultDto;
import com.isofuture.uptime.dto.CheckResultUpdateRequest;
import com.isofuture.uptime.dto.ExecuteCheckRequest;
import com.isofuture.uptime.entity.CheckResultEntity;
import com.isofuture.uptime.entity.MonitoredUrlEntity;
import com.isofuture.uptime.repository.CheckResultRepository;
import com.isofuture.uptime.repository.MonitoredUrlRepository;

@Service
public class CheckService {

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
    public CheckResultDto executeCheck(ExecuteCheckRequest request) {
        MonitoredUrlEntity monitor = loadAccessibleMonitor(request.getMonitorId());

        monitor.setInProgress(true);
        monitor.setUpdatedAt(Instant.now());

        Instant start = Instant.now();
        Integer httpCode = null;
        String error = null;

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(monitor.getUrl()))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();
            HttpResponse<Void> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
            httpCode = response.statusCode();
        } catch (IOException | InterruptedException e) {
            error = e.getMessage();
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        double responseTime = Duration.between(start, Instant.now()).toMillis();

        CheckResultUpdateRequest updateRequest = new CheckResultUpdateRequest();
        updateRequest.setMonitorId(monitor.getId());
        updateRequest.setHttpCode(httpCode);
        updateRequest.setErrorMessage(error);
        updateRequest.setResponseTimeMs(responseTime);
        updateRequest.setCheckedAt(Instant.now());

        return recordResult(updateRequest);
    }

    @Transactional
    public CheckResultDto recordResult(CheckResultUpdateRequest request) {
        MonitoredUrlEntity monitor = loadAccessibleMonitor(request.getMonitorId());

        CheckResultEntity result = new CheckResultEntity();
        result.setMonitoredUrl(monitor);
        result.setHttpCode(request.getHttpCode());
        result.setErrorMessage(request.getErrorMessage());
        result.setResponseTimeMs(request.getResponseTimeMs());
        result.setCheckedAt(request.getCheckedAt() != null ? request.getCheckedAt() : Instant.now());

        monitor.setInProgress(false);
        monitor.setNextCheckAt(Instant.now().plus(monitor.getFrequencyMinutes(), ChronoUnit.MINUTES));
        monitor.setUpdatedAt(Instant.now());

        CheckResultEntity saved = checkResultRepository.save(result);

        CheckResultDto dto = new CheckResultDto();
        dto.setId(saved.getId());
        dto.setHttpCode(saved.getHttpCode());
        dto.setErrorMessage(saved.getErrorMessage());
        dto.setResponseTimeMs(saved.getResponseTimeMs());
        dto.setCheckedAt(saved.getCheckedAt());
        return dto;
    }

    private MonitoredUrlEntity loadAccessibleMonitor(Long id) {
        if (userContext.isAdmin() || userContext.isWorker()) {
            return monitoredUrlRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Monitor not found"));
        }
        return monitoredUrlRepository.findByIdAndOwnerId(id, userContext.getCurrentUser().getId())
            .orElseThrow(() -> new IllegalArgumentException("Monitor not found"));
    }
}

