$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
. (Join-Path $PSScriptRoot "Configure-Local.ps1")
$env:DB_USERNAME = "postgres"
$dbSecret = Read-Host "Mat khau PostgreSQL cua postgres (chi de seed)" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $dbSecret).Password
$env:SEED_USERNAME = Read-Host "Ten dang nhap moi (vd login.demo)"
$env:SEED_EMAIL = Read-Host "Email tai khoan moi"
$seedSecret = Read-Host "Mat khau ung dung moi: 12-128 ky tu, co chu va so" -AsSecureString
$env:SEED_PASSWORD = [System.Net.NetworkCredential]::new("", $seedSecret).Password
try {
    & java -jar "target/backend-0.0.1-SNAPSHOT.jar" "--spring.profiles.active=seed" "--server.port=0"
    if ($LASTEXITCODE -ne 0) { throw "Seed failed; existing accounts were not overwritten." }
} finally {
    Remove-Item Env:DB_PASSWORD, Env:JWT_SECRET, Env:SEED_PASSWORD, Env:SEED_USERNAME, Env:SEED_EMAIL -ErrorAction SilentlyContinue
    $env:DB_USERNAME = "farm_app"
}
