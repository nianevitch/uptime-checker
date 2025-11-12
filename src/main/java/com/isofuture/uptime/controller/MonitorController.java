package com.isofuture.uptime.controller;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(MonitorController.class);
    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping
    public ResponseEntity<List<MonitoredUrlResponse>> list() {
        log.debug("GET /api/monitors - Listing monitors");
        List<MonitoredUrlResponse> monitors = monitorService.listCurrentUserMonitors();
        log.info("GET /api/monitors - Found {} monitors", monitors.size());
        return ResponseEntity.ok(monitors);
    }

    @PostMapping
    public ResponseEntity<MonitoredUrlResponse> create(@Valid @RequestBody MonitoredUrlRequest request) {
        log.debug("POST /api/monitors - Creating monitor: {}", request.getUrl());
        MonitoredUrlResponse response = monitorService.createMonitor(request);
        log.info("POST /api/monitors - Monitor created: {} (ID: {})", response.getUrl(), response.getId());
        return ResponseEntity
            .created(URI.create("/api/monitors/" + response.getId()))
            .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MonitoredUrlResponse> update(
        @PathVariable("id") Long id,
        @Valid @RequestBody MonitoredUrlRequest request
    ) {
        log.debug("PUT /api/monitors/{} - Updating monitor", id);
        MonitoredUrlResponse response = monitorService.updateMonitor(id, request);
        log.info("PUT /api/monitors/{} - Monitor updated: {}", id, response.getUrl());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        log.debug("DELETE /api/monitors/{} - Deleting monitor", id);
        monitorService.deleteMonitor(id);
        log.info("DELETE /api/monitors/{} - Monitor deleted successfully", id);
        return ResponseEntity.noContent().build();
    }
}

