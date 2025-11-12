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
import com.isofuture.uptime.entity.MonitoredUrlEntity;
import com.isofuture.uptime.entity.UserEntity;
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
    private UserEntity testUser;
    private MonitoredUrlEntity testMonitor;

    @BeforeEach
    void setUp() {
        adminUser = createAdminUser(1L, "admin@test.com");
        regularUser = createRegularUser(2L, "user@test.com");
        testUser = createUserEntity(2L, "user@test.com", "hashed", "user");
        
        testMonitor = new MonitoredUrlEntity();
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
        when(monitoredUrlRepository.save(any(MonitoredUrlEntity.class))).thenReturn(testMonitor);
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
        verify(monitoredUrlRepository).save(any(MonitoredUrlEntity.class));
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
}


