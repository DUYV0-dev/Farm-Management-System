# AgriManage — cấu trúc hiện hành

Khung giao diện tổng được giữ, các module được thống nhất theo [hướng dẫn giao diện](UI_WORKSPACE.md). Trình duyệt hiển thị HTML/CSS/JavaScript và gọi
API; Spring Boot xác thực, phân quyền, kiểm tra nghiệp vụ và quản lý transaction;
PostgreSQL là nơi lưu dữ liệu nghiệp vụ duy nhất.

## Nguồn mã

| Thư mục | Trách nhiệm |
| --- | --- |
| `frontend/app/` | Trang đăng nhập, trang tổng tài khoản, cửa hàng, CSS và JS của shell |
| `frontend/features/` | Các module giao diện nghiệp vụ đang chạy |
| `frontend/shared/` | API client, stylesheet và tài nguyên dùng chung |
| `src/main/java/.../backend/` | API, Service, Store và cấu hình máy chủ |
| `src/main/resources/db/migration/` | Bộ migration Flyway duy nhất |
| `src/main/resources/application*.properties` | Một cấu hình runtime, nhận giá trị máy qua biến môi trường |
| `src/test/`, `frontend/tests/` | Kiểm thử backend và trình duyệt |
| `scripts/` | Các entry point phát triển, kiểm chứng, phát hành và vận hành |
| `scripts/legacy/`, `archive/` | Công cụ/schema/giao diện cũ; không thuộc JAR production |
| `target/`, `.test-data/`, `dist/` | Build, dữ liệu thử và bản phát hành; không phải nguồn code |

Frontend được đóng gói vào cùng JAR với backend, cùng origin. Điều này không thay đổi
việc phân chia trách nhiệm. Frontend không kết nối JDBC trực tiếp, không chứa mật khẩu
database và không dùng localStorage/sessionStorage/IndexedDB làm nguồn dữ liệu nghiệp vụ.
Token và trạng thái giao diện/giỏ hàng trong bộ nhớ là trạng thái tạm; tải lại phải xác thực lại.

## Backend

`Controller → Service → Store → PostgreSQL`:

- Controller xử lý HTTP, lấy actor/request ID và trả response API.
- Service kiểm tra nghiệp vụ, quyền thao tác, trạng thái và transaction.
- Store chứa SQL tham số hóa, khóa bản ghi và ánh xạ kết quả JDBC.
- PostgreSQL giữ bảng, khóa ngoại, ràng buộc, RLS, lịch sử/audit và các hàm giao dịch.

Các module operations, account portal và storefront đã được tách theo cùng cấu trúc
với các module farm, inventory, usage, sales. Không còn controller/service sử dụng
JdbcTemplate trực tiếp. `ArchitectureTests` bảo vệ ranh giới này.

Các ràng buộc/trigger và hàm store_checkout/store_receipt vẫn thuộc schema PostgreSQL;
không di chuyển hoặc viết lại các migration đã chạy. Chúng bảo vệ tính nhất quán và
quyền truy cập dữ liệu, không phải dữ liệu giả phía frontend.

## Môi trường

| Cách chạy | Cấu hình | Tài nguyên giao diện |
| --- | --- | --- |
| Run main trong IntelliJ | application.properties + biến môi trường | Đọc `frontend/` để chỉnh sửa và xem ngay |
| Maven/JUnit | Cấu hình test chỉ nằm trong src/test; runner chọn database riêng | Tài nguyên build |
| JAR | Cùng application.properties + biến môi trường triển khai | Chỉ đọc tài nguyên trong JAR |

Source và JAR dùng cùng kiểm tra bảo mật/schema nghiêm ngặt. Không có cơ chế tự tìm
cấu hình IDE hoặc import file production từ development. Xem [ENVIRONMENT](ENVIRONMENT.md).

## Dữ liệu và migration

Database hiện có ở `127.0.0.1:55433/farm_management`; role runtime là farm_app.
Role owner dùng cho migration/backup, không dùng để chạy API. Cụm 5432 độc lập và không
được chọn mặc định. Các file trong pgdata do PostgreSQL quản lý, không sửa thủ công.

Chỉ `src/main/resources/db/migration/V*.sql` là nguồn schema hiện hành. Thêm migration
mới khi cần nâng cấp; không sửa checksum của phiên bản đã áp dụng. Code refactor này
không sửa migration 001–018 hoặc chuyển/xóa dữ liệu hiện có.

## Kiểm chứng bản phát hành

Build sạch, kiểm tra không có demo trong JAR, so checksum tài nguyên với nguồn, migrate
lặp/validate trên PostgreSQL tạm, chạy test API và trình duyệt. Luồng browser tạo dữ
liệu, SQL xác nhận bản ghi, dừng và khởi động lại backend/PostgreSQL, đăng nhập mới và
xác nhận dữ liệu vẫn hiển thị. Sao lưu và phục hồi sang database khác được đối chiếu.

release.json gắn SHA256 JAR, fingerprint nguồn và kết quả kiểm chứng. Build-Release
không chấp nhận JAR hoặc nguồn đã thay đổi sau kiểm chứng. Việc phát hành không tự
triển khai hoặc thay database của máy đang sử dụng.
