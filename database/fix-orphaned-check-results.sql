-- SQL script to fix orphaned check_result records that reference non-existent ping records.
-- This script should be run BEFORE starting the application to avoid foreign key constraint violations.
--
-- Usage:
--   mysql -u uptimer -p uptime_user_management < database/fix-orphaned-check-results.sql
--
-- Or run manually in MySQL client:
--   USE uptime_user_management;
--   -- Then run the commands below

-- Step 1: Clean up orphaned check_result records that reference non-existent pings
-- This handles both monitored_url_id and ping_id columns
DELETE FROM `check_result`
WHERE (`monitored_url_id` IS NOT NULL AND `monitored_url_id` NOT IN (SELECT `id` FROM `ping`))
   OR (`ping_id` IS NOT NULL AND `ping_id` NOT IN (SELECT `id` FROM `ping`));

-- Step 2: If check_result still has monitored_url_id column, clean up orphaned records
-- (This is a fallback in case the column hasn't been renamed yet)
-- Note: MySQL doesn't support IF EXISTS for columns, so we use a stored procedure approach
-- or handle this in the application migration code.

-- Alternative approach: Temporarily disable foreign key checks, clean up, then re-enable
SET FOREIGN_KEY_CHECKS = 0;

-- Delete orphaned records
DELETE FROM `check_result`
WHERE (`monitored_url_id` IS NOT NULL AND `monitored_url_id` NOT IN (SELECT `id` FROM `ping`))
   OR (`ping_id` IS NOT NULL AND `ping_id` NOT IN (SELECT `id` FROM `ping`));

SET FOREIGN_KEY_CHECKS = 1;

-- Step 3: Verify cleanup
SELECT COUNT(*) AS orphaned_count
FROM `check_result` cr
LEFT JOIN `ping` p ON (cr.`monitored_url_id` = p.`id` OR cr.`ping_id` = p.`id`)
WHERE p.`id` IS NULL;

-- If orphaned_count > 0, there are still orphaned records that need to be manually cleaned up.

