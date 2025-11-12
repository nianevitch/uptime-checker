package com.isofuture.uptime.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.isofuture.uptime.dto.CheckResultDto;
import com.isofuture.uptime.dto.CheckResultUpdateRequest;
import com.isofuture.uptime.dto.ExecuteCheckRequest;
import com.isofuture.uptime.dto.PendingCheckResponse;
import com.isofuture.uptime.service.CheckService;
import com.isofuture.uptime.service.PingService;
import com.isofuture.uptime.service.WorkerApiKeyService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/checks")
public class CheckController {

    private static final Logger log = LoggerFactory.getLogger(CheckController.class);
    private final CheckService checkService;
    private final PingService pingService;
    private final WorkerApiKeyService workerApiKeyService;

    public CheckController(CheckService checkService, PingService pingService, WorkerApiKeyService workerApiKeyService) {
        this.checkService = checkService;
        this.pingService = pingService;
        this.workerApiKeyService = workerApiKeyService;
    }

    @GetMapping("/pending")
    public ResponseEntity<List<PendingCheckResponse>> fetchPending(
        @RequestHeader(name = WorkerApiKeyService.HEADER_NAME) String apiKey,
        @RequestParam(name = "count", required = false) Integer count
    ) {
        log.debug("GET /api/checks/pending - Fetching pending checks (count: {})", count);
        workerApiKeyService.assertValid(apiKey);
        Integer safeCount = null;
        if (count != null) {
            safeCount = Math.min(Math.max(count, 1), 50);
        }
        List<PendingCheckResponse> pending = pingService.getInProgressChecks(safeCount);
        log.info("GET /api/checks/pending - Found {} pending checks", pending.size());
        return ResponseEntity.ok(pending);
    }

    @PostMapping("/next")
    public ResponseEntity<List<PendingCheckResponse>> fetchNext(
        @RequestHeader(name = WorkerApiKeyService.HEADER_NAME) String apiKey,
        @RequestParam(name = "count", defaultValue = "1") int count
    ) {
        log.debug("POST /api/checks/next - Fetching next checks (count: {})", count);
        workerApiKeyService.assertValid(apiKey);
        int safeCount = Math.min(Math.max(count, 1), 50);
        List<PendingCheckResponse> next = pingService.fetchNextChecks(safeCount);
        log.info("POST /api/checks/next - Fetched {} next checks", next.size());
        return ResponseEntity.ok(next);
    }

    @PostMapping("/execute")
    public ResponseEntity<CheckResultDto> execute(
        @Valid @RequestBody ExecuteCheckRequest request
    ) {
        log.debug("POST /api/checks/execute - Executing check for ping ID: {}", request.getPingId());
        CheckResultDto result = checkService.executeCheck(request, false);
        log.info("POST /api/checks/execute - Check executed for ping ID: {} - HTTP {}", 
            request.getPingId(), result.getHttpCode());
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/result")
    public ResponseEntity<CheckResultDto> recordResult(
        @RequestHeader(name = WorkerApiKeyService.HEADER_NAME) String apiKey,
        @Valid @RequestBody CheckResultUpdateRequest request
    ) {
        log.debug("PATCH /api/checks/result - Recording result for ping ID: {}", request.getPingId());
        workerApiKeyService.assertValid(apiKey);
        CheckResultDto result = checkService.recordResult(request, true);
        log.info("PATCH /api/checks/result - Result recorded for ping ID: {} - HTTP {}", 
            request.getPingId(), result.getHttpCode());
        return ResponseEntity.ok(result);
    }
}

