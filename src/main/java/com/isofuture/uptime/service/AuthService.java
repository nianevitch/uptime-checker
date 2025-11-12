package com.isofuture.uptime.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.isofuture.uptime.dto.LoginRequest;
import com.isofuture.uptime.dto.LoginResponse;
import com.isofuture.uptime.dto.RegisterRequest;
import com.isofuture.uptime.entity.Role;
import com.isofuture.uptime.entity.Tier;
import com.isofuture.uptime.entity.User;
import com.isofuture.uptime.repository.RoleRepository;
import com.isofuture.uptime.repository.TierRepository;
import com.isofuture.uptime.repository.UserRepository;
import com.isofuture.uptime.security.JwtTokenProvider;
import com.isofuture.uptime.security.SecurityUser;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TierRepository tierRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(
        AuthenticationManager authenticationManager,
        UserRepository userRepository,
        RoleRepository roleRepository,
        TierRepository tierRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenProvider tokenProvider
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tierRepository = tierRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    public LoginResponse login(LoginRequest request) {
        log.debug("Login attempt for user: {}", request.getEmail());
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        SecurityUser principal = (SecurityUser) authentication.getPrincipal();
        String token = tokenProvider.generateToken(principal);
        log.info("User logged in successfully: {} (ID: {})", request.getEmail(), principal.getId());

        return new LoginResponse(token, principal.getId(), principal.getUsername());
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        log.debug("Registration attempt for user: {}", request.getEmail());
        userRepository.findActiveByEmailIgnoreCase(request.getEmail()).ifPresent(existing -> {
            log.warn("Registration failed: Email already registered: {}", request.getEmail());
            throw new IllegalArgumentException("Email already registered");
        });

        Role userRole = roleRepository.findByNameIgnoreCase("user")
            .orElseGet(() -> {
                log.debug("Creating default 'user' role during registration");
                Role role = new Role();
                role.setName("user");
                return roleRepository.save(role);
            });

        Tier freeTier = tierRepository.findActiveByNameIgnoreCase("free")
            .orElseGet(() -> {
                log.debug("Creating default 'free' tier during registration");
                Tier tier = new Tier();
                tier.setName("free");
                tier.setCreatedAt(Instant.now());
                return tierRepository.save(tier);
            });

        User entity = new User();
        entity.setEmail(request.getEmail());
        entity.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.getRoles().add(userRole);
        entity.getTiers().add(freeTier);

        User saved = userRepository.save(entity);
        SecurityUser securityUser = new SecurityUser(saved);
        String token = tokenProvider.generateToken(securityUser);
        log.info("User registered successfully: {} (ID: {})", saved.getEmail(), saved.getId());

        return new LoginResponse(token, saved.getId(), saved.getEmail());
    }
}

