# Nguồn schema duy nhất

Ứng dụng dùng Flyway và các file `src/main/resources/db/migration/V*.sql`.
Chỉ thêm migration mới; không sửa các migration đã áp dụng vào database thật.

`DatabaseMigrations` cung cấp migrate/validate/info; `scripts/Install-Database.ps1`
là entry point cài mới. Không chạy các SQL rời theo thứ tự tên file.

SQL lịch sử nằm ở `archive/database/`; công cụ chuyển dữ liệu cũ ở `scripts/legacy/`.
Lệnh quản trị cấp quyền rõ ràng nằm ở `scripts/maintenance/sql/`.
