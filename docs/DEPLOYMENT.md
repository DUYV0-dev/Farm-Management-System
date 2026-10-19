# Chạy và triển khai

Yêu cầu: Java 21, PostgreSQL đã cài schema tương thích và role `farm_app` không phải
superuser, không có BYPASSRLS và không sở hữu bảng nghiệp vụ. Build dùng Maven wrapper.

## Database mới

Tạo database trống bằng tài khoản quản trị PostgreSQL, ví dụ `CREATE DATABASE farm_management;`.
Sau đó chạy:

```powershell
.\scripts\Install-Database.ps1 -Database farm_management
```

Script hỏi mật khẩu quản trị an toàn, từ chối database đã có bảng và áp dụng
00 → 05 → 06 → 07 → 08 → 09 → 10 → 11a → 12. Schema nền 00 cũng được dùng trong bộ
kiểm thử tích hợp. Nếu một migration thất bại, script dừng; kiểm tra log trước khi chạy lại.
Không tự xóa schema để thử lại.

Đặt mật khẩu role ứng dụng trong phiên psql của quản trị viên bằng `\password farm_app`.
Không ghi mật khẩu vào SQL hoặc commit vào repository. Database hiện có làm theo
[WORKSPACE.md](WORKSPACE.md); không chạy cài mới đè dữ liệu cũ. ERD v1 với `material.unit_id`
cần khảo sát chuyển schema warehouse trước khi nâng cấp.

## Build và chạy production

```powershell
.\mvnw.cmd -o package
$env:SPRING_PROFILES_ACTIVE='production'
$env:DB_URL='jdbc:postgresql://127.0.0.1:5432/farm_management'
$env:DB_USERNAME='farm_app'
$secret=Read-Host 'Mat khau farm_app' -AsSecureString
$env:DB_PASSWORD=[System.Net.NetworkCredential]::new('',$secret).Password
# JWT_SECRET: khóa Base64 của ít nhất 32 byte ngẫu nhiên, lấy từ secret manager.
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

`-o` dùng thư viện Maven đã có trên máy; máy mới dùng `mvnw.cmd package` để tải dependencies.
Đặt `JWT_SECRET` trong môi trường dịch vụ trước khi chạy. Khóa phải giữ ổn định giữa lần
khởi động và giữa các instance. Production không tự sinh khóa hoặc mở hộp thoại/trình duyệt.
Có thể tạo khóa một lần bằng PowerShell rồi lưu vào secret manager:

```powershell
$bytes=New-Object byte[] 32
$random=[System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $random.GetBytes($bytes) } finally { $random.Dispose() }
$env:JWT_SECRET=[Convert]::ToBase64String($bytes)
```

Mặc định production nghe cổng 8080, có thể đặt `SERVER_PORT`, `SERVER_ADDRESS`,
`DB_POOL_SIZE` và `LOG_FILE`. Chỉ cần JAR và cấu hình môi trường, không cần thư mục frontend.
Chạy dưới tài khoản dịch vụ, đưa sau reverse proxy có HTTPS khi mở ra Internet;
chỉ công khai cổng HTTPS, giới hạn PostgreSQL trong mạng nội bộ. Cấu hình proxy, chứng chỉ,
domain thuộc môi trường triển khai và chưa được thực hiện trên máy này.
Quy trình backup/khôi phục đã được kiểm thử trên PostgreSQL tạm độc lập; xem
[báo cáo Backup & Recovery](DATABASE_BACKUP_RECOVERY_REPORT.md). Lập lịch, lưu bản sao ngoài máy,
mã hóa và xác minh khả năng phục hồi trên production vẫn cần được cấu hình tại môi trường triển khai.
Backup database trước khi nâng cấp; giữ JAR và cấu hình của phiên bản trước để rollback.

`GET /actuator/health` kiểm tra trạng thái ứng dụng/database, không công khai chi tiết.
Ứng dụng từ chối khởi động nếu thiếu migration tenancy hoặc role kết nối bỏ qua row security.
Hướng dẫn tạo quản trị hệ thống ở `database/Bootstrap-Admin.sql`: chọn tài khoản ACTIVE
riêng để cấp SYSTEM_ADMIN; chủ trang trại thông thường không có quyền quản trị toàn hệ thống.

## Chạy từ IDE

Run `FarmManagementSystemN02Application` với DB_URL/DB_USERNAME của database đã nâng cấp.
Chế độ local dùng cổng 5500, có thể hỏi mật khẩu qua hộp thoại và mở trình duyệt.
Đăng ký trang trại → đăng nhập → thêm nhân viên/chọn vị trí → bắt đầu thiết lập.
Quyền công việc và dữ liệu thật xem [WORKSPACE.md](WORKSPACE.md).

## Xác minh trước triển khai

```powershell
.\scripts\Test-Workspace.ps1 -Browser
```

Bộ này tạo PostgreSQL độc lập ở 55432, dùng schema cài mới, kiểm tra đăng ký, phân quyền,
khóa thiết lập, dữ liệu tách giữa trang trại, CRUD và persistence. JAR chạy profile production
từ thư mục riêng. Chrome kiểm tra menu, link giữa module, tổng quan thật, màn hình 390px,
quyền nhân viên và tài nguyên. Kiểm thử prototypes dùng JAR production, kiểm tra các
trang chính, điều hướng và việc loại script giả lập khỏi luồng ứng dụng.
Database thật ở 5432 không được thay đổi bởi bộ kiểm thử. Xem [PROTOTYPES_MAIN_UI.md](PROTOTYPES_MAIN_UI.md)
để biết giao diện chính, các chủ đề giao nhau và trạng thái chức năng.
