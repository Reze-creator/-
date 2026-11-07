param([switch]$MySqlTests)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $projectRoot
try {
    & npm.cmd --prefix frontend ci --no-fund --no-audit
    if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
    & npm.cmd --prefix frontend run format:check
    if ($LASTEXITCODE -ne 0) { throw 'Frontend format check failed.' }
    & npm.cmd --prefix frontend run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }

    $staticRoot = Join-Path $projectRoot 'backend\src\main\resources\static'
    # This generated directory contains frontend build output only.
    if (Test-Path -LiteralPath $staticRoot) {
        $resolvedStatic = (Resolve-Path -LiteralPath $staticRoot).Path
        if ($resolvedStatic -ne (Join-Path $projectRoot 'backend\src\main\resources\static')) { throw 'Unexpected static output path.' }
        Remove-Item -LiteralPath $resolvedStatic -Recurse -Force
    }
    New-Item -ItemType Directory -Path $staticRoot -Force | Out-Null
    Copy-Item -Path (Join-Path $projectRoot 'frontend\dist\*') -Destination $staticRoot -Recurse
    if ($MySqlTests) { . (Join-Path $PSScriptRoot 'load-env.ps1'); $env:PARKING_MYSQL_TEST = 'true' }
    Push-Location (Join-Path $projectRoot 'backend')
    try {
        & mvn -B -ntp verify
        if ($LASTEXITCODE -ne 0) { throw 'Backend build or tests failed.' }
    } finally { Pop-Location }
    Write-Output 'Build completed: backend\target\parking-1.0.0.jar (includes frontend).'
} finally {
    Remove-Item Env:PARKING_MYSQL_TEST -ErrorAction SilentlyContinue
    Pop-Location
}
