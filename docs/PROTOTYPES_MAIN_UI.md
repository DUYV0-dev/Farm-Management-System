# Giao diện chính và các trang bổ sung

Ứng dụng mở `/` bằng đúng giao diện cũ trong `frontend/prototypes/login.html`,
hiển thị trong khung của `index-shell.html` để phiên được giữ khi chuyển trang.
Đăng nhập và đăng ký sử dụng API thật. Phiên và token giữ trong bộ nhớ của
`app.js`, các trang features dùng chung phiên và trang trại đang chọn.

## Ghi chú các chủ đề giao nhau trước khi chuyển giao diện

| Chủ đề | Prototypes | Features | Cách sử dụng |
| --- | --- | --- | --- |
| Cây trồng, mùa vụ | Dashboard, Reports: thống kê | farm/farm.html: quản lý chi tiết | Thống kê ở trang chính, nhập và sửa ở trang bổ sung |
| Vật tư, tồn kho | Dashboard, Reports: thống kê | inventory/material.html: quản lý chi tiết | Thống kê ở trang chính, nghiệp vụ kho ở trang bổ sung |
| Bón phân, phun thuốc | Activities, Schedule: chủ đề canh tác | material-usage/material-usage.html: nhật ký sử dụng thực tế | Nhật ký thật dùng trang bổ sung; hoạt động và lịch chưa có API vận hành |

Không có hai trang hoàn toàn trùng chức năng giữa hai thư mục.
Ngoài hai thư mục, Login, Dashboard và Workers trùng chủ đề với giao diện
đăng nhập, tổng quan và nhân sự cũ trong `src/main/resources/static`.
Các trang prototypes đảm nhiệm bố cục; các script vận hành hiện có tiếp tục
xử lý đăng nhập, quyền, dữ liệu và nhân sự.

## Trạng thái các trang

- Login: đăng nhập, đăng ký chủ trang trại và trang trại bằng PostgreSQL.
- Dashboard, Reports: khôi phục nguyên bố cục, thẻ thống kê, biểu đồ và bảng báo cáo cũ.
  Số liệu vẫn là dữ liệu minh họa, chưa nối API thống kê vận hành.
- Workers: giữ bảng, bộ lọc và hộp thoại nhân viên cũ với dữ liệu mẫu.
- Activities, Index (Công việc), Schedule, Notifications: giữ giao diện và thao tác
  cũ; dữ liệu mẫu lưu trong trình duyệt hoặc bộ nhớ, không phải dữ liệu PostgreSQL.
- Nhân sự & Phân quyền: mục bổ sung riêng để chủ tạo tài khoản nhân viên thật,
  cấp quyền và hoàn tất thiết lập trang trại bằng API workspace.
- Features: Trang trại & Cây trồng, Vật tư & Kho, Chi phí, Phân bón & Thuốc BVTV
  mở từ menu trong khung giao diện chính.

Các HTML khôi phục từ bản cũ, dùng CSS, icon và avatar nội bộ. JAR đóng gói cả script
giao diện cũ trong `prototypes/js`, cùng `bridge.js`, `charts.js`, `host.css`, features
và shared. Không tải `demo.js` hoặc `demo.css`, không có banner DEMO.
Đăng nhập giả và nút tài khoản demo đã thay bằng đăng nhập/đăng ký thật trong `js/auth.js`.
`bridge.js` nối menu cũ với phiên thật và các trang features; các hành động của bảng
được gắn qua script ngoài để giữ chính sách CSP không cho phép script inline.

Liên kết trực tiếp tới một trang prototypes sẽ đưa vào giao diện đăng nhập chính,
giữ mục cần mở bằng URL hash. Tải lại trang yêu cầu đăng nhập lại.
