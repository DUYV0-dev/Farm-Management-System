<#
.SYNOPSIS
    Backup, restore or verify the PostgreSQL database without changing existing databases.
.DESCRIPTION
    Requires Python 3.10+ and PostgreSQL 18 client tools. Uses pgpass.conf,
    PGPASSFILE or PGPASSWORD for authentication; never accepts a password argument.
    Backups contain sensitive data. Choose a restricted backup location outside Git.
    Restore requires a NEW database name and pre-provisioned PostgreSQL roles.
    Returns exit code 0 on PASS and 1 on failure. Does not schedule backups or prune files.
.EXAMPLE
    .\scripts\Invoke-DatabaseRecovery.ps1 -Action check-backup -BackupDirectory D:\FarmBackups\backup-20261019 -Report D:\FarmBackups\integrity-report-20261019.json
.EXAMPLE
    .\scripts\Invoke-DatabaseRecovery.ps1 -Action backup -Database farm_management -BackupDirectory D:\FarmBackups\backup-20261019 -WritersPaused -Report D:\FarmBackups\backup-report-20261019.json
.EXAMPLE
    .\scripts\Invoke-DatabaseRecovery.ps1 -Action restore -Database farm_recovered -BackupDirectory D:\FarmBackups\backup-20261019 -Report D:\FarmBackups\recovery-report-20261019.json
.EXAMPLE
    .\scripts\Invoke-DatabaseRecovery.ps1 -Action verify -Database farm_recovered -BackupDirectory D:\FarmBackups\backup-20261019
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('backup','check-backup','restore','verify')]
    [string]$Action,
    [string]$Database,
    [Parameter(Mandatory=$true)]
    [string]$BackupDirectory,
    [string]$DatabaseHost='127.0.0.1',
    [ValidateRange(1,65535)]
    [int]$Port=5432,
    [string]$Username='postgres',
    [string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin',
    [string]$Python='python',
    [string]$Report,
    [ValidateRange(1,2147483647)]
    [int]$RpoHours=24,
    [ValidateRange(1,2147483647)]
    [int]$RtoMinutes=60,
    [ValidateRange(1,2147483647)]
    [int]$RetentionDays=30,
    [switch]$WritersPaused
)
$ErrorActionPreference='Stop'
$arguments=@(
    (Join-Path $PSScriptRoot 'database_recovery.py'), $Action,
    '--backup-dir', $BackupDirectory,
    '--host', $DatabaseHost, '--port', [string]$Port,
    '--username', $Username, '--postgres-bin', $PostgresBin,
    '--rpo-hours', [string]$RpoHours, '--rto-minutes', [string]$RtoMinutes,
    '--retention-days', [string]$RetentionDays
)
if($Database) { $arguments+=@('--database',$Database) }
if($Report) { $arguments+=@('--report',$Report) }
if($WritersPaused) { $arguments+='--writers-paused' }
& $Python @arguments
if($LASTEXITCODE -ne 0) { throw 'Database recovery command failed. See the JSON output/report for details.' }
