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
import org.springframework.web.bind.annotation.RestController;

import com.isofuture.uptime.dto.PingRequest;
import com.isofuture.uptime.dto.PingResponse;
import com.isofuture.uptime.service.PingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/pings")
public class PingController {

    private static final Logger log = LoggerFactory.getLogger(PingController.class);
    private final PingService pingService;

    public PingController(PingService pingService) {
        this.pingService = pingService;
    }

    @GetMapping
    public ResponseEntity<List<PingResponse>> list() {
        log.debug("GET /api/pings - Listing pings");
        List<PingResponse> pings = pingService.listCurrentUserPings();
        log.info("GET /api/pings - Found {} pings", pings.size());
        return ResponseEntity.ok(pings);
    }

    @PostMapping
    public ResponseEntity<PingResponse> create(@Valid @RequestBody PingRequest request) {
        log.debug("POST /api/pings - Creating ping: {}", request.getUrl());
        PingResponse response = pingService.createPing(request);
        log.info("POST /api/pings - Ping created: {} (ID: {})", response.getUrl(), response.getId());
        return ResponseEntity
            .created(URI.create("/api/pings/" + response.getId()))
            .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PingResponse> update(
        @PathVariable("id") Long id,
        @Valid @RequestBody PingRequest request
    ) {
        log.debug("PUT /api/pings/{} - Updating ping", id);
        PingResponse response = pingService.updatePing(id, request);
        log.info("PUT /api/pings/{} - Ping updated: {}", id, response.getUrl());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        log.debug("DELETE /api/pings/{} - Deleting ping", id);
        pingService.deletePing(id);
        log.info("DELETE /api/pings/{} - Ping deleted successfully", id);
        return ResponseEntity.noContent().build();
    }
}

