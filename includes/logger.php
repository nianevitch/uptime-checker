<?php
declare(strict_types=1);

const APP_LOG_FILENAME = 'app.log';

/**
 * Write a message to the application log.
 *
 * @param string $message  Log message.
 * @param array<string, mixed> $context Optional structured context.
 * @param string $fileName Custom log filename (defaults to APP_LOG_FILENAME).
 */
function app_log(string $message, array $context = [], string $fileName = APP_LOG_FILENAME): void
{
    static $logPaths = [];

    $fileName = basename($fileName);

    if (!isset($logPaths[$fileName])) {
        $baseDir = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'logs';

        if (!is_dir($baseDir) && !@mkdir($baseDir, 0775, true) && !is_dir($baseDir)) {
            error_log('[uptime-checker] Unable to create log directory: ' . $baseDir);
            return;
        }

        if (!is_writable($baseDir)) {
            error_log('[uptime-checker] Log directory not writable: ' . $baseDir);
            return;
        }

        $logPaths[$fileName] = $baseDir . DIRECTORY_SEPARATOR . $fileName;
    }

    $timestamp = (new DateTimeImmutable())->format(DateTimeInterface::ATOM);
    $entry = sprintf('[%s] %s', $timestamp, $message);

    if ($context !== []) {
        $json = json_encode($context, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        if ($json !== false) {
            $entry .= ' ' . $json;
        }
    }

    file_put_contents($logPaths[$fileName], $entry . PHP_EOL, FILE_APPEND | LOCK_EX);
}

