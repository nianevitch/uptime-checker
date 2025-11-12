package com.isofuture.uptime.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    public DataSeeder(
        UserRepository userRepository,
        RoleRepository roleRepository,
        TierRepository tierRepository,
        PingRepository pingRepository,
        PasswordEncoder passwordEncoder,
        JdbcTemplate jdbcTemplate
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tierRepository = tierRepository;
        this.pingRepository = pingRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(String... args) {
        fixTierCreatedAt();
        seedRoles();
        seedTiers();
        seedUsers();
        seedPings();
    }
    
    /**
     * Fixes existing tiers with NULL or invalid created_at values.
     * This migration ensures all tiers have a valid created_at timestamp.
     * This is necessary when the tier table was created before the created_at
     * column was added, or when existing rows have invalid datetime values
     * (e.g., '0000-00-00 00:00:00' which MySQL 8.0+ rejects).
     * 
     * Uses native SQL to fix invalid datetime values that Hibernate cannot read.
     * Hibernate's ddl-auto: update will handle column creation/alteration.
     */
    private void fixTierCreatedAt() {
        try {
            // Check if tier table exists
            jdbcTemplate.execute("SELECT 1 FROM `tier` LIMIT 1");
            
            // Update NULL and invalid datetime values using native SQL
            // This works even if Hibernate cannot read the rows due to invalid datetime values
            // We use COALESCE to handle different invalid datetime formats
            int updated = jdbcTemplate.update(
                "UPDATE `tier` SET `created_at` = CURRENT_TIMESTAMP(6) " +
                "WHERE `created_at` IS NULL " +
                "   OR CAST(`created_at` AS CHAR) = '0000-00-00 00:00:00' " +
                "   OR `created_at` < '1970-01-01 00:00:00'"
            );
            
            if (updated > 0) {
                System.out.println("Fixed " + updated + " tier(s) with NULL or invalid created_at values via SQL");
            }
        } catch (Exception e) {
            // Table might not exist yet, or column doesn't exist
            // Hibernate will create it via ddl-auto: update
            // Ignore and continue - the column will be created as nullable
        }
        
        // Now fix any remaining NULL values using entity update
        // This ensures all tiers have a valid created_at after Hibernate creates the column
        try {
            List<Tier> allTiers = tierRepository.findAllIncludingDeleted();
            Instant now = Instant.now();
            boolean entityUpdated = false;
            
            for (Tier tier : allTiers) {
                if (tier.getCreatedAt() == null) {
                    tier.setCreatedAt(now);
                    tierRepository.save(tier);
                    entityUpdated = true;
                }
            }
            
            if (entityUpdated) {
                System.out.println("Fixed remaining tiers with NULL created_at values via entity update");
            }
        } catch (Exception e) {
            // If Hibernate can't read tiers due to invalid datetime values,
            // the native SQL update above should have fixed them
            // Ignore and continue
        }
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

