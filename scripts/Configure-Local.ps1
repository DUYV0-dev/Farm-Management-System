$ErrorActionPreference = 'Stop'
# Values are supplied by the process or the current Windows user's environment.
# No alternate Spring properties file or automatic database selection.
$farmEnvironmentNames = @('DB_URL','DB_USERNAME','DB_PASSWORD','JWT_SECRET','SERVER_ADDRESS','SERVER_PORT',
    'SERVER_SSL_ENABLED','TLS_KEY_STORE','TLS_KEY_STORE_PASSWORD','LOG_FILE',
    'APP_OPEN_BROWSER','APP_FRONTEND_WORKING_TREE','DB_POOL_SIZE')
if ($env:OS -eq 'Windows_NT') {
    foreach ($name in $farmEnvironmentNames) {
        if ($null -eq [Environment]::GetEnvironmentVariable($name, 'Process')) {
            $value = [Environment]::GetEnvironmentVariable($name, 'User')
            if ($null -ne $value) { [Environment]::SetEnvironmentVariable($name, $value, 'Process') }
        }
    }
}
foreach ($name in @('DB_URL','DB_USERNAME','DB_PASSWORD','JWT_SECRET')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
        throw "Missing environment variable: $name. See docs/ENVIRONMENT.md."
    }
}
if ($env:SPRING_CONFIG_LOCATION -or $env:SPRING_CONFIG_ADDITIONAL_LOCATION) {
    throw 'Remove SPRING_CONFIG_LOCATION/SPRING_CONFIG_ADDITIONAL_LOCATION to use the single bundled application.properties.'
}
