package com.isofuture.uptime.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.isofuture.uptime.BaseTest;
import com.isofuture.uptime.dto.CheckResultDto;
import com.isofuture.uptime.dto.CheckResultUpdateRequest;
import com.isofuture.uptime.dto.ExecuteCheckRequest;
import com.isofuture.uptime.entity.CheckResult;
import com.isofuture.uptime.entity.MonitoredUrl;
import com.isofuture.uptime.entity.User;
import com.isofuture.uptime.repository.CheckResultRepository;
import com.isofuture.uptime.repository.MonitoredUrlRepository;
import com.isofuture.uptime.security.SecurityUser;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckService Unit Tests")
class CheckServiceTest extends BaseTest {

    @Mock
    private MonitoredUrlRepository monitoredUrlRepository;

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private UserContext userContext;

    @InjectMocks
    private CheckService checkService;

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
    @DisplayName("executeCheck - Admin can execute any check")
    void testExecuteCheck_Admin_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(monitoredUrlRepository.findById(1L)).thenReturn(java.util.Optional.of(testMonitor));
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(invocation -> {
            CheckResult result = invocation.getArgument(0);
            result.setId(100L);
            return result;
        });

        ExecuteCheckRequest request = new ExecuteCheckRequest();
        request.setMonitorId(1L);

        // When
        CheckResultDto result = checkService.executeCheck(request, false);

