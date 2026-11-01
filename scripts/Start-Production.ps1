param([Parameter(Mandatory=$true)][string]$ReleaseJar)
$ErrorActionPreference='Stop'
$jar=(Resolve-Path -LiteralPath $ReleaseJar).Path
$manifestPath=Join-Path (Split-Path $jar -Parent) 'release.json'
$manifest=Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if(-not $manifest.browser -or -not $manifest.restartPersistence -or -not $manifest.backupRestore -or
   $manifest.sha256 -ne (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash){
    throw 'Production requires a release built by Build-Release.ps1 with browser, restart/persistence and recovery verification.'
}
. (Join-Path $PSScriptRoot 'Configure-Local.ps1')
& java -jar $jar '--app.open-browser=false' '--app.frontend.working-tree=false'
if($LASTEXITCODE -ne 0){throw 'Production backend exited with an error. Inspect its log.'}
