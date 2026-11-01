param([Parameter(Mandatory=$true)][string]$ReleaseJar,
 [Parameter(Mandatory=$true)][string]$VerificationFile,[string]$InstallRoot=$PSScriptRoot)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Lan-Common.ps1')
Assert-LanLauncherMode
$verified=Get-Content -Raw -LiteralPath $VerificationFile | ConvertFrom-Json
$hash=(Get-FileHash -Algorithm SHA256 -LiteralPath $ReleaseJar).Hash
if(-not $verified.browser -or -not $verified.backupRestore -or $hash -ne $verified.sha256){throw 'Release must match a successful production/browser/recovery verification'}
$configPath=Join-Path $InstallRoot 'config.json';$config=Get-Content -Raw -LiteralPath $configPath | ConvertFrom-Json
$backup=Join-Path $InstallRoot ('backups\before-upgrade-'+(Get-Date -Format yyyyMMdd-HHmmss))
& (Join-Path $PSScriptRoot 'Backup-Lan.ps1') -InstallRoot $InstallRoot -Destination $backup -LeaveStopped
$release=Join-Path $InstallRoot ('releases\farm-'+(Get-Date -Format yyyyMMdd-HHmmss)+'.jar')
Copy-Item -LiteralPath $ReleaseJar -Destination $release
$previous=@{};foreach($name in @('DB_URL','DB_MIGRATION_USERNAME','DB_MIGRATION_PASSWORD')){$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
try{
 $env:DB_URL="jdbc:postgresql://127.0.0.1:$($config.databasePort)/farm_management";$env:DB_MIGRATION_USERNAME='farm_owner';$env:DB_MIGRATION_PASSWORD=Read-LanSecret $InstallRoot 'owner'
 & $config.java '-Dloader.main=com.farmmanagement.backend.config.DatabaseMigrations' -cp $release org.springframework.boot.loader.launch.PropertiesLauncher migrate *> (Join-Path $InstallRoot 'logs\upgrade.log')
 if($LASTEXITCODE -ne 0){throw "Upgrade failed. Keep application stopped; inspect upgrade.log and verified backup $backup"}
 Copy-Item -LiteralPath $configPath -Destination (Join-Path $InstallRoot ('config-before-'+(Get-Date -Format yyyyMMdd-HHmmss)+'.json'))
 $config.jar=$release;$config | ConvertTo-Json | Set-Content -LiteralPath $configPath -Encoding UTF8
 foreach($script in @('Start-Lan.ps1','Backup-Lan.ps1','Update-Lan.ps1','Lan-Common.ps1','database_recovery.py')){
  $source=Join-Path $PSScriptRoot $script;$target=Join-Path $InstallRoot $script
  if([IO.Path]::GetFullPath($source) -ne [IO.Path]::GetFullPath($target)){Copy-Item -LiteralPath $source -Destination $target -Force}
 }
 Start-LanApplication $InstallRoot
 if(-not(Wait-LanHealth $config $InstallRoot)){Stop-LanApplication $config $InstallRoot;throw "Updated app did not become healthy. Inspect logs; verified backup is $backup. No automatic schema downgrade was attempted."}
 Write-Host "Upgrade started. Previous release and verified backup retained: $backup"
}finally{foreach($name in $previous.Keys){[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')}}
