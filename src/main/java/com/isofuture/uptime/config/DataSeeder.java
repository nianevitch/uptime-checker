package com.isofuture.uptime.config;

import java.time.Instant;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.isofuture.uptime.entity.RoleEntity;
import com.isofuture.uptime.entity.UserEntity;
import com.isofuture.uptime.repository.RoleRepository;
import com.isofuture.uptime.repository.UserRepository;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(
        UserRepository userRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedRoles();
        seedUsers();
    }

    private void seedRoles() {
        if (roleRepository.count() == 0) {
            RoleEntity userRole = new RoleEntity();
            userRole.setName("user");
            roleRepository.save(userRole);

            RoleEntity adminRole = new RoleEntity();
            adminRole.setName("admin");
            roleRepository.save(adminRole);
        }
    }

    private void seedUsers() {
        // Only seed if no users exist
        if (userRepository.count() == 0) {
            RoleEntity userRole = roleRepository.findByNameIgnoreCase("user")
                .orElseThrow(() -> new IllegalStateException("User role not found"));
            RoleEntity adminRole = roleRepository.findByNameIgnoreCase("admin")
                .orElseThrow(() -> new IllegalStateException("Admin role not found"));

            // Create mary@invoken.com (user role)
            if (userRepository.findActiveByEmailIgnoreCase("mary@invoken.com").isEmpty()) {
                UserEntity mary = new UserEntity();
                mary.setEmail("mary@invoken.com");
                mary.setPasswordHash(passwordEncoder.encode("pass"));
                mary.setCreatedAt(Instant.now());
                mary.getRoles().add(userRole);
                userRepository.save(mary);
            }

            // Create zookeeper@invoken.com (admin + user roles)
            if (userRepository.findActiveByEmailIgnoreCase("zookeeper@invoken.com").isEmpty()) {
                UserEntity zookeeper = new UserEntity();
                zookeeper.setEmail("zookeeper@invoken.com");
                zookeeper.setPasswordHash(passwordEncoder.encode("pass"));
                zookeeper.setCreatedAt(Instant.now());
                zookeeper.getRoles().add(userRole);
                zookeeper.getRoles().add(adminRole);
                userRepository.save(zookeeper);
            }
        }
    }
}

