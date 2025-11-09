<?php
declare(strict_types=1);

/**
 * Generate or retrieve the CSRF token stored in the session.
 */
function csrf_token(): string
{
    if (empty($_SESSION['csrf_token'])) {
        $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
    }

    return $_SESSION['csrf_token'];
}

/**
 * Validate the CSRF token from the current request.
 */
function validate_csrf(?string $token): bool
{
    return is_string($token)
        && isset($_SESSION['csrf_token'])
        && hash_equals($_SESSION['csrf_token'], $token);
}

/**
 * Determine whether a user is logged in.
 */
function is_logged_in(): bool
{
    return isset($_SESSION['user_id'], $_SESSION['user_email']);
}

/**
 * Enforce that a user is logged in, otherwise redirect to the login page.
 */
function require_login(): void
{
    if (!is_logged_in()) {
        header('Location: login.php');
        exit;
    }
}

/**
 * Log out the current user.
 */
function logout_user(): void
{
    $_SESSION = [];
    if (ini_get('session.use_cookies')) {
        $params = session_get_cookie_params();
        setcookie(
            session_name(),
            '',
            (time() - 42000),
            $params['path'],
            $params['domain'],
            $params['secure'],
            $params['httponly']
        );
    }
    session_destroy();
}

/**
 * Retrieve the current user's roles from the session.
 */
function current_user_roles(): array
{
    $roles = $_SESSION['user_roles'] ?? [];

    return is_array($roles) ? $roles : [];
}

/**
 * Check whether the current user is an administrator.
 */
function is_admin(): bool
{
    return user_has_role('admin');
}

/**
 * Check whether the user has a specific role.
 */
function user_has_role(string $role): bool
{
    return in_array($role, current_user_roles(), true);
}

/**
 * Attempt to register a user. Returns [bool success, string|null error].
 */
function register_user(\PDO $pdo, string $email, string $password): array
{
    $email = trim(strtolower($email));

    if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
        return [false, 'Please enter a valid email address.'];
    }

    if (strlen($password) < 8) {
        return [false, 'Password must be at least 8 characters long.'];
    }

    try {
        $existing = find_user_by_email($pdo, $email);
        if ($existing !== null) {
            return [false, 'An account with that email already exists.'];
        }

        $pdo->beginTransaction();

        $hash = password_hash($password, PASSWORD_DEFAULT);
        $stmt = $pdo->prepare('INSERT INTO users (email, password_hash) VALUES (:email, :password_hash)');
        $stmt->execute([
            ':email' => $email,
            ':password_hash' => $hash,
        ]);

        $userId = (int) $pdo->lastInsertId();
        if (!assign_role($pdo, $userId, 'user')) {
            $pdo->rollBack();
            return [false, 'Registration failed. Please contact support.'];
        }

        $pdo->commit();

        return [true, null];
    } catch (\Throwable $e) {
        if ($pdo->inTransaction()) {
            $pdo->rollBack();
        }
        error_log('Registration failed: ' . $e->getMessage());
        return [false, 'Registration failed. Please try again.'];
    }
}

/**
 * Attempt to log in a user. Returns [bool success, string|null error].
 */
function login_user(\PDO $pdo, string $email, string $password): array
{
    $email = trim(strtolower($email));

    if ($email === '' || $password === '') {
        return [false, 'Email and password are required.'];
    }

    try {
        $user = find_user_by_email($pdo, $email);

        if ($user === null || !password_verify($password, $user['password_hash'])) {
            return [false, 'Invalid email or password.'];
        }

        $_SESSION['user_id'] = (int) $user['id'];
        $_SESSION['user_email'] = $user['email'];

        $roles = fetch_user_roles($pdo, (int) $user['id']);
        if (empty($roles)) {
            assign_role($pdo, (int) $user['id'], 'user');
            $roles = fetch_user_roles($pdo, (int) $user['id']);
        }
        $_SESSION['user_roles'] = $roles;

        return [true, null];
    } catch (\PDOException $e) {
        error_log('Login failed: ' . $e->getMessage());
        return [false, 'Login failed. Please try again.'];
    }
}

/**
 * Fetch a user by email address.
 */
function find_user_by_email(\PDO $pdo, string $email): ?array
{
    $stmt = $pdo->prepare('SELECT id, email, password_hash, created_at FROM users WHERE email = :email LIMIT 1');
    $stmt->execute([':email' => $email]);
    $user = $stmt->fetch();

    return $user === false ? null : $user;
}

/**
 * Fetch the distinct role names assigned to a user.
 */
function fetch_user_roles(\PDO $pdo, int $userId): array
{
    $stmt = $pdo->prepare(
        'SELECT r.name
         FROM user_roles ur
         INNER JOIN roles r ON r.id = ur.role_id
         WHERE ur.user_id = :user_id'
    );
    $stmt->execute([':user_id' => $userId]);

    return array_values(array_unique(array_column($stmt->fetchAll(), 'name')));
}

/**
 * Assign a role to a user. Returns false if the role name is unknown.
 */
function assign_role(\PDO $pdo, int $userId, string $roleName): bool
{
    $roleId = get_role_id($pdo, $roleName);
    if ($roleId === null) {
        error_log(sprintf('Role "%s" does not exist.', $roleName));
        return false;
    }

    $stmt = $pdo->prepare(
        'INSERT IGNORE INTO user_roles (user_id, role_id) VALUES (:user_id, :role_id)'
    );

    return $stmt->execute([
        ':user_id' => $userId,
        ':role_id' => $roleId,
    ]);
}

/**
 * Resolve a role name to its identifier.
 */
function get_role_id(\PDO $pdo, string $roleName): ?int
{
    $stmt = $pdo->prepare('SELECT id FROM roles WHERE name = :name LIMIT 1');
    $stmt->execute([':name' => $roleName]);
    $role = $stmt->fetch();

    return $role === false ? null : (int) $role['id'];
}

