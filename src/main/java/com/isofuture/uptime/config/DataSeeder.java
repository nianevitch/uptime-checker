package com.isofuture.uptime.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.isofuture.uptime.entity.Ping;
import com.isofuture.uptime.entity.Role;
import com.isofuture.uptime.entity.Tier;
import com.isofuture.uptime.entity.User;
import com.isofuture.uptime.repository.PingRepository;
import com.isofuture.uptime.repository.RoleRepository;
import com.isofuture.uptime.repository.TierRepository;
import com.isofuture.uptime.repository.UserRepository;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TierRepository tierRepository;
    private final PingRepository pingRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(
        UserRepository userRepository,
        RoleRepository roleRepository,
        TierRepository tierRepository,
        PingRepository pingRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tierRepository = tierRepository;
        this.pingRepository = pingRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedRoles();
        seedTiers();
        seedUsers();
        seedPings();
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

    private void seedTiers() {
        if (tierRepository.count() == 0) {
            Tier freeTier = new Tier();
            freeTier.setName("free");
            freeTier.setCreatedAt(Instant.now());
            tierRepository.save(freeTier);

            Tier premiumTier = new Tier();
            premiumTier.setName("premium");
            premiumTier.setCreatedAt(Instant.now());
            tierRepository.save(premiumTier);

            Tier enterpriseTier = new Tier();
            enterpriseTier.setName("enterprise");
            enterpriseTier.setCreatedAt(Instant.now());
            tierRepository.save(enterpriseTier);
        }
    }

    private void seedUsers() {
        // Only seed if no users exist
        if (userRepository.count() == 0) {
            Role userRole = roleRepository.findByNameIgnoreCase("user")
                .orElseThrow(() -> new IllegalStateException("User role not found"));
            Role adminRole = roleRepository.findByNameIgnoreCase("admin")
                .orElseThrow(() -> new IllegalStateException("Admin role not found"));
            
            Tier freeTier = tierRepository.findActiveByNameIgnoreCase("free")
                .orElseThrow(() -> new IllegalStateException("Free tier not found"));
            Tier premiumTier = tierRepository.findActiveByNameIgnoreCase("premium")
                .orElseThrow(() -> new IllegalStateException("Premium tier not found"));

            // Create mary@invoken.com (user role, free tier)
            if (userRepository.findActiveByEmailIgnoreCase("mary@invoken.com").isEmpty()) {
                User mary = new User();
                mary.setEmail("mary@invoken.com");
                mary.setPasswordHash(passwordEncoder.encode("pass"));
                mary.setCreatedAt(Instant.now());
                mary.getRoles().add(userRole);
                mary.getTiers().add(freeTier);
                userRepository.save(mary);
            }

            // Create zookeeper@invoken.com (admin + user roles, premium tier)
            if (userRepository.findActiveByEmailIgnoreCase("zookeeper@invoken.com").isEmpty()) {
                User zookeeper = new User();
                zookeeper.setEmail("zookeeper@invoken.com");
                zookeeper.setPasswordHash(passwordEncoder.encode("pass"));
                zookeeper.setCreatedAt(Instant.now());
                zookeeper.getRoles().add(userRole);
                zookeeper.getRoles().add(adminRole);
                zookeeper.getTiers().add(premiumTier);
                userRepository.save(zookeeper);
            }
        }
    }

    private void seedPings() {
        // Only seed if no pings exist
        if (pingRepository.count() == 0) {
            User mary = userRepository.findActiveByEmailIgnoreCase("mary@invoken.com").orElse(null);
            User zookeeper = userRepository.findActiveByEmailIgnoreCase("zookeeper@invoken.com").orElse(null);

            if (mary != null) {
                List<Ping> maryPings = List.of(
                    createPing(mary, "Status Page", "https://status.invoken.com", 5),
                    createPing(mary, "Docs", "https://docs.invoken.com", 5),
                    createPing(mary, "Google", "https://www.google.com", 5),
                    createPing(mary, "YouTube", "https://www.youtube.com", 5),
                    createPing(mary, "Gmail", "https://mail.google.com", 5),
                    createPing(mary, "Google News", "https://news.google.com", 5),
                    createPing(mary, "Google Maps", "https://maps.google.com", 5),
                    createPing(mary, "Yahoo", "https://www.yahoo.com", 5),
                    createPing(mary, "Yahoo Finance", "https://finance.yahoo.com", 5),
                    createPing(mary, "Yahoo Mail", "https://mail.yahoo.com", 5)
                );
                pingRepository.saveAll(maryPings);
            }

            if (zookeeper != null) {
                List<Ping> zookeeperPings = List.of(
                    createPing(zookeeper, "Admin Portal", "https://admin.invoken.com", 5),
                    createPing(zookeeper, "Microsoft", "https://www.microsoft.com", 5),
                    createPing(zookeeper, "Bing", "https://www.bing.com", 5),
                    createPing(zookeeper, "Outlook", "https://outlook.live.com", 5),
                    createPing(zookeeper, "LinkedIn", "https://www.linkedin.com", 5),
                    createPing(zookeeper, "GitHub", "https://github.com", 5),
                    createPing(zookeeper, "Stack Overflow", "https://stackoverflow.com", 5)
                );
                pingRepository.saveAll(zookeeperPings);
            }
        }
    }

    private Ping createPing(User owner, String label, String url, int frequencyMinutes) {
        Ping ping = new Ping();
        ping.setOwner(owner);
        ping.setLabel(label);
        ping.setUrl(url);
        ping.setFrequencyMinutes(frequencyMinutes);
        ping.setNextCheckAt(Instant.now().plus(frequencyMinutes, ChronoUnit.MINUTES));
        ping.setInProgress(false);
        Instant now = Instant.now();
        ping.setCreatedAt(now);
        ping.setUpdatedAt(now);
        return ping;
    }
}

