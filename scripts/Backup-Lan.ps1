param([string]$InstallRoot=$PSScriptRoot,[string]$Destination,[switch]$LeaveStopped)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Lan-Common.ps1')
Assert-LanLauncherMode
$config=Get-Content -Raw -LiteralPath (Join-Path $InstallRoot 'config.json') | ConvertFrom-Json
if(-not $Destination){$Destination=Join-Path $InstallRoot ('backups\'+(Get-Date -Format yyyyMMdd-HHmmss)+'-'+[guid]::NewGuid().ToString('N').Substring(0,6))}
if(Test-Path -LiteralPath $Destination){throw 'Backup destination must be a new directory'}
Stop-LanApplication $config $InstallRoot
$previousPassword=$env:PGPASSWORD
try{
 & (Join-Path $config.postgresBin 'pg_ctl.exe') -D (Join-Path $InstallRoot 'pgdata') status *> $null
 if($LASTEXITCODE -eq 0){
  & (Join-Path $config.postgresBin 'pg_ctl.exe') -D (Join-Path $InstallRoot 'pgdata') -m fast -w stop
  if($LASTEXITCODE -ne 0){throw 'Cannot stop private database cleanly before maintenance'}
 }
 # Task Scheduler may terminate the launcher's child PostgreSQL process. Restart
 # after the task has stopped, so no pending launcher cleanup can interrupt pg_dump.
 & (Join-Path $config.postgresBin 'pg_ctl.exe') -D (Join-Path $InstallRoot 'pgdata') -l (Join-Path $InstallRoot 'logs\postgres.log') -w start
 if($LASTEXITCODE -ne 0){throw 'Cannot start database for backup'}
 $env:PGPASSWORD=Read-LanSecret $InstallRoot 'owner'
 & python (Join-Path $PSScriptRoot 'database_recovery.py') backup --database farm_management --backup-dir $Destination --host 127.0.0.1 --port $config.databasePort --username farm_owner --postgres-bin $config.postgresBin --writers-paused
 if($LASTEXITCODE -ne 0){throw 'Backup failed. Application remains stopped for inspection.'}
 & python (Join-Path $PSScriptRoot 'database_recovery.py') check-backup --backup-dir $Destination --postgres-bin $config.postgresBin
 if($LASTEXITCODE -ne 0){throw 'Backup integrity check failed'}
 # The destination inherits protected installation ACLs by default. For an external
 # destination the operator must provision an encrypted, access-controlled location.
 Write-Host "Verified backup: $Destination"
 if(-not $LeaveStopped){Start-LanApplication $InstallRoot;if(-not(Wait-LanHealth $config $InstallRoot)){throw 'Backup verified, but application restart failed. Inspect backend.log.'}}
}finally{$env:PGPASSWORD=$previousPassword}
