param(
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [switch]$Browser,
    [string]$Chrome = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$projectRoot = (Get-Location).Path
$pgCtl = Join-Path $PostgresBin 'pg_ctl.exe'
$psql = Join-Path $PostgresBin 'psql.exe'
$initdb = Join-Path $PostgresBin 'initdb.exe'
if (-not (Test-Path -LiteralPath $initdb)) { throw 'PostgreSQL bin directory not found. Pass -PostgresBin.' }
foreach ($port in @(55432) + $(if ($Browser) { @(18080,9223) } else { @() })) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try { $client.Connect('127.0.0.1',$port); throw "Port $port is occupied; stop the previous test process first." }
    catch [System.Net.Sockets.SocketException] { }
    finally { $client.Dispose() }
}
$testDirectory = Join-Path $projectRoot ('target\management-check-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testDirectory -Force | Out-Null
$cluster = Join-Path $testDirectory 'pgdata'
$started = $false
$appProcess = $null
$browserProcess = $null
function Run-Sql([string[]]$Files) {
    $sqlArgs = @('-h','127.0.0.1','-p','55432','-U','postgres','-d','management_test','-w','-v','ON_ERROR_STOP=1')
    foreach ($file in $Files) { $sqlArgs += @('-f',$file) }
    & $psql @sqlArgs
    if ($LASTEXITCODE -ne 0) { throw 'SQL setup failed.' }
}
try {
    & $initdb -D $cluster -U postgres -A trust --encoding=UTF8 --no-locale
    if ($LASTEXITCODE -ne 0) { throw 'initdb failed.' }
    & $pgCtl -D $cluster -l (Join-Path $testDirectory 'postgres.log') -o '-h 127.0.0.1 -p 55432' -w start
    if ($LASTEXITCODE -ne 0) { throw 'Test PostgreSQL failed to start.' }
    $started = $true
    & $psql -h 127.0.0.1 -p 55432 -U postgres -d postgres -w -v ON_ERROR_STOP=1 -c 'CREATE DATABASE management_test'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create test database.' }
    Run-Sql @('src/test/resources/management-bootstrap.sql','database/05_Authentication_Sessions.sql','database/06_User_Role_Management.sql','database/07_Farm_Plot_Crop.sql','database/08_Material_Warehouse.sql','database/09_Management_Integrity.sql')
    # Prove the module migrations are repeatable on an already installed schema.
    Run-Sql @('database/07_Farm_Plot_Crop.sql','database/08_Material_Warehouse.sql','database/09_Management_Integrity.sql')
    & .\mvnw.cmd -o package '-Dmanagement.test.db=true'
    if ($LASTEXITCODE -ne 0) { throw 'Backend tests or packaging failed.' }
    if ($Browser) {
        if (-not (Test-Path -LiteralPath $Chrome)) { throw 'Chrome not found. Pass -Chrome.' }
        $username = & $psql -h 127.0.0.1 -p 55432 -U postgres -d management_test -w -t -A -c 'SELECT username FROM app_user ORDER BY user_id DESC LIMIT 1'
        $username.Trim() | Set-Content -Encoding UTF8 target/browser-username.txt
        $random = New-Object byte[] 32
        $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        $generator.GetBytes($random)
        $generator.Dispose()
        $testJwt = [Convert]::ToBase64String($random)
        $argumentFile = Join-Path $testDirectory 'app.args'
        @('-jar', 'target/backend-0.0.1-SNAPSHOT.jar', '--server.port=18080',
          '--spring.datasource.url=jdbc:postgresql://127.0.0.1:55432/management_test',
          '--spring.datasource.username=farm_app', '--spring.datasource.password=',
          ('--auth.jwt.secret='+$testJwt), '--logging.file.name=target/browser-app.log') |
          Set-Content -Encoding ASCII -LiteralPath $argumentFile
        $appProcess = Start-Process -FilePath 'java' -ArgumentList ('@"'+$argumentFile+'"') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $testDirectory 'app.stdout.log') -RedirectStandardError (Join-Path $testDirectory 'app.stderr.log')
        $ready = $false
        for ($attempt=0; $attempt -lt 60; $attempt++) {
            try { $null=Invoke-WebRequest 'http://127.0.0.1:18080/' -UseBasicParsing -TimeoutSec 1; $ready=$true; break } catch { Start-Sleep -Milliseconds 500 }
        }
        if (-not $ready) { throw 'Test application did not start. Inspect app logs.' }
        $profile = Join-Path $testDirectory 'chrome'
        $browserProcess = Start-Process -FilePath $Chrome -ArgumentList @('--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9223',('--user-data-dir="'+$profile+'"'),'about:blank') -WindowStyle Hidden -PassThru
        for ($attempt=0; $attempt -lt 30; $attempt++) {
            try { $null=Invoke-WebRequest 'http://127.0.0.1:9223/json' -UseBasicParsing -TimeoutSec 1; break } catch { Start-Sleep -Milliseconds 300 }
        }
        & node frontend/tests/management-browser.cjs
        if ($LASTEXITCODE -ne 0) { throw 'Browser verification failed.' }
    }
    Write-Host 'Management verification PASSED. Reports: target/surefire-reports; JAR: target/backend-0.0.1-SNAPSHOT.jar'
} finally {
    if ($browserProcess -and -not $browserProcess.HasExited) { Stop-Process -Id $browserProcess.Id }
    if ($appProcess -and -not $appProcess.HasExited) { Stop-Process -Id $appProcess.Id }
    if ($started) { & $pgCtl -D $cluster -m fast -w stop }
    # Keep the isolated database and logs for inspection; never delete a user database.
}
