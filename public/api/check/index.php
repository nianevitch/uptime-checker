<?php
declare(strict_types=1);

require_once __DIR__ . '/../../../includes/bootstrap.php';

$pdo = get_pdo();
$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';

if ($method === 'GET') {
    header('Content-Type: application/json');
    try {
        $count = extract_count_from_uri($_SERVER['REQUEST_URI'] ?? '');
        $jobs = fetch_checks_for_agent($pdo, $count);
        echo json_encode($jobs);
    } catch (Throwable $e) {
        http_response_code(500);
        echo json_encode(['error' => 'Failed to fetch checks.', 'details' => $e->getMessage()]);
    }
    exit;
}

if ($method === 'POST') {
    $body = file_get_contents('php://input');
    $payload = json_decode($body ?? '', true);

    if (!is_array($payload)) {
        http_response_code(400);
        header('Content-Type: application/json');
        echo json_encode(['error' => 'Invalid JSON payload.']);
        exit;
    }

    try {
        $result = record_check_result($pdo, $payload);
        header('Content-Type: application/json');
        echo json_encode($result);
    } catch (RuntimeException $e) {
        http_response_code(400);
        header('Content-Type: application/json');
        echo json_encode(['error' => $e->getMessage()]);
    } catch (Throwable $e) {
        http_response_code(500);
        header('Content-Type: application/json');
        echo json_encode(['error' => 'Failed to store result.', 'details' => $e->getMessage()]);
    }
    exit;
}

http_response_code(405);
header('Content-Type: application/json');
echo json_encode(['error' => 'Method not allowed']);

function extract_count_from_uri(string $uri): int
{
    $path = parse_url($uri, PHP_URL_PATH) ?? '';
    $segments = array_values(array_filter(explode('/', trim($path, '/'))));

    $count = 1;
    if (!empty($segments)) {
        $last = $segments[count($segments) - 1];
        if (is_numeric($last)) {
            $count = max(1, min(100, (int) $last));
        }
    }

    return $count;
}

