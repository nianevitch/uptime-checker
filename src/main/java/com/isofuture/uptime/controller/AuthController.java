package com.isofuture.uptime.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.isofuture.uptime.dto.LoginRequest;
import com.isofuture.uptime.dto.LoginResponse;
import com.isofuture.uptime.dto.RegisterRequest;
import com.isofuture.uptime.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.debug("POST /api/auth/login - Login request for: {}", request.getEmail());
        LoginResponse response = authService.login(request);
        log.info("POST /api/auth/login - Login successful for: {}", request.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.debug("POST /api/auth/register - Registration request for: {}", request.getEmail());
        LoginResponse response = authService.register(request);
        log.info("POST /api/auth/register - Registration successful for: {}", request.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        log.debug("POST /api/auth/logout - Logout request");
        // Token is stateless; the client should drop it. Endpoint provided for symmetry.
        return ResponseEntity.noContent().build();
    }
}

