package com.isofuture.uptime.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import com.isofuture.uptime.entity.MonitoredUrlEntity;
import com.isofuture.uptime.entity.RoleEntity;
import com.isofuture.uptime.entity.UserEntity;
import com.isofuture.uptime.repository.RoleRepository;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("MonitoredUrlRepository Integration Tests")
class MonitoredUrlRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MonitoredUrlRepository monitoredUrlRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("findByOwner - Returns monitors for specific owner")
    void testFindByOwner_Success() {
        // Given
        UserEntity owner1 = createUser("owner1@test.com");
        UserEntity owner2 = createUser("owner2@test.com");
        
        MonitoredUrlEntity monitor1 = createMonitor(owner1, "https://site1.com");
        MonitoredUrlEntity monitor2 = createMonitor(owner1, "https://site2.com");
        MonitoredUrlEntity monitor3 = createMonitor(owner2, "https://site3.com");
        
        entityManager.persistAndFlush(monitor1);
        entityManager.persistAndFlush(monitor2);
        entityManager.persistAndFlush(monitor3);

        // When
        List<MonitoredUrlEntity> owner1Monitors = monitoredUrlRepository.findByOwner(owner1);

        // Then
        assertEquals(2, owner1Monitors.size());
        assertTrue(owner1Monitors.stream().anyMatch(m -> m.getUrl().equals("https://site1.com")));
        assertTrue(owner1Monitors.stream().anyMatch(m -> m.getUrl().equals("https://site2.com")));
    }

    @Test
    @DisplayName("findReadyForCheck - Returns monitors ready for check")
    void testFindReadyForCheck_Success() {
        // Given
        UserEntity owner = createUser("owner@test.com");
        
        MonitoredUrlEntity ready1 = createMonitor(owner, "https://ready1.com");
        ready1.setNextCheckAt(Instant.now().minusSeconds(60));
        ready1.setInProgress(false);
        
        MonitoredUrlEntity ready2 = createMonitor(owner, "https://ready2.com");
        ready2.setNextCheckAt(null);
        ready2.setInProgress(false);
        
        MonitoredUrlEntity notReady = createMonitor(owner, "https://notready.com");
        notReady.setNextCheckAt(Instant.now().plusSeconds(3600));
        notReady.setInProgress(false);
        
        MonitoredUrlEntity inProgress = createMonitor(owner, "https://inprogress.com");
        inProgress.setNextCheckAt(Instant.now().minusSeconds(60));
        inProgress.setInProgress(true);
        
        entityManager.persistAndFlush(ready1);
        entityManager.persistAndFlush(ready2);
        entityManager.persistAndFlush(notReady);
        entityManager.persistAndFlush(inProgress);

        // When
        List<MonitoredUrlEntity> ready = monitoredUrlRepository.findReadyForCheck(Instant.now());

        // Then
        assertEquals(2, ready.size());
        assertTrue(ready.stream().anyMatch(m -> m.getUrl().equals("https://ready1.com")));
        assertTrue(ready.stream().anyMatch(m -> m.getUrl().equals("https://ready2.com")));
    }

    @Test
    @DisplayName("findByIdAndOwnerId - Returns monitor if owned by user")
    void testFindByIdAndOwnerId_Success() {
        // Given
        UserEntity owner = createUser("owner@test.com");
        MonitoredUrlEntity monitor = createMonitor(owner, "https://site.com");
        entityManager.persistAndFlush(monitor);

        // When
        var found = monitoredUrlRepository.findByIdAndOwnerId(monitor.getId(), owner.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals("https://site.com", found.get().getUrl());
    }

    @Test
    @DisplayName("findByInProgressTrueOrderByUpdatedAtAsc - Returns only in-progress monitors")
    void testFindByInProgressTrue_Success() {
        // Given
        UserEntity owner = createUser("owner@test.com");
        
        MonitoredUrlEntity inProgress1 = createMonitor(owner, "https://progress1.com");
        inProgress1.setInProgress(true);
        inProgress1.setUpdatedAt(Instant.now().minusSeconds(120));
        
        MonitoredUrlEntity inProgress2 = createMonitor(owner, "https://progress2.com");
        inProgress2.setInProgress(true);
        inProgress2.setUpdatedAt(Instant.now().minusSeconds(60));
        
        MonitoredUrlEntity notInProgress = createMonitor(owner, "https://notprogress.com");
        notInProgress.setInProgress(false);
        
        entityManager.persistAndFlush(inProgress1);
        entityManager.persistAndFlush(inProgress2);
        entityManager.persistAndFlush(notInProgress);

        // When
        List<MonitoredUrlEntity> inProgress = monitoredUrlRepository.findByInProgressTrueOrderByUpdatedAtAsc();

        // Then
        assertEquals(2, inProgress.size());
        assertEquals("https://progress1.com", inProgress.get(0).getUrl()); // Older first
        assertEquals("https://progress2.com", inProgress.get(1).getUrl());
    }

    private UserEntity createUser(String email) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash("hashed");
        user.setCreatedAt(Instant.now());
        
        // Check if role exists, otherwise create it
        RoleEntity role = roleRepository.findByNameIgnoreCase("user")
            .orElseGet(() -> {
                RoleEntity newRole = new RoleEntity();
                newRole.setName("user");
                entityManager.persist(newRole);
                entityManager.flush();
                return newRole;
            });
        user.getRoles().add(role);
        
        entityManager.persist(user);
        return user;
    }

    private MonitoredUrlEntity createMonitor(UserEntity owner, String url) {
        MonitoredUrlEntity monitor = new MonitoredUrlEntity();
        monitor.setOwner(owner);
        monitor.setUrl(url);
        monitor.setLabel("Test Monitor");
        monitor.setFrequencyMinutes(5);
        monitor.setInProgress(false);
        monitor.setCreatedAt(Instant.now());
        monitor.setUpdatedAt(Instant.now());
        return monitor;
    }
}

