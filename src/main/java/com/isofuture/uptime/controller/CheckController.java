package com.isofuture.uptime.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.isofuture.uptime.dto.CheckResultDto;
import com.isofuture.uptime.dto.CheckResultUpdateRequest;
import com.isofuture.uptime.dto.ExecuteCheckRequest;
import com.isofuture.uptime.dto.PendingCheckResponse;
import com.isofuture.uptime.service.CheckService;
import com.isofuture.uptime.service.MonitorService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/checks")
public class CheckController {

    private final CheckService checkService;
    private final MonitorService monitorService;

    public CheckController(CheckService checkService, MonitorService monitorService) {
        this.checkService = checkService;
        this.monitorService = monitorService;
    }

    @GetMapping("/pending")
    public ResponseEntity<List<PendingCheckResponse>> fetchPending(
    		@RequestParam(name = "count", defaultValue = "1") int count
    ) {
        int safeCount = Math.min(Math.max(count, 1), 50);
        return ResponseEntity.ok(monitorService.claimPendingChecks(safeCount));
    }

    @PostMapping("/execute")
    public ResponseEntity<CheckResultDto> execute(@Valid @RequestBody ExecuteCheckRequest request) {
        return ResponseEntity.ok(checkService.executeCheck(request));
    }

    @PostMapping("/result")
    public ResponseEntity<CheckResultDto> recordResult(@Valid @RequestBody CheckResultUpdateRequest request) {
        return ResponseEntity.ok(checkService.recordResult(request));
    }
}

