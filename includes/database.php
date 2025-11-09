<?php
declare(strict_types=1);

/**
 * Create (and memoize) the PDO connection.
 *
 * @throws \PDOException
 */
function get_pdo(): \PDO
{
    static $pdo = null;

    if ($pdo instanceof \PDO) {
        return $pdo;
    }

    $dsn = sprintf('mysql:host=%s;dbname=%s;charset=utf8mb4', DB_HOST, DB_NAME);

    $options = [
        \PDO::ATTR_ERRMODE            => \PDO::ERRMODE_EXCEPTION,
        \PDO::ATTR_DEFAULT_FETCH_MODE => \PDO::FETCH_ASSOC,
        \PDO::ATTR_EMULATE_PREPARES   => false,
    ];

    $pdo = new \PDO($dsn, DB_USER, DB_PASS, $options);

    return $pdo;
}

