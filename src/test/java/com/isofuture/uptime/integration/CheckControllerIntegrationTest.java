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
import com.isofuture.uptime.dto.CheckResultUpdateRequest;
import com.isofuture.uptime.dto.ExecuteCheckRequest;
import com.isofuture.uptime.entity.CheckResult;
import com.isofuture.uptime.entity.MonitoredUrl;
import com.isofuture.uptime.entity.Role;
import com.isofuture.uptime.entity.User;
import com.isofuture.uptime.repository.CheckResultRepository;
import com.isofuture.uptime.repository.MonitoredUrlRepository;
import com.isofuture.uptime.repository.RoleRepository;
import com.isofuture.uptime.repository.UserRepository;
import com.isofuture.uptime.security.SecurityUser;
import com.isofuture.uptime.service.CheckService;
import com.isofuture.uptime.service.MonitorService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("CheckController Integration Tests with Cross-Referencing")
class CheckControllerIntegrationTest {

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
    private CheckResultRepository checkResultRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CheckService checkService;

    @Autowired
    private MonitorService monitorService;

    private User adminUser;
    private User regularUser;
    private MonitoredUrl testMonitor;
    private String adminToken;
    private String regularUserToken;
    private String workerApiKey = "test-worker-key-12345";

    @BeforeEach
    void setUp() throws Exception {
        // Clean up
        checkResultRepository.deleteAll();
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

        // Create monitor
        testMonitor = new MonitoredUrl();
        testMonitor.setOwner(regularUser);
        testMonitor.setUrl("https://example.com");
        testMonitor.setLabel("Test Monitor");
        testMonitor.setFrequencyMinutes(5);
        testMonitor.setInProgress(false);
        testMonitor.setNextCheckAt(Instant.now().minusSeconds(60));
        testMonitor.setCreatedAt(Instant.now());
        testMonitor.setUpdatedAt(Instant.now());
        testMonitor = monitoredUrlRepository.save(testMonitor);

        // Get tokens
        adminToken = getAuthToken("admin@test.com", "password");
        regularUserToken = getAuthToken("user@test.com", "password");
    }

