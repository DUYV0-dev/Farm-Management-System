<#
.SYNOPSIS
    Automated PostgreSQL database migration runner for Farm Management System N02.
.DESCRIPTION
    Applies migrations 05 through 13 in strict sequence with transactional integrity.
.PARAMETER Host
    PostgreSQL server host (default: localhost).
.PARAMETER Port
    PostgreSQL server port (default: 5432).
.PARAMETER Database
    Target database name (default: farm_management_erd_v1).
.PARAMETER User
    PostgreSQL administrative user (default: postgres).
.PARAMETER PsqlPath
    Path to psql executable (default: psql).
.EXAMPLE
    .\scripts\Migrate-Database.ps1
#>
param(
    [string]$Host = "localhost",
    [int]$Port = 5432,
    [string]$Database = "farm_management_erd_v1",
    [string]$User = "postgres",
    [string]$PsqlPath = "psql"
)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
$ProjectRoot = (Get-Location).Path

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Farm Management System N02 - Database Migration Pipeline " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Check psql availability
$psqlCmd = Get-Command $PsqlPath -ErrorAction SilentlyContinue
if (-not $psqlCmd) {
    # Check default PostgreSQL paths on Windows
    $commonPaths = @(
        "C:\Program Files\PostgreSQL\17\bin\psql.exe",
        "C:\Program Files\PostgreSQL\16\bin\psql.exe",
        "C:\Program Files\PostgreSQL\15\bin\psql.exe",
        "C:\Program Files\PostgreSQL\14\bin\psql.exe"
    )
    foreach ($p in $commonPaths) {
        if (Test-Path $p) {
            $PsqlPath = $p
            $psqlCmd = $p
            break
        }
    }
}

if (-not $psqlCmd) {
    Write-Host "WARNING: psql.exe not found in PATH or standard Program Files locations." -ForegroundColor Yellow
    Write-Host "You can execute the migration files manually in pgAdmin Query Tool in the following strict order:" -ForegroundColor Cyan
}

$migrationFiles = @(
    "database/05_Authentication_Sessions.sql",
    "database/06_User_Role_Management.sql",
    "database/07_Farm_Plot_Crop.sql",
    "database/07_Crop_And_Product_Management.sql",
    "database/08_Material_Warehouse.sql",
    "database/09_Management_Integrity.sql",
    "database/10_Harvest_Management.sql",
    "database/11_Advanced_Dashboard_Reports.sql",
    "database/12_Supplier_Purchase_Management.sql",
    "database/13_Sales_Order_Management.sql",
    "database/Bootstrap-Admin.sql"
)

$idx = 1
foreach ($file in $migrationFiles) {
    $fullPath = Join-Path $ProjectRoot $file
    if (-not (Test-Path $fullPath)) {
        Write-Host "[$idx] MISSING: $file" -ForegroundColor Red
        continue
    }

    if ($psqlCmd) {
        Write-Host "--> [$idx/$($migrationFiles.Count)] Applying $file..." -ForegroundColor Yellow
        & $PsqlPath -h $Host -p $Port -U $User -d $Database -w -v ON_ERROR_STOP=1 -f $fullPath
        if ($LASTEXITCODE -ne 0) {
            throw "Migration failed on $file. Terminating pipeline."
        }
        Write-Host "    APPLIED: $file" -ForegroundColor Green
    } else {
        Write-Host "    $idx. $file" -ForegroundColor White
    }
    $idx++
}

if ($psqlCmd) {
    Write-Host "==> All database migrations completed successfully." -ForegroundColor Green
} else {
    Write-Host "==> Migration list printed above. Run via pgAdmin or install psql." -ForegroundColor Cyan
}
