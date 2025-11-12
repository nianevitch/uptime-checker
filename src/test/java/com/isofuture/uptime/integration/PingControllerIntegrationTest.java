package com.isofuture.uptime.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isofuture.uptime.dto.LoginRequest;
import com.isofuture.uptime.dto.PingRequest;
import com.isofuture.uptime.entity.Ping;
import com.isofuture.uptime.entity.Role;
import com.isofuture.uptime.entity.User;
import com.isofuture.uptime.repository.PingRepository;
import com.isofuture.uptime.repository.RoleRepository;
import com.isofuture.uptime.repository.UserRepository;
import com.isofuture.uptime.security.SecurityUser;
import com.isofuture.uptime.service.PingService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("PingController Integration Tests")
class PingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PingRepository pingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PingService pingService;

    private User adminUser;
    private User regularUser;
    private Ping adminPing;
    private Ping userPing;
    private String adminToken;
    private String regularUserToken;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up
        pingRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        // Create roles (check if they exist first)
        Role userRole = roleRepository.findByNameIgnoreCase("user")
            .orElseGet(() -> {
                Role role = new Role();
                role.setName("user");
                return roleRepository.save(role);
            });

        Role adminRole = roleRepository.findByNameIgnoreCase("admin")
            .orElseGet(() -> {
                Role role = new Role();
                role.setName("admin");
                return roleRepository.save(role);
            });

        // Create admin user
        adminUser = new User();
        adminUser.setEmail("admin@test.com");
        adminUser.setPasswordHash(passwordEncoder.encode("password"));
        adminUser.setCreatedAt(Instant.now());
        adminUser.setRoles(Set.of(userRole, adminRole));
        adminUser = userRepository.save(adminUser);

        // Create regular user
        regularUser = new User();
        regularUser.setEmail("user@test.com");
        regularUser.setPasswordHash(passwordEncoder.encode("password"));
        regularUser.setCreatedAt(Instant.now());
        regularUser.setRoles(Set.of(userRole));
        regularUser = userRepository.save(regularUser);

        // Create pings
        adminPing = new Ping();
        adminPing.setOwner(adminUser);
        adminPing.setUrl("https://admin-site.com");
        adminPing.setLabel("Admin Ping");
        adminPing.setFrequencyMinutes(5);
        adminPing.setInProgress(false);
        adminPing.setCreatedAt(Instant.now());
        adminPing.setUpdatedAt(Instant.now());
        adminPing = pingRepository.save(adminPing);

        userPing = new Ping();
        userPing.setOwner(regularUser);
        userPing.setUrl("https://user-site.com");
        userPing.setLabel("User Ping");
        userPing.setFrequencyMinutes(10);
        userPing.setInProgress(false);
        userPing.setCreatedAt(Instant.now());
        userPing.setUpdatedAt(Instant.now());
        userPing = pingRepository.save(userPing);

        // Get tokens
        adminToken = getAuthToken("admin@test.com", "password");
        regularUserToken = getAuthToken("user@test.com", "password");
    }

    @Test
    @DisplayName("GET /api/pings - Admin sees all pings")
    void testList_Admin_SeesAll() throws Exception {
        mockMvc.perform(get("/api/pings")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));

        // Verify via service call (set up security context first)
        setSecurityContext(adminUser);
        try {
            var pings = pingService.listCurrentUserPings();
            assertEquals(2, pings.size());
        } finally {
            clearSecurityContext();
        }
    }

    @Test
    @DisplayName("GET /api/pings - Regular user sees only own pings")
    void testList_RegularUser_SeesOwn() throws Exception {
        mockMvc.perform(get("/api/pings")
                .header("Authorization", "Bearer " + regularUserToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].url").value("https://user-site.com"));

        // Verify via service call (set up security context first)
        setSecurityContext(regularUser);
        try {
            var pings = pingService.listCurrentUserPings();
            assertEquals(1, pings.size());
            assertEquals("https://user-site.com", pings.get(0).getUrl());
        } finally {
            clearSecurityContext();
        }
    }

    @Test
    @DisplayName("POST /api/pings - User can create ping")
    void testCreate_Success() throws Exception {
        PingRequest request = new PingRequest();
        request.setUrl("https://new-site.com");
        request.setLabel("New Monitor");
        request.setFrequencyMinutes(15);

        mockMvc.perform(post("/api/pings")
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.url").value("https://new-site.com"))
            .andExpect(jsonPath("$.label").value("New Monitor"));

        // Verify via service call (set up security context first)
        setSecurityContext(regularUser);
        try {
            var pings = pingService.listCurrentUserPings();
            assertEquals(2, pings.size()); // Original + new one
        } finally {
            clearSecurityContext();
        }
        
        // Verify via repository
        var created = pingRepository.findByOwner(regularUser);
        assertEquals(2, created.size());
    }

    @Test
    @DisplayName("PUT /api/pings/{id} - User can update own ping")
    void testUpdate_OwnMonitor_Success() throws Exception {
        PingRequest request = new PingRequest();
        request.setUrl("https://updated-site.com");
        request.setLabel("Updated Monitor");
        request.setFrequencyMinutes(20);

        mockMvc.perform(put("/api/pings/" + userPing.getId())
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value("https://updated-site.com"))
            .andExpect(jsonPath("$.label").value("Updated Monitor"));

        // Verify via service call (set up security context first)
        setSecurityContext(regularUser);
        try {
            var pings = pingService.listCurrentUserPings();
            var updated = pings.stream()
                .filter(m -> m.getId().equals(userPing.getId()))
                .findFirst()
                .orElseThrow();
            assertEquals("https://updated-site.com", updated.getUrl());
        } finally {
            clearSecurityContext();
        }
        
        // Verify via repository
        var entity = pingRepository.findById(userPing.getId()).orElseThrow();
        assertEquals("https://updated-site.com", entity.getUrl());
    }

    @Test
    @DisplayName("PUT /api/pings/{id} - User cannot update other user's ping")
    void testUpdate_OtherUserMonitor_Forbidden() throws Exception {
        PingRequest request = new PingRequest();
        request.setUrl("https://hacked-site.com");
        request.setLabel("Hacked Monitor");
        request.setFrequencyMinutes(20);

        mockMvc.perform(put("/api/pings/" + adminPing.getId())
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest()); // Returns 400 for "Monitor not found"
    }

    @Test
    @DisplayName("DELETE /api/pings/{id} - User can delete own ping")
    void testDelete_OwnMonitor_Success() throws Exception {
        mockMvc.perform(delete("/api/pings/" + userPing.getId())
                .header("Authorization", "Bearer " + regularUserToken))
            .andExpect(status().isNoContent());

        // Verify via service call (set up security context first)
        setSecurityContext(regularUser);
        try {
            var pings = pingService.listCurrentUserPings();
            assertEquals(0, pings.size());
        } finally {
            clearSecurityContext();
        }
        
        // Verify via repository
        var deleted = pingRepository.findById(userPing.getId());
        assertFalse(deleted.isPresent());
    }

    @Test
    @DisplayName("DELETE /api/pings/{id} - Admin can delete any ping")
    void testDelete_Admin_CanDeleteAny() throws Exception {
        mockMvc.perform(delete("/api/pings/" + userPing.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());

        // Verify via repository
        var deleted = pingRepository.findById(userPing.getId());
        assertFalse(deleted.isPresent());
    }

    private void setSecurityContext(User user) {
        SecurityUser securityUser = new SecurityUser(user);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            securityUser,
            null,
            securityUser.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private String getAuthToken(String email, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword(password);

        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        return objectMapper.readTree(response).get("token").asText();
    }
}

