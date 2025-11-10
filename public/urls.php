<?php
declare(strict_types=1);

require_once __DIR__ . '/../includes/bootstrap.php';

require_login();

$pdo = get_pdo();
$currentUserId = (int) $_SESSION['user_id'];
$isAdmin = is_admin();

$errors = [];
$success = null;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $token = $_POST['csrf_token'] ?? null;
    if (!validate_csrf(is_string($token) ? $token : null)) {
        $errors[] = 'Invalid session. Please try again.';
    } else {
        $action = $_POST['action'] ?? '';

        if ($action === 'create') {
            $ownerId = $isAdmin ? (int) ($_POST['owner_id'] ?? 0) : $currentUserId;
            $url = (string) ($_POST['url'] ?? '');
            $label = isset($_POST['label']) ? (string) $_POST['label'] : null;
            $frequency = (int) ($_POST['frequency_minutes'] ?? 5);

            if ($ownerId <= 0) {
                $errors[] = 'Please select a valid owner for the URL.';
            } else {
                [$ok, $message] = create_monitored_url($pdo, $ownerId, $url, $label, $frequency);
                if ($ok) {
                    $success = 'Monitor created.';
                } else {
                    $errors[] = $message ?? 'Failed to create monitor.';
                }
            }
        } elseif ($action === 'update') {
            $id = (int) ($_POST['id'] ?? 0);
            $urlRow = $id > 0 ? get_monitored_url($pdo, $id) : null;

            if ($urlRow === null) {
                $errors[] = 'Monitor not found.';
            } elseif (!can_modify_url($urlRow, $currentUserId, $isAdmin)) {
                $errors[] = 'You are not allowed to edit that monitor.';
            } else {
                $ownerId = $isAdmin ? (int) ($_POST['owner_id'] ?? $urlRow['user_id']) : (int) $urlRow['user_id'];
                $url = (string) ($_POST['url'] ?? '');
                $label = isset($_POST['label']) ? (string) $_POST['label'] : null;
                $frequency = (int) ($_POST['frequency_minutes'] ?? ($urlRow['frequency_minutes'] ?? 5));

                [$ok, $message] = update_monitored_url($pdo, $id, $ownerId, $url, $label, $frequency);
                if ($ok) {
                    $success = 'Monitor updated.';
                } else {
                    $errors[] = $message ?? 'Failed to update monitor.';
                }
            }
        } elseif ($action === 'delete') {
            $id = (int) ($_POST['id'] ?? 0);
            $urlRow = $id > 0 ? get_monitored_url($pdo, $id) : null;

            if ($urlRow === null) {
                $errors[] = 'Monitor not found.';
            } elseif (!can_modify_url($urlRow, $currentUserId, $isAdmin)) {
                $errors[] = 'You are not allowed to delete that monitor.';
            } else {
                [$ok, $message] = delete_monitored_url($pdo, $id);
                if ($ok) {
                    $success = 'Monitor deleted.';
                } else {
                    $errors[] = $message ?? 'Failed to delete monitor.';
                }
            }
        } elseif ($action === 'check') {
            $id = (int) ($_POST['id'] ?? 0);
            $urlRow = $id > 0 ? get_monitored_url($pdo, $id) : null;

            if ($urlRow === null) {
                $errors[] = 'Monitor not found.';
            } elseif (!can_modify_url($urlRow, $currentUserId, $isAdmin)) {
                $errors[] = 'You are not allowed to check that monitor.';
            } else {
                if (schedule_monitor_check($pdo, (int) $urlRow['id'])) {
                    $success = 'Check scheduled. Results will appear after the agent reports back.';
                } else {
                    $errors[] = 'Monitor is already being processed or unavailable.';
                }
            }
        } elseif ($action === 'run_all') {
            $targetUserId = $isAdmin ? (int) ($_POST['target_user_id'] ?? 0) : $currentUserId;

            if ($targetUserId <= 0) {
                $errors[] = 'Please select a user to run checks for.';
            } elseif (!$isAdmin && $targetUserId !== $currentUserId) {
                $errors[] = 'You can only run checks for your own monitors.';
            } else {
                $scheduled = schedule_checks_for_user($pdo, $targetUserId);
                if ($scheduled === 0) {
                    $success = 'No monitors available to schedule right now.';
                } else {
                    $success = sprintf('Scheduled %d monitor check(s).', $scheduled);
                }
            }
        } else {
            $errors[] = 'Unknown action.';
        }
    }
}

