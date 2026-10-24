# Quản lý sử dụng phân bón và thuốc bảo vệ thực vật

Chức năng ghi nhận mỗi lần bón phân hoặc phun thuốc, số lượng, ngày sử dụng,
ghi chú, người ghi nhận và mùa vụ liên quan. Trang trại, khu đất và cây trồng
được xác định từ mùa vụ; người dùng không cần nhập lại các quan hệ này.

## Cài đặt

Chạy `database/11_Material_Usage_Management.sql` bằng tài khoản chủ database
trên ERD v1 hiện có, sau các migration xác thực 05/06. Module sử dụng
`material.unit_id` liên kết `unit.unit_id` để lấy `unit_code`, và
`material.category` để hiển thị nhóm vật tư. Không cần bảng `material_category`
hay hàm `lock_farm_management` từ các script 08/09.
Dự án tắt tự động khởi tạo SQL/Flyway,
nên cần chạy script này trước khi sử dụng API và giao diện.

Ví dụ chạy bằng PowerShell (PostgreSQL hỏi mật khẩu, không lưu trong lệnh):

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -h 127.0.0.1 -U postgres -d farm_management_erd_v1 -W -v ON_ERROR_STOP=1 -f database/11_Material_Usage_Management.sql
```

Nếu IntelliJ báo `Unable to resolve` sau khi migration chạy thành công,
chọn datasource `farm_management_erd_v1`, bật schema `public` và dùng
**Refresh/Synchronize** trong cửa sổ Database. Đảm bảo file SQL gắn với
datasource này và dialect PostgreSQL. Thông báo của trình soạn thảo
không tự xác nhận rằng câu SQL đã thất bại khi thực thi.

Script cấp `usage:read` và `usage:create` cho SYSTEM_ADMIN. Vai trò khác
cần được cấp quyền tương ứng qua quản lý vai trò hiện có. Đăng nhập lại
hoặc mở lại module để giao diện nhận các quyền mới.

Mở mục **Phân bón & Thuốc BVTV** trên Dashboard, liên kết trong trang
**Vật tư & Kho**, hoặc `/frontend/material-usage.html`.

## Quy tắc nghiệp vụ

- Loại sử dụng: `FERTILIZER` (phân bón), `PESTICIDE` (thuốc bảo vệ thực vật).
  Danh mục vật tư hiện tại không có mã loại cố định; người ghi nhận chọn loại
  sử dụng phù hợp cho vật tư, không suy luận loại từ tên danh mục.
- Bắt buộc chọn một mùa vụ và một vật tư hiện có. Chấp nhận mùa vụ
  `IN_PROGRESS` hoặc `COMPLETED` để có thể bổ sung nhật ký đã thực hiện.
  Trang trại, khu đất, cây trồng và vật tư phải đang hoạt động.
- Số lượng lớn hơn 0, không quá `999999999999.999`, tối đa 3 chữ số thập phân.
  Đơn vị lấy từ danh mục vật tư. Tên và đơn vị vật tư được lưu tại thời điểm
  ghi nhận, nên thay đổi danh mục về sau không thay đổi nhật ký cũ.
- Ngày sử dụng định dạng `YYYY-MM-DD`, không ở tương lai theo múi giờ
  `Asia/Bangkok`. Đây là ngày bón/phun, khác thời điểm tạo bản ghi.
- Ghi chú tối đa 1000 ký tự. Bản ghi chỉ được thêm và đọc qua API;
  không có API sửa/xóa nhằm giữ lịch sử. Lịch sử vẫn đọc được khi
  vật tư hoặc mùa vụ không còn đủ điều kiện ghi nhận mới.
- Tạo nhật ký và audit trong cùng giao dịch. Migration tự định nghĩa
  `lock_material_usage`; trigger khóa các dòng mùa vụ, trang trại, khu đất,
  cây trồng, vật tư và đơn vị để tránh thay đổi đồng thời khi ghi nhận.
- Tổng lượng sử dụng cộng riêng theo loại, vật tư, tên và đơn vị đã lưu;
  không cộng chung kg, lít, bao hoặc các vật tư khác nhau.
- Nhật ký không tự trừ tồn kho: yêu cầu không xác định kho xuất. Cập nhật
  tồn kho vẫn thực hiện qua chức năng kho hiện có.

## API

Mọi API cần Bearer token; sử dụng định dạng phản hồi `Api.success` hiện có.

| Phương thức | Đường dẫn | Quyền | Chức năng |
| --- | --- | --- | --- |
| GET | `/api/v1/material-usages/options` | `usage:read` hoặc `usage:create` | Loại, mùa vụ và vật tư; `available` xác định lựa chọn được phép ghi nhận |
| POST | `/api/v1/material-usages` | `usage:create` | Ghi nhận, trả HTTP 201 và Location |
| GET | `/api/v1/material-usages/{id}` | `usage:read` | Chi tiết lần sử dụng |
| GET | `/api/v1/material-usages` | `usage:read` | Lịch sử phân trang |
| GET | `/api/v1/material-usages/summary` | `usage:read` | Tổng số lần và tổng lượng theo vật tư/đơn vị |

Ví dụ body tạo nhật ký (ID phải tồn tại):

```json
{
  "seasonId": 1,
  "materialId": 2,
  "type": "FERTILIZER",
  "quantity": "12.500",
  "applicationDate": "2026-10-10",
  "notes": "Bón thúc tại khu đất phía đông"
}
```

Lịch sử và tổng hợp hỗ trợ `farmId`, `seasonId`, `materialId`, `type`,
`fromDate`, `toDate`; khoảng ngày bao gồm cả hai đầu. Lịch sử thêm `page`
(bắt đầu từ 0, tối đa 100000) và `size` (1–100, mặc định 20).
Sắp xếp ngày sử dụng giảm dần, sau đó ID giảm dần. Tham số không hỗ trợ,
lặp lại, ID không dương hoặc khoảng ngày đảo ngược bị từ chối.

Không viết hoặc chạy test theo yêu cầu.
