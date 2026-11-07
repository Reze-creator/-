$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'load-env.ps1')
$env:PARKING_MYSQL_TEST = 'true'
Push-Location (Join-Path $PSScriptRoot '..\backend')
try {
    # The test class explicitly connects to parking_test and deletes only its business tables.
    & mvn -B -ntp '-Dtest=MySqlApiTest' test
    if ($LASTEXITCODE -ne 0) { throw 'MySQL integration tests failed.' }
} finally {
    Remove-Item Env:PARKING_MYSQL_TEST -ErrorAction SilentlyContinue
    Pop-Location
}
