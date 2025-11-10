<?php
declare(strict_types=1);

require_once __DIR__ . '/../includes/bootstrap.php';

if (is_logged_in()) {
    header('Location: /urls.php');
} else {
    header('Location: /login.php');
}
exit;

