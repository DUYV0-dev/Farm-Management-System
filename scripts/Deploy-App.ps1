<#
.SYNOPSIS
    Automated deployment and build script for Farm Management System N02 (Windows PowerShell).
.DESCRIPTION
    Builds the production JAR (including backend & frontend static assets), verifies environment
    variables, generates cryptographically strong JWT secret if needed, and starts the service.
.PARAMETER Start
    If specified, runs the application after packaging.
.PARAMETER SkipBuild
    If specified, skips Maven package step and uses existing JAR.
.EXAMPLE
    .\scripts\Deploy-App.ps1 -Start
#>
param(
    [switch]$Start,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
$ProjectRoot = (Get-Location).Path

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Farm Management System N02 - Build & Deployment Pipeline " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Check Java version
Write-Host "--> Checking Java runtime environment..." -ForegroundColor Yellow
$javaOutput = cmd.exe /c "java -version 2>&1"
if ($LASTEXITCODE -ne 0) {
    throw "Java runtime not found. Please install Java 21 or later."
}
Write-Host "    $($javaOutput[0])" -ForegroundColor Green

# 2. Configure Environment Variables & Secrets
Write-Host "--> Verifying configuration and environment variables..." -ForegroundColor Yellow

if (-not $env:DB_URL) {
    $env:DB_URL = "jdbc:postgresql://localhost:5432/farm_management_erd_v1"
    Write-Host "    DB_URL not set; defaulted to $env:DB_URL" -ForegroundColor DarkGray
}

if (-not $env:DB_USERNAME) {
    $env:DB_USERNAME = "farm_app"
    Write-Host "    DB_USERNAME not set; defaulted to $env:DB_USERNAME" -ForegroundColor DarkGray
}

if (-not $env:JWT_SECRET) {
    # Check if DPAPI stored secret exists
    $keyDir = Join-Path $env:LOCALAPPDATA "FarmN02"
    $keyPath = Join-Path $keyDir "jwt-secret.dpapi"
    if (Test-Path $keyPath) {
        $storedSecret = (Get-Content -Raw -LiteralPath $keyPath).Trim() | ConvertTo-SecureString
        $env:JWT_SECRET = [System.Net.NetworkCredential]::new("", $storedSecret).Password
        Write-Host "    Loaded existing JWT_SECRET from DPAPI storage." -ForegroundColor Green
    } else {
        # Generate new 32-byte cryptographic random secret
        $bytes = New-Object byte[] 32
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
        [Array]::Clear($bytes, 0, $bytes.Length)
        Write-Host "    Generated new cryptographically secure 256-bit JWT_SECRET." -ForegroundColor Green
    }
}

# 3. Build & Package
$jarPath = Join-Path $ProjectRoot "target\backend-0.0.1-SNAPSHOT.jar"
if (-not $SkipBuild) {
    Write-Host "--> Building application package with Maven Wrapper..." -ForegroundColor Yellow
    & .\mvnw.cmd clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed. Check Maven compile errors."
    }
}

if (-not (Test-Path $jarPath)) {
    throw "Application JAR artifact not found at $jarPath"
}
$jarSizeMB = [math]::Round((Get-Item $jarPath).Length / 1MB, 2)
Write-Host "--> Build SUCCESS: $jarPath ($jarSizeMB MB)" -ForegroundColor Green

# 4. Startup Verification
if ($Start) {
    if (-not $env:DB_PASSWORD) {
        $dbSecret = Read-Host "Enter password for PostgreSQL user '$env:DB_USERNAME'" -AsSecureString
        $env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $dbSecret).Password
    }

    $port = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { '8080' }
    Write-Host "--> Starting Farm Management System N02 on port $port..." -ForegroundColor Cyan
    try {
        & java -XX:+UseG1GC -jar $jarPath
    } finally {
        Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    }
} else {
    Write-Host "==> Deployment package ready. Run with -Start to launch application." -ForegroundColor Cyan
}
