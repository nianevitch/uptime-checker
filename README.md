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
- `includes/` – Shared bootstrap, database, authentication, and session helpers
- `database/schema.sql` – MySQL schema for the `users` table

### Security notes

- Passwords are hashed using `password_hash()`/`password_verify()`.
- Forms include CSRF tokens stored in the session.
- Sessions are configured as HTTP only, secure (when served over HTTPS), and use the `SameSite=Lax` attribute.

