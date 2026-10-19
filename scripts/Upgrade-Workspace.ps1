param(
    [string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin',
    [string]$Database='farm_management_erd_v1',
    [string]$DatabaseHost='127.0.0.1',
    [int]$Port=5432,
    [string]$DatabaseOwner='postgres'
)
$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$psql=Join-Path $PostgresBin 'psql.exe'
$sqlArgs=@('-h',$DatabaseHost,'-p',"$Port",'-U',$DatabaseOwner,'-d',$Database,'-w','-v','ON_ERROR_STOP=1')
$previousPassword=$env:PGPASSWORD
$secret=Read-Host "Mat khau PostgreSQL cua $DatabaseOwner (khong luu vao ma nguon)" -AsSecureString
try {
    $env:PGPASSWORD=[System.Net.NetworkCredential]::new('',$secret).Password
    # Read-only preflight: do not start migrations on an incompatible ERD.
    $compatible=& $psql @sqlArgs -t -A -c "SELECT to_regclass('public.auth_permission') IS NOT NULL AND to_regclass('public.farm_member_role') IS NOT NULL AND to_regclass('public.material_category') IS NOT NULL AND to_regclass('public.farm_expense') IS NOT NULL AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='material' AND column_name='category_id')"
    if($LASTEXITCODE -ne 0) { throw 'Cannot connect to PostgreSQL with the supplied credentials.' }
    if($compatible.Trim() -ne 't') { throw 'Database requires the warehouse schema in migrations 05/06/07/08/09/10. No changes applied. See docs/WORKSPACE.md before migrating ERD-v1 material.unit_id data.' }
    foreach($file in @('database/11a_Material_Usage_Warehouse.sql','database/12_Farm_Tenancy.sql','database/13_Crop_Season_Enhancement.sql')) {
        & $psql @sqlArgs -f $file
        if($LASTEXITCODE -ne 0) { throw "Migration failed: $file" }
    }
    Write-Host 'Workspace schema installed. Existing farm records require an explicit owner assignment; see database/Assign-Farm-Owner.sql.'
    Write-Host 'Run FarmManagementSystemN02Application with DB_USERNAME=farm_app.'
} finally {
    $env:PGPASSWORD=$previousPassword
}
