param([int]$Requests = 500, [int]$Concurrency = 20, [int]$Port = 8091)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'load-env.ps1')
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw "Test port $Port is already occupied." }
$savedDatabase = $env:MYSQL_DATABASE
$savedPort = $env:SERVER_PORT
$savedSeed = $env:SEED_ENABLED
$testProcess = $null
try {
    # 只使用已有隔离测试库。此脚本不删除表，也不接触业务库 parking。
    $env:MYSQL_DATABASE = 'parking_test'
    $env:SERVER_PORT = "$Port"
    $env:SEED_ENABLED = 'true'
    $testProcess = Start-Process -FilePath java -ArgumentList @('-Dfile.encoding=UTF-8','-Duser.timezone=UTC','-jar','backend\target\parking-1.0.0.jar') -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $projectRoot 'work\load-app.log') -RedirectStandardError (Join-Path $projectRoot 'work\load-error.log') -PassThru
    $ready = $false
    for ($attempt = 0; $attempt -lt 45; $attempt++) {
        if ($testProcess.HasExited) { throw 'Isolated test server exited. See work/load-app.log.' }
        try { $health = Invoke-RestMethod "http://127.0.0.1:$Port/actuator/health"; if ($health.status -eq 'UP') { $ready = $true; break } } catch { Start-Sleep -Seconds 1 }
    }
    if (!$ready) { throw 'Isolated test server did not become ready.' }
    & node (Join-Path $PSScriptRoot 'load-test.mjs') "http://127.0.0.1:$Port" $Requests $Concurrency
    if ($LASTEXITCODE -ne 0) { throw 'Load test or invariant verification failed.' }
} finally {
    if ($testProcess -and !$testProcess.HasExited) { Stop-Process -Id $testProcess.Id }
    $env:MYSQL_DATABASE = $savedDatabase
    $env:SERVER_PORT = $savedPort
    $env:SEED_ENABLED = $savedSeed
}
