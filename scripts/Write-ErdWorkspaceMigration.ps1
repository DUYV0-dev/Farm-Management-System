param([Parameter(Mandatory=$true)][string]$OutputFile)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$parts=New-Object 'System.Collections.Generic.List[string]'
foreach($file in @('08a_Empty_ERD_Warehouse_Compatibility.sql','07_Farm_Plot_Crop.sql',
    '08_Material_Warehouse.sql','09_Management_Integrity.sql','10_Farm_Expense_Management.sql',
    '11a_Material_Usage_Warehouse.sql','12_Farm_Tenancy.sql','11b_Material_Usage_Shared_Catalog.sql')) {
    $sql=Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $projectRoot ('database/'+$file))
    # Each source has one top-level BEGIN/COMMIT. psql -1 owns the combined transaction.
    if([regex]::Matches($sql,'(?m)^BEGIN;\s*$').Count -ne 1 -or
       [regex]::Matches($sql,'(?m)^COMMIT;\s*$').Count -ne 1) {
        throw "Unexpected transaction boundaries: $file"
    }
    $parts.Add('-- Source: '+$file)
    $parts.Add([regex]::Replace($sql,'(?m)^(BEGIN|COMMIT);\s*$',''))
    if($file -eq '08_Material_Warehouse.sql') {
        $parts.Add('ALTER TABLE public.material ADD CONSTRAINT workspace_material_category_fk FOREIGN KEY (category_id) REFERENCES public.material_category(category_id) ON DELETE RESTRICT;')
    }
}
$parts.Add(@'
-- Reject additional mandatory fields that the current application cannot populate.
DO $$ DECLARE missing TEXT; BEGIN
 SELECT string_agg(c.table_name||'.'||c.column_name,', ' ORDER BY c.table_name,c.ordinal_position)
 INTO missing
 FROM information_schema.columns c
 JOIN (VALUES
   ('app_user',ARRAY['username','password_hash','full_name','email','status']),
   ('farm',ARRAY['farm_name','address','area_ha','description','created_by','owner_user_id','onboarding_status']),
   ('plot',ARRAY['farm_id','plot_name','area_ha','soil_type','location','description']),
   ('crop',ARRAY['crop_name','scientific_name','category','growth_days','description']),
   ('crop_season',ARRAY['plot_id','crop_id','status']),
   ('material',ARRAY['category_id','material_name','unit','manufacturer','description']),
   ('warehouse',ARRAY['farm_id','warehouse_name','location','capacity_text']),
   ('farm_member',ARRAY['farm_id','user_id'])
 ) AS supported(table_name,written_columns) ON supported.table_name=c.table_name
 WHERE c.table_schema='public' AND c.is_nullable='NO' AND c.column_default IS NULL
   AND c.is_identity='NO' AND c.is_generated='NEVER'
   AND NOT c.column_name=ANY(supported.written_columns);
 IF missing IS NOT NULL THEN
  RAISE EXCEPTION 'Unsupported mandatory columns: %. Entire conversion rolled back.',missing;
 END IF;
 IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='farm_app' AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls) THEN
  RAISE EXCEPTION 'farm_app must be a login role without superuser/BYPASSRLS';
 END IF;
 IF EXISTS(SELECT FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
   WHERE n.nspname='public' AND c.relname IN ('farm','plot','crop','material','warehouse')
   AND pg_has_role('farm_app',c.relowner,'USAGE')) THEN
  RAISE EXCEPTION 'farm_app must not own or inherit ownership of business tables';
 END IF;
END $$;
'@)
[System.IO.File]::WriteAllText($OutputFile,($parts -join "`r`n"),[System.Text.UTF8Encoding]::new($false))
