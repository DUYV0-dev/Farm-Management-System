param([switch]$UseVerifiedBuild,[string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Release-Common.ps1')
if(-not $UseVerifiedBuild){& (Join-Path $PSScriptRoot 'Test-Production.ps1') -PostgresBin $PostgresBin -Browser}
$jar=Join-Path $projectRoot 'target\backend-0.0.1-SNAPSHOT.jar'
$verified=Get-Content -LiteralPath (Join-Path $projectRoot 'target\production-verification.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$sha=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
if(-not $verified.browser -or -not $verified.restartPersistence -or -not $verified.backupRestore -or
   $verified.sha256 -ne $sha -or $verified.sourceFingerprint -ne (Get-SourceFingerprint $projectRoot)) {
    throw 'Current JAR and source must match a complete successful verification.'
}
Assert-ReleaseArtifact $jar $projectRoot
$release=Join-Path $projectRoot ('dist\farm-'+(Get-Date -Format yyyyMMdd-HHmmss)+'-'+$sha.Substring(0,12).ToLowerInvariant())
if(Test-Path -LiteralPath $release){throw 'Release directory exists; releases are immutable.'}
New-Item -ItemType Directory -Path $release -Force | Out-Null
Copy-Item -LiteralPath $jar -Destination (Join-Path $release 'backend.jar')
$manifest=$verified | ConvertTo-Json -Depth 6
$manifest | Set-Content -LiteralPath (Join-Path $release 'release.json') -Encoding UTF8
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Start-Production.ps1') -Destination $release
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Configure-Local.ps1') -Destination $release
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs\ENVIRONMENT.md') -Destination $release
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs\ARCHITECTURE.md') -Destination $release
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs\UI_WORKSPACE.md') -Destination $release
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs\PUBLIC_DEPLOYMENT.md') -Destination $release
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs\ADMIN.md') -Destination $release
New-Item -ItemType Directory -Path (Join-Path $release 'deploy\linux') -Force | Out-Null
foreach($name in @('Dockerfile','compose.yaml','Caddyfile','.env.example')) {
    Copy-Item -LiteralPath (Join-Path $projectRoot ('deploy\linux\'+$name)) -Destination (Join-Path $release 'deploy\linux')
}
Copy-Item -LiteralPath (Join-Path $projectRoot '.dockerignore') -Destination $release
Compress-Archive -LiteralPath $release -DestinationPath ($release+'.zip')
Write-Output "Verified release: $release"
Write-Output "Archive: $release.zip"
