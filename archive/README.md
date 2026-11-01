# Mã và công cụ lịch sử — không thuộc ứng dụng đang chạy

Thư mục này giữ lại giao diện prototype, dữ liệu mẫu/localStorage, backend lưu trong
bộ nhớ và SQL nguồn cũ để tra cứu. Maven không biên dịch hoặc đóng gói `archive/`.

- `frontend/prototypes/`, `frontend/traceability/`, `frontend/old-entry/`: giao diện cũ.
- `backend/`: các module demo dùng ConcurrentHashMap đã được thay bằng PostgreSQL.
- `database/`: SQL lịch sử; không dùng để cài mới hoặc nâng cấp production.

Nguồn hiện hành: `frontend/app`, `frontend/features`, `frontend/shared`,
`src/main/java` và `src/main/resources/db/migration/V*.sql`.
Các công cụ chuyển schema cũ ở `scripts/legacy/` chỉ dành cho bản sao database đã khảo sát.
