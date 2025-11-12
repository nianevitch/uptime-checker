package com.isofuture.uptime.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.isofuture.uptime.BaseTest;
import com.isofuture.uptime.dto.TierRequest;
import com.isofuture.uptime.dto.TierResponse;
import com.isofuture.uptime.dto.TierUpdateRequest;
import com.isofuture.uptime.entity.Tier;
import com.isofuture.uptime.exception.ResourceNotFoundException;
import com.isofuture.uptime.repository.TierRepository;
import com.isofuture.uptime.security.SecurityUser;

@ExtendWith(MockitoExtension.class)
@DisplayName("TierService Unit Tests")
class TierServiceTest extends BaseTest {

    @Mock
    private TierRepository tierRepository;

    @Mock
    private UserContext userContext;

    @InjectMocks
    private TierService tierService;

    private SecurityUser adminUser;
    private SecurityUser regularUser;
    private Tier testTier;

    @BeforeEach
    void setUp() {
        adminUser = createAdminUser(1L, "admin@test.com");
        regularUser = createRegularUser(2L, "user@test.com");
        
        testTier = new Tier();
        testTier.setId(1L);
        testTier.setName("premium");
        testTier.setCreatedAt(Instant.now());
    }

    @Test
    @DisplayName("listAll - Admin can list all tiers")
    void testListAll_Admin_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(tierRepository.findAllActive()).thenReturn(List.of(testTier));

        // When
        List<TierResponse> result = tierService.listAll();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("premium", result.get(0).getName());
        verify(userContext).isAdmin();
        verify(tierRepository).findAllActive();
    }

    @Test
    @DisplayName("listAll - Regular user cannot list all tiers")
    void testListAll_RegularUser_ThrowsAccessDenied() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);

        // When/Then
        assertThrows(AccessDeniedException.class, () -> tierService.listAll());
        verify(userContext).isAdmin();
        verify(tierRepository, never()).findAllActive();
    }

    @Test
    @DisplayName("getById - Admin can view tier")
    void testGetById_Admin_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(tierRepository.findActiveById(1L)).thenReturn(Optional.of(testTier));

        // When
        TierResponse result = tierService.getById(1L);

        // Then
        assertNotNull(result);
        assertEquals("premium", result.getName());
        verify(userContext).isAdmin();
        verify(tierRepository).findActiveById(1L);
    }

    @Test
    @DisplayName("getById - Regular user cannot view tier")
    void testGetById_RegularUser_ThrowsAccessDenied() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);

        // When/Then
        assertThrows(AccessDeniedException.class, () -> tierService.getById(1L));
        verify(userContext).isAdmin();
        verify(tierRepository, never()).findActiveById(anyLong());
    }

    @Test
    @DisplayName("getById - Non-existent tier throws ResourceNotFoundException")
    void testGetById_NotFound_ThrowsException() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(tierRepository.findActiveById(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(ResourceNotFoundException.class, () -> tierService.getById(999L));
    }

    @Test
    @DisplayName("create - Admin can create tier")
    void testCreate_Admin_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(tierRepository.findActiveByNameIgnoreCase("newtier")).thenReturn(Optional.empty());
        when(tierRepository.save(any(Tier.class))).thenAnswer(invocation -> {
            Tier tier = invocation.getArgument(0);
            tier.setId(10L);
            tier.setCreatedAt(Instant.now());
            return tier;
        });

        TierRequest request = new TierRequest();
        request.setName("newtier");

        // When
        TierResponse result = tierService.create(request);

        // Then
        assertNotNull(result);
        assertEquals("newtier", result.getName());
        verify(userContext).isAdmin();
        verify(tierRepository).findActiveByNameIgnoreCase("newtier");
        verify(tierRepository).save(any(Tier.class));
    }

    @Test
    @DisplayName("create - Regular user cannot create tier")
    void testCreate_RegularUser_ThrowsAccessDenied() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);

        TierRequest request = new TierRequest();
        request.setName("newtier");

        // When/Then
        assertThrows(AccessDeniedException.class, () -> tierService.create(request));
        verify(userContext).isAdmin();
        verify(tierRepository, never()).save(any());
    }

    @Test
    @DisplayName("create - Duplicate name throws IllegalArgumentException")
    void testCreate_DuplicateName_ThrowsException() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(tierRepository.findActiveByNameIgnoreCase("existing")).thenReturn(Optional.of(testTier));

        TierRequest request = new TierRequest();
        request.setName("existing");

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> tierService.create(request));
        verify(tierRepository, never()).save(any());
    }

    @Test
    @DisplayName("update - Admin can update tier")
    void testUpdate_Admin_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(tierRepository.findActiveById(1L)).thenReturn(Optional.of(testTier));
        when(tierRepository.findActiveByNameIgnoreCase("updated")).thenReturn(Optional.empty());
        when(tierRepository.save(any(Tier.class))).thenReturn(testTier);

        TierUpdateRequest request = new TierUpdateRequest();
        request.setName("updated");

        // When
        TierResponse result = tierService.update(1L, request);

        // Then
        assertNotNull(result);
        verify(userContext).isAdmin();
        verify(tierRepository).save(any(Tier.class));
    }

    @Test
    @DisplayName("update - Regular user cannot update tier")
    void testUpdate_RegularUser_ThrowsAccessDenied() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);

        TierUpdateRequest request = new TierUpdateRequest();
        request.setName("updated");

        // When/Then
        assertThrows(AccessDeniedException.class, () -> tierService.update(1L, request));
        verify(userContext).isAdmin();
        verify(tierRepository, never()).save(any());
    }

    @Test
    @DisplayName("update - Non-existent tier throws ResourceNotFoundException")
    void testUpdate_NotFound_ThrowsException() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(tierRepository.findActiveById(999L)).thenReturn(Optional.empty());

        TierUpdateRequest request = new TierUpdateRequest();
        request.setName("updated");

        // When/Then
        assertThrows(ResourceNotFoundException.class, () -> tierService.update(999L, request));
        verify(tierRepository, never()).save(any());
    }

    @Test
    @DisplayName("softDelete - Admin can delete tier")
    void testSoftDelete_Admin_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(tierRepository.findActiveById(1L)).thenReturn(Optional.of(testTier));
        when(tierRepository.save(any(Tier.class))).thenReturn(testTier);

        // When
        tierService.softDelete(1L);

        // Then
        verify(userContext).isAdmin();
        verify(tierRepository).findActiveById(1L);
        verify(tierRepository).save(any(Tier.class));
        assertNotNull(testTier.getDeletedAt());
    }

    @Test
    @DisplayName("softDelete - Regular user cannot delete tier")
    void testSoftDelete_RegularUser_ThrowsAccessDenied() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);

        // When/Then
        assertThrows(AccessDeniedException.class, () -> tierService.softDelete(1L));
        verify(userContext).isAdmin();
        verify(tierRepository, never()).save(any());
    }

    @Test
    @DisplayName("softDelete - Non-existent tier throws ResourceNotFoundException")
    void testSoftDelete_NotFound_ThrowsException() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(tierRepository.findActiveById(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(ResourceNotFoundException.class, () -> tierService.softDelete(999L));
        verify(tierRepository, never()).save(any());
    }
}

