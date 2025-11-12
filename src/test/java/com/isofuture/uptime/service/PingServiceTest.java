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
import com.isofuture.uptime.dto.PingRequest;
import com.isofuture.uptime.dto.PingResponse;
import com.isofuture.uptime.entity.Ping;
import com.isofuture.uptime.entity.User;
import com.isofuture.uptime.mapper.PingMapper;
import com.isofuture.uptime.repository.CheckResultRepository;
import com.isofuture.uptime.repository.PingRepository;
import com.isofuture.uptime.repository.UserRepository;
import com.isofuture.uptime.security.SecurityUser;

@ExtendWith(MockitoExtension.class)
@DisplayName("PingService Unit Tests")
class PingServiceTest extends BaseTest {

    @Mock
    private PingRepository pingRepository;

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PingMapper mapper;

    @Mock
    private UserContext userContext;

    @InjectMocks
    private PingService pingService;

    private SecurityUser adminUser;
    private SecurityUser regularUser;
    private User testUser;
    private Ping testPing;

    @BeforeEach
    void setUp() {
        adminUser = createAdminUser(1L, "admin@test.com");
        regularUser = createRegularUser(2L, "user@test.com");
        testUser = createUser(2L, "user@test.com", "hashed", "user");
        
        testPing = new Ping();
        testPing.setId(1L);
        testPing.setUrl("https://example.com");
        testPing.setLabel("Test Ping");
        testPing.setOwner(testUser);
        testPing.setFrequencyMinutes(5);
        testPing.setInProgress(false);
    }

    @Test
    @DisplayName("listCurrentUserPings - Admin sees all pings")
    void testListCurrentUserPings_Admin_ReturnsAll() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(pingRepository.findAll()).thenReturn(List.of(testPing));
        when(checkResultRepository.findByPingOrderByCheckedAtDesc(any()))
            .thenReturn(List.of());
        when(mapper.toResponse(any(), anyList())).thenReturn(new PingResponse());

        // When
        List<PingResponse> result = pingService.listCurrentUserPings();

