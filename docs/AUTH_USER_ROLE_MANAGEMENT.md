# Authentication, Authorization & User/Role Management

## Phạm vi đã triển khai

- Đăng nhập và đăng xuất bằng Bearer JWT; kiểm tra phiên, trạng thái tài khoản và quyền từ PostgreSQL mỗi request.
- Argon2id cho mật khẩu; đổi mật khẩu có xác nhận mật khẩu cũ; quản trị viên đặt lại mật khẩu người khác. Mật khẩu mới dài 12–128 ký tự, có chữ và số, không tự trim.
- Tạo, xem, tìm kiếm, phân trang, sửa thông tin, kích hoạt/vô hiệu hóa người dùng; gán nhiều vai trò hệ thống.
- Tạo/sửa vai trò hệ thống và thay thế tập quyền của vai trò.
- Giao diện tiếng Việt tại `/`: đăng nhập, đổi mật khẩu, quản lý người dùng và vai trò theo quyền.
- API và nội dung trang quản trị đều được bảo vệ phía server. Nội dung `/admin/users` và `/admin/roles` được tải bằng Bearer token qua giao diện; mở URL trực tiếp không có token trả 401.

Các API quản trị này áp dụng cho `scope=SYSTEM`. Membership và vai trò `scope=FARM` vẫn thuộc trang trại tương ứng; không được dùng để cấp quyền quản trị hệ thống. Repo hiện chưa có các API nghiệp vụ trang trại để tích hợp quyền theo từng farm. Các đường dẫn chưa mở tiếp tục bị từ chối mặc định.

## Cài đặt trên database hiện có

Yêu cầu Java 21 trở lên, PostgreSQL đã có ERD v1 và migration `05_Authentication_Sessions.sql`.

1. Dừng backend và chạy **một lần**, bằng tài khoản chủ database, file `database/06_User_Role_Management.sql` trong database `farm_management_erd_v1`. Với pgAdmin, mở Query Tool và chạy toàn bộ file; kết quả phải là COMMIT. Nếu lỗi, ROLLBACK và xử lý lỗi trước khi chạy tiếp.
2. Migration thêm `auth_permission`, `auth_role_permission`, danh mục 9 quyền và vai trò `SYSTEM_ADMIN`/`USER` nếu chưa có. Không tự cấp quản trị cho tài khoản nào. `SYSTEM_ADMIN` có đủ quyền; `USER` ban đầu không có quyền quản trị.
3. Nếu chưa có tài khoản, chạy `scripts/Seed-Local.ps1` theo README_VI.md để tạo tài khoản với mật khẩu do bạn nhập.
4. Cấp vai trò quản trị cho **tài khoản ACTIVE đã chọn** bằng psql, sử dụng quyền chủ database:

   ```powershell
   psql -U postgres -d farm_management_erd_v1 -v admin_username=your.username -f database/Bootstrap-Admin.sql
   ```

   Nếu `psql` không có trong PATH, dùng đường dẫn đầy đủ đến `psql.exe` của bản PostgreSQL đã cài. File Bootstrap có lệnh riêng của psql, không chạy nguyên file trong pgAdmin. Script không tạo tài khoản, không thay mật khẩu và có ghi audit.
5. Chạy kiểm thử/đóng gói và khởi động:

   ```powershell
   .\mvnw.cmd clean verify
   .\scripts\Start-Local.ps1
   ```

6. Mở `http://127.0.0.1:8080`, đăng nhập tài khoản quản trị. Hai mục Người dùng và Vai trò và quyền sẽ xuất hiện.

Flyway vẫn tắt để phù hợp database đã được tạo thủ công. Không bật `ddl-auto=create/update`. Migration 06 phụ thuộc cấu trúc bảng hiện được `AuthStore` và `SeedAccount` sử dụng; schema ERD gốc chưa có trong repo, nên cần đối chiếu constraint, độ dài cột và audit enum trên database đích trước nghiệm thu.

## API

Thành công: `{ "data": ..., "requestId": "..." }`, ngoại trừ 204 không có body. Mọi API dưới đây dùng prefix `/api/v1`. Các request có body yêu cầu `Content-Type: application/json`. Gửi token bằng `Authorization: Bearer <token>`.

| Phương thức/đường dẫn | Quyền | JSON body / query |
|---|---|---|
| POST `/auth/login` | Công khai | `{"username":"...","password":"..."}` |
| GET `/auth/me` | Đã đăng nhập | Trả user, permissions và memberships |
| POST `/auth/logout` | Đã đăng nhập | Không body; thu hồi phiên hiện tại |
| POST `/auth/password` | Đã đăng nhập | `{"currentPassword":"...","newPassword":"..."}`; 204 và thu hồi mọi phiên |
| GET `/users` | `users:read` | `page=0&size=20&search=...`; size 1–100; trả items/page/size/total |
| GET `/users/{id}` | `users:read` | Thông tin người dùng, vai trò và quyền; không có hash |
| POST `/users` | `users:create` | `{"username":"worker01","password":"...","fullName":"...","email":"..."}`; 201 và Location; vai trò mặc định USER |
| PUT `/users/{id}` | `users:update` | `{"fullName":"...","email":"..."}` |
| PUT `/users/{id}/status` | `users:status` | `{"status":"ACTIVE"}` hoặc `INACTIVE` |
| PUT `/users/{id}/roles` | `users:assign` | `{"roleIds":[1,2]}`; thay thế toàn bộ vai trò SYSTEM; không được rỗng |
| PUT `/users/{id}/password` | `users:password` | `{"newPassword":"..."}`; 204, thu hồi mọi phiên |
| GET `/roles` | `roles:read` | Vai trò SYSTEM và quyền hiện tại |
| GET `/roles/{id}` | `roles:read` | Một vai trò SYSTEM |
| POST `/roles` | `roles:write` | `{"name":"USER_MANAGER","description":"..."}`; 201 và Location |
| PUT `/roles/{id}` | `roles:write` | `{"name":"USER_MANAGER","description":"..."}` |
| GET `/permissions` | `roles:read` | Danh mục quyền được backend hỗ trợ |
| PUT `/roles/{id}/permissions` | `roles:permissions` | `{"permissions":["users:read","users:update"]}`; mảng rỗng gỡ mọi quyền của vai trò tùy chỉnh |

