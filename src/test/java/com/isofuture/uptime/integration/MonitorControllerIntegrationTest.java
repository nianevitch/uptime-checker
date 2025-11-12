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
import com.isofuture.uptime.dto.MonitoredUrlRequest;
import com.isofuture.uptime.entity.MonitoredUrl;
import com.isofuture.uptime.entity.Role;
import com.isofuture.uptime.entity.User;
import com.isofuture.uptime.repository.MonitoredUrlRepository;
import com.isofuture.uptime.repository.RoleRepository;
import com.isofuture.uptime.repository.UserRepository;
import com.isofuture.uptime.security.SecurityUser;
import com.isofuture.uptime.service.MonitorService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("MonitorController Integration Tests")
class MonitorControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private MonitoredUrlRepository monitoredUrlRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MonitorService monitorService;

    private User adminUser;
    private User regularUser;
    private MonitoredUrl adminMonitor;
    private MonitoredUrl userMonitor;
    private String adminToken;
    private String regularUserToken;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up
        monitoredUrlRepository.deleteAll();
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

        // Create monitors
        adminMonitor = new MonitoredUrl();
        adminMonitor.setOwner(adminUser);
        adminMonitor.setUrl("https://admin-site.com");
        adminMonitor.setLabel("Admin Monitor");
        adminMonitor.setFrequencyMinutes(5);
        adminMonitor.setInProgress(false);
        adminMonitor.setCreatedAt(Instant.now());
        adminMonitor.setUpdatedAt(Instant.now());
        adminMonitor = monitoredUrlRepository.save(adminMonitor);

        userMonitor = new MonitoredUrl();
        userMonitor.setOwner(regularUser);
        userMonitor.setUrl("https://user-site.com");
        userMonitor.setLabel("User Monitor");
        userMonitor.setFrequencyMinutes(10);
        userMonitor.setInProgress(false);
        userMonitor.setCreatedAt(Instant.now());
        userMonitor.setUpdatedAt(Instant.now());
        userMonitor = monitoredUrlRepository.save(userMonitor);

        // Get tokens
        adminToken = getAuthToken("admin@test.com", "password");
        regularUserToken = getAuthToken("user@test.com", "password");
    }

    @Test
    @DisplayName("GET /api/monitors - Admin sees all monitors")
    void testList_Admin_SeesAll() throws Exception {
        mockMvc.perform(get("/api/monitors")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));

        // Verify via service call (set up security context first)
        setSecurityContext(adminUser);
        try {
            var monitors = monitorService.listCurrentUserMonitors();
            assertEquals(2, monitors.size());
        } finally {
            clearSecurityContext();
        }
    }

    @Test
    @DisplayName("GET /api/monitors - Regular user sees only own monitors")
    void testList_RegularUser_SeesOwn() throws Exception {
        mockMvc.perform(get("/api/monitors")
                .header("Authorization", "Bearer " + regularUserToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].url").value("https://user-site.com"));

        // Verify via service call (set up security context first)
        setSecurityContext(regularUser);
        try {
            var monitors = monitorService.listCurrentUserMonitors();
            assertEquals(1, monitors.size());
            assertEquals("https://user-site.com", monitors.get(0).getUrl());
        } finally {
            clearSecurityContext();
        }
    }

    @Test
    @DisplayName("POST /api/monitors - User can create monitor")
    void testCreate_Success() throws Exception {
        MonitoredUrlRequest request = new MonitoredUrlRequest();
        request.setUrl("https://new-site.com");
        request.setLabel("New Monitor");
        request.setFrequencyMinutes(15);

        mockMvc.perform(post("/api/monitors")
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.url").value("https://new-site.com"))
            .andExpect(jsonPath("$.label").value("New Monitor"));

        // Verify via service call (set up security context first)
        setSecurityContext(regularUser);
        try {
            var monitors = monitorService.listCurrentUserMonitors();
            assertEquals(2, monitors.size()); // Original + new one
        } finally {
            clearSecurityContext();
        }
        
        // Verify via repository
        var created = monitoredUrlRepository.findByOwner(regularUser);
        assertEquals(2, created.size());
    }

    @Test
    @DisplayName("PUT /api/monitors/{id} - User can update own monitor")
    void testUpdate_OwnMonitor_Success() throws Exception {
        MonitoredUrlRequest request = new MonitoredUrlRequest();
        request.setUrl("https://updated-site.com");
        request.setLabel("Updated Monitor");
        request.setFrequencyMinutes(20);

        mockMvc.perform(put("/api/monitors/" + userMonitor.getId())
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value("https://updated-site.com"))
            .andExpect(jsonPath("$.label").value("Updated Monitor"));

        // Verify via service call (set up security context first)
        setSecurityContext(regularUser);
        try {
            var monitors = monitorService.listCurrentUserMonitors();
            var updated = monitors.stream()
                .filter(m -> m.getId().equals(userMonitor.getId()))
                .findFirst()
                .orElseThrow();
            assertEquals("https://updated-site.com", updated.getUrl());
        } finally {
            clearSecurityContext();
        }
        
        // Verify via repository
        var entity = monitoredUrlRepository.findById(userMonitor.getId()).orElseThrow();
        assertEquals("https://updated-site.com", entity.getUrl());
    }

    @Test
    @DisplayName("PUT /api/monitors/{id} - User cannot update other user's monitor")
    void testUpdate_OtherUserMonitor_Forbidden() throws Exception {
        MonitoredUrlRequest request = new MonitoredUrlRequest();
        request.setUrl("https://hacked-site.com");
        request.setLabel("Hacked Monitor");
        request.setFrequencyMinutes(20);

        mockMvc.perform(put("/api/monitors/" + adminMonitor.getId())
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest()); // Returns 400 for "Monitor not found"
    }

    @Test
    @DisplayName("DELETE /api/monitors/{id} - User can delete own monitor")
    void testDelete_OwnMonitor_Success() throws Exception {
        mockMvc.perform(delete("/api/monitors/" + userMonitor.getId())
                .header("Authorization", "Bearer " + regularUserToken))
            .andExpect(status().isNoContent());

        // Verify via service call (set up security context first)
        setSecurityContext(regularUser);
        try {
            var monitors = monitorService.listCurrentUserMonitors();
            assertEquals(0, monitors.size());
        } finally {
            clearSecurityContext();
        }
        
        // Verify via repository
        var deleted = monitoredUrlRepository.findById(userMonitor.getId());
        assertFalse(deleted.isPresent());
    }

    @Test
    @DisplayName("DELETE /api/monitors/{id} - Admin can delete any monitor")
    void testDelete_Admin_CanDeleteAny() throws Exception {
        mockMvc.perform(delete("/api/monitors/" + userMonitor.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());

        // Verify via repository
        var deleted = monitoredUrlRepository.findById(userMonitor.getId());
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