$editId = isset($_GET['edit']) ? (int) $_GET['edit'] : null;
$editMonitor = null;
if ($editId) {
    $candidate = get_monitored_url($pdo, $editId);
    if ($candidate === null || !can_modify_url($candidate, $currentUserId, $isAdmin)) {
        $errors[] = 'You are not allowed to edit that monitor.';
    } else {
        $editMonitor = $candidate;
    }
}

$monitors = get_monitored_urls($pdo, $currentUserId, $isAdmin);
$users = $isAdmin ? list_users($pdo) : [];
$selectedOwnerId = $editMonitor
    ? (int) $editMonitor['user_id']
    : ($isAdmin ? (int) ($_POST['owner_id'] ?? 0) : $currentUserId);
$formLabel = $editMonitor['label'] ?? (string) ($_POST['label'] ?? '');
$formUrl = $editMonitor['url'] ?? (string) ($_POST['url'] ?? '');
$formFrequency = isset($editMonitor['frequency_minutes'])
    ? (string) $editMonitor['frequency_minutes']
    : (isset($_POST['frequency_minutes']) ? (string) $_POST['frequency_minutes'] : '5');
$recentResults = fetch_recent_uptime_results($pdo, $currentUserId, $isAdmin, 10);

function esc(string $value): string
{
    return htmlspecialchars($value, ENT_QUOTES, 'UTF-8');
}

function selected(int $left, int $right): string
{
    return $left === $right ? 'selected' : '';
}

