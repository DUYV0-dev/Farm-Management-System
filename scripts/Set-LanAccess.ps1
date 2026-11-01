param([ValidateSet('Local','LAN')][string]$Access='Local',
 [string]$InstallRoot=(Join-Path $env:LOCALAPPDATA 'FarmManagement\production'))
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Lan-Common.ps1')
Assert-LanLauncherMode
& (Join-Path $PSScriptRoot 'Backup-Lan.ps1') -InstallRoot $InstallRoot -LeaveStopped
$configPath=Join-Path $InstallRoot 'config.json';$config=Get-Content -Raw -LiteralPath $configPath | ConvertFrom-Json
$address=if($Access -eq 'Local'){'127.0.0.1'}else{'0.0.0.0'}
$config | Add-Member -MemberType NoteProperty -Name serverAddress -Value $address -Force
$config | ConvertTo-Json | Set-Content -LiteralPath $configPath -Encoding UTF8
$source=Join-Path $PSScriptRoot 'Start-Lan.ps1';$target=Join-Path $InstallRoot 'Start-Lan.ps1'
if([IO.Path]::GetFullPath($source) -ne [IO.Path]::GetFullPath($target)){Copy-Item -LiteralPath $source -Destination $target -Force}
Start-LanApplication $InstallRoot
if(-not(Wait-LanHealth $config $InstallRoot)){throw 'Application did not become healthy after changing access mode'}
Write-Host "Access mode: $Access. HTTPS listening on ${address}:$($config.httpsPort). Windows network profile was not changed."
