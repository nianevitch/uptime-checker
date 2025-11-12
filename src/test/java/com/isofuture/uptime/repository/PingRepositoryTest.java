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

import com.isofuture.uptime.entity.Ping;
import com.isofuture.uptime.entity.Role;
import com.isofuture.uptime.entity.User;
import com.isofuture.uptime.repository.RoleRepository;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("PingRepository Integration Tests")
class PingRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PingRepository pingRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("findByOwner - Returns pings for specific owner")
    void testFindByOwner_Success() {
        // Given
        User owner1 = createUser("owner1@test.com");
        User owner2 = createUser("owner2@test.com");
        
        Ping ping1 = createPing(owner1, "https://site1.com");
        Ping ping2 = createPing(owner1, "https://site2.com");
        Ping ping3 = createPing(owner2, "https://site3.com");
        
        entityManager.persistAndFlush(ping1);
        entityManager.persistAndFlush(ping2);
        entityManager.persistAndFlush(ping3);

        // When
        List<Ping> owner1Monitors = pingRepository.findByOwner(owner1);

        // Then
        assertEquals(2, owner1Monitors.size());
        assertTrue(owner1Monitors.stream().anyMatch(m -> m.getUrl().equals("https://site1.com")));
        assertTrue(owner1Monitors.stream().anyMatch(m -> m.getUrl().equals("https://site2.com")));
    }

    @Test
    @DisplayName("findReadyForCheck - Returns pings ready for check")
    void testFindReadyForCheck_Success() {
        // Given
        User owner = createUser("owner@test.com");
        
        Ping ready1 = createPing(owner, "https://ready1.com");
        ready1.setNextCheckAt(Instant.now().minusSeconds(60));
        ready1.setInProgress(false);
        
        Ping ready2 = createPing(owner, "https://ready2.com");
        ready2.setNextCheckAt(null);
        ready2.setInProgress(false);
        
        Ping notReady = createPing(owner, "https://notready.com");
        notReady.setNextCheckAt(Instant.now().plusSeconds(3600));
        notReady.setInProgress(false);
        
        Ping inProgress = createPing(owner, "https://inprogress.com");
        inProgress.setNextCheckAt(Instant.now().minusSeconds(60));
        inProgress.setInProgress(true);
        
        entityManager.persistAndFlush(ready1);
        entityManager.persistAndFlush(ready2);
        entityManager.persistAndFlush(notReady);
        entityManager.persistAndFlush(inProgress);

        // When
        List<Ping> ready = pingRepository.findReadyForCheck(Instant.now());

        // Then
        assertEquals(2, ready.size());
        assertTrue(ready.stream().anyMatch(m -> m.getUrl().equals("https://ready1.com")));
        assertTrue(ready.stream().anyMatch(m -> m.getUrl().equals("https://ready2.com")));
    }

    @Test
    @DisplayName("findByIdAndOwnerId - Returns ping if owned by user")
    void testFindByIdAndOwnerId_Success() {
        // Given
        User owner = createUser("owner@test.com");
        Ping ping = createPing(owner, "https://site.com");
        entityManager.persistAndFlush(ping);

        // When
        var found = pingRepository.findByIdAndOwnerId(ping.getId(), owner.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals("https://site.com", found.get().getUrl());
    }

    @Test
    @DisplayName("findByInProgressTrueOrderByUpdatedAtAsc - Returns only in-progress pings")
    void testFindByInProgressTrue_Success() {
        // Given
        User owner = createUser("owner@test.com");
        
        Ping inProgress1 = createPing(owner, "https://progress1.com");
        inProgress1.setInProgress(true);
        inProgress1.setUpdatedAt(Instant.now().minusSeconds(120));
        
        Ping inProgress2 = createPing(owner, "https://progress2.com");
        inProgress2.setInProgress(true);
        inProgress2.setUpdatedAt(Instant.now().minusSeconds(60));
        
        Ping notInProgress = createPing(owner, "https://notprogress.com");
        notInProgress.setInProgress(false);
        
        entityManager.persistAndFlush(inProgress1);
        entityManager.persistAndFlush(inProgress2);
        entityManager.persistAndFlush(notInProgress);

        // When
        List<Ping> inProgress = pingRepository.findByInProgressTrueOrderByUpdatedAtAsc();

        // Then
        assertEquals(2, inProgress.size());
        assertEquals("https://progress1.com", inProgress.get(0).getUrl()); // Older first
        assertEquals("https://progress2.com", inProgress.get(1).getUrl());
    }

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hashed");
        user.setCreatedAt(Instant.now());
        
        // Check if role exists, otherwise create it
        Role role = roleRepository.findByNameIgnoreCase("user")
            .orElseGet(() -> {
                Role newRole = new Role();
                newRole.setName("user");
                entityManager.persist(newRole);
                entityManager.flush();
                return newRole;
            });
        user.getRoles().add(role);
        
        entityManager.persist(user);
        return user;
    }

    private Ping createPing(User owner, String url) {
        Ping ping = new Ping();
        ping.setOwner(owner);
        ping.setUrl(url);
        ping.setLabel("Test Monitor");
        ping.setFrequencyMinutes(5);
        ping.setInProgress(false);
        ping.setCreatedAt(Instant.now());
        ping.setUpdatedAt(Instant.now());
        return ping;
    }
}

