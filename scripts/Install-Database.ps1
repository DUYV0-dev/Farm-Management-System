param([string]$Database='farm_management',[string]$DatabaseHost='127.0.0.1',[int]$Port=5432,[string]$DatabaseOwner='postgres')
$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if($Database -notmatch '^[a-zA-Z_][a-zA-Z0-9_]{0,62}$'){throw 'Unsupported database name'}
$jar=Join-Path (Get-Location).Path 'target/backend-0.0.1-SNAPSHOT.jar'
if(-not(Test-Path -LiteralPath $jar)){throw 'Build and test the application before migrating'}
$secret=Read-Host "PostgreSQL password for $DatabaseOwner" -AsSecureString
$previous=@{};foreach($name in @('DB_URL','DB_MIGRATION_USERNAME','DB_MIGRATION_PASSWORD')){$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
try{
 $env:DB_URL="jdbc:postgresql://${DatabaseHost}:$Port/$Database";$env:DB_MIGRATION_USERNAME=$DatabaseOwner;$env:DB_MIGRATION_PASSWORD=[Net.NetworkCredential]::new('',$secret).Password
 & java '-Dloader.main=com.farmmanagement.backend.config.DatabaseMigrations' -cp $jar org.springframework.boot.loader.launch.PropertiesLauncher migrate
 if($LASTEXITCODE -ne 0){throw 'Migration failed. Legacy databases require the reviewed upgrade procedure.'}
 Write-Host 'Complete schema installed with Flyway. Configure a strong farm_app password before production startup.'
}finally{foreach($name in $previous.Keys){[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')}}
