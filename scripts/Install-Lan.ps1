param([string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin',
 [string]$InstallRoot=(Join-Path $env:LOCALAPPDATA 'FarmManagement\production'),
 [int]$DatabasePort=55433,[int]$HttpsPort=8443,[switch]$Autostart,[switch]$OpenFirewall,[switch]$TrustCertificate)
$ErrorActionPreference='Stop'
$sourceRoot=Split-Path $PSScriptRoot -Parent
$jar=Join-Path $sourceRoot 'target\backend-0.0.1-SNAPSHOT.jar'
if(-not(Test-Path -LiteralPath $jar)){throw 'Build and test the release first: scripts/Test-Production.ps1 -Browser'}
$verification=Get-Content -Raw -LiteralPath (Join-Path $sourceRoot 'target\production-verification.json') | ConvertFrom-Json
if(-not $verification.browser -or -not $verification.backupRestore -or $verification.sha256 -ne (Get-FileHash -Algorithm SHA256 -LiteralPath $jar).Hash){throw 'Release must pass production/browser/recovery verification before installation'}
if(Test-Path -LiteralPath $InstallRoot){throw 'Installation directory exists. Use the upgrade procedure; existing data has not been changed.'}
$java=(Get-Command java).Source
$keytool=Join-Path (Split-Path $java -Parent) 'keytool.exe'
if(-not(Test-Path $keytool)){$keytool=Join-Path $env:JAVA_HOME 'bin\keytool.exe'}
foreach($tool in @($java,$keytool,(Join-Path $PostgresBin 'initdb.exe'))){if(-not(Test-Path -LiteralPath $tool)){throw "Missing tool: $tool"}}
foreach($port in @($DatabasePort,$HttpsPort)){if(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue){throw "Port already occupied: $port"}}
New-Item -ItemType Directory -Path $InstallRoot | Out-Null
$InstallRoot=(Resolve-Path -LiteralPath $InstallRoot).Path
$sid=[System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $InstallRoot /inheritance:r /grant:r "*${sid}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
if($LASTEXITCODE -ne 0){throw 'Cannot restrict installation directory permissions'}
foreach($dir in @('logs','backups','releases')){New-Item -ItemType Directory -Path (Join-Path $InstallRoot $dir) | Out-Null}
function New-Secret {
 $bytes=New-Object byte[] 32;$rng=[Security.Cryptography.RandomNumberGenerator]::Create()
 try{$rng.GetBytes($bytes);return [Convert]::ToBase64String($bytes)}finally{$rng.Dispose();[Array]::Clear($bytes,0,$bytes.Length)}
}
$secrets=@{owner=New-Secret;app=New-Secret;jwt=New-Secret;tls=New-Secret}
foreach($name in $secrets.Keys){ConvertTo-SecureString $secrets[$name] -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -LiteralPath (Join-Path $InstallRoot ($name+'.dpapi')) -Encoding ASCII}
$cluster=Join-Path $InstallRoot 'pgdata';$pwFile=Join-Path $InstallRoot 'init-password.tmp'
$previous=@{};foreach($name in @('PGPASSWORD','DB_URL','DB_MIGRATION_USERNAME','DB_MIGRATION_PASSWORD','DB_APP_PASSWORD','FM_TLS_PASSWORD')){$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
$started=$false
try{
 [IO.File]::WriteAllText($pwFile,$secrets.owner+[Environment]::NewLine,[Text.Encoding]::ASCII)
 & (Join-Path $PostgresBin 'initdb.exe') -D $cluster -U farm_owner -A scram-sha-256 --encoding=UTF8 --no-locale --pwfile=$pwFile *> (Join-Path $InstallRoot 'logs\install-postgres.log')
 if($LASTEXITCODE -ne 0){throw 'PostgreSQL initialization failed. See install-postgres.log'}
 Add-Content -LiteralPath (Join-Path $cluster 'postgresql.conf') -Value @("listen_addresses='127.0.0.1'","port=$DatabasePort",'password_encryption=scram-sha-256')
 & (Join-Path $PostgresBin 'pg_ctl.exe') -D $cluster -l (Join-Path $InstallRoot 'logs\postgres.log') -w start
 if($LASTEXITCODE -ne 0){throw 'Cannot start private PostgreSQL cluster'}
 $started=$true;$env:PGPASSWORD=$secrets.owner
 & (Join-Path $PostgresBin 'psql.exe') -X -h 127.0.0.1 -p $DatabasePort -U farm_owner -d postgres -w -v ON_ERROR_STOP=1 -c 'CREATE DATABASE farm_management' *> (Join-Path $InstallRoot 'logs\create-database.log')
 if($LASTEXITCODE -ne 0){throw 'Cannot create production database'}
 $release=Join-Path $InstallRoot ('releases\farm-'+(Get-Date -Format yyyyMMdd-HHmmss)+'.jar');Copy-Item -LiteralPath $jar -Destination $release
 $env:DB_URL="jdbc:postgresql://127.0.0.1:$DatabasePort/farm_management";$env:DB_MIGRATION_USERNAME='farm_owner';$env:DB_MIGRATION_PASSWORD=$secrets.owner;$env:DB_APP_PASSWORD=$secrets.app
 & $java '-Dloader.main=com.farmmanagement.backend.config.DatabaseMigrations' -cp $release org.springframework.boot.loader.launch.PropertiesLauncher migrate *> (Join-Path $InstallRoot 'logs\migration.log')
 if($LASTEXITCODE -ne 0){throw 'Production migration failed. See migration.log'}
 $addresses=@(Get-NetIPAddress -AddressFamily IPv4 | Where-Object {$_.IPAddress -ne '127.0.0.1' -and $_.IPAddress -notlike '169.254.*'} | Select-Object -ExpandProperty IPAddress)
 $san='dns:localhost,dns:'+$env:COMPUTERNAME+',ip:127.0.0.1';foreach($address in $addresses){$san+=',ip:'+$address}
 $env:FM_TLS_PASSWORD=$secrets.tls
 $ErrorActionPreference='Continue'
 & $keytool -genkeypair -alias farm-lan -keyalg RSA -keysize 3072 -validity 365 -storetype PKCS12 -keystore (Join-Path $InstallRoot 'lan.p12') -storepass:env FM_TLS_PASSWORD -dname "CN=$env:COMPUTERNAME,OU=FarmManagement" -ext "SAN=$san" -noprompt 2>&1 | Out-File -Encoding UTF8 -LiteralPath (Join-Path $InstallRoot 'logs\certificate.log')
 $certExit=$LASTEXITCODE;$ErrorActionPreference='Stop'
 if($certExit -ne 0){throw 'Cannot generate LAN TLS certificate'}
 $ErrorActionPreference='Continue'
 & $keytool -exportcert -alias farm-lan -keystore (Join-Path $InstallRoot 'lan.p12') -storepass:env FM_TLS_PASSWORD -rfc -file (Join-Path $InstallRoot 'farm-lan-public.crt') 2>&1 | Out-File -Encoding UTF8 -LiteralPath (Join-Path $InstallRoot 'logs\certificate-export.log')
 $certExit=$LASTEXITCODE;$ErrorActionPreference='Stop'
 if($certExit -ne 0){throw 'Cannot export public certificate'}
 if($TrustCertificate){Import-Certificate -FilePath (Join-Path $InstallRoot 'farm-lan-public.crt') -CertStoreLocation Cert:\CurrentUser\Root | Out-Null}
 @{java=$java;postgresBin=$PostgresBin;databasePort=$DatabasePort;httpsPort=$HttpsPort;jar=$release;hostName=$env:COMPUTERNAME;addresses=$addresses} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $InstallRoot 'config.json') -Encoding UTF8
 foreach($script in @('Start-Lan.ps1','Backup-Lan.ps1','Update-Lan.ps1','Lan-Common.ps1','database_recovery.py')){Copy-Item -LiteralPath (Join-Path $PSScriptRoot $script) -Destination (Join-Path $InstallRoot $script)}
 if($OpenFirewall){& (Join-Path $PSScriptRoot 'Enable-LanFirewall.ps1') -HttpsPort $HttpsPort}
 if($Autostart){
  $action=New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoProfile -WindowStyle Hidden -File "'+(Join-Path $InstallRoot 'Start-Lan.ps1')+'"')
  $trigger=New-ScheduledTaskTrigger -AtLogOn -User ([Security.Principal.WindowsIdentity]::GetCurrent().Name)
  $principal=New-ScheduledTaskPrincipal -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive
  $settings=New-ScheduledTaskSettingsSet -StartWhenAvailable -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
  Register-ScheduledTask -TaskName 'FarmManagement-LAN' -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Description 'Farm Management HTTPS for the private local network' | Out-Null
  $backupAction=New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoProfile -WindowStyle Hidden -File "'+(Join-Path $InstallRoot 'Backup-Lan.ps1')+'"')
  $backupTrigger=New-ScheduledTaskTrigger -Daily -At '02:00'
  Register-ScheduledTask -TaskName 'FarmManagement-Backup' -Action $backupAction -Trigger $backupTrigger -Principal $principal -Settings $settings -Description 'Verified nightly backup; runs while this Windows user is logged on' | Out-Null
 }
 Write-Host "Installed: $InstallRoot"
 Write-Host "Local URL: https://localhost:$HttpsPort/"
 foreach($address in $addresses){Write-Host "LAN URL: https://${address}:$HttpsPort/"}
 Write-Host 'Trust farm-lan-public.crt on client devices after verifying its fingerprint. The app starts with Start-Lan.ps1.'
}finally{
 if(Test-Path -LiteralPath $pwFile){Remove-Item -LiteralPath $pwFile -Force}
 if($started){& (Join-Path $PostgresBin 'pg_ctl.exe') -D $cluster -m fast -w stop}
 foreach($name in $previous.Keys){[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')}
 $secrets.Clear()
}
