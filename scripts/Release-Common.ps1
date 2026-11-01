function Get-SourceFingerprint([string]$ProjectRoot) {
    $entries=New-Object 'System.Collections.Generic.List[string]'
    $paths=@('pom.xml','.dockerignore' | ForEach-Object { Join-Path $ProjectRoot $_ })
    foreach($directory in @('src/main','frontend/app','frontend/features','frontend/shared','deploy/linux')) {
        $paths+=@(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot $directory) -Recurse -File |
            Where-Object { $_.Name -ne '.env' -and $_.FullName -notmatch '[\\/]backups[\\/]' } | Select-Object -ExpandProperty FullName)
    }
    foreach($path in ($paths | Sort-Object)) {
        $relative=$path.Substring($ProjectRoot.Length).TrimStart('\','/').Replace('\','/')
        $entries.Add($relative+' '+(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash)
    }
    $hash=[Security.Cryptography.SHA256]::Create()
    try{return ([BitConverter]::ToString($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes(($entries -join "`n"))))).Replace('-','')}
    finally{$hash.Dispose()}
}
function Assert-ReleaseArtifact([string]$Jar,[string]$ProjectRoot) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip=[IO.Compression.ZipFile]::OpenRead($Jar)
    try {
        foreach($entry in $zip.Entries) {
            if($entry.FullName -match '^BOOT-INF/classes/(static/ui/(prototypes|features/traceability)/|com/farmmanagement/backend/(activity|notification|traceability)/)') {
                throw ('Historical/demo code was included in the release: '+$entry.FullName)
            }
        }
        $configuration=$zip.GetEntry('BOOT-INF/classes/application.properties')
        if(-not $configuration){throw 'Release is missing application.properties.'}
        $reader=New-Object IO.StreamReader($configuration.Open())
        try{$properties=$reader.ReadToEnd()}finally{$reader.Dispose()}
        foreach($line in ($properties -split "`n")) {
            if($line -match '^\s*[^#!\s=]*(?:password|secret)\s*=(.*)$' -and
                $Matches[1].Trim() -notmatch '^\$\{[A-Z_]+:?\}$') {
                throw 'Release contains a literal credential in application.properties.'
            }
        }
        foreach($mapping in @(@('frontend/app','static/'),@('frontend/features','static/ui/features/'),@('frontend/shared','static/ui/shared/'),@('src/main/resources',''))) {
            $sourceRoot=Join-Path $ProjectRoot $mapping[0]
            foreach($file in (Get-ChildItem -LiteralPath $sourceRoot -Recurse -File)) {
                $relative=$file.FullName.Substring($sourceRoot.Length).TrimStart('\','/').Replace('\','/')
                $name='BOOT-INF/classes/'+$mapping[1]+$relative
                $entry=$zip.GetEntry($name)
                if(-not $entry){throw "Release is missing source resource: $name"}
                $stream=$entry.Open();$hash=[Security.Cryptography.SHA256]::Create()
                try{$actual=([BitConverter]::ToString($hash.ComputeHash($stream))).Replace('-','')}
                finally{$stream.Dispose();$hash.Dispose()}
                if($actual -ne (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash){throw "Release/source resource mismatch: $name"}
            }
        }
    } finally{$zip.Dispose()}
}
