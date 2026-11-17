$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

$mailpitBin = Join-Path $root '.tmp\mailpit\bin'
$originalEmailMode = $env:ITAM_EMAIL_MODE
if (Test-Path (Join-Path $mailpitBin 'mailpit.exe')) {
    Start-Process powershell -WindowStyle Hidden -WorkingDirectory $mailpitBin -ArgumentList @(
        '-NoExit', '-Command', '.\mailpit.exe --listen 127.0.0.1:8025 --smtp 127.0.0.1:1025'
    )
} else {
    $env:ITAM_EMAIL_MODE = 'simulated'
    Write-Warning 'Mailpit was not found; backend email mode is simulated for this launch.'
}

try {
    Start-Process powershell -WindowStyle Hidden -WorkingDirectory (Join-Path $root 'backend') -ArgumentList @(
        '-NoExit', '-Command', '.\mvnw.cmd spring-boot:run'
    )
} finally {
    $env:ITAM_EMAIL_MODE = $originalEmailMode
}

Start-Process powershell -WindowStyle Hidden -WorkingDirectory (Join-Path $root 'frontend') -ArgumentList @(
    '-NoExit', '-Command', 'npm.cmd run dev -- --host 127.0.0.1'
)

Write-Host 'ITAM services are starting:' -ForegroundColor Green
Write-Host '  Frontend: http://127.0.0.1:5173'
Write-Host '  Backend:  http://127.0.0.1:8080/api'
if (Test-Path (Join-Path $mailpitBin 'mailpit.exe')) { Write-Host '  Mailpit:  http://127.0.0.1:8025' }
