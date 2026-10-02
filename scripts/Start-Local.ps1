$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
. (Join-Path $PSScriptRoot "Configure-Local.ps1")
$env:DB_USERNAME = "farm_app"
$dbSecret = Read-Host "Mat khau PostgreSQL cua farm_app" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $dbSecret).Password
Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
try {
    & java -jar "target/backend-0.0.1-SNAPSHOT.jar"
    if ($LASTEXITCODE -ne 0) { throw "Backend failed; inspect the log." }
} finally {
    Remove-Item Env:DB_PASSWORD, Env:JWT_SECRET -ErrorAction SilentlyContinue
}
