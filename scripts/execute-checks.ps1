# PowerShell script to fetch pending checks and execute them in parallel using HEAD requests
# Usage: .\execute-checks.ps1 -ApiUrl "http://localhost:8080" -ApiKey "worker-default-41e03e338437-key" -Count 10 -MaxConcurrent 5

param(
    [Parameter(Mandatory=$true)]
    [string]$ApiUrl,
    
    [Parameter(Mandatory=$true)]
    [string]$ApiKey,
    
    [Parameter(Mandatory=$false)]
    [int]$Count = 10,
    
    [Parameter(Mandatory=$false)]
    [int]$MaxConcurrent = 5,
    
    [Parameter(Mandatory=$false)]
    [int]$TimeoutSeconds = 15
)

# Validate parameters
if ($Count -lt 1 -or $Count -gt 50) {
    Write-Error "Count must be between 1 and 50"
    exit 1
}

if ($MaxConcurrent -lt 1) {
    Write-Error "MaxConcurrent must be at least 1"
    exit 1
}

# Remove trailing slash from API URL
$ApiUrl = $ApiUrl.TrimEnd('/')

# Headers for API requests
$Headers = @{
    "X-API-Key" = $ApiKey
    "Content-Type" = "application/json"
}

# Function to execute a single HEAD request and return result
function Execute-HeadRequest {
    param(
        [long]$PingId,
        [string]$Url,
        [string]$Label,
        [int]$TimeoutSeconds
    )
    
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $checkedAt = [DateTimeOffset]::UtcNow
    $httpCode = $null
    $errorMessage = $null
    
    try {
        Write-Host "[$PingId] Checking $Url..." -ForegroundColor Cyan
        
        # Execute HEAD request with timeout
        $requestParams = @{
            Uri = $Url
            Method = 'HEAD'
            TimeoutSec = $TimeoutSeconds
            ErrorAction = 'Stop'
            UseBasicParsing = $true
        }
        
        try {
            $response = Invoke-WebRequest @requestParams
            $httpCode = $response.StatusCode
            $success = $true
        } catch {
            # Try to extract HTTP status code from the exception
            if ($_.Exception.Response) {
                $httpCode = [int]$_.Exception.Response.StatusCode.value__
            } else {
                $httpCode = $null
            }
            $errorMessage = $_.Exception.Message
            $success = $false
        }
        
        $stopwatch.Stop()
        $responseTimeMs = $stopwatch.Elapsed.TotalMilliseconds
        $responseTimeRounded = [math]::Round($responseTimeMs, 0)
        
        if ($success -and $httpCode) {
            Write-Host "[$PingId] OK $Url - HTTP $httpCode - ${responseTimeRounded}ms" -ForegroundColor Green
        } else {
            Write-Host "[$PingId] FAIL $Url - Error: $errorMessage (${responseTimeRounded}ms)" -ForegroundColor Red
        }
        
        return @{
            PingId = $PingId
            HttpCode = $httpCode
            ErrorMessage = $errorMessage
            ResponseTimeMs = $responseTimeMs
            CheckedAt = $checkedAt
            Success = $success
        }
    } catch {
        $stopwatch.Stop()
        $responseTimeMs = $stopwatch.Elapsed.TotalMilliseconds
        $errorMessage = $_.Exception.Message
        $responseTimeRounded = [math]::Round($responseTimeMs, 0)
        Write-Host "[$PingId] FAIL $Url - Exception: $errorMessage (${responseTimeRounded}ms)" -ForegroundColor Red
        
        return @{
            PingId = $PingId
            HttpCode = $null
            ErrorMessage = $errorMessage
            ResponseTimeMs = $responseTimeMs
            CheckedAt = $checkedAt
            Success = $false
        }
    }
}

