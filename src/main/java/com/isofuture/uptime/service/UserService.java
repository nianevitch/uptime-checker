package com.isofuture.uptime.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.isofuture.uptime.dto.PasswordChangeRequest;
import com.isofuture.uptime.dto.UserRequest;
import com.isofuture.uptime.dto.UserResponse;
import com.isofuture.uptime.dto.UserUpdateRequest;
import com.isofuture.uptime.entity.Role;
import com.isofuture.uptime.entity.User;
import com.isofuture.uptime.exception.ResourceNotFoundException;
import com.isofuture.uptime.repository.RoleRepository;
import com.isofuture.uptime.repository.UserRepository;
import com.isofuture.uptime.security.SecurityUser;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserContext userContext;

    public UserService(
        UserRepository userRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder,
        UserContext userContext
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.userContext = userContext;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listAll() {
        log.debug("Listing all users");
        if (!userContext.isAdmin()) {
            log.warn("Non-admin user attempted to list all users");
            throw new AccessDeniedException("Only admins can list all users");
        }
        List<UserResponse> users = userRepository.findAllActive().stream()
            .map(UserResponse::new)
            .collect(Collectors.toList());
        log.debug("Found {} active users", users.size());
        return users;
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        log.debug("Getting user by ID: {}", id);
        User entity = userRepository.findActiveById(id)
            .orElseThrow(() -> {
                log.warn("User not found: {}", id);
                return new ResourceNotFoundException("User not found");
            });
        
        SecurityUser currentUser = userContext.getCurrentUser();
        if (!userContext.isAdmin() && !currentUser.getId().equals(id)) {
            log.warn("User {} attempted to access user {} profile", currentUser.getId(), id);
            throw new AccessDeniedException("You can only view your own profile");
        }
        
        log.debug("User found: {} ({})", entity.getEmail(), id);
        return new UserResponse(entity);
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        log.debug("Creating user: {}", request.getEmail());
        if (!userContext.isAdmin()) {
            log.warn("Non-admin user attempted to create user: {}", request.getEmail());
            throw new AccessDeniedException("Only admins can create users");
        }

        userRepository.findActiveByEmailIgnoreCase(request.getEmail()).ifPresent(existing -> {
            log.warn("Attempted to create user with existing email: {}", request.getEmail());
            throw new IllegalArgumentException("Email already registered");
        });

        User entity = new User();
        entity.setEmail(request.getEmail());
        entity.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        entity.setCreatedAt(Instant.now());

        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            log.debug("Assigning roles to user: {}", request.getRoles());
            Set<Role> roles = request.getRoles().stream()
                .map(roleName -> roleRepository.findByNameIgnoreCase(roleName)
                    .orElseGet(() -> {
                        log.debug("Creating new role: {}", roleName);
                        Role role = new Role();
                        role.setName(roleName);
                        return roleRepository.save(role);
                    }))
                .collect(Collectors.toSet());
            entity.setRoles(roles);
        } else {
            Role userRole = roleRepository.findByNameIgnoreCase("user")
                .orElseGet(() -> {
                    log.debug("Creating default 'user' role");
                    Role role = new Role();
                    role.setName("user");
                    return roleRepository.save(role);
                });
            entity.getRoles().add(userRole);
        }

        User saved = userRepository.save(entity);
        log.info("User created successfully: {} (ID: {})", saved.getEmail(), saved.getId());
        return new UserResponse(saved);
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        log.debug("Updating user: {}", id);
        User entity = userRepository.findActiveById(id)
            .orElseThrow(() -> {
                log.warn("User not found for update: {}", id);
                return new ResourceNotFoundException("User not found");
            });

        SecurityUser currentUser = userContext.getCurrentUser();
        boolean isOwnProfile = currentUser.getId().equals(id);
        
        if (!userContext.isAdmin() && !isOwnProfile) {
            log.warn("User {} attempted to update user {} profile", currentUser.getId(), id);
            throw new AccessDeniedException("You can only edit your own profile");
        }

        if (request.getEmail() != null && !request.getEmail().equals(entity.getEmail())) {
            log.debug("Updating email from {} to {}", entity.getEmail(), request.getEmail());
            userRepository.findActiveByEmailIgnoreCase(request.getEmail()).ifPresent(existing -> {
                log.warn("Attempted to update user {} with existing email: {}", id, request.getEmail());
                throw new IllegalArgumentException("Email already registered");
            });
            entity.setEmail(request.getEmail());
        }

        if (request.getRoles() != null && userContext.isAdmin()) {
            log.debug("Updating roles for user {}: {}", id, request.getRoles());
            Set<Role> roles = request.getRoles().stream()
                .map(roleName -> roleRepository.findByNameIgnoreCase(roleName)
                    .orElseGet(() -> {
                        log.debug("Creating new role: {}", roleName);
                        Role role = new Role();
                        role.setName(roleName);
                        return roleRepository.save(role);
                    }))
                .collect(Collectors.toCollection(java.util.HashSet::new));
            // Create a new HashSet to avoid Hibernate collection modification issues
            entity.setRoles(roles);
        } else {
            // Ensure roles collection is initialized if we're not updating it
            // Access the collection to force Hibernate to load it within the transaction
            Set<Role> currentRoles = entity.getRoles();
            if (currentRoles != null) {
                // Create a new HashSet with current roles to avoid Hibernate proxy issues
                Set<Role> rolesCopy = new java.util.HashSet<>(currentRoles);
                entity.setRoles(rolesCopy);
            }
        }

        User saved = userRepository.save(entity);
        log.info("User updated successfully: {} (ID: {})", saved.getEmail(), saved.getId());
        return new UserResponse(saved);
    }

    @Transactional
    public void changePassword(Long id, PasswordChangeRequest request) {
        log.debug("Changing password for user: {}", id);
        User entity = userRepository.findActiveById(id)
            .orElseThrow(() -> {
                log.warn("User not found for password change: {}", id);
                return new ResourceNotFoundException("User not found");
            });

        SecurityUser currentUser = userContext.getCurrentUser();
        boolean isOwnProfile = currentUser.getId().equals(id);
        
        if (!userContext.isAdmin() && !isOwnProfile) {
            log.warn("User {} attempted to change password for user {}", currentUser.getId(), id);
            throw new AccessDeniedException("You can only change your own password");
        }

        // Ensure roles collection is initialized to avoid Hibernate proxy issues
        Set<Role> currentRoles = entity.getRoles();
        if (currentRoles != null) {
            Set<Role> rolesCopy = new java.util.HashSet<>(currentRoles);
            entity.setRoles(rolesCopy);
        }

        entity.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(entity);
        log.info("Password changed successfully for user: {} (ID: {})", entity.getEmail(), id);
    }

    /**
     * Soft deletes a user by setting the deletedAt timestamp.
     * 
     * IMPORTANT: User records are NEVER physically deleted from the database.
     * This method only sets the deletedAt timestamp, making the user invisible
     * to all active user queries (which filter by deletedAt IS NULL).
     * 
     * After soft delete:
     * - User cannot login (DatabaseUserDetailsService uses findActiveByEmailIgnoreCase)
     * - User will not appear in listAll() (uses findAllActive())
     * - User will not appear in getById() (uses findActiveById())
     * - User record remains in database for audit/history purposes
     * 
     * @param id User ID to soft delete
     * @throws AccessDeniedException if current user is not an admin
     * @throws IllegalArgumentException if trying to delete own account
     * @throws ResourceNotFoundException if user not found
     */
    @Transactional
    public void softDelete(Long id) {
        log.debug("Soft deleting user: {}", id);
        if (!userContext.isAdmin()) {
            log.warn("Non-admin user attempted to delete user: {}", id);
            throw new AccessDeniedException("Only admins can delete users");
        }

        User entity = userRepository.findActiveById(id)
            .orElseThrow(() -> {
                log.warn("User not found for deletion: {}", id);
                return new ResourceNotFoundException("User not found");
            });

        SecurityUser currentUser = userContext.getCurrentUser();
        if (currentUser.getId().equals(id)) {
            log.warn("User {} attempted to delete their own account", id);
            throw new IllegalArgumentException("Cannot delete your own account");
        }

        // Ensure roles collection is initialized to avoid Hibernate proxy issues
        Set<Role> currentRoles = entity.getRoles();
        if (currentRoles != null) {
            Set<Role> rolesCopy = new java.util.HashSet<>(currentRoles);
            entity.setRoles(rolesCopy);
        }

        // SOFT DELETE: Set deletedAt timestamp - record is NEVER physically deleted
        entity.setDeletedAt(Instant.now());
        userRepository.save(entity);
        log.info("User soft deleted successfully: {} (ID: {})", entity.getEmail(), id);
        
        // Note: The user record remains in the database with deletedAt set.
        // All active user queries filter by deletedAt IS NULL, so this user
        // will be effectively invisible to the application.
    }
}

