# Chạy dashboard từ IntelliJ

Run `FarmManagementSystemN02Application` bằng Java 21. Ứng dụng phục vụ frontend và API trên `http://127.0.0.1:5500`, tự mở `/` sau khi khởi động thành công. Luồng mới và hướng dẫn nâng cấp database: [WORKSPACE.md](WORKSPACE.md). Không chạy Live Server hoặc Python HTTP server trên cùng cổng.

Database PostgreSQL `farm_management_erd_v1` cần schema warehouse và migration 05–10, 11a, 12 theo `WORKSPACE.md`. Nếu chưa cấu hình `DB_PASSWORD` trong Environment variables của Run Configuration, cửa sổ khởi động hỏi mật khẩu PostgreSQL của `farm_app`. Có thể cấu hình `DB_USERNAME`, `DB_URL`, `JWT_SECRET`, `SERVER_PORT` qua environment variables. Không đưa mật khẩu vào mã nguồn hoặc Git.

Giao diện chính dùng nguyên prototypes cũ, gồm các bảng, biểu đồ, lịch và hộp thoại; số liệu ở các trang cũ vẫn là dữ liệu minh họa. Đăng nhập và đăng ký dùng API thật. Chủ trang trại vào mục bổ sung Nhân sự & Phân quyền để thêm nhân viên và bắt đầu thiết lập. Nhân viên dùng tài khoản chủ tạo. Các trang features sử dụng dữ liệu PostgreSQL thật và quyền tài khoản hiện có. Phiên giữ trong bộ nhớ; tải lại yêu cầu đăng nhập lại. Xem [PROTOTYPES_MAIN_UI.md](PROTOTYPES_MAIN_UI.md) để biết các chủ đề giao nhau và trạng thái chức năng.

Mở module trực tiếp sẽ đưa về trang đăng nhập chính; các module trong trang chủ dùng chung phiên. URL `/frontend/dashboard.html` và `/frontend/login.html` cũ chuyển về `/#home`. Khi Run từ thư mục dự án, Spring đọc giao diện trong `frontend/prototypes`, `frontend/features` và `frontend/shared`; khi triển khai JAR ngoài thư mục dự án, dùng giao diện đã đóng gói.

Nếu không đặt `JWT_SECRET`, launcher tạo khóa ngẫu nhiên cho lần chạy này; khởi động lại làm token cũ mất hiệu lực. Để dùng khóa cố định, cấu hình `JWT_SECRET` Base64 (ít nhất 32 byte ngẫu nhiên). Script `Start-Local.ps1` vẫn dùng khóa lưu bằng DPAPI như trước.

Tắt tự mở trình duyệt bằng argument `--app.open-browser=false`. `SERVER_PORT` vẫn cho phép đổi cổng khi cần. Các bài kiểm thử không bật hộp thoại hoặc tự mở trình duyệt.
