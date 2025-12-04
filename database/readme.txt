
PS C:\Users\nikia> Get-Content -Raw "C:\dev\git_clone\uptime-checker\database\full_dataset.sql" |
>>   mysql -h localhost -u uptimer -puptimer uptime_user_management


PS C:\Users\nikia> C:\dev\git_clone\uptime-checker\scripts\execute-checks.ps1 
PS C:\Users\nikia> C:\dev\git_clone\uptime-checker\scripts\watch-execute-checks.ps1

pwsh -File scripts/execute-checks.ps1 `
  -ApiUrl http://localhost:8080 `
  -ApiKey worker-default-41e03e338437-key `
  -Count 10 `
  -MaxConcurrent 5
  
  
powershell -File scripts/watch-execute-checks.ps1 `
  -ApiUrl http://localhost:8080 `
  -ApiKey worker-default-41e03e338437-key `
  -Count 20 `
  -MaxConcurrent 20 `
  -IntervalSeconds 1  