## Simple PHP User Management

This project provides a minimal PHP user registration and login flow with password hashing, CSRF protection, and session management. It targets a classic LAMP stack (PHP 8.1+, MySQL 8+, Apache or Nginx).

### 1. Configure the database

1. Create the schema:
   ```bash
   mysql -u root -p < database/schema.sql
   ```
2. Create a dedicated MySQL user (optional but recommended):
   ```sql
   CREATE USER 'uptime_user'@'localhost' IDENTIFIED BY 'change_me';
   GRANT ALL PRIVILEGES ON uptime_user_management.* TO 'uptime_user'@'localhost';
   FLUSH PRIVILEGES;
   ```

### 2. Configure the application

1. Copy `includes/config.php` to `includes/config.local.php`.
2. Update the constants in `includes/config.local.php` with your database host, name, username, and password.

### 3. Serve the application

Point your web server’s document root to the `public/` directory (or run PHP’s built-in web server):

```bash
php -S localhost:8000 -t public
```

Then visit `http://localhost:8000` to register, log in, and test session persistence.

### Project structure

- `public/` – Front-end entry points (`login.php`, `register.php`, `dashboard.php`, `logout.php`, `styles.css`)
- `includes/` – Shared bootstrap, database, authentication, session, and URL helpers
- `database/schema.sql` – MySQL schema for the `users`, `roles`, and `user_roles` tables

### Managing roles

- New registrations automatically receive the `user` role (stored in `user_roles`).
- Promote a user to admin directly in MySQL:
  ```sql
  INSERT IGNORE INTO roles (name) VALUES ('admin');
  INSERT IGNORE INTO user_roles (user_id, role_id)
  SELECT u.id, r.id
  FROM users u
  JOIN roles r ON r.name = 'admin'
  WHERE u.email = 'you@example.com';
  ```
- Users can hold multiple roles simultaneously (e.g. both `user` and `admin`).
- Admins see an “administrator access” banner after logging in (additional admin-only features can be added later).

> Upgrading from the previous single `role` column? Run:
> ```sql
> ALTER TABLE users DROP COLUMN role;
> ```
> then rerun `database/schema.sql` (or create the new tables manually) and populate `user_roles` as needed.

### Monitoring URLs

- Every user can add, edit, and delete their own monitors at `/urls.php`.
- Admins see all monitors, can assign owners while creating/editing, and the list displays the owning email.
- Monitors live in the `monitored_urls` table; `database/schema.sql` seeds Mary with two URLs and Zookeeper (admin) with one default URL.
- Default seed passwords are `pass`. Change them in the database before deploying anywhere beyond local testing.

### Security notes

- Passwords are hashed using `password_hash()`/`password_verify()`.
- Forms include CSRF tokens stored in the session.
- Sessions are configured as HTTP only, secure (when served over HTTPS), and use the `SameSite=Lax` attribute.

