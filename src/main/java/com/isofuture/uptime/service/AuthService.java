package com.isofuture.uptime.service;

import java.time.Instant;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.isofuture.uptime.dto.LoginRequest;
import com.isofuture.uptime.dto.LoginResponse;
import com.isofuture.uptime.dto.RegisterRequest;
import com.isofuture.uptime.entity.RoleEntity;
import com.isofuture.uptime.entity.UserEntity;
import com.isofuture.uptime.repository.RoleRepository;
import com.isofuture.uptime.repository.UserRepository;
import com.isofuture.uptime.security.JwtTokenProvider;
import com.isofuture.uptime.security.SecurityUser;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(
        AuthenticationManager authenticationManager,
        UserRepository userRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenProvider tokenProvider
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        SecurityUser principal = (SecurityUser) authentication.getPrincipal();
        String token = tokenProvider.generateToken(principal);

        return new LoginResponse(token, principal.getId(), principal.getUsername());
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        userRepository.findActiveByEmailIgnoreCase(request.getEmail()).ifPresent(existing -> {
            throw new IllegalArgumentException("Email already registered");
        });

        RoleEntity userRole = roleRepository.findByNameIgnoreCase("user")
            .orElseGet(() -> {
                RoleEntity role = new RoleEntity();
                role.setName("user");
                return roleRepository.save(role);
            });

        UserEntity entity = new UserEntity();
        entity.setEmail(request.getEmail());
        entity.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.getRoles().add(userRole);

        UserEntity saved = userRepository.save(entity);
        SecurityUser securityUser = new SecurityUser(saved);
        String token = tokenProvider.generateToken(securityUser);

        return new LoginResponse(token, saved.getId(), saved.getEmail());
    }
}

