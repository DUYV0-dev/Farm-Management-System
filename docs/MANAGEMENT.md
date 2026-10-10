# Trang trại, khu đất, cây trồng, vật tư và kho

Ứng dụng chính chạy tại `http://127.0.0.1:8080/`. Đăng nhập bằng tài khoản đã được cấp quyền, chọn **Trang trại & Cây trồng** hoặc **Vật tư & Kho**. Hai màn hình dùng API thật, lưu PostgreSQL và dùng chung phiên đăng nhập trong bộ nhớ. Không mở các file HTML bằng `file://` hoặc chạy chúng bằng HTTP server riêng.

## Phạm vi hoàn thành

| Đối tượng | Thao tác |
|---|---|
| Trang trại, khu đất, danh mục cây trồng | Tạo, xem danh sách/chi tiết, tìm kiếm, phân trang, sửa, kích hoạt/ngừng hoạt động |
| Mùa vụ | Tạo với khu đất/cây trồng, xem, cập nhật trạng thái |
| Nhóm vật tư, vật tư, kho | Tạo, xem danh sách/chi tiết, tìm kiếm/lọc/phân trang, sửa, kích hoạt/ngừng hoạt động |
| Tồn kho | Xem theo kho/vật tư, lọc còn hàng/hết hàng, đặt số tồn thực tế |

Ngừng hoạt động là cách thực hiện yêu cầu “delete **or** deactivate”: giữ nguyên ID, quan hệ và lịch sử; không cung cấp xóa vật lý. Số tồn mới **thay thế** số cũ, không cộng thêm. Phiếu nhập/xuất, giữ chỗ vật tư và sổ giao dịch không nằm trong phạm vi này. Audit ghi người thao tác, loại bản ghi, thao tác, thời gian và request ID.

## Cài vào database ứng dụng

Yêu cầu Java 21, PostgreSQL 14+ và schema nền tài khoản/role/audit của dự án. Nếu schema nền chưa có, cần dùng ERD gốc của nhóm; fixture `src/test/resources/management-bootstrap.sql` chỉ dùng cho kiểm thử cô lập, không phải schema triển khai.

Sau các migration 05 và 06 đã có, chạy bằng database owner, theo thứ tự:

1. `database/07_Farm_Plot_Crop.sql`
2. `database/08_Material_Warehouse.sql`
3. `database/09_Management_Integrity.sql`

