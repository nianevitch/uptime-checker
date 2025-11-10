<?php
declare(strict_types=1);

use DateTimeImmutable;
use RuntimeException;

/**
 * Fetch monitored URLs for the current user or, if admin, all users.
 *
 * @return array<int, array<string, mixed>>
 */
function get_monitored_urls(\PDO $pdo, int $currentUserId, bool $isAdmin): array
{
    if ($isAdmin) {
        $stmt = $pdo->query(
            'SELECT mu.id,
                    mu.user_id,
                    mu.label,
                    mu.url,
                    mu.frequency_minutes,
                    mu.next_check_at,
                    mu.in_progress,
                    mu.created_at,
                    mu.updated_at,
                    u.email AS owner_email
             FROM monitored_urls mu
             INNER JOIN users u ON u.id = mu.user_id
             ORDER BY u.email ASC, mu.created_at DESC'
        );
    } else {
        $stmt = $pdo->prepare(
            'SELECT mu.id,
                    mu.user_id,
                    mu.label,
                    mu.url,
                    mu.frequency_minutes,
                    mu.next_check_at,
                    mu.in_progress,
                    mu.created_at,
                    mu.updated_at,
                    NULL AS owner_email
             FROM monitored_urls mu
             WHERE mu.user_id = :user_id
             ORDER BY mu.created_at DESC'
        );
        $stmt->execute([':user_id' => $currentUserId]);
    }

    return $stmt->fetchAll() ?: [];
}

/**
 * Retrieve a single monitored URL by id.
 */
function get_monitored_url(\PDO $pdo, int $id): ?array
{
    $stmt = $pdo->prepare(
        'SELECT mu.id,
                mu.user_id,
                mu.label,
                mu.url,
                mu.frequency_minutes,
                mu.next_check_at,
                mu.in_progress,
                mu.created_at,
                mu.updated_at,
                u.email AS owner_email
         FROM monitored_urls mu
         INNER JOIN users u ON u.id = mu.user_id
         WHERE mu.id = :id
         LIMIT 1'
    );
    $stmt->execute([':id' => $id]);
    $row = $stmt->fetch();

    return $row === false ? null : $row;
}

/**
 * Determine whether the acting user can modify the given URL row.
 */
function can_modify_url(array $urlRow, int $currentUserId, bool $isAdmin): bool
{
    return $isAdmin || (int) $urlRow['user_id'] === $currentUserId;
}

/**
 * Create a new monitored URL.
 *
 * @return array{0:bool,1:string|null}
 */
function create_monitored_url(\PDO $pdo, int $ownerId, string $url, ?string $label, int $frequencyMinutes): array
{
    [$valid, $message] = validate_url_input($url);
    if (!$valid) {
        return [false, $message];
    }

    $frequencyMinutes = normalize_frequency($frequencyMinutes);
    $label = normalize_label($label);
    $nextCheckAt = calculate_next_check($frequencyMinutes);

    try {
        $stmt = $pdo->prepare(
            'INSERT INTO monitored_urls (user_id, label, url, frequency_minutes, next_check_at)
             VALUES (:user_id, :label, :url, :frequency_minutes, :next_check_at)'
        );
        $stmt->execute([
            ':user_id' => $ownerId,
            ':label' => $label,
            ':url' => $url,
            ':frequency_minutes' => $frequencyMinutes,
            ':next_check_at' => $nextCheckAt,
        ]);
        return [true, null];
    } catch (\PDOException $e) {
        if ((int) $e->getCode() === 23000) {
            return [false, 'A monitor for that URL already exists for the selected user.'];
        }
        error_log('create_monitored_url failed: ' . $e->getMessage());
        return [false, 'Unable to create monitor. Please try again.'];
    }
}

/**
 * Update an existing monitored URL.
 *
 * @return array{0:bool,1:string|null}
 */
function update_monitored_url(\PDO $pdo, int $id, int $ownerId, string $url, ?string $label, int $frequencyMinutes): array
{
    [$valid, $message] = validate_url_input($url);
    if (!$valid) {
        return [false, $message];
    }

    $frequencyMinutes = normalize_frequency($frequencyMinutes);
    $label = normalize_label($label);

    try {
        $existing = get_monitored_url($pdo, $id);
        if ($existing === null) {
            return [false, 'Monitor not found.'];
        }

        $setNextCheckAt = $existing['next_check_at'] === null;
        $sql = 'UPDATE monitored_urls
             SET user_id = :user_id,
                 label = :label,
                 url = :url,
                 frequency_minutes = :frequency_minutes';

        if ($setNextCheckAt) {
            $sql .= ',
                 next_check_at = :next_check_at';
        }

        $sql .= '
             WHERE id = :id';

        $stmt = $pdo->prepare(
            $sql
        );
        $params = [
            ':user_id' => $ownerId,
            ':label' => $label,
            ':url' => $url,
            ':frequency_minutes' => $frequencyMinutes,
            ':id' => $id,
        ];

        if ($setNextCheckAt) {
            $params[':next_check_at'] = calculate_next_check($frequencyMinutes);
        }

        $stmt->execute($params);

        return [true, null];
    } catch (\PDOException $e) {
        if ((int) $e->getCode() === 23000) {
            return [false, 'Another monitor already uses that URL for the chosen user.'];
        }
        error_log('update_monitored_url failed: ' . $e->getMessage());
        return [false, 'Unable to update monitor. Please try again.'];
    }
}

