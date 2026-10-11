# Advanced Dashboard & Reports

Mở **Dashboard và báo cáo** sau khi đăng nhập. Module tổng hợp dữ liệu thật của trang trại được phép; không dùng dữ liệu hoạt động demo trong bộ nhớ.

## Phạm vi đang hoạt động

| Nội dung | Kết quả hiện tại | Nguồn |
| --- | --- | --- |
| Production dashboard | Sản lượng, số lần thu, biểu đồ theo tháng/cây, nhóm riêng từng đơn vị | HARVEST CONFIRMED |
| Crop season statistics | Đếm vụ giao kỳ, trạng thái hiện tại, lượng trong kỳ/lũy kế, tỷ lệ đạt kế hoạch | crop_season và harvest |
| Farming activity statistics | Hiện UNAVAILABLE cùng lý do thiếu nguồn | Chưa có FARMING_TASK vận hành theo trang trại |
| Inventory statistics | Tồn hiện tại từng kho/vật tư; PARTIAL | warehouse_inventory; chưa có sổ IN/OUT hoặc ngưỡng tồn thấp |
| Expense summary | UNAVAILABLE; giá trị mua và chi phí là NULL | Chưa có PURCHASE_ORDER/PURCHASE_ORDER_ITEM xác nhận |
| Production reports | PR01 chi tiết và PR02 tổng hợp; phân trang; xuất PDF và Excel | Snapshot cùng dữ liệu, lưu trong PostgreSQL |

Hai phần công việc và giá trị mua chưa có số thống kê hoạt động. Không coi UNAVAILABLE là hoàn thành nghiệp vụ nguồn. Theo tài liệu Advanced_Dashboard_Reports_v1, task này không tạo CRUD mua hàng, kế toán hoặc dữ liệu giả. Khi nhóm triển khai các nguồn còn thiếu, cần bổ sung adapter và kiểm thử công thức, thay trạng thái chưa khả dụng. Tồn hiện tại không được gắn nhãn tồn cuối kỳ lịch sử.

## Cài đặt và quyền

1. Chạy migration đến `database/10_Harvest_Management.sql`, rồi chạy `database/11_Advanced_Dashboard_Reports.sql` bằng chủ database. Migration 11 chạy lại được, thêm expected_yield và bảng snapshot. Flyway hiện không tự thực hiện các migration thủ công này.

   ```powershell
   psql -h localhost -U postgres -d TEN_DATABASE -v ON_ERROR_STOP=1 -f .\database\11_Advanced_Dashboard_Reports.sql
   ```

2. Cấp quyền qua vai trò SYSTEM trong màn hình quản lý vai trò hiện có. Không tự cấp cho tất cả người dùng hoặc SYSTEM_ADMIN:

   | Phần | Quyền SYSTEM cần có |
   | --- | --- |
   | Mở dashboard/chọn trang trại | dashboard:read |
   | Sản lượng | dashboard:read + harvests:read |
   | Mùa vụ | dashboard:read + crops:read + harvests:read |
   | Tồn kho | dashboard:read + warehouses:read |
   | Mục giá trị mua | expenses:read, cấp riêng |
   | Tạo/đọc báo cáo | reports:read + harvests:read + crops:read |
   | Xuất PDF/Excel | Các quyền đọc báo cáo và reports:export:pdf hoặc reports:export:xlsx |

3. Người dùng phải có farm_member ACTIVE và vai trò FARM OWNER hoặc MANAGER tại trang trại. Vai trò SYSTEM không thay thế tư cách thành viên trang trại. NV/K không được mở dashboard chỉ nhờ quyền đọc module nguồn.
4. Cấu hình ngày và đơn vị mùa vụ theo [hướng dẫn Thu hoạch](HARVEST_MANAGEMENT.md). Dữ liệu expected_yield hiện do nguồn quản lý mùa vụ/database cung cấp; khi chưa có kế hoạch lớn hơn 0, tỷ lệ là NULL và hiển thị lý do. Không tự lấy kế hoạch từ crop_production vì bảng này chưa liên kết season_id.
5. Dùng JDK 21, khởi động bằng cấu hình database/JWT của dự án. Sau khi cấp quyền, đăng nhập, chọn trang trại và kỳ, nhấn **Cập nhật dashboard**. Có thể lọc mã khu đất/cây/mùa vụ cho sản xuất, mã kho/vật tư cho tồn kho và giá trị mua.

