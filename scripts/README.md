# Chọn đúng entry point

| Nhu cầu | Lệnh/script |
| --- | --- |
| Phát triển hằng ngày | Run main trong IntelliJ |
| Chạy JAR local đã build | Start-Local.ps1 (Configure-Local.ps1 nạp biến môi trường User) |
| Kiểm chứng đầy đủ | Test-Production.ps1 -Browser |
| Kiểm tra PDF trong image Linux sau khi docker build | testing/Verify-LinuxImage.ps1 |
| Đóng gói bản phát hành | Build-Release.ps1 |
| Chạy bản phát hành với cấu hình rõ ràng | Start-Production.ps1 |
| Cài schema chuẩn | Install-Database.ps1 / DatabaseMigrations |
| Cài service PostgreSQL trên máy này | Set-LocalDatabase.ps1 (preview; -Apply cần admin) |
| Sao lưu online cho IDE/service | Backup-Local.ps1 |
| Kiểm tra công cụ backup riêng | Test-DatabaseConfiguration.ps1 |
| Phục hồi/đối chiếu toàn schema khi ngừng writer | Invoke-DatabaseRecovery.ps1, database_recovery.py |

`Release-Common.ps1`, `Local-Installation.ps1`, `Lan-Common.ps1` là thư viện dùng chung.
Runner thực hiện kiểm chứng nằm ở testing/Verify-Production.ps1. Test-Workspace.ps1
chỉ chuyển tiếp sang pipeline hiện hành, không còn một bộ schema cài mới khác.

`legacy/` chứa công cụ chuyển ERD/schema cũ và các test tương ứng. Chỉ dùng trên bản
sao đã khảo sát. SQL cũ ở archive/database, không phải bộ migration production.

Install-Lan/Start-Lan/Update-Lan/Backup-Lan/Set-LanAccess là cơ chế launcher LAN lịch sử.
Các lệnh điều khiển launcher từ chối chạy khi đã có service FarmManagement-PostgreSQL,
để không tranh quyền điều khiển cụm. Chúng không thuộc luồng Run IntelliJ/release mới.

Không chạy Seed-Local trên database chính; tài khoản thực được đăng ký/cấp qua API.
Mật khẩu, DPAPI, chứng chỉ và thư mục cài nằm ngoài repository. Các script chỉ là công
cụ vận hành/kiểm thử; dữ liệu nghiệp vụ thuộc PostgreSQL.
