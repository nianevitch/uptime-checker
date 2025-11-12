package com.isofuture.uptime.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.isofuture.uptime.BaseTest;
import com.isofuture.uptime.dto.PasswordChangeRequest;
import com.isofuture.uptime.dto.UserRequest;
import com.isofuture.uptime.dto.UserResponse;
import com.isofuture.uptime.dto.UserUpdateRequest;
import com.isofuture.uptime.entity.Role;
import com.isofuture.uptime.entity.Tier;
import com.isofuture.uptime.entity.User;
import com.isofuture.uptime.exception.ResourceNotFoundException;
import com.isofuture.uptime.repository.RoleRepository;
import com.isofuture.uptime.repository.TierRepository;
import com.isofuture.uptime.repository.UserRepository;
import com.isofuture.uptime.security.SecurityUser;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest extends BaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private TierRepository tierRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserContext userContext;

    @InjectMocks
    private UserService userService;

    private SecurityUser adminUser;
    private SecurityUser regularUser;
    private User testUser;
    private Role userRole;
    private Role adminRole;
    private Tier freeTier;

    @BeforeEach
    void setUp() {
        adminUser = createAdminUser(1L, "admin@test.com");
        regularUser = createRegularUser(2L, "user@test.com");
        
        testUser = createUser(2L, "user@test.com", "hashed", "user");
        
        userRole = new Role();
        userRole.setId(1L);
        userRole.setName("user");
        
        adminRole = new Role();
        adminRole.setId(2L);
        adminRole.setName("admin");
        
        freeTier = new Tier();
        freeTier.setId(1L);
        freeTier.setName("free");
    }

    @Test
    @DisplayName("listAll - Admin can list all users")
    void testListAll_Admin_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(userRepository.findAllActive()).thenReturn(List.of(testUser));

        // When
        List<UserResponse> result = userService.listAll();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("user@test.com", result.get(0).getEmail());
        verify(userContext).isAdmin();
        verify(userRepository).findAllActive();
    }

    @Test
    @DisplayName("listAll - Regular user cannot list all users")
    void testListAll_RegularUser_ThrowsAccessDenied() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);

        // When/Then
        assertThrows(AccessDeniedException.class, () -> userService.listAll());
        verify(userContext).isAdmin();
        verify(userRepository, never()).findAllActive();
    }

    @Test
    @DisplayName("getById - Admin can view any user")
    void testGetById_Admin_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(userContext.getCurrentUser()).thenReturn(adminUser);
        when(userRepository.findActiveById(2L)).thenReturn(Optional.of(testUser));

        // When
        UserResponse result = userService.getById(2L);

        // Then
        assertNotNull(result);
        assertEquals("user@test.com", result.getEmail());
        verify(userContext).isAdmin();
        verify(userRepository).findActiveById(2L);
    }

    @Test
    @DisplayName("getById - User can view own profile")
    void testGetById_OwnProfile_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(userRepository.findActiveById(2L)).thenReturn(Optional.of(testUser));

        // When
        UserResponse result = userService.getById(2L);

        // Then
        assertNotNull(result);
        assertEquals("user@test.com", result.getEmail());
        verify(userContext).isAdmin();
        verify(userRepository).findActiveById(2L);
    }

    @Test
    @DisplayName("getById - User cannot view other user's profile")
    void testGetById_OtherUser_ThrowsAccessDenied() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        User otherUser = createUser(3L, "other@test.com", "hashed", "user");
        when(userRepository.findActiveById(3L)).thenReturn(Optional.of(otherUser));

        // When/Then
        assertThrows(AccessDeniedException.class, () -> userService.getById(3L));
        verify(userContext).isAdmin();
    }

    @Test
    @DisplayName("getById - Non-existent user throws ResourceNotFoundException")
    void testGetById_NotFound_ThrowsException() {
        // Given
        when(userRepository.findActiveById(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(ResourceNotFoundException.class, () -> userService.getById(999L));
    }

    @Test
    @DisplayName("create - Admin can create user")
    void testCreate_Admin_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(userRepository.findActiveByEmailIgnoreCase("new@test.com")).thenReturn(Optional.empty());
        when(roleRepository.findByNameIgnoreCase("user")).thenReturn(Optional.of(userRole));
        when(tierRepository.findActiveByNameIgnoreCase("free")).thenReturn(Optional.of(freeTier));
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });

        UserRequest request = new UserRequest();
        request.setEmail("new@test.com");
        request.setPassword("password123");

        // When
        UserResponse result = userService.create(request);

        // Then
        assertNotNull(result);
        assertEquals("new@test.com", result.getEmail());
        verify(userContext).isAdmin();
        verify(userRepository).findActiveByEmailIgnoreCase("new@test.com");
        verify(tierRepository).findActiveByNameIgnoreCase("free");
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("create - Regular user cannot create user")
    void testCreate_RegularUser_ThrowsAccessDenied() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);

        UserRequest request = new UserRequest();
        request.setEmail("new@test.com");
        request.setPassword("password123");

        // When/Then
        assertThrows(AccessDeniedException.class, () -> userService.create(request));
        verify(userContext).isAdmin();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("create - Duplicate email throws IllegalArgumentException")
    void testCreate_DuplicateEmail_ThrowsException() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(userRepository.findActiveByEmailIgnoreCase("existing@test.com"))
            .thenReturn(Optional.of(testUser));

        UserRequest request = new UserRequest();
        request.setEmail("existing@test.com");
        request.setPassword("password123");

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> userService.create(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("update - Admin can update any user")
    void testUpdate_Admin_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(userContext.getCurrentUser()).thenReturn(adminUser);
        when(userRepository.findActiveById(2L)).thenReturn(Optional.of(testUser));
        when(userRepository.findActiveByEmailIgnoreCase("updated@test.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("updated@test.com");

        // When
        UserResponse result = userService.update(2L, request);

        // Then
        assertNotNull(result);
        verify(userContext).isAdmin();
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("update - User can update own profile")
    void testUpdate_OwnProfile_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(userRepository.findActiveById(2L)).thenReturn(Optional.of(testUser));
        when(userRepository.findActiveByEmailIgnoreCase("updated@test.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("updated@test.com");

        // When
        UserResponse result = userService.update(2L, request);

        // Then
        assertNotNull(result);
        verify(userContext).isAdmin();
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("update - User cannot update other user's profile")
    void testUpdate_OtherUser_ThrowsAccessDenied() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        User otherUser = createUser(3L, "other@test.com", "hashed", "user");
        when(userRepository.findActiveById(3L)).thenReturn(Optional.of(otherUser));

        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("updated@test.com");

        // When/Then
        assertThrows(AccessDeniedException.class, () -> userService.update(3L, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("changePassword - Admin can change any user's password")
    void testChangePassword_Admin_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(userContext.getCurrentUser()).thenReturn(adminUser);
        when(userRepository.findActiveById(2L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("newpass")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setNewPassword("newpass");

        // When
        userService.changePassword(2L, request);

        // Then
        verify(passwordEncoder).encode("newpass");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("changePassword - User can change own password")
    void testChangePassword_OwnPassword_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(userRepository.findActiveById(2L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("newpass")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setNewPassword("newpass");

        // When
        userService.changePassword(2L, request);

        // Then
        verify(passwordEncoder).encode("newpass");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("changePassword - User cannot change other user's password")
    void testChangePassword_OtherUser_ThrowsAccessDenied() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        User otherUser = createUser(3L, "other@test.com", "hashed", "user");
        when(userRepository.findActiveById(3L)).thenReturn(Optional.of(otherUser));

        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setNewPassword("newpass");

        // When/Then
        assertThrows(AccessDeniedException.class, () -> userService.changePassword(3L, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("softDelete - Admin can delete user")
    void testSoftDelete_Admin_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(userContext.getCurrentUser()).thenReturn(adminUser);
        when(userRepository.findActiveById(2L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        userService.softDelete(2L);

        // Then
        verify(userRepository).save(any(User.class));
        verify(userContext).isAdmin();
    }

    @Test
    @DisplayName("softDelete - Regular user cannot delete user")
    void testSoftDelete_RegularUser_ThrowsAccessDenied() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);

        // When/Then
        assertThrows(AccessDeniedException.class, () -> userService.softDelete(2L));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("softDelete - Admin cannot delete own account")
    void testSoftDelete_OwnAccount_ThrowsException() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(userContext.getCurrentUser()).thenReturn(adminUser);
        User adminEntity = createUser(1L, "admin@test.com", "hashed", "admin", "user");
        when(userRepository.findActiveById(1L)).thenReturn(Optional.of(adminEntity));

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> userService.softDelete(1L));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("softDelete - Non-existent user throws ResourceNotFoundException")
    void testSoftDelete_NotFound_ThrowsException() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(userRepository.findActiveById(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(ResourceNotFoundException.class, () -> userService.softDelete(999L));
    }

    @Test
    @DisplayName("update - Admin can update user roles")
    void testUpdate_Admin_UpdateRoles_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(userContext.getCurrentUser()).thenReturn(adminUser);
        when(userRepository.findActiveById(2L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByNameIgnoreCase("admin")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByNameIgnoreCase("user")).thenReturn(Optional.of(userRole));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserUpdateRequest request = new UserUpdateRequest();
        request.setRoles(Set.of("admin", "user"));

        // When
        UserResponse result = userService.update(2L, request);

        // Then
        assertNotNull(result);
        verify(userContext, atLeastOnce()).isAdmin();
        verify(roleRepository).findByNameIgnoreCase("admin");
        verify(roleRepository).findByNameIgnoreCase("user");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("update - Duplicate email throws IllegalArgumentException")
    void testUpdate_DuplicateEmail_ThrowsException() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(userContext.getCurrentUser()).thenReturn(adminUser);
        when(userRepository.findActiveById(2L)).thenReturn(Optional.of(testUser));
        User existingUser = createUser(3L, "existing@test.com", "hashed", "user");
        when(userRepository.findActiveByEmailIgnoreCase("existing@test.com"))
            .thenReturn(Optional.of(existingUser));

        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("existing@test.com");

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> userService.update(2L, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("update - Non-existent user throws ResourceNotFoundException")
    void testUpdate_NotFound_ThrowsException() {
        // Given
        when(userRepository.findActiveById(999L)).thenReturn(Optional.empty());

        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("updated@test.com");

        // When/Then
        assertThrows(ResourceNotFoundException.class, () -> userService.update(999L, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("update - User cannot update roles")
    void testUpdate_UserCannotUpdateRoles() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(userRepository.findActiveById(2L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserUpdateRequest request = new UserUpdateRequest();
        request.setRoles(Set.of("admin"));

        // When
        UserResponse result = userService.update(2L, request);

        // Then
        assertNotNull(result);
        verify(userContext, atLeastOnce()).isAdmin();
        verify(roleRepository, never()).findByNameIgnoreCase(anyString());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("update - Update without email change")
    void testUpdate_NoEmailChange_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(userRepository.findActiveById(2L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("user@test.com"); // Same email

        // When
        UserResponse result = userService.update(2L, request);

        // Then
        assertNotNull(result);
        verify(userRepository, never()).findActiveByEmailIgnoreCase(anyString());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("changePassword - Non-existent user throws ResourceNotFoundException")
    void testChangePassword_NotFound_ThrowsException() {
        // Given
        when(userRepository.findActiveById(999L)).thenReturn(Optional.empty());

        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setNewPassword("newpass");

        // When/Then
        assertThrows(ResourceNotFoundException.class, () -> userService.changePassword(999L, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("create - Admin can create user with roles")
    void testCreate_Admin_WithRoles_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(userRepository.findActiveByEmailIgnoreCase("new@test.com")).thenReturn(Optional.empty());
        when(roleRepository.findByNameIgnoreCase("admin")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByNameIgnoreCase("user")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });

        UserRequest request = new UserRequest();
        request.setEmail("new@test.com");
        request.setPassword("password123");
        request.setRoles(Set.of("admin", "user"));

        // When
        UserResponse result = userService.create(request);

        // Then
        assertNotNull(result);
        assertEquals("new@test.com", result.getEmail());
        verify(roleRepository).findByNameIgnoreCase("admin");
        verify(roleRepository).findByNameIgnoreCase("user");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("create - Admin can create user with new role")
    void testCreate_Admin_WithNewRole_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(userRepository.findActiveByEmailIgnoreCase("new@test.com")).thenReturn(Optional.empty());
        when(roleRepository.findByNameIgnoreCase("custom")).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            role.setId(3L);
            return role;
        });
        when(tierRepository.findActiveByNameIgnoreCase("free")).thenReturn(Optional.of(freeTier));
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });

        UserRequest request = new UserRequest();
        request.setEmail("new@test.com");
        request.setPassword("password123");
        request.setRoles(Set.of("custom"));

        // When
        UserResponse result = userService.create(request);

        // Then
        assertNotNull(result);
        verify(roleRepository).findByNameIgnoreCase("custom");
        verify(roleRepository).save(any(Role.class));
        verify(tierRepository).findActiveByNameIgnoreCase("free");
        verify(userRepository).save(any(User.class));
    }
}

