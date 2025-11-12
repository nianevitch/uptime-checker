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
import com.isofuture.uptime.dto.TierRequest;
import com.isofuture.uptime.dto.TierUpdateRequest;
import com.isofuture.uptime.entity.Role;
import com.isofuture.uptime.entity.Tier;
import com.isofuture.uptime.entity.User;
import com.isofuture.uptime.repository.CheckResultRepository;
import com.isofuture.uptime.repository.MonitoredUrlRepository;
import com.isofuture.uptime.repository.RoleRepository;
import com.isofuture.uptime.repository.TierRepository;
import com.isofuture.uptime.repository.UserRepository;
import com.isofuture.uptime.service.AuthService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("TierController Integration Tests")
class TierControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TierRepository tierRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private MonitoredUrlRepository monitoredUrlRepository;

    @Autowired
    private CheckResultRepository checkResultRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    private User adminUser;
    private User regularUser;
    private String adminToken;
    private String regularUserToken;
    private Tier testTier;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up (delete in correct order to avoid foreign key violations)
        checkResultRepository.deleteAll();
        monitoredUrlRepository.deleteAll();
        userRepository.deleteAll();
        tierRepository.deleteAll();
        roleRepository.deleteAll();

        // Create roles
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

        // Create tiers
        Tier freeTier = new Tier();
        freeTier.setName("free");
        freeTier.setCreatedAt(Instant.now());
        freeTier = tierRepository.save(freeTier);

        testTier = new Tier();
        testTier.setName("premium");
        testTier.setCreatedAt(Instant.now());
        testTier = tierRepository.save(testTier);

        // Create admin user
        adminUser = new User();
        adminUser.setEmail("admin@test.com");
        adminUser.setPasswordHash(passwordEncoder.encode("password"));
        adminUser.setCreatedAt(Instant.now());
        adminUser.setRoles(Set.of(userRole, adminRole));
        adminUser.setTiers(Set.of(freeTier));
        adminUser = userRepository.save(adminUser);

        // Create regular user
        regularUser = new User();
        regularUser.setEmail("user@test.com");
        regularUser.setPasswordHash(passwordEncoder.encode("password"));
        regularUser.setCreatedAt(Instant.now());
        regularUser.setRoles(Set.of(userRole));
        regularUser.setTiers(Set.of(freeTier));
        regularUser = userRepository.save(regularUser);

        // Login and get tokens
        LoginRequest adminLoginRequest = new LoginRequest();
        adminLoginRequest.setEmail("admin@test.com");
        adminLoginRequest.setPassword("password");
        adminToken = authService.login(adminLoginRequest).getToken();

        LoginRequest userLoginRequest = new LoginRequest();
        userLoginRequest.setEmail("user@test.com");
        userLoginRequest.setPassword("password");
        regularUserToken = authService.login(userLoginRequest).getToken();
    }

    @Test
    @DisplayName("GET /api/tiers - Admin can list all tiers")
    void testListAll_Admin_Success() throws Exception {
        mockMvc.perform(get("/api/tiers")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").exists())
            .andExpect(jsonPath("$[0].id").exists());
    }

    @Test
    @DisplayName("GET /api/tiers - Regular user cannot list tiers")
    void testListAll_RegularUser_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/tiers")
                .header("Authorization", "Bearer " + regularUserToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/tiers - Unauthenticated request returns 401")
    void testListAll_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/tiers"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/tiers/{id} - Admin can get tier by ID")
    void testGetById_Admin_Success() throws Exception {
        mockMvc.perform(get("/api/tiers/" + testTier.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testTier.getId()))
            .andExpect(jsonPath("$.name").value("premium"));
    }

    @Test
    @DisplayName("GET /api/tiers/{id} - Regular user cannot get tier")
    void testGetById_RegularUser_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/tiers/" + testTier.getId())
                .header("Authorization", "Bearer " + regularUserToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/tiers/{id} - Non-existent tier returns 404")
    void testGetById_NotFound_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/tiers/999")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/tiers - Admin can create tier")
    void testCreate_Admin_Success() throws Exception {
        TierRequest request = new TierRequest();
        request.setName("enterprise");

        mockMvc.perform(post("/api/tiers")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("enterprise"))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("POST /api/tiers - Regular user cannot create tier")
    void testCreate_RegularUser_ReturnsForbidden() throws Exception {
        TierRequest request = new TierRequest();
        request.setName("enterprise");

        mockMvc.perform(post("/api/tiers")
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/tiers - Duplicate name returns 400")
    void testCreate_DuplicateName_ReturnsBadRequest() throws Exception {
        TierRequest request = new TierRequest();
        request.setName("premium");

        mockMvc.perform(post("/api/tiers")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/tiers/{id} - Admin can update tier")
    void testUpdate_Admin_Success() throws Exception {
        TierUpdateRequest request = new TierUpdateRequest();
        request.setName("updated-premium");

        mockMvc.perform(put("/api/tiers/" + testTier.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("updated-premium"))
            .andExpect(jsonPath("$.id").value(testTier.getId()));
    }

    @Test
    @DisplayName("PUT /api/tiers/{id} - Regular user cannot update tier")
    void testUpdate_RegularUser_ReturnsForbidden() throws Exception {
        TierUpdateRequest request = new TierUpdateRequest();
        request.setName("updated-premium");

        mockMvc.perform(put("/api/tiers/" + testTier.getId())
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/tiers/{id} - Non-existent tier returns 404")
    void testUpdate_NotFound_ReturnsNotFound() throws Exception {
        TierUpdateRequest request = new TierUpdateRequest();
        request.setName("updated");

        mockMvc.perform(put("/api/tiers/999")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/tiers/{id} - Admin can soft delete tier")
    void testDelete_Admin_Success() throws Exception {
        mockMvc.perform(delete("/api/tiers/" + testTier.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());

        // Verify tier is soft deleted (not found in active query)
        assertFalse(tierRepository.findActiveById(testTier.getId()).isPresent());
        
        // Verify tier still exists in database (including deleted)
        assertTrue(tierRepository.findByIdIncludingDeleted(testTier.getId()).isPresent());
        
        // Verify deletedAt is set
        Tier deletedTier = tierRepository.findByIdIncludingDeleted(testTier.getId()).orElseThrow();
        assertNotNull(deletedTier.getDeletedAt());
    }

    @Test
    @DisplayName("DELETE /api/tiers/{id} - Regular user cannot delete tier")
    void testDelete_RegularUser_ReturnsForbidden() throws Exception {
        mockMvc.perform(delete("/api/tiers/" + testTier.getId())
                .header("Authorization", "Bearer " + regularUserToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/tiers/{id} - Non-existent tier returns 404")
    void testDelete_NotFound_ReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/tiers/999")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/tiers/{id} - Soft deleted tier is not returned in list")
    void testDelete_SoftDeletedTier_NotInList() throws Exception {
        // Delete tier
        mockMvc.perform(delete("/api/tiers/" + testTier.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());

        // List tiers should not include deleted tier
        mockMvc.perform(get("/api/tiers")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("free"));
    }
}


