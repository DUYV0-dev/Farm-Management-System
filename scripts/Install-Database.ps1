param(
    [string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin',
    [string]$Database='farm_management',
    [string]$DatabaseHost='127.0.0.1',
    [int]$Port=5432,
    [string]$DatabaseOwner='postgres'
)
$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$psql=Join-Path $PostgresBin 'psql.exe'
$sqlArgs=@('-h',$DatabaseHost,'-p',"$Port",'-U',$DatabaseOwner,'-d',$Database,'-w','-v','ON_ERROR_STOP=1')
$previousPassword=$env:PGPASSWORD
$secret=Read-Host "PostgreSQL password for $DatabaseOwner" -AsSecureString
try {
    $env:PGPASSWORD=[System.Net.NetworkCredential]::new('',$secret).Password
    $empty=& $psql @sqlArgs -t -A -c "SELECT NOT EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','S'))"
    if($LASTEXITCODE -ne 0) { throw 'Cannot connect. Create the empty database first; see docs/DEPLOYMENT.md.' }
    if($empty.Trim() -ne 't') { throw 'Database is not empty. No changes applied. Use the upgrade workflow for existing data.' }
    foreach($file in @('database/00_Identity_Schema.sql','database/05_Authentication_Sessions.sql',
        'database/06_User_Role_Management.sql','database/07_Farm_Plot_Crop.sql','database/08_Material_Warehouse.sql',
        'database/09_Management_Integrity.sql','database/10_Farm_Expense_Management.sql',
        'database/11a_Material_Usage_Warehouse.sql','database/12_Farm_Tenancy.sql',
        'database/13_Crop_Season_Enhancement.sql')) {
        & $psql @sqlArgs -f $file
        if($LASTEXITCODE -ne 0) { throw "Installation stopped: $file. Do not run the application until all migrations succeed." }
    }
    Write-Host 'Schema installed. Set the farm_app password with psql \password farm_app before production startup.'
} finally { $env:PGPASSWORD=$previousPassword }
