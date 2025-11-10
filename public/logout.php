<?php
declare(strict_types=1);

require_once __DIR__ . '/../includes/bootstrap.php';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $token = $_POST['csrf_token'] ?? null;
    if (validate_csrf(is_string($token) ? $token : null)) {
        logout_user();
    }
}

header('Location: /login.php');
exit;

