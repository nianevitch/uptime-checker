<?php
declare(strict_types=1);

/**
 * Application configuration.
 *
 * Copy this file to config.local.php and fill in the real credentials for your environment.
 * The default values target a local MySQL instance.
 */

const DB_HOST = '127.0.0.1';
const DB_NAME = 'uptime_user_management';
const DB_USER = 'uptimer';
const DB_PASS = 'uptimer';

/**
 * If you have a `config.local.php` in the same directory, it will override the defaults above.
 */
$localConfig = __DIR__ . '/config.local.php';
if (is_file($localConfig)) {
    require_once $localConfig;
}