        // Then
        assertNotNull(result);
        verify(userContext).isAdmin();
        verify(pingRepository).findAll();
        verify(pingRepository, never()).findByOwner(any());
    }

    @Test
    @DisplayName("listCurrentUserPings - Regular user sees only own pings")
    void testListCurrentUserPings_RegularUser_ReturnsOwn() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(userRepository.findById(2L)).thenReturn(Optional.of(testUser));
        when(pingRepository.findByOwner(testUser)).thenReturn(List.of(testPing));
        when(checkResultRepository.findByPingOrderByCheckedAtDesc(any()))
            .thenReturn(List.of());
        when(mapper.toResponse(any(), anyList())).thenReturn(new PingResponse());

        // When
        List<PingResponse> result = pingService.listCurrentUserPings();

        // Then
        assertNotNull(result);
        verify(userContext).isAdmin();
        verify(pingRepository).findByOwner(testUser);
        verify(pingRepository, never()).findAll();
    }

    @Test
    @DisplayName("createPing - Creates ping with correct owner")
    void testCreatePing_Success() {
        // Given
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(userRepository.findById(2L)).thenReturn(Optional.of(testUser));
        when(pingRepository.save(any(Ping.class))).thenReturn(testPing);
        when(checkResultRepository.findByPingOrderByCheckedAtDesc(any()))
            .thenReturn(List.of());
        when(mapper.toResponse(any(), anyList())).thenReturn(new PingResponse());

        PingRequest request = new PingRequest();
        request.setUrl("https://example.com");
        request.setLabel("Test Ping");
        request.setFrequencyMinutes(5);

        // When
        PingResponse result = pingService.createPing(request);

        // Then
        assertNotNull(result);
        verify(userRepository).findById(2L);
        verify(pingRepository).save(any(Ping.class));
    }

    @Test
    @DisplayName("updatePing - Admin can update any ping")
    void testUpdatePing_Admin_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(pingRepository.findById(1L)).thenReturn(Optional.of(testPing));
        when(checkResultRepository.findByPingOrderByCheckedAtDesc(any()))
            .thenReturn(List.of());
        when(mapper.toResponse(any(), anyList())).thenReturn(new PingResponse());

        PingRequest request = new PingRequest();
        request.setUrl("https://updated.com");
        request.setLabel("Updated Ping");
        request.setFrequencyMinutes(10);

        // When
        PingResponse result = pingService.updatePing(1L, request);

        // Then
        assertNotNull(result);
        // isAdmin() is called twice: once in log statement, once in if statement
        verify(userContext, atLeast(1)).isAdmin();
        verify(pingRepository).findById(1L);
    }

    @Test
    @DisplayName("updatePing - User can update own ping")
    void testUpdatePing_OwnPing_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(pingRepository.findByIdAndOwnerId(1L, 2L)).thenReturn(Optional.of(testPing));
        when(checkResultRepository.findByPingOrderByCheckedAtDesc(any()))
            .thenReturn(List.of());
        when(mapper.toResponse(any(), anyList())).thenReturn(new PingResponse());

        PingRequest request = new PingRequest();
        request.setUrl("https://updated.com");
        request.setLabel("Updated Ping");
        request.setFrequencyMinutes(10);

        // When
        PingResponse result = pingService.updatePing(1L, request);

        // Then
        assertNotNull(result);
        // isAdmin() is called once: in log statement (user is not admin, so if statement doesn't execute)
        verify(userContext, atLeast(1)).isAdmin();
        verify(pingRepository).findByIdAndOwnerId(1L, 2L);
    }

    @Test
    @DisplayName("deletePing - Admin can delete any ping")
    void testDeletePing_Admin_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(pingRepository.findById(1L)).thenReturn(Optional.of(testPing));

        // When
        pingService.deletePing(1L);

        // Then
        verify(pingRepository).delete(testPing);
    }

    @Test
    @DisplayName("deletePing - User can delete own ping")
    void testDeletePing_OwnPing_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(pingRepository.findByIdAndOwnerId(1L, 2L)).thenReturn(Optional.of(testPing));

        // When
        pingService.deletePing(1L);

        // Then
        verify(pingRepository).delete(testPing);
    }

    @Test
    @DisplayName("updatePing - Non-existent ping throws IllegalArgumentException")
    void testUpdatePing_NotFound_ThrowsException() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(pingRepository.findById(999L)).thenReturn(Optional.empty());

        PingRequest request = new PingRequest();
        request.setUrl("https://updated.com");
        request.setLabel("Updated Ping");
        request.setFrequencyMinutes(10);

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> pingService.updatePing(999L, request));
    }

    @Test
    @DisplayName("updatePing - User cannot update other user's ping")
    void testUpdatePing_OtherUserPing_ThrowsException() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(pingRepository.findByIdAndOwnerId(1L, 2L)).thenReturn(Optional.empty());

        PingRequest request = new PingRequest();
        request.setUrl("https://updated.com");
        request.setLabel("Updated Ping");
        request.setFrequencyMinutes(10);

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> pingService.updatePing(1L, request));
    }

    @Test
    @DisplayName("deletePing - Non-existent ping throws IllegalArgumentException")
    void testDeletePing_NotFound_ThrowsException() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(pingRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> pingService.deletePing(999L));
        verify(pingRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deletePing - User cannot delete other user's ping")
    void testDeletePing_OtherUserPing_ThrowsException() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(pingRepository.findByIdAndOwnerId(1L, 2L)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> pingService.deletePing(1L));
        verify(pingRepository, never()).delete(any());
    }

    @Test
    @DisplayName("getInProgressChecks - Returns in-progress checks with limit")
    void testGetInProgressChecks_WithLimit_Success() {
        // Given
        Ping inProgressPing = new Ping();
        inProgressPing.setId(2L);
        inProgressPing.setUrl("https://example2.com");
        inProgressPing.setLabel("In Progress Ping");
        inProgressPing.setInProgress(true);

        when(pingRepository.findByInProgressTrueOrderByUpdatedAtAsc())
            .thenReturn(List.of(testPing, inProgressPing));

        // When
        List<com.isofuture.uptime.dto.PendingCheckResponse> result = pingService.getInProgressChecks(1);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(pingRepository).findByInProgressTrueOrderByUpdatedAtAsc();
    }

    @Test
    @DisplayName("getInProgressChecks - Returns all in-progress checks without limit")
    void testGetInProgressChecks_WithoutLimit_Success() {
        // Given
        Ping inProgressPing = new Ping();
        inProgressPing.setId(2L);
        inProgressPing.setUrl("https://example2.com");
        inProgressPing.setLabel("In Progress Ping");
        inProgressPing.setInProgress(true);

        when(pingRepository.findByInProgressTrueOrderByUpdatedAtAsc())
            .thenReturn(List.of(testPing, inProgressPing));

        // When
        List<com.isofuture.uptime.dto.PendingCheckResponse> result = pingService.getInProgressChecks(null);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(pingRepository).findByInProgressTrueOrderByUpdatedAtAsc();
    }

    @Test
    @DisplayName("getInProgressChecks - Returns empty list when no in-progress checks")
    void testGetInProgressChecks_NoInProgress_ReturnsEmpty() {
        // Given
        when(pingRepository.findByInProgressTrueOrderByUpdatedAtAsc())
            .thenReturn(List.of());

        // When
        List<com.isofuture.uptime.dto.PendingCheckResponse> result = pingService.getInProgressChecks(null);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("fetchNextChecks - Returns ready pings and marks them in progress")
    void testFetchNextChecks_Success() {
        // Given
        Ping readyPing = new Ping();
        readyPing.setId(2L);
        readyPing.setUrl("https://example2.com");
        readyPing.setLabel("Ready Ping");
        readyPing.setFrequencyMinutes(5);
        readyPing.setInProgress(false);
        readyPing.setNextCheckAt(Instant.now().minusSeconds(60));

        when(pingRepository.findReadyForCheck(any(Instant.class)))
            .thenReturn(List.of(testPing, readyPing));

        // When
        List<com.isofuture.uptime.dto.PendingCheckResponse> result = pingService.fetchNextChecks(2);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(pingRepository).findReadyForCheck(any(Instant.class));
    }

    @Test
    @DisplayName("fetchNextChecks - Limits results to specified count")
    void testFetchNextChecks_WithLimit_Success() {
        // Given
        Ping readyPing1 = new Ping();
        readyPing1.setId(2L);
        readyPing1.setUrl("https://example2.com");
        readyPing1.setLabel("Ready Ping 1");
        readyPing1.setFrequencyMinutes(5);
        readyPing1.setInProgress(false);

        Ping readyPing2 = new Ping();
        readyPing2.setId(3L);
        readyPing2.setUrl("https://example3.com");
        readyPing2.setLabel("Ready Ping 2");
        readyPing2.setFrequencyMinutes(5);
        readyPing2.setInProgress(false);

        when(pingRepository.findReadyForCheck(any(Instant.class)))
            .thenReturn(List.of(testPing, readyPing1, readyPing2));

        // When
        List<com.isofuture.uptime.dto.PendingCheckResponse> result = pingService.fetchNextChecks(2);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("fetchNextChecks - Returns empty list when no ready pings")
    void testFetchNextChecks_NoReady_ReturnsEmpty() {
        // Given
        when(pingRepository.findReadyForCheck(any(Instant.class)))
            .thenReturn(List.of());

        // When
        List<com.isofuture.uptime.dto.PendingCheckResponse> result = pingService.fetchNextChecks(5);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("listCurrentUserPings - Returns empty list when user has no pings")
    void testListCurrentUserPings_NoPings_ReturnsEmpty() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(userRepository.findById(2L)).thenReturn(Optional.of(testUser));
        when(pingRepository.findByOwner(testUser)).thenReturn(List.of());

        // When
        List<PingResponse> result = pingService.listCurrentUserPings();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(checkResultRepository, never()).findByPingOrderByCheckedAtDesc(any());
        verify(mapper, never()).toResponse(any(), anyList());
    }
}



