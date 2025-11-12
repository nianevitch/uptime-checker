package com.isofuture.uptime.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database migration component that runs BEFORE other components.
 * This handles schema migrations that must occur before Hibernate's ddl-auto: update.
 * 
 * Order(1) ensures this runs before DataSeeder (which runs at default order).
 */
@Component
@Order(1)
public class DatabaseMigration implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(String... args) {
        migrateMonitoredUrlToPing();
    }

    /**
     * Migrates the database schema from monitored_url to ping.
     * This must run BEFORE Hibernate's ddl-auto: update to avoid foreign key constraint violations.
     * 
     * Steps:
     * 1. Check if monitored_url table exists and ping table doesn't exist
     * 2. If monitored_url exists, rename it to ping
     * 3. Clean up orphaned check_result records
     * 4. Update check_result table to change monitored_url_id to ping_id
     * 5. Update foreign key constraints and indexes
     */
    private void migrateMonitoredUrlToPing() {
        try {
            // Check if monitored_url table exists
            Integer monitoredUrlExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = DATABASE() AND table_name = 'monitored_url'",
                Integer.class
            );
            
            // Check if ping table exists
            Integer pingExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = DATABASE() AND table_name = 'ping'",
                Integer.class
            );
            
            boolean hasMonitoredUrl = monitoredUrlExists != null && monitoredUrlExists > 0;
            boolean hasPing = pingExists != null && pingExists > 0;
            
            if (hasMonitoredUrl && !hasPing) {
                // Case 1: monitored_url exists, ping doesn't - simple rename
                System.out.println("[Migration] Migrating monitored_url table to ping...");
                
                // Step 1: Drop ALL foreign key constraints from check_result that reference monitored_url_id
                try {
                    // Find all foreign key constraints that reference monitored_url_id column
                    List<String> fkNames = jdbcTemplate.queryForList(
                        "SELECT constraint_name FROM information_schema.key_column_usage " +
                        "WHERE table_schema = DATABASE() " +
                        "AND table_name = 'check_result' " +
                        "AND column_name = 'monitored_url_id' " +
                        "AND referenced_table_name IS NOT NULL",
                        String.class
                    );
                    
                    for (String fkName : fkNames) {
                        try {
                            jdbcTemplate.execute("ALTER TABLE `check_result` DROP FOREIGN KEY `" + fkName + "`");
                            System.out.println("[Migration] Dropped foreign key: " + fkName);
                        } catch (Exception e) {
                            System.out.println("[Migration] Warning: Could not drop foreign key " + fkName + ": " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[Migration] Info: Could not find/drop foreign keys (might not exist): " + e.getMessage());
                }
                
                // Step 2: Clean up orphaned check_result records before renaming
                try {
                    jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
                    
                    Integer orphanedCount = jdbcTemplate.update(
                        "DELETE FROM `check_result` " +
                        "WHERE `monitored_url_id` NOT IN (SELECT `id` FROM `monitored_url`)"
                    );
                    
                    jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
                    
                    if (orphanedCount != null && orphanedCount > 0) {
                        System.out.println("[Migration] Cleaned up " + orphanedCount + " orphaned check_result records");
                    }
                } catch (Exception e) {
                    // Re-enable foreign key checks in case of error
                    try {
                        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
                    } catch (Exception e2) {
                        // Ignore
                    }
                    // check_result table might not exist yet, continue
                    System.out.println("[Migration] Info: Could not clean up orphaned records (table might not exist): " + e.getMessage());
                }
                
                // Step 3: Rename monitored_url table to ping
                try {
                    jdbcTemplate.execute("RENAME TABLE `monitored_url` TO `ping`");
                    System.out.println("[Migration] Renamed monitored_url table to ping");
                    hasPing = true;
                } catch (Exception e) {
                    System.out.println("[Migration] Error: Could not rename monitored_url table to ping: " + e.getMessage());
                    throw e; // Re-throw to prevent continuing with invalid state
                }
            } else if (hasMonitoredUrl && hasPing) {
                // Case 2: Both tables exist - migrate data and drop old table
                System.out.println("[Migration] Both monitored_url and ping tables exist - migrating data...");
                
                // Step 1: Drop ALL foreign key constraints from check_result that reference monitored_url_id
                try {
                    // Find all foreign key constraints that reference monitored_url_id column
                    List<String> fkNames = jdbcTemplate.queryForList(
                        "SELECT constraint_name FROM information_schema.key_column_usage " +
                        "WHERE table_schema = DATABASE() " +
                        "AND table_name = 'check_result' " +
                        "AND column_name = 'monitored_url_id' " +
                        "AND referenced_table_name IS NOT NULL",
                        String.class
                    );
                    
                    for (String fkName : fkNames) {
                        try {
                            jdbcTemplate.execute("ALTER TABLE `check_result` DROP FOREIGN KEY `" + fkName + "`");
                            System.out.println("[Migration] Dropped foreign key: " + fkName);
                        } catch (Exception e) {
                            System.out.println("[Migration] Warning: Could not drop foreign key " + fkName + ": " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[Migration] Warning: Could not find/drop foreign keys: " + e.getMessage());
                }
                
                // Step 2: Clean up orphaned records in check_result (temporarily disable foreign key checks)
                try {
                    jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
                    
                    Integer orphanedCount = jdbcTemplate.update(
                        "DELETE FROM `check_result` " +
                        "WHERE (`monitored_url_id` IS NOT NULL AND `monitored_url_id` NOT IN (SELECT `id` FROM `ping`)) " +
                        "OR (`ping_id` IS NOT NULL AND `ping_id` NOT IN (SELECT `id` FROM `ping`))"
                    );
                    
                    jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
                    
                    if (orphanedCount != null && orphanedCount > 0) {
                        System.out.println("[Migration] Cleaned up " + orphanedCount + " orphaned check_result records");
                    }
                } catch (Exception e) {
                    // Re-enable foreign key checks in case of error
                    try {
                        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
                    } catch (Exception e2) {
                        // Ignore
                    }
                    System.out.println("[Migration] Warning: Could not clean up orphaned records: " + e.getMessage());
                }
                
                // Step 3: Migrate data from monitored_url to ping (if not already migrated)
                try {
                    Integer dataMigrated = jdbcTemplate.update(
                        "INSERT INTO `ping` (`id`, `user_id`, `label`, `url`, `frequency_minutes`, " +
                        "`next_check_at`, `in_progress`, `created_at`, `updated_at`) " +
                        "SELECT `id`, `user_id`, `label`, `url`, `frequency_minutes`, " +
                        "`next_check_at`, `in_progress`, `created_at`, `updated_at` " +
                        "FROM `monitored_url` " +
                        "WHERE NOT EXISTS (SELECT 1 FROM `ping` p WHERE p.`id` = `monitored_url`.`id`)"
                    );
                    
                    if (dataMigrated != null && dataMigrated > 0) {
                        System.out.println("[Migration] Migrated " + dataMigrated + " row(s) from monitored_url to ping");
                    }
                } catch (Exception e) {
                    System.out.println("[Migration] Warning: Could not migrate data from monitored_url to ping: " + e.getMessage());
                }
                
                // Step 4: Drop old monitored_url table (now that foreign key is dropped)
                try {
                    jdbcTemplate.execute("DROP TABLE IF EXISTS `monitored_url`");
                    System.out.println("[Migration] Dropped old monitored_url table");
                } catch (Exception e) {
                    System.out.println("[Migration] Warning: Could not drop monitored_url table: " + e.getMessage());
                }
            } else if (!hasMonitoredUrl && hasPing) {
                // Case 3: ping exists, monitored_url doesn't - migration already complete
                System.out.println("[Migration] Migration already complete - ping table exists");
            } else {
                // Case 4: Neither table exists - Hibernate will create ping table
                System.out.println("[Migration] No migration needed - tables will be created by Hibernate");
                return;
            }
            
            // Now handle check_result table migration
            if (hasPing || pingExists != null && pingExists > 0) {
                try {
                    // Check if check_result table has monitored_url_id column
                    Integer columnExists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = 'check_result' " +
                        "AND column_name = 'monitored_url_id'",
                        Integer.class
                    );
                    
                    if (columnExists != null && columnExists > 0) {
                        // Step 1: Drop ALL foreign key constraints that reference monitored_url_id (MUST be done first)
                        try {
                            // Find all foreign key constraints that reference monitored_url_id column
                            List<String> fkNames = jdbcTemplate.queryForList(
                                "SELECT constraint_name FROM information_schema.key_column_usage " +
                                "WHERE table_schema = DATABASE() " +
                                "AND table_name = 'check_result' " +
                                "AND column_name = 'monitored_url_id' " +
                                "AND referenced_table_name IS NOT NULL",
                                String.class
                            );
                            
                            for (String fkName : fkNames) {
                                try {
                                    jdbcTemplate.execute("ALTER TABLE `check_result` DROP FOREIGN KEY `" + fkName + "`");
                                    System.out.println("[Migration] Dropped foreign key: " + fkName);
                                } catch (Exception e) {
                                    System.out.println("[Migration] Warning: Could not drop foreign key " + fkName + ": " + e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("[Migration] Warning: Could not find/drop foreign keys: " + e.getMessage());
                        }
                        
                        // Step 2: Drop old index if it exists
                        try {
                            Integer idxExists = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM information_schema.statistics " +
                                "WHERE table_schema = DATABASE() AND table_name = 'check_result' " +
                                "AND index_name = 'IX_check_result_monitored_url_id'",
                                Integer.class
                            );
                            
                            if (idxExists != null && idxExists > 0) {
                                jdbcTemplate.execute("ALTER TABLE `check_result` DROP INDEX `IX_check_result_monitored_url_id`");
                                System.out.println("[Migration] Dropped index IX_check_result_monitored_url_id");
                            }
                        } catch (Exception e) {
                            System.out.println("[Migration] Warning: Could not drop index IX_check_result_monitored_url_id: " + e.getMessage());
                        }
                        
                        // Step 3: Clean up orphaned records (temporarily disable foreign key checks)
                        try {
                            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
                            
                            Integer orphanedCount = jdbcTemplate.update(
                                "DELETE FROM `check_result` " +
                                "WHERE `monitored_url_id` NOT IN (SELECT `id` FROM `ping`)"
                            );
                            
                            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
                            
                            if (orphanedCount != null && orphanedCount > 0) {
                                System.out.println("[Migration] Cleaned up " + orphanedCount + " orphaned check_result records");
                            }
                        } catch (Exception e) {
                            // Re-enable foreign key checks in case of error
                            try {
                                jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
                            } catch (Exception e2) {
                                // Ignore
                            }
                            System.out.println("[Migration] Warning: Could not clean up orphaned records: " + e.getMessage());
                        }
                        
                        // Step 4: Rename monitored_url_id column to ping_id
                        // Check if both columns exist (Hibernate might have added ping_id already)
                        try {
                            Integer pingIdExists = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM information_schema.columns " +
                                "WHERE table_schema = DATABASE() AND table_name = 'check_result' " +
                                "AND column_name = 'ping_id'",
                                Integer.class
                            );
                            
                            Integer monitoredUrlIdExists = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM information_schema.columns " +
                                "WHERE table_schema = DATABASE() AND table_name = 'check_result' " +
                                "AND column_name = 'monitored_url_id'",
                                Integer.class
                            );
                            
                            boolean hasPingId = pingIdExists != null && pingIdExists > 0;
                            boolean hasMonitoredUrlId = monitoredUrlIdExists != null && monitoredUrlIdExists > 0;
                            
                            if (hasPingId && hasMonitoredUrlId) {
                                // Both columns exist - copy data and drop old column
                                System.out.println("[Migration] Both ping_id and monitored_url_id columns exist - copying data and dropping old column");
                                
                                try {
                                    jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
                                    
                                    // Copy data from monitored_url_id to ping_id (only if ping_id is NULL)
                                    int rowsUpdated = jdbcTemplate.update(
                                        "UPDATE `check_result` SET `ping_id` = `monitored_url_id` " +
                                        "WHERE `monitored_url_id` IS NOT NULL AND (`ping_id` IS NULL OR `ping_id` = 0)"
                                    );
                                    jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
                                    System.out.println("[Migration] Copied " + rowsUpdated + " row(s) from monitored_url_id to ping_id");
                                } catch (Exception e) {
                                    try {
                                        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
                                    } catch (Exception e2) {
                                        // Ignore
                                    }
                                    System.out.println("[Migration] Warning: Could not copy data: " + e.getMessage());
                                }
                                
                                // Drop old monitored_url_id column
                                try {
                                    jdbcTemplate.execute("ALTER TABLE `check_result` DROP COLUMN `monitored_url_id`");
                                    System.out.println("[Migration] Dropped old monitored_url_id column");
                                } catch (Exception e) {
                                    System.out.println("[Migration] Warning: Could not drop monitored_url_id column: " + e.getMessage());
                                }
                                
                            } else if (!hasPingId && hasMonitoredUrlId) {
                                // Only monitored_url_id exists - rename it to ping_id
                                System.out.println("[Migration] Only monitored_url_id exists - renaming to ping_id");
                                
                                // Get the current column definition
                                String columnType = jdbcTemplate.queryForObject(
                                    "SELECT column_type FROM information_schema.columns " +
                                    "WHERE table_schema = DATABASE() " +
                                    "AND table_name = 'check_result' " +
                                    "AND column_name = 'monitored_url_id'",
                                    String.class
                                );
                                
                                String isNullable = jdbcTemplate.queryForObject(
                                    "SELECT is_nullable FROM information_schema.columns " +
                                    "WHERE table_schema = DATABASE() " +
                                    "AND table_name = 'check_result' " +
                                    "AND column_name = 'monitored_url_id'",
                                    String.class
                                );
                                
                                // Build the column definition
                                StringBuilder columnDef = new StringBuilder(columnType != null ? columnType : "INT UNSIGNED");
                                if (isNullable != null && "NO".equals(isNullable)) {
                                    columnDef.append(" NOT NULL");
                                }
                                
                                // Add new ping_id column
                                try {
                                    jdbcTemplate.execute(
                                        "ALTER TABLE `check_result` ADD COLUMN `ping_id` " + columnDef.toString() + " AFTER `monitored_url_id`"
                                    );
                                    System.out.println("[Migration] Added new ping_id column");
                                    
                                    // Copy data
                                    jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
                                    int rowsUpdated = jdbcTemplate.update(
                                        "UPDATE `check_result` SET `ping_id` = `monitored_url_id` WHERE `monitored_url_id` IS NOT NULL"
                                    );
                                    jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
                                    System.out.println("[Migration] Copied " + rowsUpdated + " row(s) from monitored_url_id to ping_id");
                                    
                                    // Drop old column
                                    jdbcTemplate.execute("ALTER TABLE `check_result` DROP COLUMN `monitored_url_id`");
                                    System.out.println("[Migration] Dropped old monitored_url_id column");
                                } catch (Exception e) {
                                    try {
                                        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
                                    } catch (Exception e2) {
                                        // Ignore
                                    }
                                    System.out.println("[Migration] Error: Could not rename column: " + e.getMessage());
                                }
                                
                            } else if (hasPingId && !hasMonitoredUrlId) {
                                // Only ping_id exists - migration already complete
                                System.out.println("[Migration] ping_id column already exists, migration complete");
                            } else {
                                // Neither column exists - Hibernate will create ping_id
                                System.out.println("[Migration] Neither column exists, Hibernate will create ping_id");
                            }
                        } catch (Exception e) {
                            System.out.println("[Migration] Error: Could not check/rename column: " + e.getMessage());
                        }
                        
                        // Step 5: Add new foreign key constraint (if it doesn't exist)
                        try {
                            Integer fkExists = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM information_schema.table_constraints " +
                                "WHERE table_schema = DATABASE() AND table_name = 'check_result' " +
                                "AND constraint_name = 'FK_check_result_ping_id'",
                                Integer.class
                            );
                            
                            if (fkExists == null || fkExists == 0) {
                                jdbcTemplate.execute(
                                    "ALTER TABLE `check_result` ADD CONSTRAINT `FK_check_result_ping_id` " +
                                    "FOREIGN KEY (`ping_id`) REFERENCES `ping` (`id`) ON DELETE CASCADE"
                                );
                                System.out.println("[Migration] Added new foreign key FK_check_result_ping_id");
                            }
                        } catch (Exception e) {
                            System.out.println("[Migration] Warning: Could not add foreign key FK_check_result_ping_id: " + e.getMessage());
                        }
                        
                        // Step 6: Add new index if it doesn't exist
                        try {
                            Integer idxExists = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM information_schema.statistics " +
                                "WHERE table_schema = DATABASE() AND table_name = 'check_result' " +
                                "AND index_name = 'IX_check_result_ping_id'",
                                Integer.class
                            );
                            
                            if (idxExists == null || idxExists == 0) {
                                jdbcTemplate.execute("ALTER TABLE `check_result` ADD INDEX `IX_check_result_ping_id` (`ping_id`)");
                                System.out.println("[Migration] Added new index IX_check_result_ping_id");
                            }
                        } catch (Exception e) {
                            System.out.println("[Migration] Warning: Could not add index IX_check_result_ping_id: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    // check_result table might not exist yet, continue
                    System.out.println("[Migration] Warning: Could not migrate check_result table: " + e.getMessage());
                }
            }
            
            System.out.println("[Migration] Migration from monitored_url to ping completed");
        } catch (Exception e) {
            // Tables might not exist yet (new database), or migration already completed
            // Hibernate will create/update the schema via ddl-auto: update
            System.out.println("[Migration] Migration skipped (tables might not exist yet): " + e.getMessage());
        }
    }
}

