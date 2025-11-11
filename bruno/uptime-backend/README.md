## Bruno Collection – Uptime Backend

This folder contains a Bruno suite covering the Spring Boot REST API.

### Structure

- `collection.json` – collection metadata (compatible with Bruno ≥2.13.2).
- `environments/local.json` – default environment variables (base URL, credentials, placeholders).
- `auth/*.json` – authentication workflows (register and login).
- `monitors/*.json` – CRUD operations on monitors.
- `checks/*.json` – agent-style endpoints (fetch, execute, submit results).

### Usage

1. Install [Bruno](https://www.usebruno.com/) and open this folder as a workspace/collection.
2. Select the `Local` environment (or duplicate/edit it with your own values).  
   - `loginEmail` / `loginPassword` should match an existing account.  
   - `monitorUrl`, `monitorLabel`, and `monitorFrequency` are used by create/update requests.
3. Run requests in order:
   - `auth/login` stores a JWT in `authToken`.
   - `monitors/create` captures the new `monitorId` for update/delete/check calls.
4. Adjust payloads as necessary (e.g., override `monitorId` to target an existing monitor).

Scripts store `authToken` and `monitorId` in Bruno globals so subsequent calls reuse them automatically.

