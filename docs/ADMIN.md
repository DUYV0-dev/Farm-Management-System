# Trang quản trị người dùng

Mở `/admin` trên cùng địa chỉ website, ví dụ `https://localhost:8443/admin`.
Đăng nhập bằng tài khoản đã được cấp quyền. Menu **Quản trị hệ thống** cũng xuất
hiện trong trang tài khoản khi người dùng có quyền đọc người dùng hoặc vai trò.

Trang có tìm kiếm/phân trang, thêm tài khoản, sửa họ tên/email, khóa/mở khóa,
đặt lại mật khẩu và quản lý vai trò/quyền. Mỗi thao tác kiểm tra quyền ở backend.
Khóa tài khoản hoặc đặt lại mật khẩu thu hồi các phiên hiện tại. Không thể tự
khóa mình hoặc loại bỏ quản trị viên đang hoạt động cuối cùng qua API.

## Cấp quyền admin trực tiếp bằng PostgreSQL

Không có tài khoản/mật khẩu admin mặc định và không tự nâng quyền người đăng ký
đầu tiên. Đăng ký tài khoản bình thường trước, rồi mở Query Tool của pgAdmin trên
đúng database `farm_management` bằng tài khoản quản trị database. Thay `ten_dang_nhap`
bằng username thực trong câu SQL dưới đây:

```sql
BEGIN;
SELECT pg_advisory_xact_lock(62024001);
INSERT INTO public.user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM public.app_user u CROSS JOIN public.role r
WHERE u.username = 'ten_dang_nhap'
  AND u.status = 'ACTIVE'
  AND r.scope = 'SYSTEM'
  AND r.role_name = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;
COMMIT;
```

Kiểm tra đúng tài khoản/vai trò:

```sql
SELECT u.user_id, u.username, u.status, r.role_name
FROM public.app_user u
JOIN public.user_role ur ON ur.user_id = u.user_id
JOIN public.role r ON r.role_id = ur.role_id
WHERE u.username = 'ten_dang_nhap' AND r.scope = 'SYSTEM';
```

Nếu câu INSERT không thêm dòng và chưa có `SYSTEM_ADMIN`, kiểm tra username,
trạng thái ACTIVE và database đang kết nối. Quyền nằm trong quan hệ
`app_user -> user_role -> role`; không đổi `status` thành `ADMIN` và không dùng
vai trò PostgreSQL `farm_app` làm vai trò người dùng website.

Để có kiểm tra tài khoản tồn tại và ghi audit tự động, dùng script psql hiện có:

```sh
psql -h <db-host> -p <db-port> -U <db-owner> -d farm_management -v admin_username=ten_dang_nhap -f scripts/maintenance/sql/Bootstrap-Admin.sql
```

Trên máy local, database đang dùng cổng `55433`. Khi dùng pgAdmin, chạy SQL thuần
ở trên; cú pháp `\set`/`:'admin_username'` của file script chỉ dành cho psql.
Không có thao tác đổi quyền database vận hành được thực hiện tự động trong lần
thêm trang này. Không chỉnh các migration Flyway đã chạy.

Sau khi cấp quyền, đăng nhập lại hoặc bấm menu Quản trị hệ thống/tải lại trang.
API đọc lại quyền từ database mỗi request, nên thu hồi vai trò có hiệu lực với
cả token đang tồn tại; giao diện đang mở cập nhật khi tải lại chức năng.

Để thu hồi admin qua PostgreSQL, giữ ít nhất một quản trị viên ACTIVE khác, rồi
xóa riêng liên kết SYSTEM_ADMIN của tài khoản cần thu hồi; giữ vai trò USER để
tài khoản tiếp tục dùng website bình thường. Sửa trực tiếp SQL bỏ qua các chốt
kiểm tra admin cuối cùng của API.
