param([string]$InstallRoot=(Join-Path $env:LOCALAPPDATA 'FarmManagement\production'), [switch]$Apply)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Local-Installation.ps1')
$installation = Get-LocalInstallation $InstallRoot
$serviceName = 'FarmManagement-PostgreSQL'
$pgCtl = Join-Path $installation.Config.postgresBin 'pg_ctl.exe'
$service = Get-Service -Name $serviceName -ErrorAction SilentlyContinue
if ($service) {
    $imagePath = (Get-ItemProperty -LiteralPath ('HKLM:\SYSTEM\CurrentControlSet\Services\' + $serviceName)).ImagePath
    $imagePath = $imagePath.Replace('/','\')
    if ($imagePath.IndexOf('"' + $installation.Cluster + '"',[StringComparison]::OrdinalIgnoreCase) -lt 0 -or
        $imagePath.IndexOf($pgCtl,[StringComparison]::OrdinalIgnoreCase) -lt 0) {
        throw 'Existing service belongs to another cluster. No changes were made.'
    }
}
Write-Output "Database: 127.0.0.1:$($installation.Port)/$($installation.Database)"
Write-Output "Data directory: $($installation.Cluster)"
Write-Output "Plan: automatic Windows service $serviceName, recovery on failure, nightly online backup at 02:00."
Write-Output 'The legacy database task will be disabled; IntelliJ will continue to control the website.'
if (-not $Apply) { Write-Output 'Preview only. Use -Apply in an administrator PowerShell to apply.'; return }
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Applying a Windows service requires an administrator PowerShell. Preview and project changes do not require administrator rights.'
}
. (Join-Path $PSScriptRoot 'Lan-Common.ps1')
# This also confirms that the elevated process is the installation's Windows owner.
$secretProbe = Read-LanSecret $installation.Root 'owner'
$secretProbe = $null
foreach ($scriptName in @('Backup-Local.ps1','Local-Installation.ps1','Lan-Common.ps1')) {
    if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot $scriptName))) { throw "Missing prepared script: $scriptName" }
}
# Validate task ownership before making any changes.
$oldTask = Get-ScheduledTask -TaskName 'FarmManagement-Database' -ErrorAction SilentlyContinue
if ($oldTask -and -not (@($oldTask.Actions | Where-Object { $_.Arguments.Contains($installation.Cluster) }).Count)) {
    throw 'Existing database task points elsewhere. No changes were made.'
}
$backupTask = Get-ScheduledTask -TaskName 'FarmManagement-LocalBackup' -ErrorAction SilentlyContinue
if ($backupTask -and -not (@($backupTask.Actions | Where-Object { $_.Arguments.Contains($installation.Root) }).Count)) {
    throw 'Existing local backup task belongs to another installation. No changes were made.'
}
foreach ($name in @('FarmManagement-LAN','FarmManagement-Backup')) {
    $task = Get-ScheduledTask -TaskName $name -ErrorAction SilentlyContinue
    if ($task -and $task.State -ne 'Disabled') { throw "Disable the legacy task $name before switching to IDE/service mode." }
}
if (-not $service) {
    & $pgCtl -D $installation.Cluster status *> $null
    if ($LASTEXITCODE -eq 0) { throw 'The private database is already running outside the service. Stop it cleanly during maintenance before applying.' }
    if ($LASTEXITCODE -ne 3) { throw 'Cannot establish that the private PostgreSQL cluster is stopped.' }
    & $pgCtl register -N $serviceName -D $installation.Cluster -S auto -w
    if ($LASTEXITCODE -ne 0) { throw 'Cannot register PostgreSQL service.' }
}
Set-Service -Name $serviceName -StartupType Automatic
& sc.exe failure $serviceName 'reset=' '86400' 'actions=' 'restart/5000/restart/15000/restart/60000'
if ($LASTEXITCODE -ne 0) { throw 'Cannot set service recovery policy.' }
& sc.exe failureflag $serviceName 1
if ($LASTEXITCODE -ne 0) { throw 'Cannot enable recovery on service failures.' }
if ($oldTask) { Disable-ScheduledTask -TaskName 'FarmManagement-Database' | Out-Null }
Start-Service -Name $serviceName
(Get-Service -Name $serviceName).WaitForStatus('Running', [TimeSpan]::FromSeconds(45))
foreach ($scriptName in @('Backup-Local.ps1','Local-Installation.ps1','Lan-Common.ps1')) {
    $source = Join-Path $PSScriptRoot $scriptName
    $target = Join-Path $installation.Root $scriptName
    if ([IO.Path]::GetFullPath($source) -ne [IO.Path]::GetFullPath($target)) {
        if (Test-Path -LiteralPath $target) {
            if ((Get-FileHash -LiteralPath $source).Hash -eq (Get-FileHash -LiteralPath $target).Hash) { continue }
            Copy-Item -LiteralPath $target -Destination ($target + '.before-' + (Get-Date -Format yyyyMMdd-HHmmss))
        }
        Copy-Item -LiteralPath $source -Destination $target -Force
    }
}
& (Join-Path $installation.Root 'Backup-Local.ps1') -InstallRoot $installation.Root
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoProfile -WindowStyle Hidden -File "' + (Join-Path $installation.Root 'Backup-Local.ps1') + '" -InstallRoot "' + $installation.Root + '"')
$trigger = New-ScheduledTaskTrigger -Daily -At '02:00'
$taskPrincipal = New-ScheduledTaskPrincipal -UserId $identity.Name -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 5) -ExecutionTimeLimit (New-TimeSpan -Hours 2) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
Register-ScheduledTask -TaskName 'FarmManagement-LocalBackup' -Action $action -Trigger $trigger -Principal $taskPrincipal -Settings $settings -Description 'Online backup of the private FarmManagement database; runs while its Windows owner is logged on.' -Force | Out-Null
Write-Output "Applied. Service: $serviceName. Backup task: FarmManagement-LocalBackup."
