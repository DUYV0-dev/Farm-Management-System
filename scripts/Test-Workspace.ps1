param([string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin',[switch]$Browser,[switch]$LegacyErd,[switch]$SchemaOnly,
    [string]$Chrome='C:\Program Files\Google\Chrome\Application\chrome.exe')
$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$root=(Get-Location).Path
$testRoot=Join-Path $root ('.test-data\workspace-check-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
$cluster=Join-Path $testRoot 'pgdata'
$pgCtl=Join-Path $PostgresBin 'pg_ctl.exe'
$psql=Join-Path $PostgresBin 'psql.exe'
$started=$false
$appProcess=$null
$browserProcess=$null
try {
    & (Join-Path $PostgresBin 'initdb.exe') -D $cluster -U postgres -A trust --encoding=UTF8 --no-locale
    if($LASTEXITCODE -ne 0) { throw 'initdb failed' }
    & $pgCtl -D $cluster -l (Join-Path $testRoot 'postgres.log') -o '-h 127.0.0.1 -p 55432' -w start
    if($LASTEXITCODE -ne 0) { throw 'Test PostgreSQL failed to start' }
    $started=$true
    & $psql -h 127.0.0.1 -p 55432 -U postgres -d postgres -w -v ON_ERROR_STOP=1 -c 'CREATE DATABASE management_test'
    if($LASTEXITCODE -ne 0) { throw 'Cannot create test database' }
    $schemaFiles=@('src/test/resources/management-bootstrap.sql','database/05_Authentication_Sessions.sql',
        'database/06_User_Role_Management.sql','database/07_Farm_Plot_Crop.sql','database/08_Material_Warehouse.sql',
        'database/09_Management_Integrity.sql','database/10_Farm_Expense_Management.sql','database/11a_Material_Usage_Warehouse.sql','database/12_Farm_Tenancy.sql','database/12_Farm_Tenancy.sql')
    if($LegacyErd) {
        $conversionFile=Join-Path $testRoot 'conversion.sql'
        & (Join-Path $PSScriptRoot 'Write-ErdWorkspaceMigration.ps1') -OutputFile $conversionFile
        $schemaFiles=@('src/test/resources/management-bootstrap.sql','database/05_Authentication_Sessions.sql',
            'database/06_User_Role_Management.sql','src/test/resources/empty-erd-workspace-bootstrap.sql',$conversionFile)
    }
    foreach($file in $schemaFiles) {
        # The combined ERD conversion has no inner BEGIN/COMMIT statements.
        $transactionArgs=@()
        if($LegacyErd -and $file -eq $conversionFile) {
            & (Join-Path $PSScriptRoot 'Test-ErdWorkspaceRollback.ps1') -Psql $psql -ConversionFile $conversionFile -TestRoot $testRoot
            $transactionArgs=@('--single-transaction')
        }
        & $psql -X -h 127.0.0.1 -p 55432 -U postgres -d management_test -w -v ON_ERROR_STOP=1 @transactionArgs -f $file
        if($LASTEXITCODE -ne 0) { throw "Migration failed: $file" }
    }
    if($LegacyErd) {
        & $psql -X -h 127.0.0.1 -p 55432 -U postgres -d management_test -w -v ON_ERROR_STOP=1 -f src/test/resources/empty-erd-workspace-check.sql
        if($LASTEXITCODE -ne 0) { throw 'ERD preservation and business-write checks failed' }
    }
    if($SchemaOnly) { Write-Host "Schema checks passed. Isolated data and logs: $testRoot"; return }
    & .\mvnw.cmd -o package '-Dworkspace.test.db=true'
    if($LASTEXITCODE -ne 0) { throw 'Workspace tests failed' }
    if($Browser) {
        $argumentFile=Join-Path $testRoot 'app.args'
        $jwtBytes=New-Object byte[] 32
        $random=[System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $random.GetBytes($jwtBytes) } finally { $random.Dispose() }
        $jwtKey=[Convert]::ToBase64String($jwtBytes)
        @('-jar',('"'+(Join-Path $root 'target/backend-0.0.1-SNAPSHOT.jar').Replace('\','/')+'"'),'--server.port=18080',
            '--spring.profiles.active=production','--server.address=127.0.0.1',
            '--spring.datasource.url=jdbc:postgresql://127.0.0.1:55432/management_test',
            '--spring.datasource.username=farm_app','--spring.datasource.password=',
            ('--auth.jwt.secret='+$jwtKey),
            '--app.open-browser=false','--logging.file.name=target/workspace-browser.log') | Set-Content -Encoding ASCII -LiteralPath $argumentFile
        $appProcess=Start-Process java -ArgumentList ('@"'+$argumentFile+'"') -WorkingDirectory $testRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $testRoot 'app.stdout.log') -RedirectStandardError (Join-Path $testRoot 'app.stderr.log')
        $ready=$false
        for($attempt=0;$attempt -lt 60;$attempt++) {
            try { $null=Invoke-WebRequest 'http://127.0.0.1:18080/' -UseBasicParsing -TimeoutSec 1;$ready=$true;break } catch { Start-Sleep -Milliseconds 500 }
        }
        if(-not $ready) { throw 'Browser test app did not start' }
        $browserProcess=Start-Process $Chrome -ArgumentList @('--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9223',('--user-data-dir="'+(Join-Path $testRoot 'chrome')+'"'),'about:blank') -WindowStyle Hidden -PassThru
        for($attempt=0;$attempt -lt 30;$attempt++) {
            try { $null=Invoke-WebRequest 'http://127.0.0.1:9223/json' -UseBasicParsing -TimeoutSec 1;break } catch { Start-Sleep -Milliseconds 300 }
        }
        & node frontend/tests/restored-prototypes-browser.cjs
        if($LASTEXITCODE -ne 0) { throw 'Workspace browser test failed' }
    }
    Write-Host "Workspace checks passed. Isolated data and logs: $testRoot"
} finally {
    if($browserProcess -and -not $browserProcess.HasExited) { Stop-Process -Id $browserProcess.Id }
    if($appProcess -and -not $appProcess.HasExited) { Stop-Process -Id $appProcess.Id }
    if($started) { & $pgCtl -D $cluster -m fast -w stop }
}
