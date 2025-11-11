<?php
declare(strict_types=1);

require_once __DIR__ . '/../../../includes/bootstrap.php';

header('Content-Type: text/plain; charset=utf-8');

$maxCount = 20;
$requestedCount = filter_input(INPUT_GET, 'count', FILTER_VALIDATE_INT, [
    'options' => ['min_range' => 1, 'max_range' => $maxCount],
]);
$count = $requestedCount !== false && $requestedCount !== null ? $requestedCount : 1;
$logFile = 'agent.log';

$baseUrl = resolve_base_url();
$log = [];
$log[] = sprintf('[%s] Agent starting (count=%d)', date('c'), $count);
app_log('Agent run started', ['count' => $count], $logFile);

try {
    $fetchUrl = sprintf('%s/api/check/index.php?count=%d', $baseUrl, $count);
    $log[] = 'Fetching jobs: ' . $fetchUrl;
    app_log('Agent fetching jobs', ['url' => $fetchUrl], $logFile);
    $jobs = http_get_json($fetchUrl);
    $total = is_countable($jobs) ? count($jobs) : 0;
    $log[] = sprintf('Received %d job%s.', $total, $total === 1 ? '' : 's');
    app_log('Agent received jobs', ['count' => $total], $logFile);

    if ($total === 0) {
        app_log('Agent exiting (no jobs)', [], $logFile);
        output_log($log);
        exit;
    }

    foreach ($jobs as $job) {
        if (!isset($job['id'], $job['url'])) {
            $log[] = 'Skipping malformed job payload: ' . json_encode($job);
            app_log('Agent skipped malformed job', ['job' => $job], $logFile);
            continue;
        }

        $id = (int) $job['id'];
        $url = (string) $job['url'];
        $log[] = sprintf('→ Checking #%d %s', $id, $url);
        app_log('Agent checking monitor', ['id' => $id, 'url' => $url], $logFile);

        $result = run_single_check($url);
        $log[] = sprintf(
            '   Result: http=%s, time=%s ms%s',
            $result['http_code'] === null ? 'N/A' : (string) $result['http_code'],
            $result['response_time_ms'] !== null ? number_format((float) $result['response_time_ms'], 2) : 'N/A',
            $result['error'] !== null ? ', error=' . $result['error'] : ''
        );
        app_log('Agent check complete', [
            'id' => $id,
            'http_code' => $result['http_code'],
            'response_time_ms' => $result['response_time_ms'],
            'error' => $result['error'],
        ], $logFile);

        $payload = [
            'id' => $id,
            'http_code' => $result['http_code'],
            'response_time_ms' => $result['response_time_ms'],
            'checked_at' => $result['checked_at'],
        ];
        if ($result['error'] !== null) {
            $payload['error'] = $result['error'];
        }

        try {
            $postUrl = sprintf('%s/api/check/index.php', $baseUrl);
            $apiResponse = http_post_json($postUrl, $payload);
            $log[] = '   Posted result: ' . json_encode($apiResponse);
            app_log('Agent posted result', ['id' => $id], $logFile);
        } catch (Throwable $postError) {
            $log[] = '   Failed to post result: ' . $postError->getMessage();
            app_log('Agent failed to post result', [
                'id' => $id,
                'error' => $postError->getMessage(),
            ], $logFile);
        }
    }
} catch (Throwable $e) {
    $log[] = 'Agent failed: ' . $e->getMessage();
    app_log('Agent run failed', ['error' => $e->getMessage()], $logFile);
}

app_log('Agent run completed', [], $logFile);
output_log($log);

/**
 * Run an HTTP check against a URL.
 *
 * @return array{http_code:int|null,response_time_ms:float|null,error:string|null,checked_at:string}
 */
