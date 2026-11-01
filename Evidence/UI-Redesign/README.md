# Kiểm chứng giao diện — 31/10/2026

Mã nguồn và JAR đã qua `scripts/Test-Production.ps1 -Browser`.
Kết quả máy đọc được: [verification.json](verification.json).

- Backend: **140 đạt**, **15 bỏ qua**, **0 lỗi** trên tổng 155 test.
  Các test bỏ qua thuộc nhóm kiểm thử cũ yêu cầu `management.test.db` và
  context test được bật riêng bằng `RUN_DB_CONTEXT_TEST`. Bộ `workspace.test.db`
  chạy với PostgreSQL kiểm thử riêng trong lần kiểm chứng này.
- Trình duyệt: đăng ký, tạo hai trang trại, tạo nhân viên từ hộp thoại,
  tạo công việc trong lịch, xem tháng/tuần và chi tiết công việc,
  mở các module, báo cáo với thu hoạch thật, đăng bán,
  khám phá sản phẩm công khai của trang trại khác, đặt mua và xác nhận đơn.
- Kiểm tra bố cục trên màn hình 1440×1000 và 390×844; không có lỗi JavaScript
  hoặc tài nguyên module tải lỗi trong luồng kiểm thử.
- Bản ghi tạo từ giao diện có trong PostgreSQL; sau khi khởi động lại backend
  và PostgreSQL, đăng nhập mới vẫn đọc được bản ghi.
- Sao lưu và khôi phục: dữ liệu so sánh nhất quán.
- Khởi chạy từ `target/classes` tương tự IntelliJ: profile `development`,
  HTTPS, kết nối `127.0.0.1:55433/farm_management`, phục vụ giao diện hiện hành.
  Kiểm tra này chỉ gọi health/trang chủ, không đăng nhập hoặc ghi dữ liệu nghiệp vụ.

Các bản ghi minh họa trong ảnh được tạo trong database kiểm thử ở cổng 55432.
Không sửa mật khẩu, migration hoặc dữ liệu của database đang dùng.

| Giao diện | Ảnh |
| --- | --- |
| Tổng quan trang trại | [Xem ảnh](redesign-overview.png) |
| Báo cáo với số liệu thu hoạch | [Xem ảnh](redesign-dashboard.png) |
| Lịch tháng | [Xem ảnh](redesign-calendar.png) |
| Hộp thoại thêm nhân viên | [Xem ảnh](redesign-staff.png) |
| Chi phí | [Xem ảnh](redesign-expenses.png) |
| Vật tư & Kho | [Xem ảnh](redesign-material.png) |
| Mua hàng | [Xem ảnh](redesign-purchase.png) |
| Đơn bán hàng | [Xem ảnh](redesign-sales.png) |
| Thu hoạch | [Xem ảnh](redesign-harvest.png) |
| Sản phẩm của tôi | [Xem ảnh](redesign-marketplace.png) |

Xem [hướng dẫn giao diện](../../docs/UI_WORKSPACE.md) để biết vị trí mã nguồn
và cách chạy lại trong IntelliJ.
