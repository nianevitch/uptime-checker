package com.isofuture.uptime.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.isofuture.uptime.BaseTest;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest extends BaseTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private TierRepository tierRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private Role userRole;
    private Tier freeTier;
    private SecurityUser securityUser;

    @BeforeEach
    void setUp() {
        testUser = createUser(1L, "test@example.com", "$2a$10$encoded", "user");
        userRole = new Role();
        userRole.setId(1L);
        userRole.setName("user");
        freeTier = new Tier();
        freeTier.setId(1L);
        freeTier.setName("free");
        securityUser = new SecurityUser(testUser);
    }

    @Test
    @DisplayName("login - Successful login returns token")
    void testLogin_Success() {
        // Given
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(securityUser);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
            .thenReturn(authentication);
        when(tokenProvider.generateToken(securityUser)).thenReturn("jwt-token");

        // When
        LoginResponse response = authService.login(request);

        // Then
        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals(1L, response.getUserId());
        assertEquals("test@example.com", response.getEmail());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(tokenProvider).generateToken(securityUser);
    }

    @Test
    @DisplayName("login - Invalid credentials throws BadCredentialsException")
    void testLogin_InvalidCredentials_ThrowsException() {
        // Given
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("wrongpassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
            .thenThrow(new BadCredentialsException("Bad credentials"));

        // When/Then
        assertThrows(BadCredentialsException.class, () -> authService.login(request));
        verify(tokenProvider, never()).generateToken(any());
    }

    @Test
    @DisplayName("register - Successful registration returns token")
    void testRegister_Success() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("password123");

        when(userRepository.findActiveByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByNameIgnoreCase("user")).thenReturn(Optional.of(userRole));
        when(tierRepository.findActiveByNameIgnoreCase("free")).thenReturn(Optional.of(freeTier));
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(2L);
            return user;
        });
        when(tokenProvider.generateToken(any(SecurityUser.class))).thenReturn("jwt-token");

        // When
        LoginResponse response = authService.register(request);

        // Then
        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals("new@example.com", response.getEmail());
        verify(userRepository).findActiveByEmailIgnoreCase("new@example.com");
        verify(tierRepository).findActiveByNameIgnoreCase("free");
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
        verify(tokenProvider).generateToken(any(SecurityUser.class));
    }

    @Test
    @DisplayName("register - Duplicate email throws IllegalArgumentException")
    void testRegister_DuplicateEmail_ThrowsException() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@example.com");
        request.setPassword("password123");

        when(userRepository.findActiveByEmailIgnoreCase("existing@example.com"))
            .thenReturn(Optional.of(testUser));

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
        verify(tokenProvider, never()).generateToken(any());
    }

    @Test
    @DisplayName("register - Creates user role if it doesn't exist")
    void testRegister_CreatesUserRole_IfNotExists() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("password123");

        when(userRepository.findActiveByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByNameIgnoreCase("user")).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            role.setId(1L);
            return role;
        });
        when(tierRepository.findActiveByNameIgnoreCase("free")).thenReturn(Optional.empty());
        when(tierRepository.save(any(Tier.class))).thenAnswer(invocation -> {
            Tier tier = invocation.getArgument(0);
            tier.setId(1L);
            return tier;
        });
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(2L);
            return user;
        });
        when(tokenProvider.generateToken(any(SecurityUser.class))).thenReturn("jwt-token");

        // When
        LoginResponse response = authService.register(request);

        // Then
        assertNotNull(response);
        verify(roleRepository).save(any(Role.class));
        verify(tierRepository).findActiveByNameIgnoreCase("free");
        verify(tierRepository).save(any(Tier.class));
        verify(userRepository).save(any(User.class));
    }
}



