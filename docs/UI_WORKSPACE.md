# Giao diện AgriManage

Giữ khung tổng màu xanh trầm và nền kem. Các module nghiệp vụ dùng chung bộ kiểu
trong `frontend/shared/workspace.css`; không duy trì một bản giao diện demo khác.

## Các trang đã cập nhật

| Trang | Hành vi |
| --- | --- |
| Nhân sự & Phân quyền | Danh sách và tìm kiếm là nội dung chính. Nút **Thêm nhân viên** mở hộp thoại tạo tài khoản. Nút **Phân quyền** mở hộp thoại chỉnh vai trò/trạng thái. |
| Tổng quan trang trại | Chỉ số hiện tại và biểu đồ tiến độ công việc, sản lượng, chi phí, tình trạng tồn kho theo quyền được cấp. |
| Báo cáo trang trại | Bộ lọc ngày gọn, biểu đồ đường theo tháng, cột theo cây trồng, vòng theo trạng thái mùa vụ/công việc/tồn kho. Có thể mở bảng số liệu chi tiết và xuất báo cáo như trước. |
| Lịch canh tác | Xem tháng, tuần hoặc danh sách; chuyển kỳ, về hôm nay, lọc trạng thái/mùa vụ/nhân viên. Mở công việc để xem lịch sử, điều chỉnh lịch hoặc cập nhật tiến độ theo quyền. |
| Chi phí, Mua hàng, Vật tư & Kho, Khu đất & Mùa vụ | Dùng cùng kiểu nút, thẻ thống kê, tab, bộ lọc, bảng và hộp thoại; bỏ tiêu đề trùng và nền gradient của trang con. |
| Thu hoạch | Bộ lọc theo mùa vụ/ngày và lịch sử ở bên dưới. Chọn mùa vụ rồi bấm **Ghi nhận thu hoạch** để mở form. |
| Đơn bán hàng | Hai hướng: **Đăng sản phẩm** lên cửa hàng hoặc **Tạo đơn bán** trực tiếp cho khách. Danh sách đơn bán là tab mặc định. |
| Cửa hàng của tôi | Mặc định hiển thị sản phẩm thuộc các trang trại bạn sở hữu. Tìm kiếm, lọc trang trại/trạng thái và thiết lập đăng bán. |
| Khám phá nông sản | Hiển thị sản phẩm đã công khai của các trang trại khác. Nút **Xem & đặt mua** mở cửa hàng công khai với đúng bộ lọc trang trại. |

Các trang con gửi chiều cao nội dung lên khung tổng để dùng một thanh cuộn dọc
của trang. Bảng rộng và lịch trên điện thoại vẫn có thể cuộn ngang.

## Dữ liệu và nghiệp vụ

- Các biểu đồ gọi API backend; khi chưa có bản ghi, hiển thị trạng thái trống.
- Sản lượng có đơn vị khác nhau được vẽ riêng. Không cộng kg với tấn hay đơn vị khác.
- Chi phí sản xuất và giá trị mua hàng được trình bày thành hai khoản riêng.
- Tồn kho biểu diễn **số dòng vật tư** còn tồn/hết tồn; bảng chi tiết giữ số lượng và đơn vị thật.
- Báo cáo và thu hoạch chọn đúng trang trại đang làm việc.
- Lịch dùng ngày bắt đầu và hạn hoàn thành thật. Kéo công việc sang ngày khác
  mở form xác nhận lịch mới, giữ độ dài công việc và kiểm tra phiên bản khi lưu.
- Shop riêng dùng API tài khoản. Khám phá và cửa hàng khách dùng API sản phẩm
  công khai, không đọc danh mục riêng của chủ trại khác.
- Đăng bán, nhân sự, thu hoạch và công việc vẫn được backend xác thực/phân quyền
  và lưu trong PostgreSQL. Không tạo dữ liệu nghiệp vụ giả trong trình duyệt.

## Nơi chỉnh mã

| Tệp | Trách nhiệm |
| --- | --- |
| `frontend/app/app.js` | Điều hướng và khung tổng; biểu đồ doanh thu tài khoản. |
| `frontend/app/marketplace.js` | Hai tab cửa hàng riêng/khám phá và bộ lọc sản phẩm. |
| `frontend/app/features/workspace/staff.js` | Danh sách và hộp thoại nhân sự. |
| `frontend/shared/charts.js` | Biểu đồ SVG dùng chung; chú thích, giá trị và trạng thái trống. |
| `frontend/shared/embedded.js` | Đồng bộ chiều cao trang con và vị trí hộp thoại. |
| `frontend/shared/workspace.css` | Bộ kiểu thống nhất cho các module hiện hành. |
| `frontend/features/operations/calendar.js` | Lịch và các thao tác công việc từ lịch. |
| `frontend/features/operations/insights.js` | Tổng quan trang trại bằng dữ liệu API. |
| `frontend/features/advanced-dashboard/` | Báo cáo, biểu đồ và xuất PDF/Excel. |
| `frontend/features/harvest/` | Bộ lọc, form và lịch sử thu hoạch. |
| `backend/operations/OperationsController → OperationsService → OperationsStore` | API lịch; kiểm tra quyền/ngày/bộ lọc, đọc PostgreSQL trong phạm vi trang trại. |

Không thêm migration hoặc sửa dữ liệu PostgreSQL hiện có cho đợt thay đổi giao diện này.

## Chạy và kiểm tra

Trong IntelliJ, Stop rồi Run lại `FarmManagementSystemN02Application`. Tải lại trang
bằng **Ctrl + F5** để nhận các tệp giao diện mới. Cấu hình kết nối database giữ theo
quy trình hiện hành trong `DATABASE_CONFIGURATION.md`.

Kiểm thử độc lập:

```powershell
.\scripts\Test-Production.ps1 -Browser
```

Quy trình dùng PostgreSQL kiểm thử riêng: build JAR, kiểm thử backend, thao tác
trình duyệt trên màn hình lớn/điện thoại, đọc lại bản ghi được tạo từ giao diện,
khởi động lại backend và PostgreSQL, rồi kiểm tra sao lưu–khôi phục.
Không dùng database đang chạy thật làm nơi tạo dữ liệu thử.

Ảnh và kết quả kiểm chứng của bản giao diện được lưu trong `Evidence/UI-Redesign/`.
