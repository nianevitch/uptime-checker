# Database Migration: monitored_url to ping

This document explains how to migrate the database schema from `monitored_url` to `ping`.

## Problem

If you're seeing this error:
```
Cannot add or update a child row: a foreign key constraint fails 
(`uptime_user_management`.`#sql-19d4_5e`, CONSTRAINT `FK_check_result_ping_id` 
FOREIGN KEY (`ping_id`) REFERENCES `ping` (`id`))
```

This means there are orphaned records in the `check_result` table that reference non-existent `ping` records. This can happen when:
1. The `ping` table was dropped and recreated, but `check_result` still has old references
2. The database schema was partially migrated
3. Data was manually deleted from the `ping` table without cleaning up `check_result` records

## Solution

### Option 1: Fix column mismatch (If you see "Field 'monitored_url_id' doesn't have a default value")

Run this script FIRST to fix the column mismatch:

```bash
mysql -u uptimer -p uptime_user_management < database/fix-check-result-column.sql
```

Or manually in MySQL client:

```sql
USE uptime_user_management;

SET FOREIGN_KEY_CHECKS = 0;

-- Drop foreign key constraint if it exists
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

-- If both columns exist, copy data and drop old column
UPDATE `check_result` SET `ping_id` = `monitored_url_id` 
WHERE `monitored_url_id` IS NOT NULL AND (`ping_id` IS NULL OR `ping_id` = 0);

ALTER TABLE `check_result` DROP COLUMN IF EXISTS `monitored_url_id`;

SET FOREIGN_KEY_CHECKS = 1;

-- Add foreign key constraint if it doesn't exist
ALTER TABLE `check_result` ADD CONSTRAINT IF NOT EXISTS `FK_check_result_ping_id` 
FOREIGN KEY (`ping_id`) REFERENCES `ping` (`id`) ON DELETE CASCADE;
```

### Option 2: Run the SQL cleanup script (For orphaned records)

Run the SQL script to clean up orphaned records:

```bash
mysql -u uptimer -p uptime_user_management < database/fix-orphaned-check-results.sql
```

Or manually in MySQL client:

```sql
USE uptime_user_management;

-- Temporarily disable foreign key checks
SET FOREIGN_KEY_CHECKS = 0;

-- Delete orphaned records
DELETE FROM `check_result`
WHERE (`monitored_url_id` IS NOT NULL AND `monitored_url_id` NOT IN (SELECT `id` FROM `ping`))
   OR (`ping_id` IS NOT NULL AND `ping_id` NOT IN (SELECT `id` FROM `ping`));

-- Re-enable foreign key checks
SET FOREIGN_KEY_CHECKS = 1;
```

### Option 2: Run the full migration script

If you need to migrate from `monitored_url` to `ping`:

```bash
mysql -u uptimer -p uptime_user_management < database/migrate-monitored-url-to-ping.sql
```

### Option 3: Let the application handle it automatically

The application includes automatic migration code that runs on startup. However, if Hibernate fails during startup due to foreign key constraint violations, the migration won't run.

In this case:
1. First, run the cleanup script (Option 1) to fix orphaned records
2. Then, restart the application - it will handle the rest of the migration automatically

## Verification

After running the cleanup script, verify that there are no orphaned records:

```sql
SELECT COUNT(*) AS orphaned_count
FROM `check_result` cr
LEFT JOIN `ping` p ON (cr.`monitored_url_id` = p.`id` OR cr.`ping_id` = p.`id`)
WHERE p.`id` IS NULL;
```

If `orphaned_count` is 0, the cleanup was successful.

## Automatic Migration

The application includes a `DatabaseMigration` component that runs automatically on startup (with `@Order(1)` to run before other components). This migration:

1. Renames `monitored_url` table to `ping` (if it exists)
2. Updates `check_result` table to change `monitored_url_id` to `ping_id`
3. Cleans up orphaned records
4. Updates foreign key constraints and indexes

However, if Hibernate fails during startup due to foreign key constraint violations, you'll need to run the cleanup script manually first.

## Troubleshooting

### Error: "Table 'ping' doesn't exist"

This means the migration hasn't run yet. The application will handle this automatically on startup, or you can run the migration script manually.

### Error: "Foreign key constraint fails"

This means there are orphaned records. Run the cleanup script (Option 1) first, then restart the application.

### Error: "Duplicate key error"

This means the migration has already run, but there's a duplicate key issue. Check the `ping` table for duplicate IDs, or drop and recreate the tables if this is a development database.

## Notes

- The migration is idempotent - it's safe to run multiple times
- The migration preserves all data from `monitored_url` to `ping`
- Orphaned `check_result` records are deleted (they reference non-existent pings)
- The migration runs automatically on application startup, but may fail if there are foreign key constraint violations