        // Then
        assertNotNull(result);
        verify(userContext, atLeast(1)).isAdmin(); // Called in executeCheck and recordResult
        verify(monitoredUrlRepository, atLeast(1)).findById(1L); // Called in executeCheck and recordResult
    }

    @Test
    @DisplayName("executeCheck - User can execute own check")
    void testExecuteCheck_OwnMonitor_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(monitoredUrlRepository.findByIdAndOwnerId(1L, 2L))
            .thenReturn(java.util.Optional.of(testMonitor));
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(invocation -> {
            CheckResult result = invocation.getArgument(0);
            result.setId(100L);
            return result;
        });

        ExecuteCheckRequest request = new ExecuteCheckRequest();
        request.setMonitorId(1L);

        // When
        CheckResultDto result = checkService.executeCheck(request, false);

        // Then
        assertNotNull(result);
        verify(userContext, atLeast(1)).isAdmin(); // Called in executeCheck and recordResult
        verify(monitoredUrlRepository, atLeast(1)).findByIdAndOwnerId(1L, 2L); // Called in executeCheck and recordResult
    }

    @Test
    @DisplayName("executeCheck - User cannot execute other user's check")
    void testExecuteCheck_OtherUserMonitor_ThrowsAccessDenied() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(monitoredUrlRepository.findByIdAndOwnerId(1L, 2L))
            .thenReturn(java.util.Optional.empty());

        ExecuteCheckRequest request = new ExecuteCheckRequest();
        request.setMonitorId(1L);

        // When/Then
        assertThrows(AccessDeniedException.class, () -> checkService.executeCheck(request, false));
    }

    @Test
    @DisplayName("recordResult - Worker can record result for any monitor")
    void testRecordResult_Worker_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(monitoredUrlRepository.findById(1L)).thenReturn(java.util.Optional.of(testMonitor));
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(invocation -> {
            CheckResult result = invocation.getArgument(0);
            result.setId(100L);
            return result;
        });

        CheckResultUpdateRequest request = new CheckResultUpdateRequest();
        request.setMonitorId(1L);
        request.setHttpCode(200);
        request.setResponseTimeMs(150.0);
        request.setCheckedAt(Instant.now());

        // When
        CheckResultDto result = checkService.recordResult(request, true);

        // Then
        assertNotNull(result);
        assertEquals(200, result.getHttpCode());
        verify(monitoredUrlRepository).findById(1L);
    }

    @Test
    @DisplayName("recordResult - User can record result for own monitor")
    void testRecordResult_OwnMonitor_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(monitoredUrlRepository.findByIdAndOwnerId(1L, 2L))
            .thenReturn(java.util.Optional.of(testMonitor));
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(invocation -> {
            CheckResult result = invocation.getArgument(0);
            result.setId(100L);
            return result;
        });

        CheckResultUpdateRequest request = new CheckResultUpdateRequest();
        request.setMonitorId(1L);
        request.setHttpCode(200);
        request.setResponseTimeMs(150.0);
        request.setCheckedAt(Instant.now());

        // When
        CheckResultDto result = checkService.recordResult(request, false);

        // Then
        assertNotNull(result);
        verify(monitoredUrlRepository).findByIdAndOwnerId(1L, 2L);
    }

    @Test
    @DisplayName("executeCheck - Non-existent monitor throws IllegalArgumentException")
    void testExecuteCheck_NotFound_ThrowsException() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(monitoredUrlRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        ExecuteCheckRequest request = new ExecuteCheckRequest();
        request.setMonitorId(999L);

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> checkService.executeCheck(request, false));
        verify(checkResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("recordResult - Non-existent monitor throws IllegalArgumentException")
    void testRecordResult_NotFound_ThrowsException() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(monitoredUrlRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        CheckResultUpdateRequest request = new CheckResultUpdateRequest();
        request.setMonitorId(999L);
        request.setHttpCode(200);
        request.setResponseTimeMs(150.0);

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> checkService.recordResult(request, true));
        verify(checkResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("recordResult - User cannot record result for other user's monitor")
    void testRecordResult_OtherUserMonitor_ThrowsAccessDenied() {
        // Given
        when(userContext.isAdmin()).thenReturn(false);
        when(userContext.getCurrentUser()).thenReturn(regularUser);
        when(monitoredUrlRepository.findByIdAndOwnerId(1L, 2L))
            .thenReturn(java.util.Optional.empty());

        CheckResultUpdateRequest request = new CheckResultUpdateRequest();
        request.setMonitorId(1L);
        request.setHttpCode(200);
        request.setResponseTimeMs(150.0);

        // When/Then
        assertThrows(AccessDeniedException.class, () -> checkService.recordResult(request, false));
        verify(checkResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("recordResult - Handles null httpCode")
    void testRecordResult_NullHttpCode_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(monitoredUrlRepository.findById(1L)).thenReturn(java.util.Optional.of(testMonitor));
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(invocation -> {
            CheckResult result = invocation.getArgument(0);
            result.setId(100L);
            return result;
        });

        CheckResultUpdateRequest request = new CheckResultUpdateRequest();
        request.setMonitorId(1L);
        request.setHttpCode(null);
        request.setErrorMessage("Connection timeout");
        request.setResponseTimeMs(150.0);
        request.setCheckedAt(Instant.now());

        // When
        CheckResultDto result = checkService.recordResult(request, true);

        // Then
        assertNotNull(result);
        assertNull(result.getHttpCode());
        assertEquals("Connection timeout", result.getErrorMessage());
        verify(checkResultRepository).save(any(CheckResult.class));
    }

    @Test
    @DisplayName("recordResult - Handles null checkedAt")
    void testRecordResult_NullCheckedAt_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(monitoredUrlRepository.findById(1L)).thenReturn(java.util.Optional.of(testMonitor));
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(invocation -> {
            CheckResult result = invocation.getArgument(0);
            result.setId(100L);
            return result;
        });

        CheckResultUpdateRequest request = new CheckResultUpdateRequest();
        request.setMonitorId(1L);
        request.setHttpCode(200);
        request.setResponseTimeMs(150.0);
        request.setCheckedAt(null);

        // When
        CheckResultDto result = checkService.recordResult(request, true);

        // Then
        assertNotNull(result);
        assertNotNull(result.getCheckedAt());
        verify(checkResultRepository).save(any(CheckResult.class));
    }

    @Test
    @DisplayName("executeCheck - Worker can execute any check")
    void testExecuteCheck_Worker_Success() {
        // Given
        when(monitoredUrlRepository.findById(1L)).thenReturn(java.util.Optional.of(testMonitor));
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(invocation -> {
            CheckResult result = invocation.getArgument(0);
            result.setId(100L);
            return result;
        });

        ExecuteCheckRequest request = new ExecuteCheckRequest();
        request.setMonitorId(1L);

        // When
        CheckResultDto result = checkService.executeCheck(request, true);

        // Then
        assertNotNull(result);
        verify(monitoredUrlRepository, atLeast(1)).findById(1L);
        verify(checkResultRepository).save(any(CheckResult.class));
    }

    @Test
    @DisplayName("recordResult - Handles null responseTimeMs")
    void testRecordResult_NullResponseTime_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(monitoredUrlRepository.findById(1L)).thenReturn(java.util.Optional.of(testMonitor));
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(invocation -> {
            CheckResult result = invocation.getArgument(0);
            result.setId(100L);
            return result;
        });

        CheckResultUpdateRequest request = new CheckResultUpdateRequest();
        request.setMonitorId(1L);
        request.setHttpCode(200);
        request.setResponseTimeMs(null);
        request.setCheckedAt(Instant.now());

        // When
        CheckResultDto result = checkService.recordResult(request, true);

        // Then
        assertNotNull(result);
        assertNull(result.getResponseTimeMs());
        verify(checkResultRepository).save(any(CheckResult.class));
    }

    @Test
    @DisplayName("recordResult - Handles null errorMessage")
    void testRecordResult_NullErrorMessage_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(monitoredUrlRepository.findById(1L)).thenReturn(java.util.Optional.of(testMonitor));
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(invocation -> {
            CheckResult result = invocation.getArgument(0);
            result.setId(100L);
            return result;
        });

        CheckResultUpdateRequest request = new CheckResultUpdateRequest();
        request.setMonitorId(1L);
        request.setHttpCode(200);
        request.setResponseTimeMs(150.0);
        request.setErrorMessage(null);
        request.setCheckedAt(Instant.now());

        // When
        CheckResultDto result = checkService.recordResult(request, true);

        // Then
        assertNotNull(result);
        assertNull(result.getErrorMessage());
        assertEquals(200, result.getHttpCode());
        verify(checkResultRepository).save(any(CheckResult.class));
    }

    @Test
    @DisplayName("executeCheck - Admin can execute any check")
    void testExecuteCheck_Admin_AnyMonitor_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(monitoredUrlRepository.findById(1L)).thenReturn(java.util.Optional.of(testMonitor));
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(invocation -> {
            CheckResult result = invocation.getArgument(0);
            result.setId(100L);
            return result;
        });

        ExecuteCheckRequest request = new ExecuteCheckRequest();
        request.setMonitorId(1L);

        // When
        CheckResultDto result = checkService.executeCheck(request, false);

        // Then
        assertNotNull(result);
        verify(userContext, atLeast(1)).isAdmin();
        verify(monitoredUrlRepository, atLeast(1)).findById(1L);
    }

    @Test
    @DisplayName("recordResult - Admin can record result for any monitor")
    void testRecordResult_Admin_AnyMonitor_Success() {
        // Given
        when(userContext.isAdmin()).thenReturn(true);
        when(monitoredUrlRepository.findById(1L)).thenReturn(java.util.Optional.of(testMonitor));
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(invocation -> {
            CheckResult result = invocation.getArgument(0);
            result.setId(100L);
            return result;
        });

        CheckResultUpdateRequest request = new CheckResultUpdateRequest();
        request.setMonitorId(1L);
        request.setHttpCode(500);
        request.setResponseTimeMs(200.0);
        request.setErrorMessage("Internal Server Error");
        request.setCheckedAt(Instant.now());

        // When
        CheckResultDto result = checkService.recordResult(request, false);

        // Then
        assertNotNull(result);
        assertEquals(500, result.getHttpCode());
        assertEquals("Internal Server Error", result.getErrorMessage());
        // isAdmin() is called twice: once in log statement, once in if statement
        verify(userContext, atLeast(1)).isAdmin();
        verify(monitoredUrlRepository).findById(1L);
    }
}

