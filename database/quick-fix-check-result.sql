-- Quick fix script for check_result column mismatch
-- Run this if you see: "Field 'monitored_url_id' doesn't have a default value"
-- Usage: mysql -u uptimer -p uptime_user_management < database/quick-fix-check-result.sql

USE `uptime_user_management`;

-- Disable foreign key checks temporarily
SET FOREIGN_KEY_CHECKS = 0;

-- Drop any foreign key constraints referencing monitored_url_id
SET @fk_name = (SELECT constraint_name FROM information_schema.table_constraints 
    WHERE table_schema = DATABASE() 
    AND table_name = 'check_result' 
    AND constraint_name LIKE '%monitored_url_id%'
    LIMIT 1);

SET @sql = IF(@fk_name IS NOT NULL,
    CONCAT('ALTER TABLE `check_result` DROP FOREIGN KEY `', @fk_name, '`'),
    'SELECT "No foreign key to drop" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Check if ping_id column exists
SET @ping_id_exists = (SELECT COUNT(*) FROM information_schema.columns 
    WHERE table_schema = DATABASE() 
    AND table_name = 'check_result' 
    AND column_name = 'ping_id');

SET @monitored_url_id_exists = (SELECT COUNT(*) FROM information_schema.columns 
    WHERE table_schema = DATABASE() 
    AND table_name = 'check_result' 
    AND column_name = 'monitored_url_id');

-- If both columns exist, copy data and drop old column
SET @sql = IF(@ping_id_exists > 0 AND @monitored_url_id_exists > 0,
    CONCAT('UPDATE `check_result` SET `ping_id` = `monitored_url_id` ',
           'WHERE `monitored_url_id` IS NOT NULL AND (`ping_id` IS NULL OR `ping_id` = 0); ',
           'ALTER TABLE `check_result` DROP COLUMN `monitored_url_id`;'),
    IF(@ping_id_exists = 0 AND @monitored_url_id_exists > 0,
        'ALTER TABLE `check_result` CHANGE COLUMN `monitored_url_id` `ping_id` INT UNSIGNED NOT NULL;',
        'SELECT "Migration not needed" AS message;')
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Re-enable foreign key checks
SET FOREIGN_KEY_CHECKS = 1;

-- Add foreign key constraint if it doesn't exist
SET @fk_exists = (SELECT COUNT(*) FROM information_schema.table_constraints 
    WHERE table_schema = DATABASE() 
    AND table_name = 'check_result' 
    AND constraint_name = 'FK_check_result_ping_id');

SET @sql = IF(@fk_exists = 0,
    'ALTER TABLE `check_result` ADD CONSTRAINT `FK_check_result_ping_id` FOREIGN KEY (`ping_id`) REFERENCES `ping` (`id`) ON DELETE CASCADE',
    'SELECT "Foreign key already exists" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Verify the fix
SELECT 
    column_name, 
    column_type, 
    is_nullable
FROM information_schema.columns 
WHERE table_schema = DATABASE() 
AND table_name = 'check_result' 
AND column_name IN ('ping_id', 'monitored_url_id')
ORDER BY column_name;