/**
 * Delete a monitored URL.
 *
 * @return array{0:bool,1:string|null}
 */
function delete_monitored_url(\PDO $pdo, int $id): array
{
    try {
        $stmt = $pdo->prepare('DELETE FROM monitored_urls WHERE id = :id');
        $stmt->execute([':id' => $id]);

        if ($stmt->rowCount() === 0) {
            return [false, 'Monitor not found.'];
        }

        return [true, null];
    } catch (\PDOException $e) {
        error_log('delete_monitored_url failed: ' . $e->getMessage());
        return [false, 'Unable to delete monitor. Please try again.'];
    }
}

/**
 * Fetch registered users for admin dropdowns.
 *
 * @return array<int, array<string, mixed>>
 */
function list_users(\PDO $pdo): array
{
    $stmt = $pdo->query('SELECT id, email FROM users ORDER BY email ASC');

    return $stmt->fetchAll() ?: [];
}

/**
 * Schedule uptime checks for every monitor belonging to the specified user.
 */
function schedule_checks_for_user(\PDO $pdo, int $userId): int
{
    $stmt = $pdo->prepare(
        'UPDATE monitored_urls
         SET next_check_at = NOW(), in_progress = 0, updated_at = NOW()
         WHERE user_id = :user_id
           AND in_progress = 0'
    );
    $stmt->execute([':user_id' => $userId]);

    return (int) $stmt->rowCount();
}

/**
 * Schedule a single monitor to be checked.
 */
function schedule_monitor_check(\PDO $pdo, int $urlId): bool
{
    $stmt = $pdo->prepare(
        'UPDATE monitored_urls
         SET next_check_at = NOW(), in_progress = 0, updated_at = NOW()
         WHERE id = :id
           AND in_progress = 0'
    );
    $stmt->execute([':id' => $urlId]);

    return $stmt->rowCount() > 0;
}

/**
 * Fetch monitors ready to be checked for the external agent.
 *
 * @return array<int, array{id:int,url:string}>
 */
function fetch_checks_for_agent(\PDO $pdo, int $limit): array
{
    if ($limit <= 0) {
        return [];
    }

    try {
        $pdo->beginTransaction();

        $stmt = $pdo->prepare(
            'SELECT id, url
             FROM monitored_urls
             WHERE in_progress = 0
               AND (next_check_at IS NULL OR next_check_at <= NOW())
             ORDER BY next_check_at ASC
             LIMIT :limit
             FOR UPDATE SKIP LOCKED'
        );
        $stmt->bindValue(':limit', $limit, \PDO::PARAM_INT);
        $stmt->execute();
        $rows = $stmt->fetchAll();

        if (!$rows) {
            $pdo->commit();
            return [];
        }

        $update = $pdo->prepare(
            'UPDATE monitored_urls
             SET in_progress = 1, updated_at = NOW()
             WHERE id = :id'
        );

        foreach ($rows as $row) {
            $update->execute([':id' => $row['id']]);
        }

        $pdo->commit();

        return array_map(static function (array $row): array {
            return [
                'id' => (int) $row['id'],
                'url' => $row['url'],
            ];
        }, $rows);
    } catch (\Throwable $e) {
        if ($pdo->inTransaction()) {
            $pdo->rollBack();
        }
        throw $e;
    }
}

/**
 * Record a result payload received from the agent.
 */
function record_check_result(\PDO $pdo, array $payload): array
{
    $urlId = (int) ($payload['id'] ?? 0);
    if ($urlId <= 0) {
        throw new RuntimeException('Missing monitor id.');
    }

    $monitor = get_monitored_url($pdo, $urlId);
    if ($monitor === null) {
        throw new RuntimeException('Monitor not found for id ' . $urlId);
    }

    $httpCode = isset($payload['http_code']) ? (int) $payload['http_code'] : null;
    $error = isset($payload['error']) ? (string) $payload['error'] : null;
    $responseTime = null;
    if (isset($payload['response_time_ms'])) {
        $responseTime = $payload['response_time_ms'] !== null ? (float) $payload['response_time_ms'] : null;
    } elseif (isset($payload['response_time'])) {
        $responseTime = $payload['response_time'] !== null ? (float) $payload['response_time'] : null;
    }
    $checkedAt = $payload['checked_at'] ?? (new DateTimeImmutable())->format(\DateTimeInterface::ATOM);

    $status = determine_status_from_http($httpCode, $error);

    store_check_result($pdo, $urlId, [
        'http_code' => $httpCode,
        'error' => $error,
        'response_time_ms' => $responseTime,
        'checked_at' => $checkedAt,
    ]);

    update_next_check_at($pdo, $urlId, (int) $monitor['frequency_minutes']);
    set_monitor_in_progress($pdo, $urlId, false);

    return [
        'id' => $urlId,
        'url' => $monitor['url'],
        'status' => $status,
        'http_code' => $httpCode,
        'response_time' => $responseTime,
        'response_time_ms' => $responseTime,
        'checked_at' => $checkedAt,
        'error' => $error,
    ];
}

