# Quản lý thu hoạch

Module đáp ứng sáu yêu cầu: tạo bản ghi, lưu ngày, sản lượng, chất lượng, liên kết mùa vụ và xem lịch sử. Mở **Thu hoạch** trong màn hình quản lý sau khi đăng nhập. Lịch sử có bộ lọc trang trại, mùa vụ, khoảng ngày và phân trang.

## Chuẩn bị và sử dụng

1. Dùng JDK 21. Chạy các migration nền của dự án đến `09_Management_Integrity.sql`, sau đó chạy `database/10_Harvest_Management.sql` bằng tài khoản chủ cơ sở dữ liệu. Flyway hiện chưa tự chạy migration này. Ví dụ trong PowerShell, thay tên database và tài khoản bằng cấu hình của bạn:

   ```powershell
   psql -h localhost -U postgres -d TEN_DATABASE -v ON_ERROR_STOP=1 -f .\database\10_Harvest_Management.sql
   ```

2. Người quản trị tạo/gán vai trò SYSTEM có `harvests:read` để xem, thêm `harvests:create` để ghi, thêm `crops:update` để cấu hình ngày và đơn vị mùa vụ. Dùng màn hình quản lý vai trò hiện có. Migration không tự cấp các quyền mới cho người dùng.
3. Người dùng phải có `farm_member` ACTIVE tại trang trại và vai trò FARM `MANAGER` để ghi; `OWNER` hoặc `MANAGER` để đọc. SYSTEM_ADMIN vẫn cần quyền thu hoạch và vai trò tại trang trại. Việc gán thành viên FARM hiện thực hiện trong database; không nhầm với vai trò SYSTEM của màn hình quản trị.
4. Tạo trang trại, khu đất, cây trồng và mùa vụ trong mục quản lý hiện có. Chọn mùa vụ ở mục **Thu hoạch**, mở **Cấu hình ngày và đơn vị của mùa vụ**, nhập tên, ngày bắt đầu/kết thúc dự kiến, đơn vị kg hoặc tấn. Những mùa vụ cũ chưa có dữ liệu này sẽ không được ghi thu hoạch.
5. Đặt mùa vụ `IN_PROGRESS` ở mục quản lý mùa vụ. Nhập ngày thu hoạch, sản lượng và chất lượng rồi lưu. Kiểm tra kết quả trong lịch sử.

## Quy tắc

- Sản lượng lớn hơn 0, tối đa 3 chữ số thập phân và không vượt `9999999999999.999`; chất lượng tùy chọn, tối đa 50 ký tự. Đơn vị lấy từ mùa vụ.
- Ngày thu hoạch nằm trong thời gian mùa vụ, gồm cả hai đầu, và không vượt ngày hiện tại theo giờ Việt Nam. Ưu tiên ngày thực tế nếu có, dùng ngày dự kiến khi chưa có ngày thực tế.
- Mùa vụ phải `IN_PROGRESS` và `CONFIRMED`. Các mùa vụ của schema hiện tại được mặc định xác nhận để tương thích chức năng đang có.
- Lưu xác nhận ngay; không có luồng nháp, duyệt, sửa hoặc xóa trong yêu cầu sáu chức năng này. Có thể có nhiều lần thu hoạch trong cùng ngày. Ghi dữ liệu và audit trong cùng transaction.
- Khi đã có thu hoạch, khóa thay đổi khu đất/cây trồng, tên, ngày và đơn vị mùa vụ để bảo vệ lịch sử. Vẫn có thể kết thúc mùa vụ.
- Khi mất kết nối lúc lưu, tải lại lịch sử để kiểm tra trước khi gửi lại. API hiện chưa có khóa chống gửi lặp.

## API

Tất cả endpoint yêu cầu Bearer token; API trả envelope chung của dự án.

| Phương thức | Đường dẫn | Chức năng |
| --- | --- | --- |
| GET | `/api/v1/harvest-farms` | Trang trại người dùng được đọc |
| GET | `/api/v1/harvest-units` | Đơn vị sản lượng |
| GET | `/api/v1/farms/{farmId}/harvest-seasons` | Mùa vụ trong trang trại |
| PUT | `/api/v1/farms/{farmId}/harvest-seasons/{id}` | Cấu hình `name`, `startDate`, `endDate`, `unitId` |
| POST | `/api/v1/farms/{farmId}/harvests` | Tạo thu hoạch, trả 201 |
| GET | `/api/v1/farms/{farmId}/harvests` | Lịch sử; `seasonId`, `fromDate`, `toDate`, `page`, `size` |
| GET | `/api/v1/farms/{farmId}/harvests/{id}` | Chi tiết thu hoạch |

Ví dụ body tạo thu hoạch:

```json
{"seasonId": 2, "harvestDate": "2026-10-10", "quantity": "120.125", "quality": "Loại A"}
```

`page` bắt đầu từ 0, `size` mặc định 20, tối đa 50. Người dùng không thuộc trang trại nhận 404 để không tiết lộ dữ liệu; thiếu quyền SYSTEM nhận 403.

## Kiểm tra

`mvnw.cmd verify` chạy kiểm thử API với store giả lập: quyền, phạm vi trang trại, ngày, lượng thập phân, chất lượng, lịch sử và khóa cấu hình. Kiểm thử PostgreSQL thực được bật qua `scripts/Test-Management.ps1 -PostgresBin 'C:\DUONG_DAN_POSTGRES\bin'`; script tạo database kiểm thử riêng, chạy migration hai lần và kiểm tra lưu thu hoạch, audit, trigger và quyền thành viên. Cần PostgreSQL cài trên máy để chạy phần này.
