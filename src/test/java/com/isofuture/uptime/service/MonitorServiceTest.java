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

import com.isofuture.uptime.BaseTest;
import com.isofuture.uptime.dto.MonitoredUrlRequest;
import com.isofuture.uptime.dto.MonitoredUrlResponse;
import com.isofuture.uptime.entity.MonitoredUrl;
import com.isofuture.uptime.entity.User;
import com.isofuture.uptime.mapper.MonitoredUrlMapper;
import com.isofuture.uptime.repository.CheckResultRepository;
import com.isofuture.uptime.repository.MonitoredUrlRepository;
import com.isofuture.uptime.repository.UserRepository;
import com.isofuture.uptime.security.SecurityUser;

@ExtendWith(MockitoExtension.class)
@DisplayName("MonitorService Unit Tests")
class MonitorServiceTest extends BaseTest {

    @Mock
    private MonitoredUrlRepository monitoredUrlRepository;

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MonitoredUrlMapper mapper;

    @Mock
    private UserContext userContext;

    @InjectMocks
    private MonitorService monitorService;

    private SecurityUser adminUser;
    private SecurityUser regularUser;
    private User testUser;
    private MonitoredUrl testMonitor;

    @BeforeEach
    void setUp() {
        adminUser = createAdminUser(1L, "admin@test.com");
        regularUser = createRegularUser(2L, "user@test.com");
        testUser = createUser(2L, "user@test.com", "hashed", "user");
        
        testMonitor = new MonitoredUrl();
        testMonitor.setId(1L);
        testMonitor.setUrl("https://example.com");
        testMonitor.setLabel("Test Monitor");
        testMonitor.setOwner(testUser);
        testMonitor.setFrequencyMinutes(5);
        testMonitor.setInProgress(false);
    }

    @Test
    @DisplayName("listCurrentUserMonitors - Admin sees all monitors")
    void testListCurrentUserMonitors_Admin_ReturnsAll() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(monitoredUrlRepository.findAll()).thenReturn(List.of(testMonitor));
        when(checkResultRepository.findByMonitoredUrlOrderByCheckedAtDesc(any()))
            .thenReturn(List.of());
        when(mapper.toResponse(any(), anyList())).thenReturn(new MonitoredUrlResponse());

        // When
        List<MonitoredUrlResponse> result = monitorService.listCurrentUserMonitors();

