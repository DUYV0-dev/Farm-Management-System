param([string]$Image='farm-management:deployment-check')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$jar=Join-Path $projectRoot 'target\backend-0.0.1-SNAPSHOT.jar'
$smokeRoot=Join-Path $projectRoot ('target\linux-pdf-smoke-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $smokeRoot | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::ExtractToDirectory($jar,$smokeRoot)
$classes=Join-Path $smokeRoot 'smoke-classes'
New-Item -ItemType Directory -Path $classes | Out-Null
$classpath=(Join-Path $smokeRoot 'BOOT-INF\classes')+';'+(Join-Path $smokeRoot 'BOOT-INF\lib\*')
& javac -encoding UTF-8 -cp $classpath -d $classes (Join-Path $PSScriptRoot 'BundledPdfSmoke.java')
if($LASTEXITCODE -ne 0){throw 'Linux PDF smoke compilation failed.'}
& docker run --rm --network none --read-only --tmpfs /tmp --mount ('type=bind,source='+$classes+',target=/smoke,readonly') --entrypoint java $Image '-Dloader.main=com.farmmanagement.backend.dashboard.BundledPdfSmoke' '-Dloader.path=/smoke' -cp /app/backend.jar org.springframework.boot.loader.launch.PropertiesLauncher
if($LASTEXITCODE -ne 0){throw 'Linux runtime PDF smoke failed.'}
