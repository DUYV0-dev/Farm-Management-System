param([string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jar = Join-Path $projectRoot 'target\backend-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Run mvn verify first.' }
function Free-Port {
    $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
$databasePort = Free-Port
$webPort = Free-Port
while ($webPort -eq $databasePort) { $webPort = Free-Port }
$testRoot = Join-Path $projectRoot ('.test-data\database-configuration-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
New-Item -ItemType Directory -Path (Join-Path $testRoot 'backups') | Out-Null
$cluster = Join-Path $testRoot 'pgdata'
$pgCtl = Join-Path $PostgresBin 'pg_ctl.exe'
$psql = Join-Path $PostgresBin 'psql.exe'
$started = $false
$app = $null
$previous = @{}
foreach ($name in @('DB_URL','DB_MIGRATION_USERNAME','DB_MIGRATION_PASSWORD','JWT_SECRET','SERVER_SSL_ENABLED','LOG_FILE')) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process')
}
try {
    $env:SERVER_SSL_ENABLED='false';$env:LOG_FILE='logs/backend.log'
    & (Join-Path $PostgresBin 'initdb.exe') -D $cluster -U farm_owner -A trust --encoding=UTF8 --no-locale *> (Join-Path $testRoot 'initdb.log')
    if ($LASTEXITCODE -ne 0) { throw 'Test initdb failed.' }
    $launcher = Start-Process -FilePath $pgCtl -ArgumentList @('-D',('"'+$cluster+'"'),'-l',('"'+(Join-Path $testRoot 'postgres.log')+'"'),'-o',('"-h 127.0.0.1 -p '+$databasePort+'"'),'-w','start') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $testRoot 'pg-start.log') -RedirectStandardError (Join-Path $testRoot 'pg-start-errors.log')
    if (-not $launcher.WaitForExit(45000)) { throw 'Test PostgreSQL startup timed out.' }
    & $pgCtl -D $cluster status *> (Join-Path $testRoot 'pg-status.log')
    if ($LASTEXITCODE -ne 0) { throw 'Test PostgreSQL failed to start.' }
    $started = $true
    & $psql -X -w -h 127.0.0.1 -p $databasePort -U farm_owner -d postgres -v ON_ERROR_STOP=1 -c 'CREATE DATABASE farm_management' *> (Join-Path $testRoot 'create.log')
    if ($LASTEXITCODE -ne 0) { throw 'Test database creation failed.' }
    $env:DB_URL = "jdbc:postgresql://127.0.0.1:$databasePort/farm_management"
    $env:DB_MIGRATION_USERNAME = 'farm_owner'
    $env:DB_MIGRATION_PASSWORD = 'isolated-test-only'
    & java '-Dloader.main=com.farmmanagement.backend.config.DatabaseMigrations' -cp $jar org.springframework.boot.loader.launch.PropertiesLauncher migrate *> (Join-Path $testRoot 'migration.log')
    if ($LASTEXITCODE -ne 0) { throw 'Test migrations failed.' }
    & $psql -X -w -h 127.0.0.1 -p $databasePort -U farm_owner -d farm_management -v ON_ERROR_STOP=1 -c "INSERT INTO app_user(username,password_hash,full_name,email) VALUES ('backup_probe','test-only','Backup probe','probe@example.invalid')" *> (Join-Path $testRoot 'probe.log')
    if ($LASTEXITCODE -ne 0) { throw 'Cannot insert isolated backup probe.' }
    @{postgresBin=$PostgresBin;databasePort=$databasePort} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $testRoot 'config.json') -Encoding UTF8
    ConvertTo-SecureString 'isolated-test-only' -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -LiteralPath (Join-Path $testRoot 'owner.dpapi')
    $key = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($key) } finally { $rng.Dispose() }
    $env:JWT_SECRET = [Convert]::ToBase64String($key)
    $app = Start-Process java -ArgumentList @('-jar',('"'+$jar+'"'),'--spring.profiles.active=production',('--server.port='+$webPort),'--server.address=127.0.0.1',('--spring.datasource.url='+$env:DB_URL),'--spring.datasource.username=farm_app','--spring.datasource.password=','--app.open-browser=false','--debug=false','--trace=false') -WorkingDirectory $testRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $testRoot 'app.log') -RedirectStandardError (Join-Path $testRoot 'app-errors.log')
    $ready = $false
    for ($attempt=0; $attempt -lt 45; $attempt++) {
        if ($app.HasExited) { break }
        try {
            $response = Invoke-RestMethod "http://127.0.0.1:$webPort/actuator/health" -TimeoutSec 1
            if ($response.status -eq 'UP') { $ready=$true; break }
        } catch { Start-Sleep -Milliseconds 300 }
    }
    if (-not $ready) { throw "Production startup check failed: $testRoot\app.log" }
    & (Join-Path $PSScriptRoot 'Backup-Local.ps1') -InstallRoot $testRoot
    $backup = Get-ChildItem -LiteralPath (Join-Path $testRoot 'backups') -Directory | Select-Object -First 1
    $archive = Join-Path $backup.FullName 'database.dump'
    $metadata = Get-Content -LiteralPath (Join-Path $backup.FullName 'backup.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($metadata.sha256 -ne (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash) { throw 'Backup checksum mismatch.' }
    & $psql -X -w -h 127.0.0.1 -p $databasePort -U farm_owner -d postgres -v ON_ERROR_STOP=1 -c 'CREATE DATABASE configuration_restored' *> (Join-Path $testRoot 'restore-create.log')
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create restore test database.' }
    & (Join-Path $PostgresBin 'pg_restore.exe') -w -h 127.0.0.1 -p $databasePort -U farm_owner -d configuration_restored --exit-on-error $archive *> (Join-Path $testRoot 'restore.log')
    if ($LASTEXITCODE -ne 0) { throw 'Backup restore failed.' }
    $count = & $psql -X -w -t -A -h 127.0.0.1 -p $databasePort -U farm_owner -d configuration_restored -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM app_user WHERE username='backup_probe'"
    if ($LASTEXITCODE -ne 0 -or $count.Trim() -ne '1') { throw 'Restored data mismatch.' }
    $health = Invoke-RestMethod "http://127.0.0.1:$webPort/actuator/health" -TimeoutSec 2
    if ($health.status -ne 'UP' -or $app.HasExited) { throw 'Backup interrupted the running application.' }
    Write-Output "PASS: production startup as farm_app, online backup, checksum, actual restore and retained application health. Evidence: $testRoot"
} finally {
    if ($app -and -not $app.HasExited) { Stop-Process -Id $app.Id; $app.WaitForExit(10000) | Out-Null }
    if ($started) { & $pgCtl -D $cluster -m fast -w stop }
    foreach ($name in $previous.Keys) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
}
