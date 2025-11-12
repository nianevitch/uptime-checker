package com.isofuture.uptime.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.isofuture.uptime.dto.PasswordChangeRequest;
import com.isofuture.uptime.dto.UserRequest;
import com.isofuture.uptime.dto.UserResponse;
import com.isofuture.uptime.dto.UserUpdateRequest;
import com.isofuture.uptime.entity.RoleEntity;
import com.isofuture.uptime.entity.UserEntity;
import com.isofuture.uptime.exception.ResourceNotFoundException;
import com.isofuture.uptime.repository.RoleRepository;
import com.isofuture.uptime.repository.UserRepository;
import com.isofuture.uptime.security.SecurityUser;

@Service
public class UserService {

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
        if (!userContext.isAdmin()) {
            throw new AccessDeniedException("Only admins can list all users");
        }
        return userRepository.findAllActive().stream()
            .map(UserResponse::new)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        UserEntity entity = userRepository.findActiveById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        SecurityUser currentUser = userContext.getCurrentUser();
        if (!userContext.isAdmin() && !currentUser.getId().equals(id)) {
            throw new AccessDeniedException("You can only view your own profile");
        }
        
        return new UserResponse(entity);
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        if (!userContext.isAdmin()) {
            throw new AccessDeniedException("Only admins can create users");
        }

        userRepository.findActiveByEmailIgnoreCase(request.getEmail()).ifPresent(existing -> {
            throw new IllegalArgumentException("Email already registered");
        });

        UserEntity entity = new UserEntity();
        entity.setEmail(request.getEmail());
        entity.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        entity.setCreatedAt(Instant.now());

        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            Set<RoleEntity> roles = request.getRoles().stream()
                .map(roleName -> roleRepository.findByNameIgnoreCase(roleName)
                    .orElseGet(() -> {
                        RoleEntity role = new RoleEntity();
                        role.setName(roleName);
                        return roleRepository.save(role);
                    }))
                .collect(Collectors.toSet());
            entity.setRoles(roles);
        } else {
            RoleEntity userRole = roleRepository.findByNameIgnoreCase("user")
                .orElseGet(() -> {
                    RoleEntity role = new RoleEntity();
                    role.setName("user");
                    return roleRepository.save(role);
                });
            entity.getRoles().add(userRole);
        }

        UserEntity saved = userRepository.save(entity);
        return new UserResponse(saved);
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        UserEntity entity = userRepository.findActiveById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        SecurityUser currentUser = userContext.getCurrentUser();
        boolean isOwnProfile = currentUser.getId().equals(id);
        
        if (!userContext.isAdmin() && !isOwnProfile) {
            throw new AccessDeniedException("You can only edit your own profile");
        }

        if (request.getEmail() != null && !request.getEmail().equals(entity.getEmail())) {
            userRepository.findActiveByEmailIgnoreCase(request.getEmail()).ifPresent(existing -> {
                throw new IllegalArgumentException("Email already registered");
            });
            entity.setEmail(request.getEmail());
        }

        if (request.getRoles() != null && userContext.isAdmin()) {
            Set<RoleEntity> roles = request.getRoles().stream()
                .map(roleName -> roleRepository.findByNameIgnoreCase(roleName)
                    .orElseGet(() -> {
                        RoleEntity role = new RoleEntity();
                        role.setName(roleName);
                        return roleRepository.save(role);
                    }))
                .collect(Collectors.toSet());
            entity.setRoles(roles);
        }

        UserEntity saved = userRepository.save(entity);
        return new UserResponse(saved);
    }

    @Transactional
    public void changePassword(Long id, PasswordChangeRequest request) {
        UserEntity entity = userRepository.findActiveById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        SecurityUser currentUser = userContext.getCurrentUser();
        boolean isOwnProfile = currentUser.getId().equals(id);
        
        if (!userContext.isAdmin() && !isOwnProfile) {
            throw new AccessDeniedException("You can only change your own password");
        }

        entity.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(entity);
    }

    @Transactional
    public void softDelete(Long id) {
        if (!userContext.isAdmin()) {
            throw new AccessDeniedException("Only admins can delete users");
        }

        UserEntity entity = userRepository.findActiveById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        SecurityUser currentUser = userContext.getCurrentUser();
        if (currentUser.getId().equals(id)) {
            throw new IllegalArgumentException("Cannot delete your own account");
        }

        entity.setDeletedAt(Instant.now());
        userRepository.save(entity);
    }
}