## Công thức và thời gian

- Kỳ bắt buộc yyyy-MM-dd, gồm cả hai đầu, tối đa 10 năm. Thu hoạch dùng harvest_date. Không cộng kg với tấn hoặc lượng vật tư khác nhau.
- Sản lượng trong kỳ chỉ gồm CONFIRMED, mỗi harvest một dòng. Vụ sau đó bị CANCELLED vẫn giữ sản lượng xác nhận và được chú thích trong báo cáo.
- Đếm mùa vụ CONFIRMED chưa CANCELLED có khoảng ngày giao kỳ. Ngày thực tế ưu tiên hơn dự kiến. Vụ thiếu ngày được đếm riêng cho toàn trang trại; không tự đoán kỳ của vụ. Trạng thái hiển thị là hiện tại, không phải trạng thái tại toDate.
- Lũy kế là tất cả thu hoạch xác nhận đến toDate, gồm phần trước fromDate. Tỷ lệ = lũy kế / expected_yield × 100, làm tròn HALF_UP 2 chữ số. Không chặn ở 100%. Không tính kg/ha từ diện tích khu đất hiện tại.
- Nguồn hợp lệ nhưng rỗng trả bảng trống/số đếm 0. Nguồn chưa có trả UNAVAILABLE và lý do. Lỗi database trả 503; không đổi lỗi thành số 0.

## Báo cáo và xuất file

Nhấn **Tạo báo cáo** để lưu bộ lọc, dữ liệu chi tiết và tổng hợp trong cùng transaction REPEATABLE_READ. Báo cáo thuộc người tạo và một trang trại, tồn tại 24 giờ. Khi dữ liệu nguồn thay đổi, các file PDF/Excel vẫn dùng snapshot đã tạo. Mỗi lần đọc/xuất kiểm tra lại quyền hiện tại và tư cách thành viên; reportId không phải liên kết công khai.

PR01 chứa ngày, mùa vụ/trạng thái, cây, khu đất, lượng, đơn vị, chất lượng. PR02 chứa số lần thu, lượng trong kỳ, lũy kế, kế hoạch, tỷ lệ và lý do thiếu tỷ lệ. Xuất toàn bộ tập đã lọc, không chỉ trang đang xem. Tối đa 9.999 dòng chi tiết và 9.999 dòng tổng hợp; vượt giới hạn trả 413, không cắt dữ liệu. Chỉ một yêu cầu xuất tại một thời điểm trong mỗi tiến trình backend; yêu cầu đồng thời nhận 409.

Excel có ô số/ngày đúng kiểu và ghi văn bản người dùng dưới dạng text, không công thức. Do Excel chỉ bảo toàn khoảng 15 chữ số có nghĩa, giá trị thập phân lớn còn có bản nguyên vẹn dạng text trong sheet **Giá trị chính xác**. PDF lặp tiêu đề, xuống dòng trong ô, ngắt trang theo hàng và có số trang.

PDF mặc định dùng `C:/Windows/Fonts/arial.ttf`, được nhúng vào file khi xuất. Máy khác cần chỉ định `--reports.pdf.font=/duong/dan/font-tieng-viet.ttf` hoặc biến môi trường REPORTS_PDF_FONT. Font không có hoặc không chứa ký tự dữ liệu sẽ từ chối xuất rõ ràng; không bỏ ký tự âm thầm. Không đưa font hệ thống vào Git. Chủ database có thể dọn snapshot hết hạn bằng `DELETE FROM public.production_report_snapshot WHERE expires_at < CURRENT_TIMESTAMP`; ứng dụng không có quyền sửa/xóa snapshot.

## API

B = `/api/v1/farms/{farmId}`. Tất cả endpoint cần Bearer token. Các bộ lọc không được hỗ trợ hoặc lặp lại bị từ chối, không âm thầm bỏ qua.

