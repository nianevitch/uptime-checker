<?php
declare(strict_types=1);

require_once __DIR__ . '/../includes/bootstrap.php';

if (is_logged_in()) {
    header('Location: /urls.php');
    exit;
}

$email = '';
$error = null;
$success = false;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $email = trim((string) ($_POST['email'] ?? ''));
    $password = (string) ($_POST['password'] ?? '');
    $confirm = (string) ($_POST['confirm_password'] ?? '');
    $token = $_POST['csrf_token'] ?? null;

    if (!validate_csrf(is_string($token) ? $token : null)) {
        $error = 'Invalid session. Please try again.';
    } elseif ($password !== $confirm) {
        $error = 'Passwords do not match.';
    } else {
        [$success, $message] = register_user(get_pdo(), $email, $password);
        if ($success) {
            $success = true;
        } else {
            $error = $message;
        }
    }
}

function esc(string $value): string
{
    return htmlspecialchars($value, ENT_QUOTES, 'UTF-8');
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Register</title>
    <link rel="stylesheet" href="styles.css">
</head>
<body>
    <main class="container">
        <h1>Create an account</h1>

        <?php if ($error !== null): ?>
            <div class="alert alert-error"><?= esc($error) ?></div>
        <?php elseif ($success): ?>
            <div class="alert alert-success">
                Account created. <a href="login.php">Log in now</a>.
            </div>
        <?php endif; ?>

        <?php if (!$success): ?>
        <form method="post" novalidate>
            <input type="hidden" name="csrf_token" value="<?= esc(csrf_token()) ?>">
            <div class="field">
                <label for="email">Email</label>
                <input type="email" id="email" name="email" required value="<?= esc($email) ?>">
            </div>
            <div class="field">
                <label for="password">Password</label>
                <input type="password" id="password" name="password" required minlength="8">
            </div>
            <div class="field">
                <label for="confirm_password">Confirm Password</label>
                <input type="password" id="confirm_password" name="confirm_password" required minlength="8">
            </div>
            <div class="actions">
                <button type="submit">Register</button>
                <a href="login.php">Already have an account?</a>
            </div>
        </form>
        <?php endif; ?>
    </main>
</body>
</html>
