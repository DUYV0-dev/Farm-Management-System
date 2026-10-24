# Đồng bộ main vào son — 19/10/2026

Nguồn: `main` tại `7719d32`; mã nguồn của Sơn trước hợp nhất: `213aaa2`.
Nhánh sao lưu cục bộ: `backup/son-before-main-20261019-213aaa2`.

## Xử lý xung đột

- Giữ luồng đăng ký, đăng nhập, chọn trang trại, phân quyền và cấu trúc
  `frontend/features`, `frontend/shared`, `frontend/prototypes` của nhánh Sơn.
- Giữ mã nguồn Backup & Recovery, Import/Export và các module nghiệp vụ của Sơn.
- Tiếp nhận backend thu hoạch, dashboard/báo cáo, nhà cung cấp/mua hàng,
  khách hàng/bán hàng và truy xuất nguồn gốc từ main.
- Chuyển các trang và JavaScript mới vào thư mục feature tương ứng; cập nhật
  tài nguyên tĩnh, menu và đường dẫn cũ để tương thích với cấu trúc hiện tại.
- Hợp nhất xử lý tải CSV/JSON và PDF/XLSX; giữ phiên đăng nhập trong bộ nhớ.
- Giữ cơ chế yêu cầu đăng nhập của nhánh Sơn cho API nghiệp vụ.
- Giữ dependencies Apache POI và PDFBox, cấu hình đóng gói frontend và tên JAR.
- Giữ cả các migration riêng của hai nhánh theo tên tệp đầy đủ. Các tiền tố
  10/11/12/13 có nhiều tệp khác nhau; không chạy migration chỉ dựa vào số thứ tự.

## Kiểm tra và giới hạn

Biên dịch backend bằng `mvnw.cmd -DskipTests compile` thành công; kiểm tra cú pháp
JavaScript của shell và bridge thành công. Không chạy bộ kiểm thử hoặc migration,
không kết nối database vận hành theo yêu cầu của người dùng.

Import/Export vẫn chưa được kiểm thử nghiệp vụ. Kết quả Backup & Recovery 12/12
đã lưu là kết quả trước lần đồng bộ main này, không phải kiểm thử bản hợp nhất.
Các module bổ sung cần schema và quyền tương ứng theo tài liệu của main; việc gộp
mã nguồn không tự cài đặt migration hoặc cấp quyền mới trên database đang dùng.

Giao diện lịch và truy xuất từ main còn có dữ liệu minh họa; việc giữ mã nguồn
không xác nhận các phần này đã được tích hợp đầy đủ với database production.
