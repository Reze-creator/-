$ErrorActionPreference = 'Stop'
$pidFile = Join-Path $PSScriptRoot '..\work\app.pid'
if (!(Test-Path -LiteralPath $pidFile)) { Write-Output 'No tracked background process. Stop foreground start.ps1 with Ctrl+C.'; return }
$parkingPid = [int](Get-Content -LiteralPath $pidFile -Raw)
$running = Get-CimInstance Win32_Process -Filter "ProcessId=$parkingPid" -ErrorAction SilentlyContinue
if (!$running) { Write-Output 'Tracked process is already stopped.'; return }
if ($running.Name -ne 'java.exe' -or $running.CommandLine -notmatch 'parking-1\.0\.0\.jar') { throw 'PID belongs to another process; refusing to stop it.' }
Stop-Process -Id $parkingPid
Write-Output 'Parking background process stopped.'
