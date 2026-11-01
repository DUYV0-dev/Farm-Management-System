# Kiểm chứng một file cấu hình — 31/10/2026

Runtime chỉ còn `src/main/resources/application.properties`. Đã bỏ cấu hình
development/production và cơ chế tự tìm file IDE; file IDE ngoài project đã được
gỡ sau khi chuyển giá trị sang biến môi trường Windows User. Bản rollback mã hóa
bằng tài khoản Windows được giữ ngoài repository.

Kết nối giữ nguyên: web HTTPS 8443, PostgreSQL `127.0.0.1:55433/farm_management`.
Mật khẩu database, JWT và chứng chỉ giữ nguyên giá trị, không ghi vào source/Git.

- [Kiểm chứng production](verification.json): 155 test, 140 đạt, 15 bỏ qua,
  không lỗi/thất bại. Trình duyệt, restart backend/PostgreSQL và backup/restore đạt.
- [Chạy từ source](source-check.json): sau khi gỡ file IDE, source dùng biến Windows,
  không cần profile, HTTPS và database đúng; chỉ gọi health/trang chủ/tài nguyên,
  không đăng nhập hoặc ghi dữ liệu nghiệp vụ.
- `Test-DatabaseConfiguration.ps1`: cụm PostgreSQL riêng ở cổng tạm, không có file IDE;
  chạy app, sao lưu online, kiểm tra checksum và khôi phục thật đều đạt.
- `Set-LocalDatabase.ps1` ở chế độ preview vẫn nhận đúng service/pgdata từ metadata
  cài đặt sau khi gỡ file IDE. Không thay đổi service hoặc dữ liệu thật.
- JAR kiểm chứng: SHA256 `D8016AF3BD025034850E08FF9D81172E91A8F12CC73CAD6666C61C7045E67A68`.
- Gói phát hành: `dist/farm-20261031-103702-d8016af3bd02.zip`.

Các kiểm thử được bỏ qua vẫn là nhóm legacy/context yêu cầu môi trường riêng;
bộ workspace PostgreSQL và luồng trình duyệt chạy trên database tạm tách biệt.

**Thoát và mở lại IntelliJ một lần** để nhận biến môi trường mới, rồi Run main
như bình thường. Chi tiết các biến và cách triển khai: [ENVIRONMENT](../../docs/ENVIRONMENT.md).
