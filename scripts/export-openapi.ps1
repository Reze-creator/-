param([string]$BaseUrl = 'http://localhost:8090')
$ErrorActionPreference = 'Stop'
$document = Invoke-RestMethod -Uri "$BaseUrl/v3/api-docs"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$encoding = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText((Join-Path $projectRoot 'docs\openapi.json'), ($document | ConvertTo-Json -Depth 100), $encoding)
$examples = @{
    '/api/auth/login' = @{username='demo';password='Demo@123'}
    '/api/auth/register' = @{username='new_user';password='Example@123';displayName='新车主'}
    '/api/bookings' = @{requestId='{{requestId}}';resourceId='{{resourceId}}';plateNumber='浙A12345';startAt='{{startAt}}';endAt='{{endAt}}'}
    '/api/admin/resources/{id}/status' = @{enabled=$false}
    '/api/admin/devices/{id}/heartbeat' = @{fault=$false;message='设备正常'}
    '/api/admin/device-events' = @{eventId='{{eventId}}';bookingId='{{bookingId}}';sequenceNo=1;type='ENTER';occurredAt='{{startAt}}';energyKwh=0}
    '/api/admin/device-events/{id}/action' = @{action='RETRY';reason='已恢复前置条件，重新处理'}
    '/api/admin/operations/incidents/{id}/resolve' = @{resolution='恢复正常心跳并确认设备正常'}
}
foreach ($path in $document.paths.PSObject.Properties) {
    foreach ($operation in $path.Value.PSObject.Properties) {
        if ($operation.Name -notin @('get','post','patch','put','delete')) { continue }
        if ($operation.Name -in @('post','patch') -and $examples.ContainsKey($path.Name)) {
            $operation.Value.requestBody.content.'application/json' | Add-Member -NotePropertyName example -NotePropertyValue $examples[$path.Name] -Force
        }
        foreach ($parameter in $operation.Value.parameters) {
            if ($parameter.in -eq 'path' -and $parameter.name -eq 'id') {
                $value = if ($path.Name -match '/resources/|/devices/') { '{{resourceId}}' }
                    elseif ($path.Name -match '/device-events/') { '{{eventId}}' }
                    elseif ($path.Name -match '/incidents/') { '{{incidentId}}' }
                    else { '{{bookingId}}' }
                $parameter | Add-Member -NotePropertyName example -NotePropertyValue $value -Force
            }
            if ($parameter.name -in @('startAt','endAt')) {
                $parameter | Add-Member -NotePropertyName example -NotePropertyValue ('{{' + $parameter.name + '}}') -Force
            }
        }
    }
}
$document | Add-Member -NotePropertyName servers -NotePropertyValue @(@{url=$BaseUrl;description='本机开发环境'}) -Force
[System.IO.File]::WriteAllText((Join-Path $projectRoot 'docs\apifox-openapi.json'), ($document | ConvertTo-Json -Depth 100), $encoding)
Write-Output 'Exported raw OpenAPI and Apifox import file.'
