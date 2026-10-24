# Báo cáo kiểm tra giao diện và bản chạy

**Cập nhật giao diện:** prototypes hiện là giao diện chính, features là các trang bổ sung.
Các ghi nhận về nhãn DEMO và server preview bên dưới thuộc lần kiểm tra trước khi chuyển.
Xem [PROTOTYPES_MAIN_UI.md](PROTOTYPES_MAIN_UI.md) cho bố cục và trạng thái chức năng hiện tại.

Ngày kiểm tra: 12/10/2026. Phạm vi là các trang người dùng liệt kê, luồng trang trại
và cấu hình đóng gói triển khai. Đây là kết quả kiểm thử trên database riêng, chưa là
xác nhận hệ thống đã triển khai công khai hoặc database vận hành đã được nâng cấp.

## Đã xử lý

- Tám HTML demo thiếu đường dẫn CSS sau khi chuyển thư mục: đã sửa liên kết, thêm nhãn DEMO
  và kiểm tra tải CSS/JS/ảnh cho từng trang. Font, icon, avatar và biểu đồ demo chạy nội bộ.
- Menu nghiệp vụ trỏ sang file không tồn tại: thống nhất menu trang trại và chuyển module
  qua ứng dụng chính, giữ phiên đăng nhập trong bộ nhớ. URL cũ chuyển về luồng tương ứng.
- Giao diện đăng nhập/tổng quan/module dùng chung phong cách AgriManage của bản prototype:
  nền mint, thẻ trắng bo góc, đăng nhập/đăng ký dạng thẻ ở giữa và menu ngang khi đã đăng nhập.
  Tài khoản/đổi mật khẩu nằm trong menu Tài khoản; menu nghiệp vụ cuộn ngang trên điện thoại.
- Tổng quan hiện dùng số lượng lấy từ API PostgreSQL theo trang trại/quyền; không dùng
  số mẫu của dashboard demo. Kết quả tải muộn được chặn khi người dùng đổi màn hình/phiên/trang trại.
- Tổng quan có biểu đồ cột số bản ghi, biểu đồ vòng trạng thái mùa vụ và biểu đồ chi phí
  theo nhóm. Chi phí lấy từ API summary, giữ số thập phân; mùa vụ đọc đủ các trang dữ liệu.
  Không có quyền chi phí thì không tải/hiển thị biểu đồ chi phí. Dữ liệu trống và lỗi tải
  hiển thị riêng; biểu đồ SVG dùng tài nguyên nội bộ và có nhãn số liệu bằng văn bản.
- Chủ website và chủ trang trại được tách quyền: SYSTEM_ADMIN quản lý tài khoản/vai trò
  hệ thống; OWNER quản lý nhân sự và nghiệp vụ của mình; nhân viên nhận quyền theo vị trí.
- Onboarding thật: đăng ký tài khoản và trang trại trong transaction, đăng nhập, thêm
  nhân viên và vị trí, hoàn thành bước nhân sự rồi mới cho phép thiết lập nghiệp vụ.
- Production yêu cầu cấu hình database/khóa JWT, không tạo khóa mẫu hoặc mở UI desktop;
  đọc frontend từ JAR và kiểm tra tenancy/RLS khi khởi động. Prototype không đóng gói.
- Có schema nền cài mới và script cài database trống; nâng cấp dữ liệu cũ dùng luồng riêng.

## Kết quả xác minh

Chạy `scripts/Test-Workspace.ps1 -Browser` với PostgreSQL 18 và Java 21:

- 79 bài được thống kê: 66 thành công, 13 thuộc cấu hình integration cũ không bật;
  không có bài thất bại. Bộ hiện hành kiểm tra đăng ký/onboarding, quyền công việc,
  dữ liệu tách giữa trang trại, API và lưu PostgreSQL.
- Build JAR thành công. JAR chạy profile production từ thư mục riêng, sử dụng frontend
  đóng gói. Endpoint health trả 200; đường dẫn prototype không được công khai.
- Chrome kiểm tra đăng ký, mật khẩu sai, thêm nhân viên, tạo khu đất và đọc lại bằng
  tài khoản nhân viên; mở vật tư, chi phí và sử dụng vật tư; kiểm tra nút theo quyền.
- Link vật tư → sử dụng vật tư giữ nguyên phiên và menu đánh dấu module hiện tại.
  Tổng quan đếm đúng khu đất vừa tạo. Viewport 390px không tràn ngang ở tổng quan.
- Tám HTML prototype tải được với tài nguyên nội bộ và nhãn DEMO; không có exception JS
  hoặc tài nguyên lỗi trong lượt kiểm tra. Đây là xác minh khả năng xem demo, không chứng nhận
  nghiệp vụ demo lưu dữ liệu thật.

Ảnh kiểm tra: `target/workspace-browser.png`, `target/workspace-mobile.png`,
`target/workspace-login.png`, `target/workspace-register.png`, `target/workspace-overview.png`.
Log/data kiểm thử ở `.test-data/workspace-check-56bbef4f0a8f45f186abe9a1f39f8bd9/`.

## Việc cần làm trên môi trường triển khai

Database thật cổng 5432 chưa được khảo sát/nâng cấp vì chưa có thông tin xác thực quản trị.
Không xóa, ghi đè hoặc tự gán chủ cho dữ liệu cũ. Với schema ERD v1 `material.unit_id`,
cần khảo sát chuyển sang schema warehouse; script nâng cấp sẽ dừng nếu schema không phù hợp.

Thiết lập DB_PASSWORD/JWT_SECRET, tài khoản dịch vụ, HTTPS, giới hạn truy cập PostgreSQL,
rate limiting đăng nhập tại gateway, backup/khôi phục và kiểm thử tải trên máy triển khai.
Chưa kiểm chứng domain, HTTPS hoặc chịu tải vì chưa có môi trường triển khai được cung cấp.

Hoạt động/công việc/lịch/thông báo/báo cáo nâng cao vẫn là prototype, chưa phải các module
dữ liệu thật. Danh sách file và giới hạn rõ tại [DEMO_FILES.md](DEMO_FILES.md).
Thực hiện triển khai theo [DEPLOYMENT.md](DEPLOYMENT.md); luồng và quyền tại [WORKSPACE.md](WORKSPACE.md).
