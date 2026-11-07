# Dot-source this file; parse assignments as data rather than executing .env.
$configurationFile = Join-Path $PSScriptRoot '..\.env'
if (!(Test-Path -LiteralPath $configurationFile)) { throw 'Missing .env. Copy .env.example and configure the database.' }
foreach ($line in Get-Content -LiteralPath $configurationFile -Encoding utf8) {
    if ($line -match '^\s*([A-Z][A-Z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
