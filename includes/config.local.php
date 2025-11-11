<?php
declare(strict_types=1);

/**
 * Application configuration.
 *
 * Copy this file to config.local.php and fill in the real credentials for your environment.
 * The default values target a local MySQL instance.
 */

if (!getenv('MYSQL_HOST') && !getenv('DB_HOST') && !defined('DB_HOST')) {
    define('DB_HOST', '127.0.0.1:3307');
}

if (!getenv('MYSQL_DATABASE') && !getenv('DB_NAME') && !defined('DB_NAME')) {
    define('DB_NAME', 'uptime_user_management');
}

if (!getenv('MYSQL_USER') && !getenv('DB_USER') && !defined('DB_USER')) {
    define('DB_USER', 'uptime_user');
}

if (!getenv('MYSQL_PASSWORD') && !getenv('DB_PASS') && !defined('DB_PASS')) {
    define('DB_PASS', 'uptime_user');
}

