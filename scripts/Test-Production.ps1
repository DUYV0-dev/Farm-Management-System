param([string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin',[switch]$Browser,
      [string]$Chrome='C:\Program Files\Google\Chrome\Application\chrome.exe')
& (Join-Path $PSScriptRoot 'testing\Verify-Production.ps1') -PostgresBin $PostgresBin -Browser:$Browser -Chrome $Chrome
