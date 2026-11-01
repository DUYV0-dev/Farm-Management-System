# AgriManage — Farm Management System

Ứng dụng web hiện hành dùng frontend HTML/CSS/JavaScript, backend Java 21 Spring Boot
và PostgreSQL. Giao diện hiện tại được giữ nguyên; dữ liệu nghiệp vụ được lưu ở PostgreSQL.

- [Cấu trúc và phân chia trách nhiệm](docs/ARCHITECTURE.md)
- [Chạy IntelliJ, kiểm chứng và phát hành](docs/RUNBOOK.md)
- [Danh mục script và cách chọn đúng lệnh](scripts/README.md)
- [Database trên máy hiện tại](docs/DATABASE_CONFIGURATION.md)

## Chạy như hiện tại

Reload Maven nếu IntelliJ yêu cầu, Run `FarmManagementSystemN02Application.java`.
Trên máy đã cài, chương trình tự đọc cấu hình IDE riêng, mở https://localhost:8443/
và dùng database farm_management tại 127.0.0.1:55433. Stop trong IDE chỉ dừng web.

## Nguồn chính

Frontend: frontend/app, frontend/features, frontend/shared. Backend: src/main/java.
Schema duy nhất: src/main/resources/db/migration/V*.sql. Demo, SQL và hướng dẫn cũ
nằm ở archive; các công cụ chuyển schema lịch sử nằm ở scripts/legacy.

## Kiểm chứng và tạo release

```powershell
.\scripts\Build-Release.ps1
```

Lệnh kiểm tra API/giao diện, PostgreSQL, backup/restore và dữ liệu sau restart rồi tạo
JAR/ZIP có checksum và manifest trong dist. Production phục vụ đúng giao diện đã đóng
gói trong JAR, không đọc source hoặc tự chọn cấu hình cá nhân trên máy.

Các mô tả dự án và hướng dẫn trước khi chuẩn hóa được giữ tại archive/docs.
Không dùng hướng dẫn migration thủ công cũ để cài/nâng cấp database hiện hành.