Username: `[a-z0-9._-]{3,50}`, không sửa sau khi tạo. Full name tối đa 100 ký tự. Email tối đa 254 ký tự, trim và chuyển chữ thường. Tên role: `[A-Z][A-Z0-9_]{1,49}`; description tối đa 255 ký tự. Các trường ngoài hợp đồng bị từ chối. ID dùng INTEGER theo ERD.

Mã lỗi: 400 JSON sai; 401 thiếu/sai token, sai thông tin đăng nhập hoặc phiên bị thu hồi; 403 thiếu quyền; 404 dữ liệu không tồn tại; 405 sai phương thức; 409 trùng/ràng buộc dữ liệu hoặc vi phạm quy tắc quản trị; 415 sai content type; 422 đầu vào không hợp lệ; 503 database không khả dụng.

## Quy tắc phân quyền và phiên

- Spring Security kiểm tra quyền tại service bằng `@PreAuthorize`; trang quản trị còn có kiểm tra tại filter chain. Tham khảo [Spring Security Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html).
- JWT không chứa quyền cố định. Cùng một token mất quyền ở request tiếp theo sau khi vai trò/quyền bị gỡ.
- Quản trị viên được ủy quyền chỉ được cấp những quyền bản thân đang có; không thể sửa tài khoản có quyền cao hơn hoặc tài khoản SYSTEM_ADMIN. Chỉ SYSTEM_ADMIN được cấp vai trò SYSTEM_ADMIN.
- Không tự vô hiệu hóa tài khoản; không loại bỏ/vô hiệu hóa quản trị viên ACTIVE cuối cùng. Các mutation quản trị dùng transaction và PostgreSQL advisory lock chung để tuần tự hóa kiểm tra này giữa các instance ứng dụng.
- Giữ tên vai trò mặc định USER/SYSTEM_ADMIN; không sửa tập quyền SYSTEM_ADMIN qua API. Cấp quyền cho USER sẽ ảnh hưởng mọi người dùng có vai trò này, kể cả tài khoản mới, nên dùng vai trò tùy chỉnh khi cần ủy quyền riêng.
- Thay đổi thông tin người dùng, trạng thái, vai trò, quyền và reset mật khẩu đều ghi audit trong cùng transaction; lỗi audit làm rollback mutation. Audit không chứa mật khẩu/hash/token.
- Đổi/reset mật khẩu hoặc INACTIVE thu hồi mọi phiên. Kích hoạt lại không khôi phục phiên cũ. Logout chỉ thu hồi phiên hiện tại.
- Token ở bộ nhớ trình duyệt; reload cần đăng nhập lại. Không dùng cookie xác thực, không có refresh token/quên mật khẩu qua email.
- Giao diện ẩn nút theo quyền để dễ sử dụng; backend luôn kiểm tra độc lập. Trang người dùng cần `users:read`; bộ chọn vai trò cần thêm `roles:read`. Trang vai trò cần `roles:read`.

## Kiểm thử và giới hạn xác nhận

Chạy `mvnw.cmd clean verify`. Các test mới gồm `ManagementApiTests`, `ManagementServiceTests`, `PasswordHandlingTests`; giữ nguyên test JWT/Argon2id hiện có.

Test HTTP dùng MockMvc, JWT ký thật, Spring Security và service thật; store được mock. Bao phủ 401/403, từng API mutation, trang hạn chế, thu hồi quyền trên cùng token, phiên bị thu hồi, chống nâng quyền, quản trị viên cuối cùng, validation, hash mật khẩu, đổi/reset mật khẩu, audit actor và mã lỗi an toàn.

Test database hiện có chỉ chạy khi `RUN_DB_CONTEXT_TEST=true`; đây là test khởi động context, không thay thế kiểm thử CRUD trên database thực tế. Smoke test giao diện trên Edge headless với API giả lập đã đạt. Chưa xác nhận migration 06, transaction, khóa đồng thời, quyền SQL của farm_app hoặc giao diện kết nối database trên môi trường đích. Kết quả thực tế lần sửa này được ghi trong `docs/VALIDATION.md`.

Sau migration, cần kiểm tra trên database thử nghiệm:

1. Admin tạo một user; user đăng nhập thành công nhưng không mở được API/trang quản trị.
2. Tạo role có `users:read`; gán user; token đang dùng đọc được danh sách. Gỡ quyền; cùng token nhận 403.
3. Đổi mật khẩu; mọi token cũ nhận 401; chỉ mật khẩu mới đăng nhập được.
4. Admin reset mật khẩu user khác; mọi phiên user đó nhận 401.
5. Vô hiệu hóa rồi kích hoạt lại; token cũ vẫn nhận 401.
6. Thử loại bỏ admin cuối cùng, tự vô hiệu hóa và cấp quyền vượt quyền được ủy quyền; đều bị từ chối.
7. Thử dữ liệu trùng, lỗi audit và hai request quản trị đồng thời để xác nhận rollback/khóa trên PostgreSQL thực.
