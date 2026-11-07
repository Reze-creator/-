param([switch]$Build, [switch]$Background)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'load-env.ps1')
if ($Build -or !(Test-Path -LiteralPath (Join-Path $projectRoot 'backend\target\parking-1.0.0.jar'))) {
    & (Join-Path $PSScriptRoot 'build.ps1')
}
if ($Background) {
    $workRoot = Join-Path $projectRoot 'work'
    New-Item -ItemType Directory -Path $workRoot -Force | Out-Null
    $running = Start-Process -FilePath java -ArgumentList @('-Dfile.encoding=UTF-8','-Duser.timezone=UTC','-jar','backend\target\parking-1.0.0.jar') -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $workRoot 'app.log') -RedirectStandardError (Join-Path $workRoot 'app-error.log') -PassThru
    $running.Id | Set-Content -LiteralPath (Join-Path $workRoot 'app.pid')
    Write-Output "Parking started in background, PID=$($running.Id). See work/app.log."
    return
}
Push-Location $projectRoot
try {
    & java '-Dfile.encoding=UTF-8' '-Duser.timezone=UTC' -jar backend\target\parking-1.0.0.jar
    if ($LASTEXITCODE -ne 0) { throw 'Parking exited with an error.' }
} finally { Pop-Location }
