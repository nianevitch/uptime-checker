-- SQL script to fix check_result table column mismatch.
-- This script handles the case where both monitored_url_id and ping_id columns exist,
-- or where only monitored_url_id exists and needs to be renamed to ping_id.
--
-- Usage:
--   mysql -u uptimer -p uptime_user_management < database/fix-check-result-column.sql

USE `uptime_user_management`;

-- Step 1: Temporarily disable foreign key checks
SET FOREIGN_KEY_CHECKS = 0;

-- Step 2: Check if both columns exist
SET @ping_id_exists = (SELECT COUNT(*) FROM information_schema.columns 
    WHERE table_schema = DATABASE() 
    AND table_name = 'check_result' 
    AND column_name = 'ping_id');

SET @monitored_url_id_exists = (SELECT COUNT(*) FROM information_schema.columns 
    WHERE table_schema = DATABASE() 
    AND table_name = 'check_result' 
    AND column_name = 'monitored_url_id');

-- Step 3: Drop foreign key constraint if it exists
SET @fk_exists = (SELECT COUNT(*) FROM information_schema.table_constraints 
    WHERE table_schema = DATABASE() 
    AND table_name = 'check_result' 
    AND constraint_name = 'FK_check_result_monitored_url_id');

SET @sql = IF(@fk_exists > 0,
    'ALTER TABLE `check_result` DROP FOREIGN KEY `FK_check_result_monitored_url_id`',
    'SELECT "Foreign key FK_check_result_monitored_url_id does not exist" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Step 4: Handle column migration
SET @sql = IF(@ping_id_exists > 0 AND @monitored_url_id_exists > 0,
    -- Both columns exist: copy data and drop old column
    CONCAT('UPDATE `check_result` SET `ping_id` = `monitored_url_id` WHERE `monitored_url_id` IS NOT NULL AND (`ping_id` IS NULL OR `ping_id` = 0); ',
           'ALTER TABLE `check_result` DROP COLUMN `monitored_url_id`; ',
           'SELECT "Migrated data and dropped monitored_url_id column" AS message'),
    IF(@ping_id_exists = 0 AND @monitored_url_id_exists > 0,
        -- Only monitored_url_id exists: rename it
        'ALTER TABLE `check_result` CHANGE COLUMN `monitored_url_id` `ping_id` INT UNSIGNED NOT NULL; ',
        -- ping_id exists or neither exists
        'SELECT "Column migration not needed" AS message')
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Step 5: Re-enable foreign key checks
SET FOREIGN_KEY_CHECKS = 1;

-- Step 6: Add foreign key constraint if it doesn't exist
SET @fk_exists = (SELECT COUNT(*) FROM information_schema.table_constraints 
    WHERE table_schema = DATABASE() 
    AND table_name = 'check_result' 
    AND constraint_name = 'FK_check_result_ping_id');

SET @sql = IF(@fk_exists = 0,
    'ALTER TABLE `check_result` ADD CONSTRAINT `FK_check_result_ping_id` FOREIGN KEY (`ping_id`) REFERENCES `ping` (`id`) ON DELETE CASCADE',
    'SELECT "Foreign key FK_check_result_ping_id already exists" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Step 7: Verify the migration
SELECT 
    column_name, 
    column_type, 
    is_nullable,
    column_default
FROM information_schema.columns 
WHERE table_schema = DATABASE() 
AND table_name = 'check_result' 
AND column_name IN ('ping_id', 'monitored_url_id')
ORDER BY column_name;

