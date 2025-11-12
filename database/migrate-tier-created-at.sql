-- ============================================================================
-- MIGRATION: Fix tier.created_at column
-- ============================================================================
-- This script fixes existing tiers with NULL or invalid created_at values.
-- 
-- Problem: Existing tier rows may have NULL or invalid datetime values
-- (e.g., '0000-00-00 00:00:00') which MySQL 8.0+ rejects when trying to
-- add a NOT NULL constraint.
--
-- Solution:
-- 1. Make created_at nullable (if not already)
-- 2. Update NULL and invalid datetime values to CURRENT_TIMESTAMP
-- 3. Optionally make created_at NOT NULL (after all rows are fixed)
-- ============================================================================

USE `uptime_user_management`;

-- Step 1: Make created_at nullable (if it doesn't exist or is NOT NULL)
-- This allows Hibernate to add the column without constraint issues
ALTER TABLE `tier` 
  MODIFY COLUMN `created_at` DATETIME(6) NULL;

-- Step 2: Update NULL and invalid datetime values
-- MySQL 8.0+ rejects '0000-00-00 00:00:00', so we update them to CURRENT_TIMESTAMP
UPDATE `tier` 
SET `created_at` = CURRENT_TIMESTAMP(6)
WHERE `created_at` IS NULL 
   OR `created_at` = '0000-00-00 00:00:00'
   OR `created_at` < '1970-01-01 00:00:00';

-- Step 3: After all rows are fixed, you can optionally make created_at NOT NULL
-- Uncomment the following line once you've verified all rows have valid timestamps:
-- ALTER TABLE `tier` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;

-- Verify: Check that all tiers have valid created_at values
SELECT `id`, `name`, `created_at`, `deleted_at` 
FROM `tier` 
ORDER BY `id`;

