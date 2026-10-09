$ErrorActionPreference = 'SilentlyContinue'
$root = [IO.Path]::GetFullPath($PSScriptRoot)

$processes = Get-CimInstance Win32_Process | Where-Object {
    $_.CommandLine -and $_.CommandLine -like "*$root*" -and (
        $_.Name -in @('java.exe', 'node.exe', 'mailpit.exe') -or
        $_.CommandLine -match 'mvnw\.cmd|spring-boot:run|vite'
    )
}

foreach ($process in $processes) {
    Stop-Process -Id $process.ProcessId -Force
    Write-Host "Stopped $($process.Name) (PID $($process.ProcessId))"
}

if (-not $processes) {
    Write-Host 'No ITAM demo process found.'
}
