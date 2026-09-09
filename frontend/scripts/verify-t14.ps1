# Run from the repository root: .\frontend\scripts\verify-t14.ps1
# Uses the real local shell because the agent sandbox may block Java/esbuild.
$taskRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$results = [ordered]@{}
Push-Location (Join-Path $taskRoot 'backend')
try {
    & .\mvnw.cmd test *> (Join-Path $taskRoot '.tmp_t14_user_backend.log')
    $results.backend = $LASTEXITCODE
} finally { Pop-Location }
Push-Location (Join-Path $taskRoot 'frontend')
try {
    & npm.cmd test -- --run *> (Join-Path $taskRoot '.tmp_t14_user_frontend_tests.log')
    $results.frontendTests = $LASTEXITCODE
    & npm.cmd run build *> (Join-Path $taskRoot '.tmp_t14_user_frontend_build.log')
    $results.frontendBuild = $LASTEXITCODE
} finally { Pop-Location }
$results | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskRoot '.tmp_t14_validation.json') -Encoding utf8
$results | Format-Table