function run_single_check(string $url): array
{
    $ch = curl_init($url);
    if ($ch === false) {
        return [
            'http_code' => null,
            'response_time_ms' => null,
            'error' => 'Unable to initialise cURL.',
            'checked_at' => (new DateTimeImmutable())->format(DateTimeInterface::ATOM),
        ];
    }

    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HEADER => false,
        CURLOPT_FOLLOWLOCATION => true,
        CURLOPT_CONNECTTIMEOUT => 10,
        CURLOPT_TIMEOUT => 30,
        CURLOPT_USERAGENT => 'UptimeCheckerAgent/1.0',
        CURLOPT_NOBODY => false,
    ]);

    $start = microtime(true);
    $body = curl_exec($ch);
    $durationMs = (microtime(true) - $start) * 1000;

    $httpCode = curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
    $error = null;
    if ($body === false) {
        $error = curl_error($ch) ?: 'Unknown error';
        $httpCode = curl_errno($ch) === CURLE_OPERATION_TIMEDOUT ? null : $httpCode;
    }
    curl_close($ch);

    return [
        'http_code' => $httpCode > 0 ? $httpCode : null,
        'response_time_ms' => round($durationMs, 2),
        'error' => $error,
        'checked_at' => (new DateTimeImmutable())->format(DateTimeInterface::ATOM),
    ];
}

/**
 * Perform a GET request expecting JSON.
 *
 * @return array<int, mixed>
 */
function http_get_json(string $url): array
{
    $ch = curl_init($url);
    if ($ch === false) {
        throw new RuntimeException('Unable to initialise cURL.');
    }

    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CONNECTTIMEOUT => 10,
        CURLOPT_TIMEOUT => 30,
        CURLOPT_FOLLOWLOCATION => true,
        CURLOPT_USERAGENT => 'UptimeCheckerAgent/1.0',
    ]);

    $response = curl_exec($ch);
    if ($response === false) {
        $message = curl_error($ch) ?: 'Unknown cURL error';
        curl_close($ch);
        throw new RuntimeException('GET ' . $url . ' failed: ' . $message);
    }

    $status = curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
    curl_close($ch);

    if ($status >= 400) {
        throw new RuntimeException(sprintf('GET %s returned HTTP %d: %s', $url, $status, $response));
    }

    $data = json_decode($response, true);
    if (!is_array($data)) {
        throw new RuntimeException('Invalid JSON response from ' . $url);
    }

    return $data;
}

/**
 * Perform a POST request with JSON body.
 *
 * @param array<string,mixed> $payload
 *
 * @return array<string,mixed>
 */
function http_post_json(string $url, array $payload): array
{
    $ch = curl_init($url);
    if ($ch === false) {
        throw new RuntimeException('Unable to initialise cURL.');
    }

    $encoded = json_encode($payload);
    if ($encoded === false) {
        throw new RuntimeException('Failed to encode payload.');
    }

    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_POST => true,
        CURLOPT_HTTPHEADER => [
            'Content-Type: application/json',
            'Accept: application/json',
        ],
        CURLOPT_POSTFIELDS => $encoded,
        CURLOPT_CONNECTTIMEOUT => 10,
        CURLOPT_TIMEOUT => 30,
        CURLOPT_USERAGENT => 'UptimeCheckerAgent/1.0',
    ]);

    $response = curl_exec($ch);
    if ($response === false) {
        $message = curl_error($ch) ?: 'Unknown cURL error';
        curl_close($ch);
        throw new RuntimeException('POST ' . $url . ' failed: ' . $message);
    }

    $status = curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
    curl_close($ch);

    if ($status >= 400) {
        throw new RuntimeException(sprintf('POST %s returned HTTP %d: %s', $url, $status, $response));
    }

    $data = json_decode($response, true);
    if (!is_array($data)) {
        throw new RuntimeException('Invalid JSON response from ' . $url);
    }

    return $data;
}

/**
 * Build a base URL using the current request.
 */
function resolve_base_url(): string
{
    $configured = getenv('AGENT_BASE_URL');
    if (is_string($configured) && trim($configured) !== '') {
        return rtrim($configured, '/');
    }

    $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
    $host = $_SERVER['HTTP_HOST'] ?? 'localhost';

    return sprintf('%s://%s', $scheme, $host);
}

/**
 * Output the agent log.
 *
 * @param array<int,string> $lines
 */
function output_log(array $lines): void
{
    echo implode(PHP_EOL, $lines), PHP_EOL;
}

