<?php
declare(strict_types=1);

/**
 * Application configuration.
 *
 * Copy this file to config.local.php and fill in the real credentials for your environment.
 * The default values target a local MySQL instance.
 */

/**
 * If you have a `config.local.php` in the same directory, it can define the
 * constants before the defaults below are applied.
 */
$localConfig = __DIR__ . '/config.local.php';
if (is_file($localConfig)) {
    require_once $localConfig;
}

if (!defined('DB_HOST')) {
    define('DB_HOST', '127.0.0.1');
}

if (!defined('DB_NAME')) {
    define('DB_NAME', 'uptime_user_management');
}

if (!defined('DB_USER')) {
    define('DB_USER', 'uptime_user');
}

if (!defined('DB_PASS')) {
    define('DB_PASS', 'change_me');
}

