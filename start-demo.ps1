$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

Start-Process powershell -WorkingDirectory (Join-Path $root '.tmp\mailpit\bin') -ArgumentList @(
    '-NoExit', '-Command', '.\mailpit.exe --listen 127.0.0.1:8025 --smtp 127.0.0.1:1025'
)

Start-Process powershell -WorkingDirectory (Join-Path $root 'backend') -ArgumentList @(
    '-NoExit', '-Command', '.\mvnw.cmd spring-boot:run'
)

Start-Process powershell -WorkingDirectory (Join-Path $root 'frontend') -ArgumentList @(
    '-NoExit', '-Command', 'npm.cmd run dev -- --host 127.0.0.1'
)

Write-Host 'ITAM services are starting:' -ForegroundColor Green
Write-Host '  Frontend: http://127.0.0.1:5173'
Write-Host '  Backend:  http://127.0.0.1:8080/api'
Write-Host '  Mailpit:  http://127.0.0.1:8025'
