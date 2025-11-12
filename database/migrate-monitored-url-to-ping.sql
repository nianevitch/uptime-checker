-- SQL migration script to rename monitored_url table to ping and update related foreign keys.
-- This script migrates the database schema from the old "monitored_url" naming to the new "ping" naming.
--
-- Steps:
-- 1. Check if monitored_url table exists (old schema)
-- 2. If it exists, rename it to ping
-- 3. Update check_result table to change monitored_url_id to ping_id
-- 4. Update foreign key constraints
--
-- IMPORTANT: Run this script BEFORE starting the application, or let DataSeeder handle it automatically.

-- Step 1: Rename monitored_url table to ping (if it exists)
-- Note: MySQL doesn't support IF EXISTS for RENAME TABLE, so we check first
SET @table_exists = (SELECT COUNT(*) FROM information_schema.tables 
    WHERE table_schema = DATABASE() 
    AND table_name = 'monitored_url');

SET @sql = IF(@table_exists > 0,
    'RENAME TABLE `monitored_url` TO `ping`',
    'SELECT "Table monitored_url does not exist, skipping rename" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Step 2: Update check_result table column from monitored_url_id to ping_id
-- Check if monitored_url_id column exists
SET @column_exists = (SELECT COUNT(*) FROM information_schema.columns 
    WHERE table_schema = DATABASE() 
    AND table_name = 'check_result' 
    AND column_name = 'monitored_url_id');

-- If column exists, rename it to ping_id
SET @sql = IF(@column_exists > 0,
    'ALTER TABLE `check_result` CHANGE COLUMN `monitored_url_id` `ping_id` INT UNSIGNED NOT NULL',
    'SELECT "Column monitored_url_id does not exist, skipping rename" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Step 3: Drop old foreign key constraint if it exists
SET @fk_exists = (SELECT COUNT(*) FROM information_schema.table_constraints 
    WHERE table_schema = DATABASE() 
    AND table_name = 'check_result' 
    AND constraint_name = 'FK_check_result_monitored_url_id');

SET @sql = IF(@fk_exists > 0,
    'ALTER TABLE `check_result` DROP FOREIGN KEY `FK_check_result_monitored_url_id`',
    'SELECT "Foreign key FK_check_result_monitored_url_id does not exist, skipping drop" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Step 4: Drop old index if it exists
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics 
    WHERE table_schema = DATABASE() 
    AND table_name = 'check_result' 
    AND index_name = 'IX_check_result_monitored_url_id');

SET @sql = IF(@idx_exists > 0,
    'ALTER TABLE `check_result` DROP INDEX `IX_check_result_monitored_url_id`',
    'SELECT "Index IX_check_result_monitored_url_id does not exist, skipping drop" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Step 5: Add new foreign key constraint (if it doesn't exist)
SET @fk_exists = (SELECT COUNT(*) FROM information_schema.table_constraints 
    WHERE table_schema = DATABASE() 
    AND table_name = 'check_result' 
    AND constraint_name = 'FK_check_result_ping_id');

SET @sql = IF(@fk_exists = 0,
    'ALTER TABLE `check_result` ADD CONSTRAINT `FK_check_result_ping_id` FOREIGN KEY (`ping_id`) REFERENCES `ping` (`id`) ON DELETE CASCADE',
    'SELECT "Foreign key FK_check_result_ping_id already exists, skipping add" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Step 6: Add new index (if it doesn't exist)
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics 
    WHERE table_schema = DATABASE() 
    AND table_name = 'check_result' 
    AND index_name = 'IX_check_result_ping_id');

SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE `check_result` ADD INDEX `IX_check_result_ping_id` (`ping_id`)',
    'SELECT "Index IX_check_result_ping_id already exists, skipping add" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Step 7: Update indexes on ping table (if monitored_url indexes exist)
-- Drop old indexes on ping table
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics 
    WHERE table_schema = DATABASE() 
    AND table_name = 'ping' 
    AND index_name = 'IX_monitored_url_user_id');

SET @sql = IF(@idx_exists > 0,
    'ALTER TABLE `ping` DROP INDEX `IX_monitored_url_user_id`',
    'SELECT "Index IX_monitored_url_user_id does not exist, skipping drop" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics 
    WHERE table_schema = DATABASE() 
    AND table_name = 'ping' 
    AND index_name = 'IX_monitored_url_next_check_at');

SET @sql = IF(@idx_exists > 0,
    'ALTER TABLE `ping` DROP INDEX `IX_monitored_url_next_check_at`',
    'SELECT "Index IX_monitored_url_next_check_at does not exist, skipping drop" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics 
    WHERE table_schema = DATABASE() 
    AND table_name = 'ping' 
    AND index_name = 'IX_monitored_url_in_progress');

SET @sql = IF(@idx_exists > 0,
    'ALTER TABLE `ping` DROP INDEX `IX_monitored_url_in_progress`',
    'SELECT "Index IX_monitored_url_in_progress does not exist, skipping drop" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add new indexes on ping table (if they don't exist)
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics 
    WHERE table_schema = DATABASE() 
    AND table_name = 'ping' 
    AND index_name = 'IX_ping_user_id');

SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE `ping` ADD INDEX `IX_ping_user_id` (`user_id`)',
    'SELECT "Index IX_ping_user_id already exists, skipping add" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics 
    WHERE table_schema = DATABASE() 
    AND table_name = 'ping' 
    AND index_name = 'IX_ping_next_check_at');

SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE `ping` ADD INDEX `IX_ping_next_check_at` (`next_check_at`)',
    'SELECT "Index IX_ping_next_check_at already exists, skipping add" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics 
    WHERE table_schema = DATABASE() 
    AND table_name = 'ping' 
    AND index_name = 'IX_ping_in_progress');

SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE `ping` ADD INDEX `IX_ping_in_progress` (`in_progress`)',
    'SELECT "Index IX_ping_in_progress already exists, skipping add" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Step 8: Update foreign key on ping table (if old FK exists)
SET @fk_exists = (SELECT COUNT(*) FROM information_schema.table_constraints 
    WHERE table_schema = DATABASE() 
    AND table_name = 'ping' 
    AND constraint_name = 'FK_monitored_url_user_id');

SET @sql = IF(@fk_exists > 0,
    'ALTER TABLE `ping` DROP FOREIGN KEY `FK_monitored_url_user_id`',
    'SELECT "Foreign key FK_monitored_url_user_id does not exist, skipping drop" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_exists = (SELECT COUNT(*) FROM information_schema.table_constraints 
    WHERE table_schema = DATABASE() 
    AND table_name = 'ping' 
    AND constraint_name = 'FK_ping_user_id');

SET @sql = IF(@fk_exists = 0,
    'ALTER TABLE `ping` ADD CONSTRAINT `FK_ping_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE',
    'SELECT "Foreign key FK_ping_user_id already exists, skipping add" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

