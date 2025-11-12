package com.isofuture.uptime.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.isofuture.uptime.entity.MonitoredUrl;
import com.isofuture.uptime.entity.Role;
import com.isofuture.uptime.entity.User;
import com.isofuture.uptime.repository.MonitoredUrlRepository;
import com.isofuture.uptime.repository.RoleRepository;
import com.isofuture.uptime.repository.UserRepository;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MonitoredUrlRepository monitoredUrlRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(
        UserRepository userRepository,
        RoleRepository roleRepository,
        MonitoredUrlRepository monitoredUrlRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.monitoredUrlRepository = monitoredUrlRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedRoles();
        seedUsers();
        seedMonitors();
    }

    private void seedRoles() {
        if (roleRepository.count() == 0) {
            Role userRole = new Role();
            userRole.setName("user");
            roleRepository.save(userRole);

            Role adminRole = new Role();
            adminRole.setName("admin");
            roleRepository.save(adminRole);
        }
    }

    private void seedUsers() {
        // Only seed if no users exist
        if (userRepository.count() == 0) {
            Role userRole = roleRepository.findByNameIgnoreCase("user")
                .orElseThrow(() -> new IllegalStateException("User role not found"));
            Role adminRole = roleRepository.findByNameIgnoreCase("admin")
                .orElseThrow(() -> new IllegalStateException("Admin role not found"));

            // Create mary@invoken.com (user role)
            if (userRepository.findActiveByEmailIgnoreCase("mary@invoken.com").isEmpty()) {
                User mary = new User();
                mary.setEmail("mary@invoken.com");
                mary.setPasswordHash(passwordEncoder.encode("pass"));
                mary.setCreatedAt(Instant.now());
                mary.getRoles().add(userRole);
                userRepository.save(mary);
            }

            // Create zookeeper@invoken.com (admin + user roles)
            if (userRepository.findActiveByEmailIgnoreCase("zookeeper@invoken.com").isEmpty()) {
                User zookeeper = new User();
                zookeeper.setEmail("zookeeper@invoken.com");
                zookeeper.setPasswordHash(passwordEncoder.encode("pass"));
                zookeeper.setCreatedAt(Instant.now());
                zookeeper.getRoles().add(userRole);
                zookeeper.getRoles().add(adminRole);
                userRepository.save(zookeeper);
            }
        }
    }

    private void seedMonitors() {
        // Only seed if no monitors exist
        if (monitoredUrlRepository.count() == 0) {
            User mary = userRepository.findActiveByEmailIgnoreCase("mary@invoken.com").orElse(null);
            User zookeeper = userRepository.findActiveByEmailIgnoreCase("zookeeper@invoken.com").orElse(null);

            if (mary != null) {
                List<MonitoredUrl> maryMonitors = List.of(
                    createMonitor(mary, "Status Page", "https://status.invoken.com", 5),
                    createMonitor(mary, "Docs", "https://docs.invoken.com", 5),
                    createMonitor(mary, "Google", "https://www.google.com", 5),
                    createMonitor(mary, "YouTube", "https://www.youtube.com", 5),
                    createMonitor(mary, "Gmail", "https://mail.google.com", 5),
                    createMonitor(mary, "Google News", "https://news.google.com", 5),
                    createMonitor(mary, "Google Maps", "https://maps.google.com", 5),
                    createMonitor(mary, "Yahoo", "https://www.yahoo.com", 5),
                    createMonitor(mary, "Yahoo Finance", "https://finance.yahoo.com", 5),
                    createMonitor(mary, "Yahoo Mail", "https://mail.yahoo.com", 5)
                );
                monitoredUrlRepository.saveAll(maryMonitors);
            }

            if (zookeeper != null) {
                List<MonitoredUrl> zookeeperMonitors = List.of(
                    createMonitor(zookeeper, "Admin Portal", "https://admin.invoken.com", 5),
                    createMonitor(zookeeper, "Microsoft", "https://www.microsoft.com", 5),
                    createMonitor(zookeeper, "Bing", "https://www.bing.com", 5),
                    createMonitor(zookeeper, "Outlook", "https://outlook.live.com", 5),
                    createMonitor(zookeeper, "LinkedIn", "https://www.linkedin.com", 5),
                    createMonitor(zookeeper, "GitHub", "https://github.com", 5),
                    createMonitor(zookeeper, "Stack Overflow", "https://stackoverflow.com", 5)
                );
                monitoredUrlRepository.saveAll(zookeeperMonitors);
            }
        }
    }

    private MonitoredUrl createMonitor(User owner, String label, String url, int frequencyMinutes) {
        MonitoredUrl monitor = new MonitoredUrl();
        monitor.setOwner(owner);
        monitor.setLabel(label);
        monitor.setUrl(url);
        monitor.setFrequencyMinutes(frequencyMinutes);
        monitor.setNextCheckAt(Instant.now().plus(frequencyMinutes, ChronoUnit.MINUTES));
        monitor.setInProgress(false);
        Instant now = Instant.now();
        monitor.setCreatedAt(now);
        monitor.setUpdatedAt(now);
        return monitor;
    }
}