        // Then
        assertNotNull(result);
        verify(userContext).isAdmin();
        verify(monitoredUrlRepository).findAll();
        verify(monitoredUrlRepository, never()).findByOwner(any());
    }

    @Test
    @DisplayName("listCurrentUserMonitors - Regular user sees only own monitors")
    void testListCurrentUserMonitors_RegularUser_ReturnsOwn() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(userRepository.findById(2L)).thenReturn(Optional.of(testUser));
        when(monitoredUrlRepository.findByOwner(testUser)).thenReturn(List.of(testMonitor));
        when(checkResultRepository.findByMonitoredUrlOrderByCheckedAtDesc(any()))
            .thenReturn(List.of());
        when(mapper.toResponse(any(), anyList())).thenReturn(new MonitoredUrlResponse());

        // When
        List<MonitoredUrlResponse> result = monitorService.listCurrentUserMonitors();

        // Then
        assertNotNull(result);
        verify(userContext).isAdmin();
        verify(monitoredUrlRepository).findByOwner(testUser);
        verify(monitoredUrlRepository, never()).findAll();
    }

    @Test
    @DisplayName("createMonitor - Creates monitor with correct owner")
    void testCreateMonitor_Success() {
        // Given
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(userRepository.findById(2L)).thenReturn(Optional.of(testUser));
        when(monitoredUrlRepository.save(any(MonitoredUrl.class))).thenReturn(testMonitor);
        when(checkResultRepository.findByMonitoredUrlOrderByCheckedAtDesc(any()))
            .thenReturn(List.of());
        when(mapper.toResponse(any(), anyList())).thenReturn(new MonitoredUrlResponse());

        MonitoredUrlRequest request = new MonitoredUrlRequest();
        request.setUrl("https://example.com");
        request.setLabel("Test Monitor");
        request.setFrequencyMinutes(5);

        // When
        MonitoredUrlResponse result = monitorService.createMonitor(request);

        // Then
        assertNotNull(result);
        verify(userRepository).findById(2L);
        verify(monitoredUrlRepository).save(any(MonitoredUrl.class));
    }

    @Test
    @DisplayName("updateMonitor - Admin can update any monitor")
    void testUpdateMonitor_Admin_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(monitoredUrlRepository.findById(1L)).thenReturn(Optional.of(testMonitor));
        when(checkResultRepository.findByMonitoredUrlOrderByCheckedAtDesc(any()))
            .thenReturn(List.of());
        when(mapper.toResponse(any(), anyList())).thenReturn(new MonitoredUrlResponse());

        MonitoredUrlRequest request = new MonitoredUrlRequest();
        request.setUrl("https://updated.com");
        request.setLabel("Updated Monitor");
        request.setFrequencyMinutes(10);

        // When
        MonitoredUrlResponse result = monitorService.updateMonitor(1L, request);

        // Then
        assertNotNull(result);
        verify(userContext).isAdmin();
        verify(monitoredUrlRepository).findById(1L);
    }

    @Test
    @DisplayName("updateMonitor - User can update own monitor")
    void testUpdateMonitor_OwnMonitor_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(monitoredUrlRepository.findByIdAndOwnerId(1L, 2L)).thenReturn(Optional.of(testMonitor));
        when(checkResultRepository.findByMonitoredUrlOrderByCheckedAtDesc(any()))
            .thenReturn(List.of());
        when(mapper.toResponse(any(), anyList())).thenReturn(new MonitoredUrlResponse());

        MonitoredUrlRequest request = new MonitoredUrlRequest();
        request.setUrl("https://updated.com");
        request.setLabel("Updated Monitor");
        request.setFrequencyMinutes(10);

        // When
        MonitoredUrlResponse result = monitorService.updateMonitor(1L, request);

        // Then
        assertNotNull(result);
        verify(userContext).isAdmin();
        verify(monitoredUrlRepository).findByIdAndOwnerId(1L, 2L);
    }

    @Test
    @DisplayName("deleteMonitor - Admin can delete any monitor")
    void testDeleteMonitor_Admin_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(monitoredUrlRepository.findById(1L)).thenReturn(Optional.of(testMonitor));

        // When
        monitorService.deleteMonitor(1L);

        // Then
        verify(monitoredUrlRepository).delete(testMonitor);
    }

    @Test
    @DisplayName("deleteMonitor - User can delete own monitor")
    void testDeleteMonitor_OwnMonitor_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(monitoredUrlRepository.findByIdAndOwnerId(1L, 2L)).thenReturn(Optional.of(testMonitor));

        // When
        monitorService.deleteMonitor(1L);

        // Then
        verify(monitoredUrlRepository).delete(testMonitor);
    }

    @Test
    @DisplayName("updateMonitor - Non-existent monitor throws IllegalArgumentException")
    void testUpdateMonitor_NotFound_ThrowsException() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(monitoredUrlRepository.findById(999L)).thenReturn(Optional.empty());

        MonitoredUrlRequest request = new MonitoredUrlRequest();
        request.setUrl("https://updated.com");
        request.setLabel("Updated Monitor");
        request.setFrequencyMinutes(10);

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> monitorService.updateMonitor(999L, request));
    }

    @Test
    @DisplayName("updateMonitor - User cannot update other user's monitor")
    void testUpdateMonitor_OtherUserMonitor_ThrowsException() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(monitoredUrlRepository.findByIdAndOwnerId(1L, 2L)).thenReturn(Optional.empty());

        MonitoredUrlRequest request = new MonitoredUrlRequest();
        request.setUrl("https://updated.com");
        request.setLabel("Updated Monitor");
        request.setFrequencyMinutes(10);

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> monitorService.updateMonitor(1L, request));
    }

    @Test
    @DisplayName("deleteMonitor - Non-existent monitor throws IllegalArgumentException")
    void testDeleteMonitor_NotFound_ThrowsException() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(monitoredUrlRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> monitorService.deleteMonitor(999L));
        verify(monitoredUrlRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteMonitor - User cannot delete other user's monitor")
    void testDeleteMonitor_OtherUserMonitor_ThrowsException() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(monitoredUrlRepository.findByIdAndOwnerId(1L, 2L)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> monitorService.deleteMonitor(1L));
        verify(monitoredUrlRepository, never()).delete(any());
    }

    @Test
    @DisplayName("getInProgressChecks - Returns in-progress checks with limit")
    void testGetInProgressChecks_WithLimit_Success() {
        // Given
        MonitoredUrl inProgressMonitor = new MonitoredUrl();
        inProgressMonitor.setId(2L);
        inProgressMonitor.setUrl("https://example2.com");
        inProgressMonitor.setLabel("In Progress Monitor");
        inProgressMonitor.setInProgress(true);

        when(monitoredUrlRepository.findByInProgressTrueOrderByUpdatedAtAsc())
            .thenReturn(List.of(testMonitor, inProgressMonitor));

        // When
        List<com.isofuture.uptime.dto.PendingCheckResponse> result = monitorService.getInProgressChecks(1);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(monitoredUrlRepository).findByInProgressTrueOrderByUpdatedAtAsc();
    }

    @Test
    @DisplayName("getInProgressChecks - Returns all in-progress checks without limit")
    void testGetInProgressChecks_WithoutLimit_Success() {
        // Given
        MonitoredUrl inProgressMonitor = new MonitoredUrl();
        inProgressMonitor.setId(2L);
        inProgressMonitor.setUrl("https://example2.com");
        inProgressMonitor.setLabel("In Progress Monitor");
        inProgressMonitor.setInProgress(true);

        when(monitoredUrlRepository.findByInProgressTrueOrderByUpdatedAtAsc())
            .thenReturn(List.of(testMonitor, inProgressMonitor));

        // When
        List<com.isofuture.uptime.dto.PendingCheckResponse> result = monitorService.getInProgressChecks(null);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(monitoredUrlRepository).findByInProgressTrueOrderByUpdatedAtAsc();
    }

    @Test
    @DisplayName("getInProgressChecks - Returns empty list when no in-progress checks")
    void testGetInProgressChecks_NoInProgress_ReturnsEmpty() {
        // Given
        when(monitoredUrlRepository.findByInProgressTrueOrderByUpdatedAtAsc())
            .thenReturn(List.of());

        // When
        List<com.isofuture.uptime.dto.PendingCheckResponse> result = monitorService.getInProgressChecks(null);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("fetchNextChecks - Returns ready monitors and marks them in progress")
    void testFetchNextChecks_Success() {
        // Given
        MonitoredUrl readyMonitor = new MonitoredUrl();
        readyMonitor.setId(2L);
        readyMonitor.setUrl("https://example2.com");
        readyMonitor.setLabel("Ready Monitor");
        readyMonitor.setFrequencyMinutes(5);
        readyMonitor.setInProgress(false);
        readyMonitor.setNextCheckAt(Instant.now().minusSeconds(60));

        when(monitoredUrlRepository.findReadyForCheck(any(Instant.class)))
            .thenReturn(List.of(testMonitor, readyMonitor));

        // When
        List<com.isofuture.uptime.dto.PendingCheckResponse> result = monitorService.fetchNextChecks(2);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(monitoredUrlRepository).findReadyForCheck(any(Instant.class));
    }

    @Test
    @DisplayName("fetchNextChecks - Limits results to specified count")
    void testFetchNextChecks_WithLimit_Success() {
        // Given
        MonitoredUrl readyMonitor1 = new MonitoredUrl();
        readyMonitor1.setId(2L);
        readyMonitor1.setUrl("https://example2.com");
        readyMonitor1.setLabel("Ready Monitor 1");
        readyMonitor1.setFrequencyMinutes(5);
        readyMonitor1.setInProgress(false);

        MonitoredUrl readyMonitor2 = new MonitoredUrl();
        readyMonitor2.setId(3L);
        readyMonitor2.setUrl("https://example3.com");
        readyMonitor2.setLabel("Ready Monitor 2");
        readyMonitor2.setFrequencyMinutes(5);
        readyMonitor2.setInProgress(false);

        when(monitoredUrlRepository.findReadyForCheck(any(Instant.class)))
            .thenReturn(List.of(testMonitor, readyMonitor1, readyMonitor2));

        // When
        List<com.isofuture.uptime.dto.PendingCheckResponse> result = monitorService.fetchNextChecks(2);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("fetchNextChecks - Returns empty list when no ready monitors")
    void testFetchNextChecks_NoReady_ReturnsEmpty() {
        // Given
        when(monitoredUrlRepository.findReadyForCheck(any(Instant.class)))
            .thenReturn(List.of());

        // When
        List<com.isofuture.uptime.dto.PendingCheckResponse> result = monitorService.fetchNextChecks(5);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("listCurrentUserMonitors - Returns empty list when user has no monitors")
    void testListCurrentUserMonitors_NoMonitors_ReturnsEmpty() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(userRepository.findById(2L)).thenReturn(Optional.of(testUser));
        when(monitoredUrlRepository.findByOwner(testUser)).thenReturn(List.of());

        // When
        List<MonitoredUrlResponse> result = monitorService.listCurrentUserMonitors();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(checkResultRepository, never()).findByMonitoredUrlOrderByCheckedAtDesc(any());
        verify(mapper, never()).toResponse(any(), anyList());
    }
}



