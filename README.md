## Uptime Checker – Spring Boot + Angular rewrite

This repository now hosts a full-stack uptime monitoring system composed of:

- **Spring Boot (Java 21)** backend with Spring Security, JWT authentication, and REST APIs.
- **Angular 17** single-page frontend consuming the REST endpoints.
- **MySQL 8** persistence layer reusing the existing schema (`database/schema.sql`).

Legacy PHP code has been superseded by the new services. Use the instructions below to develop locally or run the stack with Docker.

---

### Project layout

- `backend/` – Spring Boot application (REST APIs, security, persistence).
- `frontend/` – Angular SPA (login flow, ping management UI).
- `database/schema.sql` – canonical MySQL schema and seed data (users, roles, pings, checks).
- `docker-compose.yml` – local development stack (MySQL, backend, Angular dev server).

---

### Prerequisites

- Java 21+
- Maven 3.9+
- Node.js 20+ (Angular CLI 17+)
- MySQL 8 (if running services outside Docker)

or simply use Docker Desktop and `docker compose`.

---

### Running with Docker Compose

1. Copy `.env.example` (if provided) or export the following variables as needed. Defaults are baked into `docker-compose.yml` (use a JWT secret ≥ 32 characters):
   - `MYSQL_ROOT_PASSWORD`
   - `MYSQL_DATABASE`
   - `MYSQL_USER`
   - `MYSQL_PASSWORD`
   - `JWT_SECRET`

2. Start the stack:
   ```bash
   docker compose up --build
   ```

3. Services:
   - Angular SPA: <http://localhost:4200>
   - Spring Boot API: <http://localhost:8080>
   - MySQL: `localhost:3307` (mapped to container `3306`)

The compose stack mounts source folders for live development:
   - Angular is served via `ng serve` with hot reload.
   - Spring Boot runs via `mvn spring-boot:run` with sources mounted; Maven dependencies are cached in a named volume.

Stop the stack using `docker compose down` (add `-v` to drop MySQL data).

---

### Running locally without Docker

1. **Database**
   ```bash
   mysql -u root -p < database/schema.sql
   ```
   Adjust credentials as required and update `backend/src/main/resources/application.yml` (`DB_HOST`, etc.).

2. **Backend**
   ```bash
   cd backend
   mvn spring-boot:run
   ```

3. **Frontend**
   ```bash
   cd frontend
   npm install
   npm start    # runs ng serve --host 0.0.0.0 --port 4200
   ```

The Angular dev server proxies directly to the backend via relative URLs (`/api/...`).

---

### Authentication & security

- Users authenticate via `/api/auth/login` with email + password.
- Successful logins return a JWT; Angular stores it in `localStorage` and attaches it via an HTTP interceptor.
- Logout simply discards the token (`/api/auth/logout` endpoint exists for symmetry).
- Registration (`/api/auth/register`) issues a JWT on success. New accounts receive the `user` role; seed data includes an admin user.
- Spring Security enforces stateless JWT auth, with CORS allowing Angular dev origins (`http://localhost:4200`).

---

### API summary

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/auth/login` | POST | Authenticate with email/password, receive JWT |
| `/api/auth/register` | POST | Register new account, receive JWT |
| `/api/auth/logout` | POST | Stateless logout (client clears token) |
| `/api/pings` | GET | List pings (current user, or all if admin) |
| `/api/pings` | POST | Create a ping (auto-sets `next_check_at`) |
| `/api/pings/{id}` | PUT | Update an existing ping (resets `next_check_at` only if null) |
| `/api/pings/{id}` | DELETE | Delete a ping (ownership enforced unless admin) |
| `/api/checks/pending?count=N` | GET | Claim N ready checks for an agent (marks them `in_progress`) |
| `/api/checks/execute` | POST | Execute a check immediately (HTTP fetch performed by backend) |
| `/api/checks/result` | POST | Record a check result (updates `check_result`, clears `in_progress`, recalculates `next_check_at`) |

Agents authenticate like any other user (for example, an admin account) and can poll `/api/checks/pending`.

---

### Frontend highlights

- Responsive layout with a pings dashboard grid.
- Authentication-aware header (login/logout).
- Ping CRUD form with inline validation.
- Recent check history per ping (HTTP status, response time, errors).
- “Run check” button triggers an immediate backend check (used for manual verification).

---

### Database schema

The schema in `database/schema.sql` matches the Spring Boot JPA mappings:

- `users`, `roles`, `user_roles` – authentication & authorization.
- `ping` – ping definitions (`frequency_minutes`, `next_check_at`, `in_progress`).
- `check_result` – historical records (`http_code`, `error_message`, `response_time_ms`, `checked_at`).

Seed data creates:
- `mary@invoken.com` (password `pass`, user role).
- `zookeeper@invoken.com` (password `pass`, admin role).
- 15 sample pings (Google, Yahoo, etc.).

---

### Logging & health

- Spring Boot exposes standard logs to stdout; wire your preferred logging backend as needed.
- Actuator health endpoint: `/actuator/health` (no auth required, useful for container readiness checks).

---

### Testing

- **Backend:** `mvn test`
- **Frontend:** `npm test`

Unit tests are scaffolded; extend as the codebase evolves.

---

### Next steps & contributions

- Harden JWT handling (refresh tokens, revocation list) for production deployments.
- Add role-based UI (admin features, additional filters).
- Extend agent tooling (dedicated client, scheduling).
- Containerize production builds (Angular → static assets served by nginx, Spring Boot as separate container).

PRs and issues are welcome. Enjoy building and monitoring! 🚀

