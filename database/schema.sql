DROP DATABASE IF EXISTS `uptime_user_management`;

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

CREATE TABLE IF NOT EXISTS `roles` (
  `id` TINYINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_roles_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_roles` (
  `user_id` INT UNSIGNED NOT NULL,
  `role_id` TINYINT UNSIGNED NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`),
  CONSTRAINT `fk_user_roles_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_user_roles_role`
    FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `roles` (`name`) VALUES ('user'), ('admin')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- Seed baseline accounts
INSERT INTO `users` (`email`, `password_hash`)
VALUES
  ('mary@invoken.com', '$2y$12$WfmbQ7DtoaVwwzcDxqeum.Xem515iGiEH381m/qdtokXI37rzjgMq'), -- password: pass
  ('zookeeper@invoken.com', '$2y$12$WfmbQ7DtoaVwwzcDxqeum.Xem515iGiEH381m/qdtokXI37rzjgMq') -- password: pass
ON DUPLICATE KEY UPDATE `email` = VALUES(`email`);

INSERT IGNORE INTO `user_roles` (`user_id`, `role_id`)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'user'
WHERE u.email IN ('mary@invoken.com', 'zookeeper@invoken.com');

INSERT IGNORE INTO `user_roles` (`user_id`, `role_id`)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'admin'
WHERE u.email = 'zookeeper@invoken.com';

CREATE TABLE IF NOT EXISTS `monitored_urls` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT UNSIGNED NOT NULL,
  `label` VARCHAR(190) DEFAULT NULL,
  `url` VARCHAR(255) NOT NULL,
  `frequency_minutes` INT UNSIGNED NOT NULL DEFAULT 5,
  `next_check_at` DATETIME DEFAULT NULL,
  `in_progress` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_user_url` (`user_id`, `url`),
  CONSTRAINT `fk_monitored_urls_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `check_results` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `monitored_url_id` INT UNSIGNED NOT NULL,
  `http_code` SMALLINT UNSIGNED DEFAULT NULL,
  `error_message` TEXT DEFAULT NULL,
  `response_time_ms` DECIMAL(10,2) DEFAULT NULL,
  `checked_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_check_results_url` (`monitored_url_id`, `checked_at`),
  CONSTRAINT `fk_check_results_url`
    FOREIGN KEY (`monitored_url_id`) REFERENCES `monitored_urls` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `monitored_urls` (`user_id`, `label`, `url`)
SELECT u.id, 'Status Page', 'https://status.invoken.com'
FROM users u
WHERE u.email = 'mary@invoken.com'
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`), `url` = VALUES(`url`);

INSERT INTO `monitored_urls` (`user_id`, `label`, `url`)
SELECT u.id, 'Docs', 'https://docs.invoken.com'
FROM users u
WHERE u.email = 'mary@invoken.com'
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`), `url` = VALUES(`url`);

INSERT INTO `monitored_urls` (`user_id`, `label`, `url`)
SELECT u.id, 'Admin Portal', 'https://admin.invoken.com'
FROM users u
WHERE u.email = 'zookeeper@invoken.com'
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`), `url` = VALUES(`url`);

-- Additional seed monitors
INSERT INTO `monitored_urls` (`user_id`, `label`, `url`)
SELECT u.id, 'Google', 'https://www.google.com'
FROM users u
WHERE u.email = 'mary@invoken.com'
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`), `url` = VALUES(`url`);

INSERT INTO `monitored_urls` (`user_id`, `label`, `url`)
SELECT u.id, 'YouTube', 'https://www.youtube.com'
FROM users u
WHERE u.email = 'mary@invoken.com'
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`), `url` = VALUES(`url`);

INSERT INTO `monitored_urls` (`user_id`, `label`, `url`)
SELECT u.id, 'Gmail', 'https://mail.google.com'
FROM users u
WHERE u.email = 'mary@invoken.com'
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`), `url` = VALUES(`url`);

INSERT INTO `monitored_urls` (`user_id`, `label`, `url`)
SELECT u.id, 'Google News', 'https://news.google.com'
FROM users u
WHERE u.email = 'mary@invoken.com'
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`), `url` = VALUES(`url`);

INSERT INTO `monitored_urls` (`user_id`, `label`, `url`)
SELECT u.id, 'Google Maps', 'https://maps.google.com'
FROM users u
WHERE u.email = 'mary@invoken.com'
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`), `url` = VALUES(`url`);

INSERT INTO `monitored_urls` (`user_id`, `label`, `url`)
SELECT u.id, 'Yahoo', 'https://www.yahoo.com'
FROM users u
WHERE u.email = 'mary@invoken.com'
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`), `url` = VALUES(`url`);

INSERT INTO `monitored_urls` (`user_id`, `label`, `url`)
SELECT u.id, 'Yahoo Finance', 'https://finance.yahoo.com'
FROM users u
WHERE u.email = 'mary@invoken.com'
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`), `url` = VALUES(`url`);

INSERT INTO `monitored_urls` (`user_id`, `label`, `url`)
SELECT u.id, 'Yahoo Mail', 'https://mail.yahoo.com'
FROM users u
WHERE u.email = 'mary@invoken.com'
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`), `url` = VALUES(`url`);

INSERT INTO `monitored_urls` (`user_id`, `label`, `url`)
SELECT u.id, 'Microsoft', 'https://www.microsoft.com'
FROM users u
WHERE u.email = 'zookeeper@invoken.com'
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`), `url` = VALUES(`url`);

INSERT INTO `monitored_urls` (`user_id`, `label`, `url`)
SELECT u.id, 'Bing', 'https://www.bing.com'
FROM users u
WHERE u.email = 'zookeeper@invoken.com'
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`), `url` = VALUES(`url`);

INSERT INTO `monitored_urls` (`user_id`, `label`, `url`)
SELECT u.id, 'Outlook', 'https://outlook.live.com'
FROM users u
WHERE u.email = 'zookeeper@invoken.com'
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`), `url` = VALUES(`url`);

INSERT INTO `monitored_urls` (`user_id`, `label`, `url`)
SELECT u.id, 'LinkedIn', 'https://www.linkedin.com'
FROM users u
WHERE u.email = 'zookeeper@invoken.com'
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`), `url` = VALUES(`url`);

INSERT INTO `monitored_urls` (`user_id`, `label`, `url`)
SELECT u.id, 'GitHub', 'https://github.com'
FROM users u
WHERE u.email = 'zookeeper@invoken.com'
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`), `url` = VALUES(`url`);

INSERT INTO `monitored_urls` (`user_id`, `label`, `url`)
SELECT u.id, 'Stack Overflow', 'https://stackoverflow.com'
FROM users u
WHERE u.email = 'zookeeper@invoken.com'
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`), `url` = VALUES(`url`);

INSERT INTO `monitored_urls` (`user_id`, `label`, `url`)
SELECT u.id, 'Wikipedia', 'https://www.wikipedia.org'
FROM users u
WHERE u.email = 'zookeeper@invoken.com'
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`), `url` = VALUES(`url`);

