$ErrorActionPreference = "Stop"
# Store the signing key encrypted for the current Windows user, outside the repository.
$keyDir = Join-Path $env:LOCALAPPDATA "FarmN02"
$keyPath = Join-Path $keyDir "jwt-secret.dpapi"
if (-not (Test-Path $keyPath)) {
    New-Item -ItemType Directory -Force -Path $keyDir | Out-Null
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $secret = ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force
    $secret | ConvertFrom-SecureString | Set-Content -LiteralPath $keyPath
    [Array]::Clear($bytes, 0, $bytes.Length)
}
$storedSecret = (Get-Content -Raw -LiteralPath $keyPath).Trim() | ConvertTo-SecureString
$env:JWT_SECRET = [System.Net.NetworkCredential]::new("", $storedSecret).Password
$env:DB_URL = "jdbc:postgresql://localhost:5432/farm_management_erd_v1"
