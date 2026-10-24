# Farm Expense Management

Module ghi nhận chi phí sản xuất nông nghiệp, gồm giao diện và backend. Sử dụng cùng PostgreSQL,
JWT, định dạng phản hồi `data`/`requestId` và audit log của hệ thống hiện có.

## Cài đặt

Chạy `database/10_Farm_Expense_Management.sql` bằng tài khoản chủ database sau
`09_Management_Integrity.sql` (cùng các script prerequisite của nó). Dự án tắt Flyway nên
script này cần được áp dụng thủ công trước khi gọi API. Script tạo bảng,
chỉ mục, trigger, quyền database và cấp hai quyền mới cho `SYSTEM_ADMIN`.
Vai trò khác được cấp quyền qua module quản lý vai trò hiện có.

## API

Mọi endpoint yêu cầu `Authorization: Bearer <token>`.

| Method | Endpoint | Quyền | Chức năng |
| --- | --- | --- | --- |
| GET | `/api/v1/expenses/categories` | `expenses:read` | Danh mục loại chi phí |
| GET | `/api/v1/expenses/options` | `expenses:read` hoặc `expenses:create` | Danh sách trang trại, mùa vụ và loại chi phí dùng trong giao diện |
| POST | `/api/v1/expenses` | `expenses:create` | Ghi nhận chi phí; trả về HTTP 201 |
| GET | `/api/v1/expenses/{id}` | `expenses:read` | Chi tiết khoản chi |
| GET | `/api/v1/expenses` | `expenses:read` | Lịch sử có phân trang và tổng tiền |
| GET | `/api/v1/expenses/summary` | `expenses:read` | Tổng tiền, số khoản chi và tổng theo loại |

Ví dụ body ghi nhận chi phí:

```json
{
  "farmId": 1,
  "seasonId": 2,
  "category": "FERTILIZER",
  "amount": "1500000.00",
  "expenseDate": "2026-10-11",
  "description": "Mua phân bón cho mùa vụ"
}
```

`farmId`, `category`, `amount`, `expenseDate` bắt buộc. `seasonId` và
`description` có thể bỏ qua hoặc để `null`. Mùa vụ phải thuộc trang trại đã chọn;
chi phí chung của trang trại dùng `seasonId: null`. Cho phép ghi nhận chi phí
cho mùa vụ đã kết thúc để bổ sung hóa đơn phát sinh muộn.

Loại chi phí: `SEEDS`, `FERTILIZER`, `PESTICIDES`, `LABOR`, `EQUIPMENT`,
`IRRIGATION`, `TRANSPORT`, `OTHER`.

Số tiền dùng VND, lớn hơn 0, tối đa `9999999999999999.99`, có tối đa hai chữ số
thập phân. Chấp nhận JSON number hoặc chuỗi thập phân; nên gửi chuỗi để giữ
độ chính xác ở client. Lưu `NUMERIC(18,2)` và tính bằng `BigDecimal`/SQL `SUM`.
Ngày có định dạng `yyyy-MM-dd`; mô tả tối đa 1000 ký tự. Dữ liệu sai trả 422,
không tìm thấy trang trại/mùa vụ/chi phí trả 404, mùa vụ sai trang trại trả 409.

Lịch sử và summary nhận bộ lọc tùy chọn `farmId`, `seasonId`, `category`,
`fromDate`, `toDate`. Khoảng ngày bao gồm hai đầu, lọc theo ngày phát sinh.
Lịch sử nhận thêm `page` (bắt đầu từ 0, mặc định 0) và `size` (1–100, mặc định 20).

Ví dụ: `/api/v1/expenses?farmId=1&seasonId=2&page=0&size=20`
và `/api/v1/expenses/summary?farmId=1&fromDate=2026-10-01&toDate=2026-10-31`.

Lịch sử trả `items`, `page`, `size`, `total`, `totalAmount`, `currency`.
Sắp xếp ngày phát sinh mới nhất trước, sau đó ID giảm dần. `totalAmount`
tính trên toàn bộ kết quả đã lọc, không chỉ trang hiện tại. Summary trả `count`,
`totalAmount`, `currency`, `byCategory`; không có khoản chi thì tổng bằng 0.
Các truy vấn trong mỗi phản hồi dùng cùng snapshot để tổng và danh sách nhất quán.

Khoản chi được giữ để xem lịch sử; module không cung cấp sửa/xóa. Ghi nhận khoản
chi và audit log thực hiện trong cùng transaction, lưu người tạo và thời điểm tạo.
Các trường `amount` và `totalAmount` trong phản hồi trả dưới dạng chuỗi thập phân
để giao diện giữ chính xác số tiền lớn và phần thập phân.

## Giao diện

Mở **Chi phí nông nghiệp** trên Dashboard hoặc `/frontend/expenses.html`.
Trang dùng cùng phong cách giao diện với module Phân bón & Thuốc BVTV.

- Quyền `expenses:create`: mở form ghi nhận khoản chi, chọn trang trại, mùa vụ
  (hoặc chi phí chung), loại chi phí, số tiền VND, ngày phát sinh và mô tả.
- Quyền `expenses:read`: xem lịch sử phân trang, chi tiết khoản chi, tổng tiền
  trên toàn bộ kết quả đã lọc và tổng theo từng loại chi phí.
- Lọc theo trang trại, mùa vụ, loại chi phí và khoảng ngày. Danh sách mùa vụ
  thay đổi theo trang trại được chọn.
- API options sử dụng quyền của module chi phí; không yêu cầu thêm
  `farms:read` hoặc `crops:read` để chọn trang trại/mùa vụ.
- Số tiền nhập không có dấu phân cách hàng nghìn, dùng dấu chấm cho phần thập phân.
  Giao diện gửi số tiền dạng chuỗi và hiển thị theo định dạng Việt Nam.

Migration 10 cần được áp dụng và quyền cần được cấp trước khi sử dụng.

## Kiểm tra

- `./mvnw.cmd -o -Dtest=FarmExpenseUiApiTests test`: kiểm tra quyền options,
  phân quyền lịch sử, bộ lọc và độ chính xác số tiền trong phản hồi.
- `node frontend/tests/expenses-browser.cjs`: chạy Chrome headless với API
  fixture riêng; kiểm tra form, lịch sử, bộ lọc, phân trang, quyền, lỗi API,
  bố cục mobile và phiên đăng nhập Dashboard. Không truy cập database thật.
  Ảnh kiểm tra được lưu trong `target/expense-browser/`.