Ví dụ cho database hiện hành (psql hỏi mật khẩu, không ghi mật khẩu vào lệnh):

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -h 127.0.0.1 -U postgres -d farm_management_erd_v1 -W -v ON_ERROR_STOP=1 -f database/07_Farm_Plot_Crop.sql -f database/08_Material_Warehouse.sql -f database/09_Management_Integrity.sql
.\mvnw.cmd -o package
.\scripts\Start-Local.ps1
```

Các migration 07–09 có transaction và chạy lại được. Migration dừng nếu phát hiện schema nháp cũ `crop.plot_id`, thay vì tự chuyển hoặc làm mất dữ liệu. Mô hình hiện hành là danh mục `crop` độc lập; quan hệ khu đất–cây trồng nằm trong `crop_season`. Trường tùy chọn cho ERD hiện hữu được bổ sung ở 09. Nếu database đã có các ràng buộc nghiệp vụ khác trong ERD ngoài repository, cần đối chiếu chúng trước khi triển khai.

Flyway và Hibernate tự thay đổi schema vẫn tắt. Build JAR **không tự chạy migration trên database hiện tại**. `SYSTEM_ADMIN` được cấp quyền module khi chạy migration; người dùng thường cần được gán role có quyền tương ứng từ trang Vai trò và quyền.

## Ràng buộc dữ liệu

- Tên bắt buộc, giới hạn độ dài; tên trang trại, cây trồng, nhóm và vật tư không trùng khi bỏ khoảng trắng đầu/cuối và không phân biệt hoa thường. Tên khu đất/kho không trùng trong cùng trang trại.
- Diện tích > 0, tối đa 9.999.999 ha, tối đa 2 chữ số thập phân. Tổng diện tích các khu đất, kể cả khu đất đã ngừng, không vượt diện tích trang trại. Không được giảm diện tích trang trại xuống dưới tổng này.
- Chỉ tạo/kích hoạt khu đất, kho trong trang trại đang hoạt động; chỉ tạo/kích hoạt vật tư thuộc nhóm đang hoạt động.
- Mùa vụ mới cần trang trại, khu đất, cây trồng đang hoạt động. Không ngừng khu đất hoặc cây trồng khi còn mùa vụ `PLANNED`/`IN_PROGRESS`. Trạng thái kết thúc: `COMPLETED` hoặc `CANCELLED`.
- Muốn ngừng trang trại, phải ngừng các khu đất và kho trước. Muốn ngừng nhóm vật tư, phải ngừng các vật tư trước.
- Không ngừng kho/vật tư khi còn số tồn > 0; không đổi đơn vị của vật tư đang có tồn kho.
- Số tồn từ 0 đến 999.999.999,999, tối đa 3 chữ số thập phân. Một dòng duy nhất cho mỗi cặp kho–vật tư. Chỉ cập nhật khi kho, trang trại, vật tư và nhóm đều đang hoạt động.
- Khi sửa: giữ cố định trang trại của khu đất/kho, nhóm của vật tư, khu đất/cây trồng của mùa vụ và cặp kho–vật tư của tồn kho.

Các điều kiện quan hệ được bảo vệ bằng trigger PostgreSQL. Khóa transaction chung cho các thao tác ghi module giúp tránh vượt diện tích hoặc thay đổi trạng thái cha/con đồng thời. Đây là lựa chọn ưu tiên tính đúng; các thao tác ghi được tuần tự hóa trong phạm vi module. Upsert tồn kho là một câu lệnh nguyên tử; yêu cầu đặt tồn đến sau có hiệu lực sau cùng.

## API và quyền

Prefix `/api/v1`; cần Bearer token. Mọi quyền được kiểm tra lại ở backend; việc ẩn nút trên giao diện không thay thế kiểm tra quyền.

| Resource | Quyền |
|---|---|
| `farms` | `farms:read/create/update/status` |
| `plots` | `plots:read/create/update/status` |
| `crops`, `seasons` | `crops:read/create/update/status` (mùa vụ dùng update cho trạng thái) |
| `material-categories`, `materials` | `materials:read/create/update/status` |
| `warehouses` | `warehouses:read/create/update/status` |
| `inventory` | đọc: `warehouses:read`; đặt tồn: `inventory:manage` |

Các danh mục: `GET /resource`, `GET /resource/{id}`, `POST /resource`, `PUT /resource/{id}`, `PUT /resource/{id}/status`. Mùa vụ: GET danh sách/chi tiết, POST, PUT trạng thái. Tồn kho: GET danh sách/chi tiết, POST đặt tồn. Danh sách nhận `page` từ 0, `size` 1–100, `search`; bộ lọc tương ứng là `farmId`, `plotId`, `categoryId`, `warehouseId`.

Theo hợp đồng API hiện hành, `areaHa`, `growthDays`, `quantity` truyền dạng chuỗi số; ID dạng số nguyên. Các trường tùy chọn trong body vẫn cần có key, giá trị có thể là `null`. Giao diện gửi đúng định dạng này. Người vận hành cần thêm quyền đọc các danh mục liên quan để chọn chúng khi tạo mới; ví dụ quản lý kho cần `farms:read`, quản lý tồn kho cần `materials:read` và `warehouses:read`.

Token không lưu localStorage/cookie; tải lại toàn bộ trang yêu cầu đăng nhập lại. Nút tải lại dữ liệu trong module vẫn giữ phiên. Dữ liệu demo cũ trong localStorage không được tự nhập vào database.

## Kiểm thử tái lập

```powershell
# Unit/security tests; không cần database
.\mvnw.cmd -o test

# PostgreSQL riêng, kiểm tra migration, API, ràng buộc, concurrency và đóng gói JAR
.\scripts\Test-Management.ps1

# Thêm kiểm thử Chrome: đăng nhập, tạo dữ liệu, sửa tồn kho, đọc lại và đăng xuất
.\scripts\Test-Management.ps1 -Browser
```

Script yêu cầu các cổng 55432 (PostgreSQL), 18080 (app kiểm thử), 9223 (Chrome khi bật Browser) chưa được dùng. Script tạo cluster riêng trong `target/management-check-*`, chỉ nghe loopback; không dùng database ứng dụng ở 5432. Tiến trình được dừng ở cuối; database và log kiểm thử được giữ lại. Truyền `-PostgresBin` hoặc `-Chrome` nếu đường dẫn cài khác. Nếu Maven chưa có dependency trong cache, bỏ `-o` ở lệnh Maven để tải lần đầu.

Kết quả trong `target/surefire-reports`; ảnh trình duyệt ở `target/management-desktop.png` và `target/management-mobile.png`. Test context cũ dùng `RUN_DB_CONTEXT_TEST` là tùy chọn và mặc định bỏ qua. Các test localStorage cũ chỉ kiểm tra demo, không dùng làm bằng chứng cho bản kết nối API này.
