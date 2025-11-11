package com.isofuture.uptime.controller;

import java.util.List;

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
import com.isofuture.uptime.service.MonitorService;
import com.isofuture.uptime.service.WorkerApiKeyService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/checks")
public class CheckController {

    private final CheckService checkService;
    private final MonitorService monitorService;
    private final WorkerApiKeyService workerApiKeyService;

    public CheckController(CheckService checkService, MonitorService monitorService, WorkerApiKeyService workerApiKeyService) {
        this.checkService = checkService;
        this.monitorService = monitorService;
        this.workerApiKeyService = workerApiKeyService;
    }

    @GetMapping("/pending")
    public ResponseEntity<List<PendingCheckResponse>> fetchPending(
        @RequestHeader(name = WorkerApiKeyService.HEADER_NAME) String apiKey,
        @RequestParam(name = "count", required = false) Integer count
    ) {
        workerApiKeyService.assertValid(apiKey);
        Integer safeCount = null;
        if (count != null) {
            safeCount = Math.min(Math.max(count, 1), 50);
        }
        return ResponseEntity.ok(monitorService.getInProgressChecks(safeCount));
    }

    @PostMapping("/next")
    public ResponseEntity<List<PendingCheckResponse>> fetchNext(
        @RequestHeader(name = WorkerApiKeyService.HEADER_NAME) String apiKey,
        @RequestParam(name = "count", defaultValue = "1") int count
    ) {
        workerApiKeyService.assertValid(apiKey);
        int safeCount = Math.min(Math.max(count, 1), 50);
        return ResponseEntity.ok(monitorService.fetchNextChecks(safeCount));
    }

    @PostMapping("/execute")
    public ResponseEntity<CheckResultDto> execute(
        @Valid @RequestBody ExecuteCheckRequest request
    ) {
        return ResponseEntity.ok(checkService.executeCheck(request, false));
    }

    @PatchMapping("/result")
    public ResponseEntity<CheckResultDto> recordResult(
        @RequestHeader(name = WorkerApiKeyService.HEADER_NAME) String apiKey,
        @Valid @RequestBody CheckResultUpdateRequest request
    ) {
        workerApiKeyService.assertValid(apiKey);
        return ResponseEntity.ok(checkService.recordResult(request, true));
    }
}