    @Test
    @DisplayName("POST /api/checks/execute - User can execute own monitor check")
    void testExecute_OwnMonitor_Success() throws Exception {
        ExecuteCheckRequest request = new ExecuteCheckRequest();
        request.setMonitorId(testMonitor.getId());

        String response = mockMvc.perform(post("/api/checks/execute")
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.httpCode").exists()) // Verify httpCode is present
            .andExpect(jsonPath("$.responseTimeMs").exists()) // Verify responseTimeMs is present
            .andExpect(jsonPath("$.checkedAt").exists()) // Verify checkedAt is present
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Cross-reference: Verify via service call
        var checkResult = objectMapper.readTree(response);
        Long resultId = checkResult.get("id").asLong();
        
        CheckResult savedResult = checkResultRepository.findById(resultId).orElseThrow();
        assertNotNull(savedResult);
        assertEquals(testMonitor.getId(), savedResult.getMonitoredUrl().getId());
        
        // Cross-reference: Verify monitor state via repository
        MonitoredUrl updatedMonitor = monitoredUrlRepository.findById(testMonitor.getId()).orElseThrow();
        assertFalse(updatedMonitor.isInProgress());
        assertNotNull(updatedMonitor.getNextCheckAt());
        
        // Cross-reference: Verify via service call (set up security context first)
        setSecurityContext(regularUser);
        try {
            var monitors = monitorService.listCurrentUserMonitors();
            var monitor = monitors.stream()
                .filter(m -> m.getId().equals(testMonitor.getId()))
                .findFirst()
                .orElseThrow();
            assertFalse(monitor.isInProgress());
        } finally {
            clearSecurityContext();
        }
    }

    @Test
    @DisplayName("POST /api/checks/execute - User cannot execute other user's monitor")
    void testExecute_OtherUserMonitor_Forbidden() throws Exception {
        // Create another user's monitor
        User otherUser = new User();
        otherUser.setEmail("other@test.com");
        otherUser.setPasswordHash(passwordEncoder.encode("password"));
        otherUser.setCreatedAt(Instant.now());
        otherUser.setRoles(Set.of(roleRepository.findByNameIgnoreCase("user").orElseThrow()));
        otherUser = userRepository.save(otherUser);

        MonitoredUrl otherMonitor = new MonitoredUrl();
        otherMonitor.setOwner(otherUser);
        otherMonitor.setUrl("https://other.com");
        otherMonitor.setLabel("Other Monitor");
        otherMonitor.setFrequencyMinutes(5);
        otherMonitor.setInProgress(false);
        otherMonitor.setCreatedAt(Instant.now());
        otherMonitor.setUpdatedAt(Instant.now());
        otherMonitor = monitoredUrlRepository.save(otherMonitor);

        ExecuteCheckRequest request = new ExecuteCheckRequest();
        request.setMonitorId(otherMonitor.getId());

        mockMvc.perform(post("/api/checks/execute")
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());

        // Cross-reference: Verify no check result was created
        var results = checkResultRepository.findAll();
        assertEquals(0, results.size());
    }

    @Test
    @DisplayName("POST /api/checks/next - Worker can fetch next checks")
    void testFetchNext_Worker_Success() throws Exception {
        String response = mockMvc.perform(post("/api/checks/next?count=1")
                .header("X-API-Key", workerApiKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Cross-reference: Verify monitor state via repository
        MonitoredUrl updatedMonitor = monitoredUrlRepository.findById(testMonitor.getId()).orElseThrow();
        assertTrue(updatedMonitor.isInProgress());
        
        // Cross-reference: Verify via service call
        var pending = monitorService.getInProgressChecks(null);
        assertEquals(1, pending.size());
        assertEquals(testMonitor.getId(), pending.get(0).getMonitorId());
    }

    @Test
    @DisplayName("PATCH /api/checks/result - Worker can record result")
    void testRecordResult_Worker_Success() throws Exception {
        // First, mark as in progress
        testMonitor.setInProgress(true);
        monitoredUrlRepository.save(testMonitor);

        CheckResultUpdateRequest request = new CheckResultUpdateRequest();
        request.setMonitorId(testMonitor.getId());
        request.setHttpCode(200);
        request.setResponseTimeMs(150.5);
        request.setErrorMessage(null);
        request.setCheckedAt(Instant.now());

        String response = mockMvc.perform(patch("/api/checks/result")
                .header("X-API-Key", workerApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.httpCode").value(200))
            .andExpect(jsonPath("$.responseTimeMs").value(150.5))
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Cross-reference: Verify via repository
        var checkResult = objectMapper.readTree(response);
        Long resultId = checkResult.get("id").asLong();
        
        CheckResult savedResult = checkResultRepository.findById(resultId).orElseThrow();
        assertEquals(200, savedResult.getHttpCode());
        assertEquals(150.5, savedResult.getResponseTimeMs());
        assertEquals(testMonitor.getId(), savedResult.getMonitoredUrl().getId());
        
        // Cross-reference: Verify monitor state via repository
        MonitoredUrl updatedMonitor = monitoredUrlRepository.findById(testMonitor.getId()).orElseThrow();
        assertFalse(updatedMonitor.isInProgress());
        assertNotNull(updatedMonitor.getNextCheckAt());
        
        // Cross-reference: Verify via service call (set up security context first)
        setSecurityContext(regularUser);
        try {
            var monitors = monitorService.listCurrentUserMonitors();
            var monitor = monitors.stream()
                .filter(m -> m.getId().equals(testMonitor.getId()))
                .findFirst()
                .orElseThrow();
            assertFalse(monitor.isInProgress());
            assertEquals(1, monitor.getRecentResults().size());
            assertEquals(200, monitor.getRecentResults().get(0).getHttpCode());
        } finally {
            clearSecurityContext();
        }
    }

    @Test
    @DisplayName("GET /api/checks/pending - Worker can fetch pending checks")
    void testFetchPending_Worker_Success() throws Exception {
        // Mark monitor as in progress
        testMonitor.setInProgress(true);
        monitoredUrlRepository.save(testMonitor);

        String response = mockMvc.perform(get("/api/checks/pending?count=5")
                .header("X-API-Key", workerApiKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Cross-reference: Verify via service call
        var pending = monitorService.getInProgressChecks(5);
        assertEquals(1, pending.size());
        assertEquals(testMonitor.getId(), pending.get(0).getMonitorId());
    }

    @Test
    @DisplayName("POST /api/checks/execute - Missing monitorId returns 400")
    void testExecute_MissingMonitorId_ReturnsBadRequest() throws Exception {
        ExecuteCheckRequest request = new ExecuteCheckRequest();
        // monitorId not set

        mockMvc.perform(post("/api/checks/execute")
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").exists())
            .andExpect(jsonPath("$.errors.monitorId").exists());
    }

    @Test
    @DisplayName("POST /api/checks/execute - Non-existent monitor returns 403")
    void testExecute_NonExistentMonitor_ReturnsForbidden() throws Exception {
        ExecuteCheckRequest request = new ExecuteCheckRequest();
        request.setMonitorId(999L); // Non-existent monitor

        mockMvc.perform(post("/api/checks/execute")
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/checks/execute - Unauthorized request returns 401")
    void testExecute_Unauthorized_ReturnsUnauthorized() throws Exception {
        ExecuteCheckRequest request = new ExecuteCheckRequest();
        request.setMonitorId(testMonitor.getId());

        mockMvc.perform(post("/api/checks/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/checks/next - Invalid API key returns 401")
    void testFetchNext_InvalidApiKey_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/checks/next?count=1")
                .header("X-API-Key", "invalid-key"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/checks/next - Missing API key returns 401")
    void testFetchNext_MissingApiKey_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/checks/next?count=1"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/checks/next - Count exceeds maximum is clamped to 50")
    void testFetchNext_CountExceedsMaximum_ClampedTo50() throws Exception {
        mockMvc.perform(post("/api/checks/next?count=100")
                .header("X-API-Key", workerApiKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
        
        // Verify that count was clamped (should not throw error)
        var pending = monitorService.getInProgressChecks(100);
        assertNotNull(pending);
    }

    @Test
    @DisplayName("PATCH /api/checks/result - Missing API key returns 401")
    void testRecordResult_MissingApiKey_ReturnsUnauthorized() throws Exception {
        CheckResultUpdateRequest request = new CheckResultUpdateRequest();
        request.setMonitorId(testMonitor.getId());
        request.setHttpCode(200);
        request.setResponseTimeMs(150.5);

        mockMvc.perform(patch("/api/checks/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /api/checks/result - Invalid API key returns 401")
    void testRecordResult_InvalidApiKey_ReturnsUnauthorized() throws Exception {
        CheckResultUpdateRequest request = new CheckResultUpdateRequest();
        request.setMonitorId(testMonitor.getId());
        request.setHttpCode(200);
        request.setResponseTimeMs(150.5);

        mockMvc.perform(patch("/api/checks/result")
                .header("X-API-Key", "invalid-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /api/checks/result - Missing monitorId returns 400")
    void testRecordResult_MissingMonitorId_ReturnsBadRequest() throws Exception {
        CheckResultUpdateRequest request = new CheckResultUpdateRequest();
        // monitorId not set
        request.setHttpCode(200);
        request.setResponseTimeMs(150.5);

        mockMvc.perform(patch("/api/checks/result")
                .header("X-API-Key", workerApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").exists())
            .andExpect(jsonPath("$.errors.monitorId").exists());
    }

    @Test
    @DisplayName("PATCH /api/checks/result - Non-existent monitor returns 400")
    void testRecordResult_NonExistentMonitor_ReturnsBadRequest() throws Exception {
        CheckResultUpdateRequest request = new CheckResultUpdateRequest();
        request.setMonitorId(999L); // Non-existent monitor
        request.setHttpCode(200);
        request.setResponseTimeMs(150.5);

        mockMvc.perform(patch("/api/checks/result")
                .header("X-API-Key", workerApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("PATCH /api/checks/result - Handles null httpCode for error scenarios")
    void testRecordResult_NullHttpCode_Success() throws Exception {
        // First, mark as in progress
        testMonitor.setInProgress(true);
        monitoredUrlRepository.save(testMonitor);

        CheckResultUpdateRequest request = new CheckResultUpdateRequest();
        request.setMonitorId(testMonitor.getId());
        request.setHttpCode(null); // Null for error scenarios
        request.setErrorMessage("Connection timeout");
        request.setResponseTimeMs(15000.0);
        request.setCheckedAt(Instant.now());

        mockMvc.perform(patch("/api/checks/result")
                .header("X-API-Key", workerApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.errorMessage").value("Connection timeout"))
            .andExpect(jsonPath("$.responseTimeMs").value(15000.0));

        // Verify monitor state
        MonitoredUrl updatedMonitor = monitoredUrlRepository.findById(testMonitor.getId()).orElseThrow();
        assertFalse(updatedMonitor.isInProgress());
    }

    @Test
    @DisplayName("GET /api/checks/pending - Invalid API key returns 401")
    void testFetchPending_InvalidApiKey_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/checks/pending?count=5")
                .header("X-API-Key", "invalid-key"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/checks/pending - Missing API key returns 401")
    void testFetchPending_MissingApiKey_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/checks/pending?count=5"))
            .andExpect(status().isUnauthorized());
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
        com.isofuture.uptime.dto.LoginRequest loginRequest = new com.isofuture.uptime.dto.LoginRequest();
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

