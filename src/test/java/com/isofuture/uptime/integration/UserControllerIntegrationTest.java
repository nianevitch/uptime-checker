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
import com.isofuture.uptime.dto.PasswordChangeRequest;
import com.isofuture.uptime.dto.UserRequest;
import com.isofuture.uptime.dto.UserUpdateRequest;
import com.isofuture.uptime.entity.RoleEntity;
import com.isofuture.uptime.entity.UserEntity;
import com.isofuture.uptime.repository.MonitoredUrlRepository;
import com.isofuture.uptime.repository.RoleRepository;
import com.isofuture.uptime.repository.UserRepository;
import com.isofuture.uptime.service.UserService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("UserController Integration Tests")
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    @Autowired
    private MonitoredUrlRepository monitoredUrlRepository;

    private UserEntity adminUser;
    private UserEntity regularUser;
    private String adminToken;
    private String regularUserToken;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up (delete in correct order to avoid foreign key violations)
        monitoredUrlRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        // Create roles (check if they exist first)
        RoleEntity userRole = roleRepository.findByNameIgnoreCase("user")
            .orElseGet(() -> {
                RoleEntity role = new RoleEntity();
                role.setName("user");
                return roleRepository.save(role);
            });

        RoleEntity adminRole = roleRepository.findByNameIgnoreCase("admin")
            .orElseGet(() -> {
                RoleEntity role = new RoleEntity();
                role.setName("admin");
                return roleRepository.save(role);
            });

        // Create admin user
        adminUser = new UserEntity();
        adminUser.setEmail("admin@test.com");
        adminUser.setPasswordHash(passwordEncoder.encode("password"));
        adminUser.setCreatedAt(Instant.now());
        adminUser.setRoles(Set.of(userRole, adminRole));
        adminUser = userRepository.save(adminUser);

        // Create regular user
        regularUser = new UserEntity();
        regularUser.setEmail("user@test.com");
        regularUser.setPasswordHash(passwordEncoder.encode("password"));
        regularUser.setCreatedAt(Instant.now());
        regularUser.setRoles(Set.of(userRole));
        regularUser = userRepository.save(regularUser);

        // Get tokens
        adminToken = getAuthToken("admin@test.com", "password");
        regularUserToken = getAuthToken("user@test.com", "password");
    }

    @Test
    @DisplayName("GET /api/users - Admin can list all users")
    void testListAll_Admin_Success() throws Exception {
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/users - Regular user cannot list all users")
    void testListAll_RegularUser_Forbidden() throws Exception {
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + regularUserToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users/{id} - Admin can view any user")
    void testGetById_Admin_Success() throws Exception {
        mockMvc.perform(get("/api/users/" + regularUser.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("user@test.com"));
    }

    @Test
    @DisplayName("GET /api/users/{id} - User can view own profile")
    void testGetById_OwnProfile_Success() throws Exception {
        mockMvc.perform(get("/api/users/" + regularUser.getId())
                .header("Authorization", "Bearer " + regularUserToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("user@test.com"));
    }

    @Test
    @DisplayName("GET /api/users/{id} - User cannot view other user's profile")
    void testGetById_OtherUser_Forbidden() throws Exception {
        mockMvc.perform(get("/api/users/" + adminUser.getId())
                .header("Authorization", "Bearer " + regularUserToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/users - Admin can create user")
    void testCreate_Admin_Success() throws Exception {
        UserRequest request = new UserRequest();
        request.setEmail("new@test.com");
        request.setPassword("password123");
        request.setRoles(Set.of("user"));

        mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("new@test.com"));

        // Verify via service call
        UserEntity created = userRepository.findActiveByEmailIgnoreCase("new@test.com").orElseThrow();
        assertNotNull(created);
        assertEquals("new@test.com", created.getEmail());
    }

    @Test
    @DisplayName("POST /api/users - Regular user cannot create user")
    void testCreate_RegularUser_Forbidden() throws Exception {
        UserRequest request = new UserRequest();
        request.setEmail("new@test.com");
        request.setPassword("password123");

        mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/users/{id} - User can update own profile")
    void testUpdate_OwnProfile_Success() throws Exception {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("updated@test.com");

        mockMvc.perform(put("/api/users/" + regularUser.getId())
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("updated@test.com"));

        // Verify via service call
        UserEntity updated = userRepository.findActiveById(regularUser.getId()).orElseThrow();
        assertEquals("updated@test.com", updated.getEmail());
    }

    @Test
    @DisplayName("PUT /api/users/{id}/password - User can change own password")
    void testChangePassword_OwnPassword_Success() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setNewPassword("newpassword123");

        mockMvc.perform(put("/api/users/" + regularUser.getId() + "/password")
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNoContent());

        // Verify password was changed by trying to login with new password
        String newToken = getAuthToken("user@test.com", "newpassword123");
        assertNotNull(newToken);
    }

    @Test
    @DisplayName("DELETE /api/users/{id} - Admin can delete user")
    void testDelete_Admin_Success() throws Exception {
        mockMvc.perform(delete("/api/users/" + regularUser.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());

        // Verify via service call - user should be soft deleted
        UserEntity deleted = userRepository.findById(regularUser.getId()).orElseThrow();
        assertNotNull(deleted.getDeletedAt());
        
        // Verify via API - should return 404
        mockMvc.perform(get("/api/users/" + regularUser.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/users/{id} - Regular user cannot delete user")
    void testDelete_RegularUser_Forbidden() throws Exception {
        mockMvc.perform(delete("/api/users/" + adminUser.getId())
                .header("Authorization", "Bearer " + regularUserToken))
            .andExpect(status().isForbidden());
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

