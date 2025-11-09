<?php
declare(strict_types=1);

require_once __DIR__ . '/../includes/bootstrap.php';

require_login();

$pdo = get_pdo();
$stmt = $pdo->prepare('SELECT email, created_at FROM users WHERE id = :id LIMIT 1');
$stmt->execute([':id' => $_SESSION['user_id']]);
$user = $stmt->fetch();

if ($user === false) {
    logout_user();
    header('Location: login.php');
    exit;
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
    <title>Dashboard</title>
    <link rel="stylesheet" href="styles.css">
</head>
<body>
    <main class="container">
        <h1>Welcome</h1>
        <p>You are logged in as <strong><?= esc($user['email']) ?></strong>.</p>
        <p>Account created on <?= esc((new DateTimeImmutable($user['created_at']))->format('F j, Y g:i a')) ?>.</p>

        <form action="logout.php" method="post">
            <input type="hidden" name="csrf_token" value="<?= esc(csrf_token()) ?>">
            <button type="submit">Log out</button>
        </form>
    </main>
</body>
</html>

