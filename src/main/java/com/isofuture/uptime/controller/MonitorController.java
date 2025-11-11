package com.isofuture.uptime.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.isofuture.uptime.dto.MonitoredUrlRequest;
import com.isofuture.uptime.dto.MonitoredUrlResponse;
import com.isofuture.uptime.service.MonitorService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/monitors")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping
    public ResponseEntity<List<MonitoredUrlResponse>> list() {
        return ResponseEntity.ok(monitorService.listCurrentUserMonitors());
    }

    @PostMapping
    public ResponseEntity<MonitoredUrlResponse> create(@Valid @RequestBody MonitoredUrlRequest request) {
        MonitoredUrlResponse response = monitorService.createMonitor(request);
        return ResponseEntity
            .created(URI.create("/api/monitors/" + response.getId()))
            .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MonitoredUrlResponse> update(
        @PathVariable("id") Long id,
        @Valid @RequestBody MonitoredUrlRequest request
    ) {
        return ResponseEntity.ok(monitorService.updateMonitor(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        monitorService.deleteMonitor(id);
        return ResponseEntity.noContent().build();
    }
}

