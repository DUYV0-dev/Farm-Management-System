# Sửa Run IntelliJ khi chưa nhận biến Windows — 31/10/2026

Log người dùng đã xác nhận PostgreSQL kết nối bằng farm_app nhưng lỗi khởi tạo JWT
vì tiến trình IntelliJ chưa có JWT_SECRET. Việc nạp biến trước khi chạy kiểm tra đã
che mất tình huống này trong lần kiểm chứng trước.

Launcher source hiện đọc lại các biến Windows User còn thiếu vào Spring defaults.
Giữ khóa JWT đã lưu, các giá trị cấu hình rõ ràng được ưu tiên. Không thêm properties
file, không tự sinh khóa, không đổi mật khẩu database hoặc ghi dữ liệu nghiệp vụ.
JAR phát hành và nền tảng ngoài Windows không đọc registry User bằng cơ chế này.

- 21 kiểm thử cấu hình/xác thực đạt, không bỏ qua, không lỗi.
- Kiểm thử mới xác nhận khôi phục JWT/TLS, giữ ưu tiên thiết lập rõ ràng và giới hạn
  cơ chế đọc Windows User cho source trên Windows.
- Kiểm tra source thực tế xóa toàn bộ các biến DB/JWT/TLS khỏi tiến trình thử trước
  khi khởi chạy main, giữ nguyên biến User đã lưu. Source vẫn kết nối
  PostgreSQL 55433/farm_management và phục vụ HTTPS trên cổng thử riêng 18443.
- Chỉ gọi health/trang chủ/tài nguyên. Không đăng nhập hoặc ghi dữ liệu thật.

Kết quả: [source-check.json](source-check.json), [environment-tests.xml](environment-tests.xml).
Đây là kiểm chứng source và kiểm thử liên quan; chưa tạo bản phát hành production mới.
