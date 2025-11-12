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
import com.isofuture.uptime.entity.Role;
import com.isofuture.uptime.entity.User;
import com.isofuture.uptime.repository.PingRepository;
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
    private PingRepository pingRepository;

    private User adminUser;
    private User regularUser;
    private String adminToken;
    private String regularUserToken;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up (delete in correct order to avoid foreign key violations)
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
        User created = userRepository.findActiveByEmailIgnoreCase("new@test.com").orElseThrow();
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
        User updated = userRepository.findActiveById(regularUser.getId()).orElseThrow();
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

        // Verify via service call - user should be soft deleted (record still exists in DB)
        User deleted = userRepository.findByIdIncludingDeleted(regularUser.getId()).orElseThrow();
        assertNotNull(deleted.getDeletedAt(), "User should be soft deleted (deletedAt should be set)");
        
        // Verify the user record still exists in the database (not physically deleted)
        assertNotNull(deleted.getId(), "User record should still exist in database");
        assertEquals(regularUser.getEmail(), deleted.getEmail(), "User email should remain unchanged");
        
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

    @Test
    @DisplayName("PUT /api/users/{id} - Update with invalid email format returns 400")
    void testUpdate_InvalidEmail_ReturnsBadRequest() throws Exception {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("invalid-email");

        mockMvc.perform(put("/api/users/" + regularUser.getId())
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").exists())
            .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    @DisplayName("PUT /api/users/{id} - Update with duplicate email returns 400")
    void testUpdate_DuplicateEmail_ReturnsBadRequest() throws Exception {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("admin@test.com"); // Already exists

        mockMvc.perform(put("/api/users/" + regularUser.getId())
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("PUT /api/users/{id}/password - Missing password returns 400")
    void testChangePassword_MissingPassword_ReturnsBadRequest() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest();
        // No password set

        mockMvc.perform(put("/api/users/" + regularUser.getId() + "/password")
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").exists())
            .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    @DisplayName("PUT /api/users/{id}/password - Non-existent user returns 404")
    void testChangePassword_NonExistentUser_ReturnsNotFound() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setNewPassword("newpassword123");

        mockMvc.perform(put("/api/users/999/password")
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/users/{id} - Non-existent user returns 404")
    void testGetById_NonExistentUser_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/users/999")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("PUT /api/users/{id} - Non-existent user returns 404")
    void testUpdate_NonExistentUser_ReturnsNotFound() throws Exception {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("updated@test.com");

        mockMvc.perform(put("/api/users/999")
                .header("Authorization", "Bearer " + regularUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/users/{id} - Non-existent user returns 404")
    void testDelete_NonExistentUser_ReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/users/999")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/users/{id} - Admin cannot delete own account")
    void testDelete_AdminOwnAccount_ReturnsBadRequest() throws Exception {
        mockMvc.perform(delete("/api/users/" + adminUser.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists())
            .andExpect(jsonPath("$.error").value("Cannot delete your own account"));
    }

    @Test
    @DisplayName("POST /api/users - Create user with duplicate email returns 400")
    void testCreate_DuplicateEmail_ReturnsBadRequest() throws Exception {
        UserRequest request = new UserRequest();
        request.setEmail("admin@test.com"); // Already exists
        request.setPassword("password123");
        request.setRoles(Set.of("user"));

        mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST /api/users - Create user with invalid email returns 400")
    void testCreate_InvalidEmail_ReturnsBadRequest() throws Exception {
        UserRequest request = new UserRequest();
        request.setEmail("invalid-email");
        request.setPassword("password123");

        mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").exists())
            .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    @DisplayName("POST /api/users - Create user with missing password returns 400")
    void testCreate_MissingPassword_ReturnsBadRequest() throws Exception {
        UserRequest request = new UserRequest();
        request.setEmail("new@test.com");
        // No password

        mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").exists())
            .andExpect(jsonPath("$.errors.password").exists());
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

