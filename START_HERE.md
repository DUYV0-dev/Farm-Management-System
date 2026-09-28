# Bắt đầu

1. Đọc README_VI.md theo thứ tự.
2. Chép src, pom.xml, database, scripts, docs vào thư mục project chứa mvnw.cmd; sao lưu trước khi ghi đè.
3. Chạy database/05_Authentication_Sessions.sql bằng postgres trên farm_management_erd_v1 đúng một lần.
4. Chạy .\mvnw.cmd clean test package.
5. Chạy .\scripts\Seed-Local.ps1 để tạo tài khoản ứng dụng thử.
6. Chạy .\scripts\Start-Local.ps1 và mở http://127.0.0.1:8080.
7. Chạy .\scripts\Test-Login.ps1 trong Terminal thứ hai và lưu minh chứng theo docs/TEST_CHECKLIST.md.

Không chép thư mục Auth_Login_v1 thành thư mục lồng trong src. Không chạy lại DDL V1, không xóa Database. Tài khoản farm_app là tài khoản PostgreSQL; tài khoản seed là tài khoản đăng nhập web.
