package com.isofuture.uptime.controller;

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

import com.isofuture.uptime.dto.PasswordChangeRequest;
import com.isofuture.uptime.dto.UserRequest;
import com.isofuture.uptime.dto.UserResponse;
import com.isofuture.uptime.dto.UserUpdateRequest;
import com.isofuture.uptime.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> listAll() {
        log.debug("GET /api/users - Listing all users");
        List<UserResponse> users = userService.listAll();
        log.info("GET /api/users - Found {} users", users.size());
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable("id") Long id) {
        log.debug("GET /api/users/{} - Getting user by ID", id);
        UserResponse user = userService.getById(id);
        log.info("GET /api/users/{} - User found: {}", id, user.getEmail());
        return ResponseEntity.ok(user);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        log.debug("POST /api/users - Creating user: {}", request.getEmail());
        UserResponse user = userService.create(request);
        log.info("POST /api/users - User created: {} (ID: {})", user.getEmail(), user.getId());
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(
        @PathVariable("id") Long id,
        @Valid @RequestBody UserUpdateRequest request
    ) {
        log.debug("PUT /api/users/{} - Updating user", id);
        UserResponse user = userService.update(id, request);
        log.info("PUT /api/users/{} - User updated: {}", id, user.getEmail());
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> changePassword(
        @PathVariable("id") Long id,
        @Valid @RequestBody PasswordChangeRequest request
    ) {
        log.debug("PUT /api/users/{}/password - Changing password", id);
        userService.changePassword(id, request);
        log.info("PUT /api/users/{}/password - Password changed successfully", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Soft deletes a user.
     * 
     * IMPORTANT: User records are NEVER physically deleted from the database.
     * This endpoint performs a soft delete by setting the deletedAt timestamp.
     * The user record remains in the database for audit/history purposes.
     * 
     * After deletion, the user will be invisible to all normal operations
     * (listAll, getById, login) but the record persists in the database.
     * 
     * @param id User ID to delete
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        log.debug("DELETE /api/users/{} - Soft deleting user", id);
        userService.softDelete(id);
        log.info("DELETE /api/users/{} - User soft deleted successfully", id);
        return ResponseEntity.noContent().build();
    }
}

