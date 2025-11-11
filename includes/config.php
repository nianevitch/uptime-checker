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
    $envHost = getenv('MYSQL_HOST') ?: getenv('DB_HOST');
    define('DB_HOST', $envHost !== false && $envHost !== '' ? $envHost : '127.0.0.1');
}

if (!defined('DB_NAME')) {
    $envDatabase = getenv('MYSQL_DATABASE') ?: getenv('DB_NAME');
    define('DB_NAME', $envDatabase !== false && $envDatabase !== '' ? $envDatabase : 'uptime_user_management');
}

if (!defined('DB_USER')) {
    $envUser = getenv('MYSQL_USER') ?: getenv('DB_USER');
    define('DB_USER', $envUser !== false && $envUser !== '' ? $envUser : 'uptime_user');
}

if (!defined('DB_PASS')) {
    $envPassword = getenv('MYSQL_PASSWORD');
    if ($envPassword === false || $envPassword === '') {
        $envPassword = getenv('DB_PASS');
    }
    define('DB_PASS', $envPassword !== false && $envPassword !== '' ? $envPassword : 'change_me');
}