/**
 * Fetch recent uptime results for a given user.
 *
 * @param int $limit Number of results per monitor (default 5).
 *
 * @return array<int, array<string, mixed>>
 */
function fetch_recent_uptime_results(\PDO $pdo, int $userId, bool $isAdmin, int $limit = 5): array
{
    $whereClause = $isAdmin ? '' : 'AND mu.user_id = :user_id';

    $sql = <<<SQL
SELECT cr.id,
       mu.user_id,
       cr.monitored_url_id AS url_id,
       mu.url,
       mu.label,
       cr.http_code,
       cr.response_time_ms,
       cr.checked_at,
       cr.error_message,
       u.email AS owner_email
FROM check_results cr
INNER JOIN monitored_urls mu ON mu.id = cr.monitored_url_id
INNER JOIN users u ON u.id = mu.user_id
WHERE 1 = 1
$whereClause
ORDER BY cr.checked_at DESC
LIMIT :limit
SQL;

    $stmt = $pdo->prepare($sql);
    if ($isAdmin) {
        $stmt->bindValue(':limit', $limit, \PDO::PARAM_INT);
        $stmt->execute();
    } else {
        $stmt->bindValue(':user_id', $userId, \PDO::PARAM_INT);
        $stmt->bindValue(':limit', $limit, \PDO::PARAM_INT);
        $stmt->execute();
    }

    $rows = $stmt->fetchAll();

    if (!$rows) {
        return [];
    }

    return array_map(static function (array $row): array {
        $status = determine_status_from_http($row['http_code'], $row['error_message']);
        return $row + ['status' => $status];
    }, $rows);
}

function set_monitor_in_progress(\PDO $pdo, int $urlId, bool $inProgress): void
{
    $stmt = $pdo->prepare(
        'UPDATE monitored_urls
         SET in_progress = :in_progress, updated_at = NOW()
         WHERE id = :id'
    );
    $stmt->execute([
        ':in_progress' => $inProgress ? 1 : 0,
        ':id' => $urlId,
    ]);
}

function store_check_result(\PDO $pdo, int $urlId, array $result): void
{
    static $stmt = null;

    if ($stmt === null) {
        $stmt = $pdo->prepare(
            'INSERT INTO check_results (monitored_url_id, http_code, error_message, response_time_ms, checked_at)
             VALUES (:monitored_url_id, :http_code, :error_message, :response_time_ms, :checked_at)'
        );
    }

    $checkedAt = strtotime($result['checked_at'] ?? '') ?: time();

    $errorMessage = $result['error'] ?? null;
    if ($errorMessage !== null && trim((string) $errorMessage) === '') {
        $errorMessage = null;
    }

    $stmt->execute([
        ':monitored_url_id' => $urlId,
        ':http_code' => $result['http_code'],
        ':error_message' => $errorMessage,
        ':response_time_ms' => $result['response_time_ms'],
        ':checked_at' => date('Y-m-d H:i:s', $checkedAt),
    ]);
}

function determine_status_from_http(?int $httpCode, ?string $error): string
{
    if ($error !== null && trim($error) !== '') {
        return 'DOWN';
    }

    if ($httpCode === null) {
        return 'DOWN';
    }

    return ($httpCode >= 200 && $httpCode < 400) ? 'UP' : 'DOWN';
}


/**
 * Update the next scheduled check for a monitor.
 */
function update_next_check_at(\PDO $pdo, int $urlId, int $frequencyMinutes): void
{
    $stmt = $pdo->prepare(
        'UPDATE monitored_urls
         SET next_check_at = :next_check_at, updated_at = NOW()
         WHERE id = :id'
    );
    $stmt->execute([
        ':next_check_at' => calculate_next_check($frequencyMinutes),
        ':id' => $urlId,
    ]);
}

/**
 * Validate the URL input.
 *
 * @return array{0:bool,1:string|null}
 */
function validate_url_input(string $url): array
{
    $url = trim($url);

    if ($url === '') {
        return [false, 'URL is required.'];
    }

    if (!filter_var($url, FILTER_VALIDATE_URL)) {
        return [false, 'Please provide a valid URL (including https://).'];
    }

    if (strlen($url) > 255) {
        return [false, 'URL is too long (max 255 characters).'];
    }

    return [true, null];
}

function normalize_label(?string $label): ?string
{
    $label = trim((string) $label);

    return $label === '' ? null : $label;
}

function normalize_frequency(int $frequencyMinutes): int
{
    if ($frequencyMinutes < 1) {
        return 1;
    }

    if ($frequencyMinutes > 1440) {
        return 1440;
    }

    return $frequencyMinutes;
}

function calculate_next_check(int $frequencyMinutes): string
{
    return (new DateTimeImmutable())
        ->modify(sprintf('+%d minutes', normalize_frequency($frequencyMinutes)))
        ->format('Y-m-d H:i:s');
}

