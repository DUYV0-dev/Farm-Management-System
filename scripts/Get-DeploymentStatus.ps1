param([string]$InstallRoot=(Join-Path $env:LOCALAPPDATA 'FarmManagement\production'),[string]$VerificationFile)
$ErrorActionPreference='Stop'
$config=Get-Content -Raw -LiteralPath (Join-Path $InstallRoot 'config.json') | ConvertFrom-Json
$health=& curl.exe --silent --show-error --fail --cacert (Join-Path $InstallRoot 'farm-lan-public.crt') "https://localhost:$($config.httpsPort)/actuator/health"
if($LASTEXITCODE -ne 0){throw 'HTTPS health check failed'}
function Read-DeploymentPage([string]$Path) {
 $lines=& curl.exe --silent --show-error --fail --cacert (Join-Path $InstallRoot 'farm-lan-public.crt') "https://localhost:$($config.httpsPort)$Path"
 if($LASTEXITCODE -ne 0){throw "HTTPS page check failed: $Path"}
 return ($lines -join "`n")
}
$portal=Read-DeploymentPage '/'
$storefront=Read-DeploymentPage '/shop/'
$catalog=Read-DeploymentPage '/api/v1/store/products' | ConvertFrom-Json
if($portal -notmatch 'id="appShell"' -or $storefront -notmatch 'id="checkoutForm"'){throw 'Installed pages are not the account portal/storefront release'}
$data=[ordered]@{health=($health | ConvertFrom-Json).status;url="https://localhost:$($config.httpsPort)/";sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $config.jar).Hash;
 portal='PASS';storefront='PASS';publicCatalog='PASS';publishedProducts=@($catalog.data).Count;
 httpsListeners=@(Get-NetTCPConnection -LocalPort $config.httpsPort -State Listen | Select-Object -ExpandProperty LocalAddress);
 databaseListeners=@(Get-NetTCPConnection -LocalPort $config.databasePort -State Listen | Select-Object -ExpandProperty LocalAddress);
 tasks=@(Get-ScheduledTask -TaskName 'FarmManagement-*' | Select-Object TaskName,@{Name='State';Expression={[string]$_.State}});
 networkProfiles=@(Get-NetConnectionProfile | Select-Object Name,@{Name='Category';Expression={[string]$_.NetworkCategory}})}
if($VerificationFile){$verified=Get-Content -Raw -LiteralPath $VerificationFile | ConvertFrom-Json;$data['matchesVerifiedRelease']=$data.sha256 -eq $verified.sha256;if(-not $data.matchesVerifiedRelease){throw 'Installed release differs from verified JAR'}}
$data | ConvertTo-Json -Depth 5
