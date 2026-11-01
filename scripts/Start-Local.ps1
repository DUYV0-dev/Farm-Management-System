$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
. (Join-Path $PSScriptRoot 'Configure-Local.ps1')
& java -jar 'target/backend-0.0.1-SNAPSHOT.jar'
if ($LASTEXITCODE -ne 0) { throw 'Backend failed; inspect its database message and configured log.' }