function format_datetime(?string $value): string
{
    if (!is_string($value) || trim($value) === '') {
        return '—';
    }

    $timestamp = strtotime($value);

    if ($timestamp === false) {
        return '—';
    }

    return date('Y-m-d H:i', $timestamp);
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Monitored URLs</title>
    <link rel="stylesheet" href="styles.css">
</head>
<body>
    <main class="container">
        <h1>Monitored URLs</h1>

        <p><a class="link-button" href="dashboard.php">&larr; Back to dashboard</a></p>

        <?php if (!empty($errors)): ?>
            <div class="alert alert-error">
                <ul class="list-unstyled">
                <?php foreach ($errors as $error): ?>
                    <li><?= esc($error) ?></li>
                <?php endforeach; ?>
                </ul>
            </div>
        <?php elseif ($success !== null): ?>
            <div class="alert alert-success"><?= esc($success) ?></div>
        <?php endif; ?>

        <section class="card">
            <h2><?= $editMonitor ? 'Edit monitor' : 'Add a monitor' ?></h2>
            <form method="post" class="stacked-form" novalidate>
                <input type="hidden" name="csrf_token" value="<?= esc(csrf_token()) ?>">
                <input type="hidden" name="action" value="<?= $editMonitor ? 'update' : 'create' ?>">
                <?php if ($editMonitor): ?>
                    <input type="hidden" name="id" value="<?= esc((string) $editMonitor['id']) ?>">
                <?php endif; ?>

                <?php if ($isAdmin): ?>
                    <div class="field">
                        <label for="owner_id">Owner</label>
                        <select id="owner_id" name="owner_id" required>
                            <option value="">Select a user</option>
                            <?php foreach ($users as $user): ?>
                                <option value="<?= esc((string) $user['id']) ?>" <?= selected((int) $user['id'], $selectedOwnerId) ?>>
                                    <?= esc($user['email']) ?>
                                </option>
                            <?php endforeach; ?>
                        </select>
                    </div>
                <?php endif; ?>

                <div class="field">
                    <label for="label">Label (optional)</label>
                    <input type="text" id="label" name="label" maxlength="190"
                           value="<?= esc($formLabel) ?>">
                </div>

                <div class="field">
                    <label for="url">URL</label>
                    <input type="url" id="url" name="url" required maxlength="255"
                           value="<?= esc($formUrl) ?>">
                </div>

                <div class="field">
                    <label for="frequency_minutes">Frequency (minutes)</label>
                    <input type="number" id="frequency_minutes" name="frequency_minutes" min="1" max="1440" required
                           value="<?= esc((string) $formFrequency) ?>">
                </div>

                <div class="actions">
                    <button type="submit"><?= $editMonitor ? 'Update monitor' : 'Add monitor' ?></button>
                    <?php if ($editMonitor): ?>
                        <a href="urls.php" class="link">Cancel</a>
                    <?php endif; ?>
                </div>
            </form>
        </section>

        <section class="card">
            <h2>Existing monitors</h2>
            <form method="post" class="stacked-form">
                <input type="hidden" name="csrf_token" value="<?= esc(csrf_token()) ?>">
                <input type="hidden" name="action" value="run_all">
                <?php if ($isAdmin): ?>
                    <div class="field">
                        <label for="target_user_id">Run checks for user</label>
                        <select name="target_user_id" id="target_user_id">
                            <option value="">Select user</option>
                            <?php foreach ($users as $user): ?>
                                <option value="<?= esc((string) $user['id']) ?>"><?= esc($user['email']) ?></option>
                            <?php endforeach; ?>
                        </select>
                    </div>
                <?php endif; ?>
                <button type="submit">Run checks</button>
                <p class="form-help">Checks are handed to the agent; results appear once processed.</p>
            </form>
            <?php if (empty($monitors)): ?>
                <p>No monitors yet.</p>
            <?php else: ?>
                <div class="table-wrapper">
                    <table class="table">
                        <thead>
                            <tr>
                                <th>Label</th>
                                <th>URL</th>
                                <th>Frequency (min)</th>
                                <th>In progress</th>
                                <th>Next check</th>
                                <?php if ($isAdmin): ?>
                                    <th>Owner</th>
                                <?php endif; ?>
                                <th>Updated</th>
                                <th></th>
                            </tr>
                        </thead>
                        <tbody>
                        <?php foreach ($monitors as $monitor): ?>
                            <tr>
                                <td><?= esc($monitor['label'] ?? '—') ?></td>
                                <td><a href="<?= esc($monitor['url']) ?>" target="_blank" rel="noopener"><?= esc($monitor['url']) ?></a></td>
                                <td><?= esc((string) ($monitor['frequency_minutes'] ?? '—')) ?></td>
                                <td><?= esc(!empty($monitor['in_progress']) ? 'Yes' : 'No') ?></td>
                                <td><?= esc(format_datetime($monitor['next_check_at'] ?? null)) ?></td>
                                <?php if ($isAdmin): ?>
                                    <td><?= esc($monitor['owner_email']) ?></td>
                                <?php endif; ?>
                                <td><?= esc((new DateTimeImmutable($monitor['updated_at']))->format('Y-m-d H:i')) ?></td>
                                <td class="table-actions">
                                    <a class="link" href="urls.php?edit=<?= esc((string) $monitor['id']) ?>">Edit</a>
                                    <form method="post" class="inline-form" onsubmit="return confirm('Delete this monitor?');">
                                        <input type="hidden" name="csrf_token" value="<?= esc(csrf_token()) ?>">
                                        <input type="hidden" name="action" value="delete">
                                        <input type="hidden" name="id" value="<?= esc((string) $monitor['id']) ?>">
                                        <button type="submit" class="link link-danger">Delete</button>
                                    </form>
                                </td>
                            </tr>
                        <?php endforeach; ?>
                        </tbody>
                    </table>
                </div>
            <?php endif; ?>
        </section>

        <section class="card">
            <h2>Recent checks</h2>
            <?php if (empty($recentResults)): ?>
                <p>No checks recorded yet.</p>
            <?php else: ?>
                <div class="table-wrapper">
                    <table class="table">
                        <thead>
                            <tr>
                                <th>URL</th>
                                <?php if ($isAdmin): ?>
                                    <th>Owner</th>
                                <?php endif; ?>
                                <th>Status</th>
                                <th>HTTP code</th>
                                <th>Error</th>
                                <th>Response time</th>
                                <th>Checked at</th>
                            </tr>
                        </thead>
                        <tbody>
                        <?php foreach ($recentResults as $row): ?>
                            <tr>
                                <td><?= esc($row['url']) ?></td>
                                <?php if ($isAdmin): ?>
                                    <td><?= esc($row['owner_email']) ?></td>
                                <?php endif; ?>
                                <td><?= esc($row['status']) ?></td>
                                <td><?= esc((string) ($row['http_code'] ?? 'N/A')) ?></td>
                                <td><?= esc($row['error_message'] ?? '—') ?></td>
                                <td><?= esc($row['response_time_ms'] !== null ? $row['response_time_ms'] . ' ms' : 'N/A') ?></td>
                                <td><?= esc((new DateTimeImmutable($row['checked_at']))->format('Y-m-d H:i:s')) ?></td>
                            </tr>
                        <?php endforeach; ?>
                        </tbody>
                    </table>
                </div>
            <?php endif; ?>
        </section>
    </main>
</body>
</html>

