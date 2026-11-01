function Get-LocalInstallation([string]$Root) {
    $rootPath = (Resolve-Path -LiteralPath $Root -ErrorAction Stop).Path
    $config = Get-Content -LiteralPath (Join-Path $rootPath 'config.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    # config.json is PostgreSQL service/backup metadata, not application settings.
    if ([int]$config.databasePort -lt 1 -or [int]$config.databasePort -gt 65535) {
        throw 'Invalid installed PostgreSQL port in config.json.'
    }
    $url = "jdbc:postgresql://127.0.0.1:$($config.databasePort)/farm_management"
    $uri = [Uri]($url.Substring(5))
    $cluster = Join-Path $rootPath 'pgdata'
    if (-not (Test-Path -LiteralPath (Join-Path $cluster 'PG_VERSION'))) { throw 'Private PostgreSQL cluster not found.' }
    foreach ($tool in @('pg_ctl.exe','pg_dump.exe','pg_restore.exe','psql.exe')) {
        if (-not (Test-Path -LiteralPath (Join-Path $config.postgresBin $tool))) { throw "Missing PostgreSQL tool: $tool" }
    }
    return [pscustomobject]@{ Root=$rootPath; Config=$config; Cluster=$cluster; Url=$url; Database='farm_management'; Port=$uri.Port }
}
