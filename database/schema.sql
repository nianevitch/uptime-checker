CREATE DATABASE IF NOT EXISTS `uptime_user_management`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Create the shared user with password (if not already created)
CREATE USER IF NOT EXISTS 'uptimer'@'%' IDENTIFIED BY 'uptimer';

-- Grant access to both databases
GRANT ALL PRIVILEGES ON uptime_user_management.* TO 'uptimer'@'%';

USE `uptime_user_management`;

CREATE TABLE IF NOT EXISTS `users` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `email` VARCHAR(190) NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

