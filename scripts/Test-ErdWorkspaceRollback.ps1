param([Parameter(Mandatory=$true)][string]$Psql,
    [Parameter(Mandatory=$true)][string]$ConversionFile,
    [Parameter(Mandatory=$true)][string]$TestRoot)
$ErrorActionPreference='Stop'
$migrationSql=Get-Content -Raw -Encoding UTF8 -LiteralPath $ConversionFile
$cases=@{
    'nonempty-material'="INSERT INTO public.material(unit_id,material_name,status) VALUES(1,'Must reject populated catalog','ACTIVE');`r`n"+$migrationSql
    'late-error'=$migrationSql+"`r`nDO `$`$ BEGIN RAISE EXCEPTION 'Intentional late rollback check'; END `$`$;"
}
foreach($name in @('nonempty-material','late-error')) {
    $file=Join-Path $TestRoot ($name+'.sql')
    [System.IO.File]::WriteAllText($file,$cases[$name],[System.Text.UTF8Encoding]::new($false))
    $arguments='-X -q -h 127.0.0.1 -p 55432 -U postgres -d management_test -w -v ON_ERROR_STOP=1 --single-transaction -f "'+$file+'"'
    $process=Start-Process -FilePath $Psql -ArgumentList $arguments -WindowStyle Hidden -Wait -PassThru `
        -RedirectStandardOutput (Join-Path $TestRoot ($name+'.stdout.log')) `
        -RedirectStandardError (Join-Path $TestRoot ($name+'.stderr.log'))
    if($process.ExitCode -eq 0) { throw "Conversion should have failed: $name" }
    $expected=if($name -eq 'nonempty-material') { 'Table material is not empty' } else { 'Intentional late rollback check' }
    if(-not (Select-String -LiteralPath (Join-Path $TestRoot ($name+'.stderr.log')) -SimpleMatch $expected -Quiet)) {
        throw "Rollback test failed for an unexpected reason: $name"
    }
    $unchanged=& $Psql -X -h 127.0.0.1 -p 55432 -U postgres -d management_test -w -t -A -v ON_ERROR_STOP=1 -c @'
SELECT to_regprocedure('public.workspace_memberships(integer)') IS NULL
 AND to_regclass('public.material_category') IS NULL
 AND to_regclass('public.warehouse_inventory') IS NULL
 AND NOT EXISTS(SELECT FROM information_schema.columns WHERE table_schema='public' AND table_name='material' AND column_name='unit')
 AND NOT EXISTS(SELECT FROM public.material)
 AND EXISTS(SELECT FROM public.farm WHERE farm_id=1 AND farm_name='Legacy retained farm' AND address='Old address')
 AND EXISTS(SELECT FROM information_schema.columns WHERE table_schema='public' AND table_name='crop_season' AND column_name='planned_start_date' AND is_nullable='NO');
'@
    if($LASTEXITCODE -ne 0 -or $unchanged.Trim() -ne 't') { throw "Conversion did not roll back: $name" }
    Write-Host "PASS rollback: $name"
}
