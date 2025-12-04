# Preface this script with rich comments, similar to a `-help` note. Provide an explanation of what the script does in a descriptive manner that would be suitable for reference documentation. No parameters should be used in this comment block.
<#
Periodically re-runs execute-checks.ps1 and streams each run’s output to the console.
Designed to work on Windows PowerShell 5.1 by avoiding Start-ThreadJob; relies on
a background job created with Start-Job and a simple loop to show real-time output.
Stop with Ctrl+C (job is cleaned up automatically).
#>

param(
    [Parameter(Mandatory = $true)]
    [string]$ApiUrl,

    [Parameter(Mandatory = $true)]
    [string]$ApiKey,

    [int]$Count = 10,
    [int]$MaxConcurrent = 5,
    [int]$TimeoutSeconds = 15,

    [double]$IntervalSeconds = 1
)

if ($IntervalSeconds -le 0) {
    throw "IntervalSeconds must be greater than zero."
}

$executeScript = Join-Path $PSScriptRoot "execute-checks.ps1"
if (-not (Test-Path $executeScript)) {
    throw "execute-checks.ps1 not found at $executeScript"
}

$scriptArgs = @{
    ApiUrl = $ApiUrl
    ApiKey = $ApiKey
    Count = $Count
    MaxConcurrent = $MaxConcurrent
    TimeoutSeconds = $TimeoutSeconds
}

Write-Host "Starting periodic execute-checks.ps1 runner (interval: $IntervalSeconds second(s))" -ForegroundColor Yellow
Write-Host "Press Ctrl+C to stop." -ForegroundColor Gray

$job = Start-Job -ScriptBlock {
    param($scriptPath, $arguments, $interval)

    $ErrorActionPreference = 'Stop'

    while ($true) {
        $timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        Write-Output ""
        Write-Output "[$timestamp] Starting execute-checks run..."

        try {
            & $scriptPath @arguments
        } catch {
            Write-Output "[$timestamp] Error running execute-checks.ps1: $($_.Exception.Message)"
            if ($_.ScriptStackTrace) {
                Write-Output $_.ScriptStackTrace
            }
        }

        Start-Sleep -Seconds $interval
    }
} -ArgumentList $executeScript, $scriptArgs, $IntervalSeconds

try {
    while ($true) {
        Receive-Job -Job $job -Keep
        if ($job.State -ne 'Running') {
            break
        }
        Start-Sleep -Milliseconds 200
    }
} catch {
    Write-Warning $_
} finally {
    if ($job -and $job.State -eq 'Running') {
        Stop-Job -Job $job -Force | Out-Null
    }
    if ($job) {
        Remove-Job -Job $job -Force | Out-Null
    }
    Write-Host "Stopped periodic runner." -ForegroundColor Yellow
}

