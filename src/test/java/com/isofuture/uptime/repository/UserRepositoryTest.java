package com.isofuture.uptime.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import com.isofuture.uptime.entity.RoleEntity;
import com.isofuture.uptime.entity.UserEntity;
import com.isofuture.uptime.repository.RoleRepository;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository Integration Tests")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("findByEmailIgnoreCase - Finds user by email case-insensitive")
    void testFindByEmailIgnoreCase_Success() {
        // Given
        UserEntity user = createUser("test@example.com", "password");
        entityManager.persistAndFlush(user);

        // When
        Optional<UserEntity> found = userRepository.findByEmailIgnoreCase("TEST@EXAMPLE.COM");

        // Then
        assertTrue(found.isPresent());
        assertEquals("test@example.com", found.get().getEmail());
    }

    @Test
    @DisplayName("findAllActive - Returns only non-deleted users")
    void testFindAllActive_ExcludesDeleted() {
        // Given
        UserEntity activeUser = createUser("active@example.com", "password");
        UserEntity deletedUser = createUser("deleted@example.com", "password");
        deletedUser.setDeletedAt(Instant.now());
        
        entityManager.persistAndFlush(activeUser);
        entityManager.persistAndFlush(deletedUser);

        // When
        List<UserEntity> active = userRepository.findAllActive();

        // Then
        assertEquals(1, active.size());
        assertEquals("active@example.com", active.get(0).getEmail());
    }

    @Test
    @DisplayName("findActiveById - Returns user if not deleted")
    void testFindActiveById_ActiveUser_Success() {
        // Given
        UserEntity user = createUser("test@example.com", "password");
        entityManager.persistAndFlush(user);

        // When
        Optional<UserEntity> found = userRepository.findActiveById(user.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals("test@example.com", found.get().getEmail());
    }

    @Test
    @DisplayName("findActiveById - Returns empty if user is deleted")
    void testFindActiveById_DeletedUser_ReturnsEmpty() {
        // Given
        UserEntity user = createUser("test@example.com", "password");
        user.setDeletedAt(Instant.now());
        entityManager.persistAndFlush(user);

        // When
        Optional<UserEntity> found = userRepository.findActiveById(user.getId());

        // Then
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("findActiveByEmailIgnoreCase - Returns user if not deleted")
    void testFindActiveByEmailIgnoreCase_ActiveUser_Success() {
        // Given
        UserEntity user = createUser("test@example.com", "password");
        entityManager.persistAndFlush(user);

        // When
        Optional<UserEntity> found = userRepository.findActiveByEmailIgnoreCase("TEST@EXAMPLE.COM");

        // Then
        assertTrue(found.isPresent());
        assertEquals("test@example.com", found.get().getEmail());
    }

    @Test
    @DisplayName("findActiveByEmailIgnoreCase - Returns empty if user is deleted")
    void testFindActiveByEmailIgnoreCase_DeletedUser_ReturnsEmpty() {
        // Given
        UserEntity user = createUser("test@example.com", "password");
        user.setDeletedAt(Instant.now());
        entityManager.persistAndFlush(user);

        // When
        Optional<UserEntity> found = userRepository.findActiveByEmailIgnoreCase("test@example.com");

        // Then
        assertFalse(found.isPresent());
    }

    private UserEntity createUser(String email, String passwordHash) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
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
        
        return user;
    }
}

