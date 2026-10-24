# Giao diện prototypes cũ và dữ liệu vận hành

Giao diện chính dùng nguyên các trang prototypes cũ: bảng, biểu đồ, lịch, bộ lọc và hộp thoại.
Không tải banner DEMO. Đăng nhập và đăng ký đã nối API thật; phiên được giữ trong bộ nhớ
của trang cha để chuyển trang không phải đăng nhập lại.

Dashboard, Reports, Workers, Activities, Tasks, Schedule và Notifications vẫn dùng dữ liệu
minh họa và thao tác của bản cũ. Một số dữ liệu được lưu trong localStorage; chúng không phải
bản ghi trong PostgreSQL. Xem [PROTOTYPES_MAIN_UI.md](PROTOTYPES_MAIN_UI.md) để biết các
chủ đề giao nhau và phần nào đã nối API thật.

Các trang features (Trang trại & Cây trồng, Vật tư & Kho, Chi phí, Phân bón & Thuốc BVTV)
sử dụng PostgreSQL và quyền của tài khoản thật. Mục bổ sung Nhân sự & Phân quyền tạo tài khoản
nhân viên thật, khác bảng Workers của prototypes.

JAR đóng gói các HTML prototypes và các script giao diện cũ. `bridge.js` nối điều hướng
với phiên thật. `js/auth.js` thay đăng nhập giả. `charts.js` vẽ các biểu đồ mẫu bằng canvas
nội bộ. `demo.js`, `demo.css` và script đăng nhập giả không tham gia luồng giao diện chính.

Backend activity và notification vẫn chỉ dành cho profile demo, chưa trở thành API vận hành.
Khởi động bằng Spring Boot hoặc JAR, không dùng server tĩnh riêng để vận hành.
