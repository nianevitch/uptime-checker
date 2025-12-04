package com.isofuture.uptime.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.bson.BsonMaximumSizeExceededException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.isofuture.uptime.exception.ResourceNotFoundException;

@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return error(HttpStatus.FORBIDDEN, ex);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials: {}", ex.getMessage());
        return error(HttpStatus.UNAUTHORIZED, ex);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUsernameNotFound(UsernameNotFoundException ex) {
        log.warn("Username not found: {}", ex.getMessage());
        return error(HttpStatus.UNAUTHORIZED, ex);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.debug("Resource not found: {}", ex.getMessage());
        return error(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException ex) {
        String resourcePath = ex.getResourcePath();
        // If it's an API path, return 404 Not Found instead of treating it as a static resource error
        if (resourcePath != null && resourcePath.startsWith("/api/")) {
            log.debug("API endpoint not found: {}", resourcePath);
            Map<String, Object> body = new HashMap<>();
            body.put("timestamp", Instant.now());
            body.put("status", HttpStatus.NOT_FOUND.value());
            body.put("error", "API endpoint not found: " + resourcePath);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
        // For non-API paths, log as debug (likely a frontend route)
        log.debug("Static resource not found: {}", resourcePath);
        return error(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        // Avoid calling getMessage() if MethodParameter is null (common in tests)
        // as it throws NullPointerException when trying to access parameter.getParameterIndex()
        String message = null;
        try {
            message = ex.getMessage();
        } catch (NullPointerException e) {
            // MethodParameter is null, skip message logging
        }
        if (message != null) {
            log.debug("Validation error: {}", message);
        } else {
            log.debug("Validation error: {} field errors", ex.getBindingResult().getFieldErrors().size());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("Maximum upload size exceeded: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.PAYLOAD_TOO_LARGE.value());
        body.put("error", "File size exceeds the maximum allowed size. Maximum file size is 50MB.");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    @ExceptionHandler(BsonMaximumSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleBsonMaximumSizeExceeded(BsonMaximumSizeExceededException ex) {
        log.error("MongoDB document size exceeded: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "File is too large to process. Please contact support if this error persists.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ex);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, Exception ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", ex.getMessage());
        return ResponseEntity.status(status).body(body);
    }
}

