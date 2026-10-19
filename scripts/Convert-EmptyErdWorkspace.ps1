param(
    [string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin',
    [string]$Database='farm_management_erd_v1',
    [string]$DatabaseHost='127.0.0.1',
    [int]$Port=5432,
    [string]$DatabaseOwner='postgres'
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$outputRoot=Join-Path $projectRoot '.test-data'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$sqlFile=Join-Path $outputRoot ('erd-workspace-conversion-'+[guid]::NewGuid().ToString('N')+'.sql')
& (Join-Path $PSScriptRoot 'Write-ErdWorkspaceMigration.ps1') -OutputFile $sqlFile
Write-Host 'Stop the backend and back up the database before conversion.'
Write-Host "Target: $DatabaseHost`:$Port / $Database. This conversion requires empty material/warehouse/inventory/history tables."
& (Join-Path $PostgresBin 'psql.exe') -X -h $DatabaseHost -p $Port -U $DatabaseOwner -d $Database -W -v ON_ERROR_STOP=1 --single-transaction -f $sqlFile
if($LASTEXITCODE -ne 0) { throw 'Conversion failed. All conversion changes were rolled back; keep the error for diagnosis.' }
Write-Host 'ERD WORKSPACE CONVERSION SUCCESS. Existing farms still require an explicit owner assignment.'
