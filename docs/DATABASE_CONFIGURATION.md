# Chọn và vận hành database trên máy này

Ứng dụng chỉ đọc **`src/main/resources/application.properties`**. Các giá trị riêng
của máy được cung cấp qua biến môi trường; xem [ENVIRONMENT](ENVIRONMENT.md).
Không còn cấu hình development/production hoặc cơ chế tự tìm file IDE.

## Kết nối hiện tại

Database chính là `127.0.0.1:55433/farm_management`, role runtime `farm_app`.
Web ở `https://localhost:8443/`. Hai cổng thuộc hai dịch vụ khác nhau.
PostgreSQL ở cổng `5432` là cụm riêng, không được tự chọn thay khi có lỗi.

Run `FarmManagementSystemN02Application` trong IntelliJ nhận các biến môi trường
Windows User: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` và thông tin TLS.
Launcher source trên Windows đọc lại các biến User còn thiếu nếu IntelliJ chưa nhận môi trường mới.
`Start-Local.ps1` có thể nạp biến Windows User vào Terminal đang mở sẵn.

Backend kiểm tra kết nối và schema bằng truy vấn chỉ đọc trước khi khởi động.
Không tự đổi database, chạy migration hoặc seed. Role migration/backup tách khỏi
role chạy API. `DatabaseMigrations validate` kiểm tra đầy đủ checksum migration.
JWT và thông tin datasource nhận từ biến môi trường Windows đã cấu hình.
Không đưa mật khẩu thật vào source, JAR hoặc Git; không có database dự phòng tự chọn.

## PostgreSQL tự chạy, website do IntelliJ điều khiển

Xem kế hoạch trước, không cần quyền quản trị:

```powershell
.\scripts\Set-LocalDatabase.ps1
```

Áp dụng từ PowerShell **Run as administrator**, bằng cùng tài khoản Windows đã cài:

```powershell
.\scripts\Set-LocalDatabase.ps1 -Apply
```

Script đối chiếu cấu hình IDE với `config.json`, đăng ký dịch vụ
`FarmManagement-PostgreSQL` chạy tự động bằng LocalSystem, dùng đúng `pgdata` của bản
cài và cấu hình khởi động lại khi lỗi. Thư mục bản cài đã được installer cấp quyền
cho SYSTEM và người cài. Database không cần giải mã DPAPI để khởi động.

Script từ chối nếu cụm đang chạy ngoài service; dừng sạch cụm trong thời gian bảo trì
rồi chạy lại. Không tự tắt một PostgreSQL đang chạy. Task database cũ được tắt để
không có hai cơ chế cùng khởi động một cụm. Task web và backup legacy phải đang tắt.
Không thay đổi dịch vụ PostgreSQL ở `5432`.

Script tạo một bản sao lưu đầu tiên sau khi dịch vụ chạy, rồi đăng ký lịch sao lưu.
Trước khi áp dụng vào máy khác có thể kiểm tra trên cụm PostgreSQL tạm:

```powershell
.\mvnw.cmd -o verify
.\scripts\Test-DatabaseConfiguration.ps1
```

Sau đó Run/Stop trong IntelliJ chỉ điều khiển website. Kiểm tra:

```powershell
Get-Service FarmManagement-PostgreSQL
.\scripts\Start-Local.ps1
```

Giữ bản database `5432` cho đến khi truy cập được, sao lưu và đối chiếu dữ liệu thật.
Không xóa, ghi đè hoặc tự gộp dữ liệu của hai cụm.

## Sao lưu cho chế độ IntelliJ/service

Script cài đặt tạo task `FarmManagement-LocalBackup` lúc 02:00, chạy khi tài khoản
cài còn đăng nhập Windows. Task có chạy bù khi có thể và thử lại khi lỗi. Máy phải bật.

`Backup-Local.ps1` dùng `pg_dump -Fc` với snapshot nhất quán, không dừng IntelliJ hay
PostgreSQL. Mỗi lần tạo thư mục mới trong `backups/local-*`, lưu archive, danh sách
nội dung và SHA256. File dở dang giữ đuôi `.partial`; chỉ ghi `COMPLETE` sau khi dump
thành công và `pg_restore --list` đọc được archive. Mật khẩu owner lấy từ DPAPI,
không truyền qua dòng lệnh. Không tự xóa backup cũ.

```powershell
.\scripts\Backup-Local.ps1
```

Đọc danh sách archive và kiểm tra checksum chưa thay thế thử phục hồi. Định kỳ phục
hồi vào database mới, kiểm tra dữ liệu và ứng dụng; không phục hồi đè database chính.
Sao chép thêm backup sang nơi lưu ngoài máy. Quy trình manifest/quiesce trong
`Backup-Lan.ps1` vẫn dành cho chế độ launcher LAN cũ, không bật task đó khi chạy IDE.

## Khi có lỗi

- Không kết nối `55433`: kiểm tra `Get-Service FarmManagement-PostgreSQL` và Windows
  Event Viewer; khởi động lại dịch vụ với quyền quản trị nếu cần.
- Sai mật khẩu: kiểm tra cấu hình của đúng cụm, không dùng mật khẩu của `5432` cho `55433`.
- Thiếu migration `018`: sao lưu rồi dùng quy trình nâng cấp versioned trong
  `docs/WINDOWS_LAN.md`; không tự sửa bảng hay lịch sử Flyway.
- Muốn thay service bằng cách chạy thủ công: dừng service trong thời gian bảo trì,
  đặt StartupType Manual rồi mới dùng `pg_ctl`; không chạy hai cơ chế song song.
