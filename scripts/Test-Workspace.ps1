param([string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin',[switch]$Browser)
# Compatibility entry point. The only current schema/test pipeline is Test-Production.
& (Join-Path $PSScriptRoot 'Test-Production.ps1') -PostgresBin $PostgresBin -Browser:$Browser
