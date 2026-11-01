# Chạy, kiểm chứng và phát hành

## Phát triển bằng IntelliJ

Run `FarmManagementSystemN02Application` trong IntelliJ. Ứng dụng dùng duy nhất
`application.properties`; database, mật khẩu, JWT, cổng và TLS nhận từ biến môi trường.
Xem [ENVIRONMENT](ENVIRONMENT.md). Launcher source đọc lại biến Windows User còn thiếu
nếu IntelliJ đang giữ môi trường cũ. Trên máy này vẫn dùng PostgreSQL `55433/farm_management` và
`https://localhost:8443/`.

Run/Stop trong IntelliJ điều khiển web. PostgreSQL chạy riêng bằng service
`FarmManagement-PostgreSQL`; Stop web không xóa dữ liệu.
`Start-Local.ps1` nạp biến môi trường Windows User còn thiếu rồi chạy JAR đã build.
JAR dùng frontend đóng gói; Run source dùng frontend hiện hành trong workspace.

## Kiểm chứng

```powershell
.\scripts\Test-Production.ps1 -Browser
```

Cần Java 21, Maven wrapper/cache, PostgreSQL 18, Node có WebSocket/fetch và Chrome.
Runner từ chối nếu cổng thử 55432/18080/9223 đang dùng. PostgreSQL tạm chỉ nghe loopback;
dữ liệu thử nằm trong .test-data, không dùng database 5432/55433.

Runner build sạch, kiểm tra artifact, chạy test với PostgreSQL, luồng giao diện thực,
backup/restore và kiểm tra dữ liệu sau khi restart cả PostgreSQL và backend.
Kết quả nằm trong target/production-verification.json và thư mục evidence được ghi ở đó.
Các test legacy/context cần môi trường riêng có thể được skip; số lượng ghi rõ trong report.

## Tạo bản phát hành

```powershell
.\scripts\Build-Release.ps1
```

Lệnh này kiểm chứng đầy đủ rồi tạo dist/farm-<time>-<hash> và ZIP, gồm backend.jar,
release.json, Start-Production.ps1, Configure-Local.ps1 và hướng dẫn ENVIRONMENT.md. Nếu vừa chạy kiểm chứng thành công
và nguồn/JAR chưa đổi, dùng `-UseVerifiedBuild` để đóng gói kết quả đó.

Không dùng trực tiếp một JAR build bỏ qua test làm bản phát hành. Không tự sửa JAR,
manifest hoặc nội dung frontend sau kiểm chứng.

## Chạy production

Chép nguyên gói release lên máy đích và cung cấp các biến DB/JWT/TLS cho tài khoản
chạy ứng dụng; xem ENVIRONMENT.md. Không cần file properties thứ hai.

```powershell
.\Start-Production.ps1 -ReleaseJar .\backend.jar
```

JAR không tự mở trình duyệt hoặc đọc frontend từ source. Phạm vi mạng, HTTPS,
domain và reverse proxy thuộc môi trường triển khai; mặc định web chỉ nghe loopback.

Trước nâng cấp: giữ release cũ, tạo và kiểm tra backup, ngừng writer trong thời gian
migrate, dùng DatabaseMigrations với role migration, validate và khởi động JAR đã kiểm
chứng. Không chạy Update-Lan/Backup-Lan trên cụm đang do Windows service quản lý.

## Sao lưu và phục hồi

`Backup-Local.ps1` tạo snapshot pg_dump khi web đang hoạt động. Lịch
FarmManagement-LocalBackup chạy 02:00 khi người cài đăng nhập, có chạy bù khi có thể.
Archive-list/SHA256 không thay thế thử phục hồi; định kỳ phục hồi vào database mới và
đối chiếu. Lưu thêm bản ngoài máy, không phục hồi đè database đang phục vụ.

`database_recovery.py`/Invoke-DatabaseRecovery.ps1 hỗ trợ kiểm chứng schema và dữ liệu
khi đã ngừng writer. DPAPI gắn với tài khoản Windows; chuyển máy cần quy trình quản
trị cấp lại bí mật. Không dùng pgdata như thư mục tài liệu để chép/đổi tên khi server chạy.
