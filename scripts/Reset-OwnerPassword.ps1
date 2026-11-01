param(
    [string]$NewPassword = 'son@123',
    [string]$InstallRoot = (Join-Path $env:LOCALAPPDATA 'FarmManagement\production'),
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin'
)

$ErrorActionPreference = 'Stop'

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Error "Script này cần quyền Administrator để restart service PostgreSQL. Vui lòng mở PowerShell bằng 'Run as administrator' và chạy lại!"
    exit 1
}

$serviceName = 'FarmManagement-PostgreSQL'
$pgData = Join-Path $InstallRoot 'pgdata'
$hbaFile = Join-Path $pgData 'pg_hba.conf'
$hbaBak = Join-Path $pgData 'pg_hba.conf.bak'
$psql = Join-Path $PostgresBin 'psql.exe'

Write-Host "1. Sao lưu pg_hba.conf..." -ForegroundColor Cyan
Copy-Item -LiteralPath $hbaFile -Destination $hbaBak -Force

try {
    Write-Host "2. Tạm thời bật chế độ xác thực trust cho farm_owner..." -ForegroundColor Cyan
    $origContent = Get-Content -LiteralPath $hbaFile -Raw
    $trustRule = "host all farm_owner 127.0.0.1/32 trust`r`n"
    Set-Content -LiteralPath $hbaFile -Value ($trustRule + $origContent) -Encoding UTF8

    Write-Host "3. Khởi động lại dịch vụ $serviceName..." -ForegroundColor Cyan
    Restart-Service -Name $serviceName -Force
    Start-Sleep -Seconds 3

    Write-Host "4. Đổi mật khẩu farm_owner thành '$NewPassword'..." -ForegroundColor Cyan
    $escapedPass = $NewPassword.Replace("'", "''")
    & $psql -h 127.0.0.1 -p 55433 -U farm_owner -d farm_management -c "ALTER USER farm_owner WITH PASSWORD '$escapedPass';"
    if ($LASTEXITCODE -ne 0) {
        throw "Không thể thực thi lệnh đổi mật khẩu trong PostgreSQL."
    }

    Write-Host "5. Cập nhật file owner.dpapi..." -ForegroundColor Cyan
    ConvertTo-SecureString $NewPassword -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -LiteralPath (Join-Path $InstallRoot 'owner.dpapi') -Encoding ASCII

    Write-Host "6. Khôi phục lại cấu hình bảo mật pg_hba.conf ban đầu..." -ForegroundColor Cyan
    Copy-Item -LiteralPath $hbaBak -Destination $hbaFile -Force
    Remove-Item -LiteralPath $hbaBak -Force

    Write-Host "7. Khởi động lại dịch vụ PostgreSQL để áp dụng bảo mật..." -ForegroundColor Cyan
    Restart-Service -Name $serviceName -Force
    Start-Sleep -Seconds 2

    Write-Host "8. Kiểm tra kết nối farm_owner với mật khẩu mới..." -ForegroundColor Cyan
    $env:PGPASSWORD = $NewPassword
    & $psql -w -h 127.0.0.1 -p 55433 -U farm_owner -d farm_management -c "SELECT 'farm_owner reset successfully' as status;"

    Write-Host "`n=== THÀNH CÔNG! ĐÃ ĐỔI MẬT KHẨU farm_owner THÀNH '$NewPassword' ===" -ForegroundColor Green
}
catch {
    Write-Error "Đã xảy ra lỗi: $_"
    if (Test-Path -LiteralPath $hbaBak) {
        Copy-Item -LiteralPath $hbaBak -Destination $hbaFile -Force
        Remove-Item -LiteralPath $hbaBak -Force
        Restart-Service -Name $serviceName -Force -ErrorAction SilentlyContinue
    }
    exit 1
}
