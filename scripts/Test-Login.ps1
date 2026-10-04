$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
$client = [System.Net.Http.HttpClient]::new()
$base = "http://127.0.0.1:8080/api/v1/auth"
$rows = [System.Collections.Generic.List[object]]::new()
function Send-Api($method, $path, $body = $null, $token = $null) {
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($method), "$base/$path")
    if ($token) { $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $token) }
    if ($null -ne $body) { $request.Content = [System.Net.Http.StringContent]::new(($body | ConvertTo-Json -Compress), [System.Text.Encoding]::UTF8, "application/json") }
    try {
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $payload = if ($text) { $text | ConvertFrom-Json } else { $null }
        return @{ Status = [int]$response.StatusCode; Body = $payload }
    } finally { $request.Dispose() }
}
function Check($name, $condition) {
    $result = if ($condition) { "PASS" } else { "FAIL" }
    $rows.Add([pscustomobject]@{Test=$name;Result=$result;Time=(Get-Date -Format o)})
    Write-Host "$result - $name"
}
$user = Read-Host "Ten dang nhap ung dung da seed"
$secret = Read-Host "Mat khau ung dung" -AsSecureString
$password = [System.Net.NetworkCredential]::new("",$secret).Password
try {
    $r=Send-Api "GET" "me"; Check "Missing token => 401" ($r.Status -eq 401)
    $r=Send-Api "POST" "login" @{username=$user;password=([Guid]::NewGuid().ToString())}; Check "Wrong password => 401" ($r.Status -eq 401)
    $r=Send-Api "POST" "login" @{username=$user;password=$password;extra="rejected"}; Check "Unknown field => 422" ($r.Status -eq 422)
    $first=Send-Api "POST" "login" @{username=$user;password=$password}
    Check "Valid login => 200" ($first.Status -eq 200)
    if($first.Status -ne 200) { throw "Valid login failed. Check seed credentials." }
    $token=$first.Body.data.accessToken
    Check "Lifetime 86400 seconds" ($first.Body.data.expiresIn -eq 86400)
    $r=Send-Api "GET" "me" $null $token; Check "Current account => 200" ($r.Status -eq 200 -and $r.Body.data.user.username -eq $user)
    $second=Send-Api "POST" "login" @{username=$user;password=$password}
    Check "Second session => 200" ($second.Status -eq 200)
    $r=Send-Api "POST" "logout" $null $token; Check "Logout => 204" ($r.Status -eq 204)
    $r=Send-Api "GET" "me" $null $token; Check "Revoked token => 401" ($r.Status -eq 401)
    if($second.Status -eq 200) {
        $token2=$second.Body.data.accessToken
        $r=Send-Api "GET" "me" $null $token2; Check "Other session remains valid" ($r.Status -eq 200)
        $null=Send-Api "POST" "logout" $null $token2
    }
} finally {
    $password=$null; $token=$null; $token2=$null; $first=$null; $second=$null
    $client.Dispose()
    $outDir=Join-Path (Split-Path $PSScriptRoot -Parent) "Evidence/Task03_Authentication"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $rows | Export-Csv -NoTypeInformation -Encoding UTF8 -Path (Join-Path $outDir "MC_API_Test_Results.csv")
}
if($rows.Result -contains "FAIL") { throw "Some tests failed; do not mark task Done." }
