package com.isofuture.uptime.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.isofuture.uptime.exception.ResourceNotFoundException;

@DisplayName("RestExceptionHandler Tests")
class RestExceptionHandlerTest {

    private RestExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new RestExceptionHandler();
    }

    @Test
    @DisplayName("handleAccessDenied - Returns 403 FORBIDDEN")
    void testHandleAccessDenied() {
        // Given
        AccessDeniedException ex = new AccessDeniedException("Access denied");

        // When
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleAccessDenied(ex);

        // Then
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().get("status"));
        assertEquals("Access denied", response.getBody().get("error"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    @DisplayName("handleBadCredentials - Returns 401 UNAUTHORIZED")
    void testHandleBadCredentials() {
        // Given
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");

        // When
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleBadCredentials(ex);

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().get("status"));
        assertEquals("Bad credentials", response.getBody().get("error"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    @DisplayName("handleUsernameNotFound - Returns 401 UNAUTHORIZED")
    void testHandleUsernameNotFound() {
        // Given
        UsernameNotFoundException ex = new UsernameNotFoundException("User not found");

        // When
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleUsernameNotFound(ex);

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().get("status"));
        assertEquals("User not found", response.getBody().get("error"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    @DisplayName("handleResourceNotFound - Returns 404 NOT_FOUND")
    void testHandleResourceNotFound() {
        // Given
        ResourceNotFoundException ex = new ResourceNotFoundException("Resource not found");

        // When
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleResourceNotFound(ex);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().get("status"));
        assertEquals("Resource not found", response.getBody().get("error"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    @DisplayName("handleValidation - Returns 400 BAD_REQUEST with field errors")
    void testHandleValidation() {
        // Given
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "target");
        bindingResult.addError(new FieldError("target", "email", "Email is required"));
        bindingResult.addError(new FieldError("target", "password", "Password is required"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        // When
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleValidation(ex);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().get("status"));
        assertNotNull(response.getBody().get("timestamp"));
        
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) response.getBody().get("errors");
        assertNotNull(errors);
        assertEquals("Email is required", errors.get("email"));
        assertEquals("Password is required", errors.get("password"));
    }

    @Test
    @DisplayName("handleValidation - Returns 400 BAD_REQUEST with empty errors map when no field errors")
    void testHandleValidation_NoFieldErrors() {
        // Given
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "target");
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        // When
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleValidation(ex);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().get("status"));
        assertNotNull(response.getBody().get("timestamp"));
        
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) response.getBody().get("errors");
        assertNotNull(errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("handleIllegalArgument - Returns 400 BAD_REQUEST")
    void testHandleIllegalArgument() {
        // Given
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");

        // When
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleIllegalArgument(ex);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().get("status"));
        assertEquals("Invalid argument", response.getBody().get("error"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    @DisplayName("handleIllegalState - Returns 400 BAD_REQUEST")
    void testHandleIllegalState() {
        // Given
        IllegalStateException ex = new IllegalStateException("Invalid state");

        // When
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleIllegalState(ex);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().get("status"));
        assertEquals("Invalid state", response.getBody().get("error"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    @DisplayName("handleGeneric - Returns 500 INTERNAL_SERVER_ERROR")
    void testHandleGeneric() {
        // Given
        Exception ex = new RuntimeException("Internal server error");

        // When
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleGeneric(ex);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().get("status"));
        assertEquals("Internal server error", response.getBody().get("error"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    @DisplayName("handleGeneric - Returns 500 INTERNAL_SERVER_ERROR with null message")
    void testHandleGeneric_NullMessage() {
        // Given
        Exception ex = new RuntimeException();

        // When
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleGeneric(ex);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().get("status"));
        assertNull(response.getBody().get("error"));
        assertNotNull(response.getBody().get("timestamp"));
    }
}

