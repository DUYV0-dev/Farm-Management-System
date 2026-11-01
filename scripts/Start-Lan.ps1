param([string]$InstallRoot=$PSScriptRoot)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Lan-Common.ps1')
Assert-LanLauncherMode
$mutex=New-Object Threading.Mutex($false,'Local\FarmManagement-LAN')
if(-not $mutex.WaitOne(0)){$mutex.Dispose();exit 0}
$appProcess=$null;$startedDatabase=$false
try{
 Set-Location $InstallRoot
 $config=Get-Content -Raw -LiteralPath (Join-Path $InstallRoot 'config.json') | ConvertFrom-Json
 function Secret($name){$value=(Get-Content -Raw -LiteralPath (Join-Path $InstallRoot ($name+'.dpapi'))).Trim() | ConvertTo-SecureString;return [Net.NetworkCredential]::new('',$value).Password}
 $cluster=Join-Path $InstallRoot 'pgdata'
 & (Join-Path $config.postgresBin 'pg_ctl.exe') -D $cluster status *> $null
 if($LASTEXITCODE -ne 0){& (Join-Path $config.postgresBin 'pg_ctl.exe') -D $cluster -l (Join-Path $InstallRoot 'logs\postgres.log') -w start;if($LASTEXITCODE -ne 0){throw 'Cannot start PostgreSQL'};$startedDatabase=$true}
 $env:DB_URL="jdbc:postgresql://127.0.0.1:$($config.databasePort)/farm_management";$env:DB_USERNAME='farm_app';$env:DB_PASSWORD=Secret 'app';$env:JWT_SECRET=Secret 'jwt'
 $env:SPRING_PROFILES_ACTIVE='production';$env:SERVER_PORT=[string]$config.httpsPort
 $env:SERVER_ADDRESS=if($config.PSObject.Properties.Name -contains 'serverAddress'){[string]$config.serverAddress}else{'0.0.0.0'}
 $env:SERVER_SSL_ENABLED='true';$env:SERVER_SSL_KEY_STORE=([Uri](Join-Path $InstallRoot 'lan.p12')).AbsoluteUri;$env:SERVER_SSL_KEY_STORE_TYPE='PKCS12';$env:SERVER_SSL_KEY_STORE_PASSWORD=Secret 'tls'
 $env:LOG_FILE=Join-Path $InstallRoot 'logs\backend.log'
 $appProcess=Start-Process -FilePath $config.java -ArgumentList @('-jar',('"'+$config.jar+'"'),'--debug=false','--trace=false') -WorkingDirectory $InstallRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $InstallRoot 'logs\app.stdout.log') -RedirectStandardError (Join-Path $InstallRoot 'logs\app.stderr.log')
 $appProcess.Id | Set-Content -LiteralPath (Join-Path $InstallRoot 'app.pid') -Encoding ASCII
 $appProcess.WaitForExit();if($appProcess.ExitCode -ne 0){throw 'Application exited. See logs/backend.log'}
}finally{
 if($appProcess -and -not $appProcess.HasExited){Stop-Process -Id $appProcess.Id}
 if($startedDatabase){& (Join-Path $config.postgresBin 'pg_ctl.exe') -D $cluster -m fast -w stop}
 foreach($name in @('DB_PASSWORD','JWT_SECRET','SERVER_SSL_KEY_STORE_PASSWORD')){[Environment]::SetEnvironmentVariable($name,$null,'Process')}
 $mutex.ReleaseMutex();$mutex.Dispose()
}