# Function to record result back to server
function Record-Result {
    param(
        [string]$ApiUrl,
        [hashtable]$Headers,
        [hashtable]$Result
    )
    
    try {
        # Prepare request body
        $body = @{
            pingId = $Result.PingId
            responseTimeMs = [math]::Round($Result.ResponseTimeMs, 2)
            checkedAt = $Result.CheckedAt.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
        }
        
        if ($Result.HttpCode -ne $null) {
            $body.httpCode = $Result.HttpCode
        }
        
        if ($Result.ErrorMessage) {
            $body.errorMessage = $Result.ErrorMessage
        }
        
        $jsonBody = $body | ConvertTo-Json -Depth 10
        
        # Record result
        $recordParams = @{
            Uri = "$ApiUrl/api/checks/result"
            Method = 'PATCH'
            Headers = $Headers
            Body = $jsonBody
            ContentType = "application/json"
            ErrorAction = 'Stop'
        }
        
        $response = Invoke-RestMethod @recordParams
        Write-Host "[$($Result.PingId)] Result recorded successfully" -ForegroundColor DarkGreen
        return $true
    } catch {
        Write-Host "[$($Result.PingId)] Failed to record result: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

# Main execution
Write-Host "=== Uptime Checker Worker ===" -ForegroundColor Yellow
Write-Host "API URL: $ApiUrl" -ForegroundColor Gray
Write-Host "Fetching up to $Count checks..." -ForegroundColor Gray
Write-Host "Max concurrent: $MaxConcurrent" -ForegroundColor Gray
Write-Host ""

try {
    # Fetch pending checks
    Write-Host "Fetching pending checks..." -ForegroundColor Cyan
    $fetchParams = @{
        Uri = "$ApiUrl/api/checks/next?count=$Count"
        Method = 'POST'
        Headers = $Headers
        ErrorAction = 'Stop'
    }
    
    $pendingChecks = Invoke-RestMethod @fetchParams
    
    if (-not $pendingChecks -or $pendingChecks.Count -eq 0) {
        Write-Host "No pending checks found." -ForegroundColor Yellow
        exit 0
    }
    
    Write-Host "Found $($pendingChecks.Count) pending checks" -ForegroundColor Green
    Write-Host ""
    
    # Process checks using runspace pool for parallel execution
    $runspacePool = [RunspaceFactory]::CreateRunspacePool(1, $MaxConcurrent)
    $runspacePool.Open()
    
    $powershellInstances = @()
    $results = @()
    $total = $pendingChecks.Count
    $completed = 0
    
    # Create a script block for executing HEAD requests
    $scriptBlock = {
        param($Url, $TimeoutSeconds)
        
        $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
        $checkedAt = [DateTimeOffset]::UtcNow
        $httpCode = $null
        $errorMessage = $null
        $success = $false
        
        try {
            $requestParams = @{
                Uri = $Url
                Method = 'HEAD'
                TimeoutSec = $TimeoutSeconds
                ErrorAction = 'Stop'
                UseBasicParsing = $true
            }
            
            try {
                $response = Invoke-WebRequest @requestParams
                $httpCode = $response.StatusCode
                $success = $true
            } catch {
                # Try to extract HTTP status code from the exception
                if ($_.Exception.Response) {
                    $httpCode = [int]$_.Exception.Response.StatusCode.value__
                }
                $errorMessage = $_.Exception.Message
            }
        } catch {
            $errorMessage = $_.Exception.Message
        }
        
        $stopwatch.Stop()
        $responseTimeMs = $stopwatch.Elapsed.TotalMilliseconds
        
        return @{
            HttpCode = $httpCode
            ErrorMessage = $errorMessage
            ResponseTimeMs = $responseTimeMs
            CheckedAt = $checkedAt
            Success = $success
        }
    }
    
    # Create PowerShell instances for each check
    foreach ($check in $pendingChecks) {
        $powershell = [PowerShell]::Create()
        $powershell.RunspacePool = $runspacePool
        $null = $powershell.AddScript($scriptBlock).AddArgument($check.url).AddArgument($TimeoutSeconds)
        
        $handle = $powershell.BeginInvoke()
        
        $powershellInstances += [PSCustomObject]@{
            Instance = $powershell
            Handle = $handle
            Check = $check
        }
        
        Write-Host "[$($check.pingId)] Started check for $($check.url)" -ForegroundColor DarkCyan
    }
    
    # Wait for all checks to complete and collect results
    Write-Host ""
    Write-Host "Waiting for all checks to complete..." -ForegroundColor Cyan
    
    foreach ($instance in $powershellInstances) {
        $checkResult = $instance.Instance.EndInvoke($instance.Handle)
        $instance.Instance.Dispose()
        
        # Create result object with ping ID
        $result = @{
            PingId = $instance.Check.pingId
            HttpCode = $checkResult.HttpCode
            ErrorMessage = $checkResult.ErrorMessage
            ResponseTimeMs = $checkResult.ResponseTimeMs
            CheckedAt = $checkResult.CheckedAt
            Success = $checkResult.Success
        }
        
        # Display result
        $responseTimeRounded = [math]::Round($result.ResponseTimeMs, 0)
        if ($result.Success -and $result.HttpCode) {
            Write-Host "[$($result.PingId)] OK $($instance.Check.url) - HTTP $($result.HttpCode) - ${responseTimeRounded}ms" -ForegroundColor Green
        } else {
            Write-Host "[$($result.PingId)] FAIL $($instance.Check.url) - Error: $($result.ErrorMessage) (${responseTimeRounded}ms)" -ForegroundColor Red
        }
        
        # Record result
        $recorded = Record-Result -ApiUrl $ApiUrl -Headers $Headers -Result $result
        $completed++
        
        if ($recorded) {
            Write-Host "[Progress] $completed/$total completed" -ForegroundColor DarkGray
        }
        
        $results += $result
    }
    
    # Clean up
    $runspacePool.Close()
    $runspacePool.Dispose()
    
    Write-Host ""
    Write-Host "=== All checks completed ===" -ForegroundColor Green
    Write-Host "Total: $total checks" -ForegroundColor Gray
    Write-Host "Completed: $completed results recorded" -ForegroundColor Gray
    
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host $_.ScriptStackTrace -ForegroundColor Red
    exit 1
}

