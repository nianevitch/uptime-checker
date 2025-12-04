# Uptime Checker Worker Scripts

## execute-checks.ps1

PowerShell script that fetches pending checks from the API and executes them in parallel using HEAD requests.

### Features

- Fetches pending checks from the API using `POST /api/checks/next`
- Executes HEAD requests in parallel using PowerShell runspace pools
- Records results back to the server using `PATCH /api/checks/result`
- Configurable concurrency and timeout settings
- Progress reporting and error handling

### Requirements

- PowerShell 5.1 or later
- Valid API key configured in the application
- Network access to the API server

### Usage

```powershell
.\execute-checks.ps1 -ApiUrl "http://localhost:8080" -ApiKey "your-api-key" -Count 10 -MaxConcurrent 5
```

### Parameters

- `-ApiUrl` (Required): Base URL of the API server (e.g., `http://localhost:8080`)
- `-ApiKey` (Required): API key for authentication (configured in `app.worker.api-keys`)
- `-Count` (Optional): Number of checks to fetch (default: 10, max: 50)
- `-MaxConcurrent` (Optional): Maximum number of concurrent checks (default: 5)
- `-TimeoutSeconds` (Optional): Timeout for each HEAD request in seconds (default: 15)

### Examples

```powershell
# Fetch 10 checks with 5 concurrent workers
.\execute-checks.ps1 -ApiUrl "http://localhost:8080" -ApiKey "my-api-key" -Count 10 -MaxConcurrent 5

# Fetch 20 checks with 10 concurrent workers and 30 second timeout
.\execute-checks.ps1 -ApiUrl "http://localhost:8080" -ApiKey "my-api-key" -Count 20 -MaxConcurrent 10 -TimeoutSeconds 30
```

### API Endpoints

The script uses the following API endpoints:

- `POST /api/checks/next?count=N` - Fetches next N pending checks (requires `X-API-Key` header)
- `PATCH /api/checks/result` - Records check result (requires `X-API-Key` header)

### Configuration

The API key must be configured in the application's `application.yml`:

```yaml
app:
  worker:
    api-keys: your-api-key-here,another-key-here
```

### Output

The script provides real-time progress updates:
- Green checkmarks (✓) for successful checks with HTTP status codes
- Red X marks (✗) for failed checks with error messages
- Progress counter showing completed/total checks

### Error Handling

- Network errors are caught and reported with error messages
- Failed result recordings are logged but don't stop execution
- HTTP status codes are extracted from exceptions when possible
- All errors are logged with detailed information

### Performance

- Uses PowerShell runspace pools for efficient parallel execution
- Respects `MaxConcurrent` limit to avoid overwhelming the system
- Measures response times for each check
- Records timestamps in UTC format







