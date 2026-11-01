param([string]$InstallRoot=(Join-Path $env:LOCALAPPDATA 'FarmManagement\production'))
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Local-Installation.ps1')
. (Join-Path $PSScriptRoot 'Lan-Common.ps1')
$installation = Get-LocalInstallation $InstallRoot
$mutex = New-Object Threading.Mutex($false, 'Local\FarmManagement-LocalBackup')
if (-not $mutex.WaitOne(0)) { $mutex.Dispose(); throw 'A local backup is already running.' }
$previousPassword = $env:PGPASSWORD
$previousTimeout = $env:PGCONNECT_TIMEOUT
try {
    $env:PGPASSWORD = Read-LanSecret $installation.Root 'owner'
    $env:PGCONNECT_TIMEOUT = '5'
    $backupRoot = Join-Path $installation.Root 'backups'
    $destination = Join-Path $backupRoot ('local-' + (Get-Date -Format yyyyMMdd-HHmmss) + '-' + [guid]::NewGuid().ToString('N').Substring(0,6))
    New-Item -ItemType Directory -Path $destination | Out-Null
    $archive = Join-Path $destination 'database.dump'
    $partial = $archive + '.partial'
    # pg_dump uses a consistent database snapshot; neither the IDE nor PostgreSQL is stopped.
    & (Join-Path $installation.Config.postgresBin 'pg_dump.exe') -w -h 127.0.0.1 -p $installation.Port -U farm_owner -d $installation.Database -Fc -f $partial
    if ($LASTEXITCODE -ne 0) { throw "Backup failed; incomplete archive retained at $partial" }
    $listing = & (Join-Path $installation.Config.postgresBin 'pg_restore.exe') --list $partial
    if ($LASTEXITCODE -ne 0) { throw 'Backup archive cannot be listed; it has not been marked complete.' }
    Move-Item -LiteralPath $partial -Destination $archive
    $listing | Set-Content -LiteralPath (Join-Path $destination 'archive-contents.txt') -Encoding UTF8
    [ordered]@{
        status='COMPLETE'; completedAt=[DateTimeOffset]::Now.ToString('o'); database=$installation.Database
        host='127.0.0.1'; port=$installation.Port; sha256=(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
        validation='pg_restore --list and SHA256; restore test is a separate operation'
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $destination 'backup.json') -Encoding UTF8
    Write-Output "Backup complete: $destination"
} finally {
    $env:PGPASSWORD = $previousPassword
    $env:PGCONNECT_TIMEOUT = $previousTimeout
    $mutex.ReleaseMutex()
    $mutex.Dispose()
}