| Method | Path | Bộ lọc/body |
| --- | --- | --- |
| GET | `/api/v1/dashboard-farms` | Không query |
| GET | B/dashboard/production | fromDate,toDate,plotId,cropId,seasonId |
| GET | B/statistics/seasons | Như sản xuất |
| GET | B/statistics/tasks | Như sản xuất; nguồn chưa khả dụng |
| GET | B/statistics/inventory | fromDate,toDate,warehouseId,materialId |
| GET | B/statistics/expenses | Như tồn kho; nguồn chưa khả dụng |
| POST | B/reports/production | JSON fromDate,toDate, tùy chọn plotId,cropId,seasonId |
| GET | B/reports/{reportId} | page từ 0, size 1–50 |
| GET | B/reports/{reportId}/export | format=pdf hoặc xlsx |

Thống kê trả generatedAt, appliedFilters, metricVersion=ADR-v1.0, timezone=Asia/Ho_Chi_Minh và sourceStatus. Lỗi dùng envelope chung có requestId: 400 bộ lọc sai, 401 chưa đăng nhập, 403 thiếu quyền, 404 ngoài trang trại/khác người tạo, 410 hết hạn, 413 quá lớn, 503 nguồn/xuất chưa sẵn sàng. Các lỗi xác thực body khác dùng 422 theo quy ước validation hiện có của dự án.

## Bằng chứng kiểm tra ngày 11/10/2026

- JDK 21: Maven verify thành công với PostgreSQL 17.4 chạy độc lập, database kiểm thử riêng localhost:55432. 106 kiểm thử: 105 đạt, 1 bỏ qua (context kiểm thử database cấu hình ngoài, độc lập với bộ integration này).
- Chạy migration 07–11 hai lần thành công. Kiểm thử SQL thật: 120+80 xác nhận = 200, nháp/hủy không tính; 100 trước kỳ làm lũy kế 300, kế hoạch 250 cho tỷ lệ 120%; kg/tấn nhóm riêng; thay nguồn sau snapshot không đổi file; hủy vụ không mất sản lượng; thu hồi thành viên chặn đọc báo cáo.
- Kiểm thử API kiểm tra quyền nguồn/quyền tiền, token thu hồi, Farm khác, ID lọc khác Farm, kỳ sai, bộ lọc không hỗ trợ, nguồn lỗi, phân trang, hết hạn và quá giới hạn.
- Kiểm thử file xác nhận tiếng Việt, nhiều trang, số/ngày Excel, text bắt đầu bằng dấu =, lượng lớn và toàn bộ dòng xuất.
- Kiểm thử Edge headless qua API thật: đăng nhập, bộ lọc, KPI, tồn kho, trạng thái nguồn thiếu, tạo báo cáo, tải PDF/Excel, màn hình 390px, đăng xuất; không có lỗi JavaScript chưa xử lý.

Log và mẫu hiện có trong target: dashboard-postgres-tests.log, dashboard-browser.log, dashboard-evidence (PDF/Excel, ảnh desktop/mobile). Đây là dữ liệu kiểm thử, không phải dữ liệu trang trại thật. Chưa đo SLA 5 giây trên dữ liệu đại diện 9.999 dòng hoặc xác nhận môi trường production.

Chạy lại bộ integration với một PostgreSQL cài đầy đủ:

```powershell
$env:JAVA_HOME='C:\DUONG_DAN_JDK_21'
$env:Path=$env:JAVA_HOME+'\bin;'+$env:Path
.\scripts\Test-Management.ps1 -PostgresBin 'C:\DUONG_DAN_POSTGRES\bin'
```

Thêm `-Browser -Chrome 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe' -Node 'C:\DUONG_DAN_NODE\node.exe'` để chạy cả kiểm thử giao diện. Script chỉ tạo database riêng, dừng các tiến trình kiểm thử và giữ log trong target. Fixture dashboard-browser-fixture.sql chỉ dùng cho database kiểm thử sau integration, không chạy trên database người dùng.

Thư viện xuất file: [Apache PDFBox](https://pdfbox.apache.org/3.0/dependencies.html), [Apache POI](https://poi.apache.org/). PostgreSQL độc lập cho kiểm thử lấy từ [Zonky Embedded PostgreSQL](https://github.com/zonkyio/embedded-postgres), không thêm vào dependency runtime hoặc cài máy.
