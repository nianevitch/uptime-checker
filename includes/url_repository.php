<?php
declare(strict_types=1);

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
function create_monitored_url(\PDO $pdo, int $ownerId, string $url, ?string $label): array
{
    [$valid, $message] = validate_url_input($url);
    if (!$valid) {
        return [false, $message];
    }

    $label = normalize_label($label);

    try {
        $stmt = $pdo->prepare(
            'INSERT INTO monitored_urls (user_id, label, url) VALUES (:user_id, :label, :url)'
        );
        $stmt->execute([
            ':user_id' => $ownerId,
            ':label' => $label,
            ':url' => $url,
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
function update_monitored_url(\PDO $pdo, int $id, int $ownerId, string $url, ?string $label): array
{
    [$valid, $message] = validate_url_input($url);
    if (!$valid) {
        return [false, $message];
    }

    $label = normalize_label($label);

    try {
        $stmt = $pdo->prepare(
            'UPDATE monitored_urls
             SET user_id = :user_id,
                 label = :label,
                 url = :url
             WHERE id = :id'
        );
        $stmt->execute([
            ':user_id' => $ownerId,
            ':label' => $label,
            ':url' => $url,
            ':id' => $id,
        ]);

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

/**
 * Perform a simple uptime check against a URL using cURL.
 *
 * @return array{
 *   status:string,
 *   http_code:int|null,
 *   response_time:float|null,
 *   checked_at:string,
 *   error:string|null
 * }
 */
function check_url_uptime(string $url): array
{
    $url = trim($url);
    $start = microtime(true);
    $httpCode = null;
    $status = 'DOWN';
    $responseTime = null;
    $error = null;

    $ch = curl_init($url);
    if ($ch === false) {
        return [
            'status' => $status,
            'http_code' => $httpCode,
            'response_time' => $responseTime,
            'checked_at' => (new DateTimeImmutable())->format(DateTimeInterface::ATOM),
            'error' => 'Unable to initialize request.',
        ];
    }

    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_NOBODY => true,
        CURLOPT_FOLLOWLOCATION => true,
        CURLOPT_TIMEOUT => 10,
        CURLOPT_CONNECTTIMEOUT => 5,
        CURLOPT_USERAGENT => 'UptimeChecker/1.0',
        CURLOPT_SSL_VERIFYPEER => true,
        CURLOPT_SSL_VERIFYHOST => 2,
    ]);

    curl_exec($ch);
    $curlErrno = curl_errno($ch);
    if ($curlErrno === 0) {
        $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $end = microtime(true);
        $responseTime = round(($end - $start) * 1000, 2); // milliseconds
        if ($httpCode >= 200 && $httpCode < 400) {
            $status = 'UP';
        }
    } else {
        $errorMessage = curl_error($ch);
        $error = $errorMessage !== '' ? $errorMessage : sprintf('Request failed (code %d).', $curlErrno);
    }

    curl_close($ch);

    return [
        'status' => $status,
        'http_code' => $httpCode,
        'response_time' => $responseTime,
        'checked_at' => (new DateTimeImmutable())->format(DateTimeInterface::ATOM),
        'error' => $error,
    ];
}

