param([string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin',[switch]$Browser,
      [string]$Chrome='C:\Program Files\Google\Chrome\Application\chrome.exe')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$scriptsRoot=Join-Path $projectRoot 'scripts'
. (Join-Path $scriptsRoot 'Release-Common.ps1')
Set-Location $projectRoot
$testRoot=Join-Path $projectRoot ('.test-data\production-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$cluster=Join-Path $testRoot 'pgdata'
$pgCtl=Join-Path $PostgresBin 'pg_ctl.exe'
$psql=Join-Path $PostgresBin 'psql.exe'
$jar=Join-Path $projectRoot 'target\backend-0.0.1-SNAPSHOT.jar'
$started=$false;$appProcess=$null;$browserProcess=$null;$launchNumber=0
$previous=@{}
foreach($name in @('DB_URL','DB_MIGRATION_USERNAME','DB_MIGRATION_PASSWORD','JWT_SECRET','RUN_DB_CONTEXT_TEST','PERSISTENCE_STATE_FILE','SERVER_SSL_ENABLED','SERVER_PORT','LOG_FILE')) {
    $previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process')
}
function Start-TestDatabase {
    $script:launchNumber++
    $script:started=$true
    $process=Start-Process -FilePath $pgCtl -ArgumentList @('-D',('"'+$cluster+'"'),'-l',('"'+(Join-Path $testRoot 'postgres.log')+'"'),'-o','"-h 127.0.0.1 -p 55432"','-w','start') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $testRoot "pg-start-$launchNumber.log") -RedirectStandardError (Join-Path $testRoot "pg-start-$launchNumber.err")
    if(-not $process.WaitForExit(45000)){throw 'Temporary PostgreSQL startup timed out.'}
    & $pgCtl -D $cluster status *> (Join-Path $testRoot 'pg-status.log')
    if($LASTEXITCODE -ne 0){throw 'Cannot start the isolated PostgreSQL cluster.'}
}
function Stop-TestApp {
    if($script:appProcess -and -not $script:appProcess.HasExited){Stop-Process -Id $script:appProcess.Id;$script:appProcess.WaitForExit(10000) | Out-Null}
    $script:appProcess=$null
}
function Start-TestApp {
    $script:launchNumber++
    $script:appProcess=Start-Process java -ArgumentList @('-jar',('"'+$jar+'"'),'--spring.profiles.active=production','--debug=false','--trace=false','--server.port=18080','--server.address=127.0.0.1','--spring.datasource.url=jdbc:postgresql://127.0.0.1:55432/management_test','--spring.datasource.username=farm_app','--spring.datasource.password=','--app.open-browser=false','--app.frontend.working-tree=true') -WorkingDirectory $testRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $testRoot "app-$launchNumber.log") -RedirectStandardError (Join-Path $testRoot "app-$launchNumber.err")
    $ready=$false;$deadline=[DateTime]::UtcNow.AddSeconds(45)
    while([DateTime]::UtcNow -lt $deadline){
        if($script:appProcess.HasExited){break}
        try{if((Invoke-RestMethod 'http://127.0.0.1:18080/actuator/health' -TimeoutSec 1).status -eq 'UP'){$ready=$true;break}}catch{Start-Sleep -Milliseconds 300}
    }
    if(-not $ready){throw "Packaged application failed to start; see $testRoot\app-$launchNumber.log"}
}
try {
    foreach($port in @(55432,18080,9223)) {
        if([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners().Port -contains $port){throw "Test port occupied: $port"}
    }
    $env:SERVER_SSL_ENABLED='false';$env:SERVER_PORT='18080';$env:LOG_FILE='logs/backend.log'
    $sourceFingerprint=Get-SourceFingerprint $projectRoot
    & .\mvnw.cmd -o -B -ntp clean package -DskipTests *> (Join-Path $testRoot 'build.log')
    if($LASTEXITCODE -ne 0){throw "Clean build failed: $testRoot\build.log"}
    Assert-ReleaseArtifact $jar $projectRoot
    & (Join-Path $PostgresBin 'initdb.exe') -D $cluster -U postgres -A trust --encoding=UTF8 --no-locale *> (Join-Path $testRoot 'initdb.log')
    if($LASTEXITCODE -ne 0){throw 'Temporary initdb failed.'}
    Start-TestDatabase
    & $psql -X -w -h 127.0.0.1 -p 55432 -U postgres -d postgres -v ON_ERROR_STOP=1 -c 'CREATE DATABASE management_test' *> (Join-Path $testRoot 'create.log')
    if($LASTEXITCODE -ne 0){throw 'Cannot create the isolated test database.'}
    $env:DB_URL='jdbc:postgresql://127.0.0.1:55432/management_test'
    $env:DB_MIGRATION_USERNAME='postgres';$env:DB_MIGRATION_PASSWORD='IsolatedTestOnly123'
    foreach($command in @('migrate','migrate','validate')) {
        & java '-Dloader.main=com.farmmanagement.backend.config.DatabaseMigrations' -cp $jar org.springframework.boot.loader.launch.PropertiesLauncher $command *> (Join-Path $testRoot ('migration-'+$command+'.log'))
        if($LASTEXITCODE -ne 0){throw "Migration $command failed."}
    }
    $env:RUN_DB_CONTEXT_TEST='false'
    $ErrorActionPreference='Continue'
    & .\mvnw.cmd -o -B -ntp verify '-Dworkspace.test.db=true' 2>&1 | Out-File -Encoding UTF8 -LiteralPath (Join-Path $testRoot 'tests.log')
    $testExit=$LASTEXITCODE;$ErrorActionPreference='Stop'
    if($testExit -ne 0){throw "Unit/integration tests failed: $testRoot\tests.log"}
    Assert-ReleaseArtifact $jar $projectRoot
    # A production JAR must ignore working-tree overrides, even beside poisoned assets.
    $poisonRoot=Join-Path $testRoot 'frontend\app'
    New-Item -ItemType Directory -Path $poisonRoot -Force | Out-Null
    'UNPACKAGED ASSET MUST NEVER BE SERVED' | Set-Content -LiteralPath (Join-Path $poisonRoot 'index-shell.html')
    $poisonFeature=Join-Path $testRoot 'frontend\features\operations'
    New-Item -ItemType Directory -Path $poisonFeature -Force | Out-Null
    'UNPACKAGED FEATURE MUST NEVER BE SERVED' | Set-Content -LiteralPath (Join-Path $poisonFeature 'operations.html')
    $jwtBytes=New-Object byte[] 32;$rng=[Security.Cryptography.RandomNumberGenerator]::Create()
    try{$rng.GetBytes($jwtBytes)}finally{$rng.Dispose()}
    $env:JWT_SECRET=[Convert]::ToBase64String($jwtBytes)
    Start-TestApp
    if((Invoke-WebRequest 'http://127.0.0.1:18080/' -UseBasicParsing).Content -notmatch 'id="authScreen"'){throw 'Production served unpackaged frontend.'}
    $restartPersistence=$false
    if($Browser) {
        $env:PERSISTENCE_STATE_FILE=Join-Path $testRoot 'browser-state.json'
        $browserProcess=Start-Process $Chrome -ArgumentList @('--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9223',('--user-data-dir="'+(Join-Path $testRoot 'chrome')+'"'),'about:blank') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $testRoot 'chrome.log') -RedirectStandardError (Join-Path $testRoot 'chrome.err')
        $ready=$false
        for($attempt=0;$attempt -lt 45;$attempt++){try{$null=Invoke-RestMethod 'http://127.0.0.1:9223/json' -TimeoutSec 1;$ready=$true;break}catch{Start-Sleep -Milliseconds 300}}
        if(-not $ready){throw 'Test browser did not start.'}
        $ErrorActionPreference='Continue'
        & node frontend/tests/production-browser.cjs 2>&1 | Out-File -Encoding UTF8 -LiteralPath (Join-Path $testRoot 'browser.log')
        $browserExit=$LASTEXITCODE;$ErrorActionPreference='Stop'
        if($browserExit -ne 0){throw "Browser flow failed: $testRoot\browser.log"}
        $ErrorActionPreference='Continue'
        & node frontend/tests/admin-browser.cjs $PostgresBin 2>&1 | Out-File -Encoding UTF8 -LiteralPath (Join-Path $testRoot 'admin-browser.log')
        $adminBrowserExit=$LASTEXITCODE;$ErrorActionPreference='Stop'
        if($adminBrowserExit -ne 0){throw "Admin browser flow failed: $testRoot\admin-browser.log"}
        $fixture=Get-Content -LiteralPath $env:PERSISTENCE_STATE_FILE -Raw | ConvertFrom-Json
        $fixtureFarm=[int]$fixture.farmId
        $sql="SELECT count(*) FROM public.farming_task WHERE farm_id=$fixtureFarm AND title='Persisted browser task'"
        $before=& $psql -X -w -t -A -h 127.0.0.1 -p 55432 -U postgres -d management_test -v ON_ERROR_STOP=1 -c $sql
        if($LASTEXITCODE -ne 0 -or $before.Trim() -ne '1'){throw 'The UI-created task was not found in PostgreSQL.'}
    }
    Stop-TestApp
    $backup=Join-Path $testRoot 'backup'
    $recoveryArgs=@('--backup-dir',$backup,'--host','127.0.0.1','--port','55432','--username','postgres','--postgres-bin',$PostgresBin)
    & python scripts/database_recovery.py backup --database management_test --writers-paused @recoveryArgs *> (Join-Path $testRoot 'backup.log')
    if($LASTEXITCODE -ne 0){throw 'Schema/data backup failed.'}
    & python scripts/database_recovery.py restore --database production_restored @recoveryArgs *> (Join-Path $testRoot 'restore.log')
    if($LASTEXITCODE -ne 0){throw 'Backup restoration/data comparison failed.'}
    if($Browser) {
        & $pgCtl -D $cluster -m fast -w stop *> (Join-Path $testRoot 'pg-stop.log')
        if($LASTEXITCODE -ne 0){throw 'Temporary PostgreSQL clean shutdown failed.'}
        $started=$false;Start-TestDatabase
        $after=& $psql -X -w -t -A -h 127.0.0.1 -p 55432 -U postgres -d management_test -v ON_ERROR_STOP=1 -c $sql
        if($LASTEXITCODE -ne 0 -or $after.Trim() -ne '1'){throw 'The UI-created task did not survive PostgreSQL restart.'}
        Start-TestApp
        $ErrorActionPreference='Continue'
        & node frontend/tests/persistence-browser.cjs 2>&1 | Out-File -Encoding UTF8 -LiteralPath (Join-Path $testRoot 'persistence-browser.log')
        $persistenceExit=$LASTEXITCODE;$ErrorActionPreference='Stop'
        if($persistenceExit -ne 0){throw "Restart/persistence browser check failed: $testRoot\persistence-browser.log"}
        $restartPersistence=$true
    }
    if($sourceFingerprint -ne (Get-SourceFingerprint $projectRoot)){throw 'Production source changed during verification. Rebuild before release.'}
    $totals=@{tests=0;failures=0;errors=0;skipped=0}
    foreach($file in (Get-ChildItem -LiteralPath 'target\surefire-reports' -Filter 'TEST-*.xml')){
        [xml]$report=Get-Content -LiteralPath $file.FullName -Raw
        foreach($key in @('tests','failures','errors','skipped')){$totals[$key]+=[int]$report.testsuite.$key}
    }
    $totals['passed']=$totals.tests-$totals.skipped-$totals.failures-$totals.errors
    [ordered]@{status='PASS';sha256=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash;sourceFingerprint=$sourceFingerprint;
        browser=[bool]$Browser;adminBrowser=[bool]$Browser;restartPersistence=$restartPersistence;backupRestore=$true;artifactVerified=$true;tests=$totals;
        evidence=$testRoot;verifiedAt=[DateTimeOffset]::UtcNow.ToString('o')} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath 'target\production-verification.json' -Encoding UTF8
    Write-Output "Production checks passed. Evidence: $testRoot"
} finally {
    Stop-TestApp
    if($browserProcess -and -not $browserProcess.HasExited){Stop-Process -Id $browserProcess.Id}
    if($started){$ErrorActionPreference='Continue';& $pgCtl -D $cluster -m fast -w stop *> (Join-Path $testRoot 'pg-final-stop.log')}
    foreach($name in $previous.Keys){[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')}
}
