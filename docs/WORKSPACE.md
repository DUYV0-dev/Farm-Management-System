# Luồng thực tế và phân quyền trang trại

Nút Run của `FarmManagementSystemN02Application` mở `http://127.0.0.1:5500/`.
Đăng ký tạo tài khoản SYSTEM USER và trang trại có chủ OWNER trong cùng một transaction.
Đăng ký thành công yêu cầu đăng nhập; không tự coi người đăng ký là quản trị hệ thống.
Chủ thêm tài khoản nhân viên, chọn vị trí rồi bấm bắt đầu thiết lập. Trước bước này API
nghiệp vụ cũng bị khóa. Sau đó thiết lập khu đất, cây trồng, mùa vụ, vật tư, kho và chi phí.
Dữ liệu tài khoản, nhân sự và nghiệp vụ lưu trong PostgreSQL, không lưu trong localStorage.
JWT ở bộ nhớ trình duyệt; tải lại trang cần đăng nhập lại nhưng dữ liệu vẫn giữ nguyên.

## Hai phạm vi quản lý

SYSTEM_ADMIN quản lý tài khoản và vai trò hệ thống tại trang chủ sau đăng nhập.
Chủ trang trại quản lý nhân sự và dữ liệu trong trang trại có OWNER. Chủ không có API
quản lý người dùng toàn hệ thống. Nhân viên dùng tài khoản chủ tạo; không tự chọn quyền
khi đăng ký công khai. Quyền hệ thống và quyền công việc được lưu riêng.

| Vị trí | Quyền nghiệp vụ |
| --- | --- |
| OWNER | Quản lý nhân sự, thông tin và nghiệp vụ trang trại |
| MANAGER | Quản lý nghiệp vụ; không quản lý tài khoản hệ thống hay phân quyền nhân sự |
| AGRONOMIST | Khu đất, cây trồng, mùa vụ, ghi nhận phân bón và thuốc |
| WAREHOUSE | Vật tư, nhóm vật tư, kho và tồn kho |
| ACCOUNTANT | Xem và ghi nhận chi phí |
| WORKER | Xem khu đất, cây trồng, mùa vụ và lịch sử sử dụng vật tư |

Mỗi request đọc lại tư cách thành viên và quyền công việc. Đổi vị trí hoặc ngừng thành viên
có hiệu lực với token đang đăng nhập. `X-Farm-Id` chọn trang trại; server kiểm tra membership.
PostgreSQL row security chặn đọc/ghi chéo trang trại, kể cả gửi ID khác vào URL hoặc body.
Thiết lập context dùng `set_config(..., true)` trong transaction, không rò sang connection tiếp theo.
Ứng dụng từ chối chạy bằng tài khoản PostgreSQL superuser, bypassrls hoặc chủ bảng nghiệp vụ.

## Nâng cấp database hiện có

Không xóa hoặc tự gán chủ cho dữ liệu cũ. Cần tài khoản quản trị PostgreSQL để nâng cấp schema.
Nếu đã có schema warehouse của **05 → 06 → 07_Farm_Plot_Crop → 08 → 09 → 10**, chạy:

```powershell
.\scripts\Upgrade-Workspace.ps1
```

Script hỏi mật khẩu an toàn rồi chạy **11a → 12**. Script kiểm tra schema trước khi ghi.
Migration 12 có thể chạy lại. `11a` dùng danh mục vật tư warehouse với `material.category_id`
và `material.unit`; migration `11` trước đây dùng ERD v1 với `material.unit_id` và bảng `unit`.
Không chạy cả hai như hai bước độc lập trên một schema chưa được kiểm tra.
Nếu database hiện chỉ có ERD v1, script dừng trước khi thay đổi: cần khảo sát và chuyển danh mục
vật tư sang schema warehouse trước. Không có mật khẩu quản trị thì chưa thể xác minh hoặc
nâng cấp database thật.

Với ERD v1 đã có migration 11 nhưng các bảng vật tư/kho/tồn kho/lịch sử sử dụng đều trống,
có đường chuyển đổi riêng giữ dữ liệu trang trại: [CONVERT_EMPTY_ERD_WORKSPACE.md](CONVERT_EMPTY_ERD_WORKSPACE.md).
Đường này chạy toàn bộ migration tương thích trong một transaction; không chạy Upgrade-Workspace.ps1
thêm sau khi chuyển đổi vì nó sẽ thay thế bản sửa trigger 11b.

Trang trại mới có chủ được gán tự động. Với trang trại cũ, chủ database gán rõ tài khoản:

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -h 127.0.0.1 -U postgres -d farm_management_erd_v1 -W -v farm_id=123 -v owner_username=ten.chu -f database/Assign-Farm-Owner.sql
```

Danh mục cũ chưa có `tenant_farm_id` được đọc chung và không cho tài khoản trang trại sửa.
Danh mục mới thuộc trang trại đang chọn. Dữ liệu trang trại cũ không được đoán quyền sở hữu.
Tạo SYSTEM_ADMIN bằng tài khoản riêng theo `database/Bootstrap-Admin.sql`; không cấp
SYSTEM_ADMIN cho chủ trang trại chỉ để mở chức năng canh tác.

Sau nâng cấp, Run ứng dụng với `DB_USERNAME=farm_app`, mật khẩu role qua `DB_PASSWORD`
hoặc hộp nhập mật khẩu khi Run. Cấu hình hiện dùng PostgreSQL trên cổng 5432.

## Thư mục

```text
src/main/java/com/farmmanagement/backend/
  auth/          đăng nhập, mật khẩu và phiên
  tenancy/       đăng ký, chủ trang trại, nhân sự và phạm vi dữ liệu
  management/    quản trị người dùng và vai trò hệ thống
  farm/          trang trại, khu đất, cây trồng và mùa vụ
  inventory/     vật tư, kho và tồn kho
  expense/       chi phí
  usage/         phân bón và thuốc
  production/    sản xuất
  product/       sản phẩm thu hoạch
frontend/
  features/farm/           giao diện và script trang trại
  features/inventory/      giao diện, script và CSS vật tư/kho
  features/expenses/       giao diện, script và CSS chi phí
  features/material-usage/ giao diện, script và CSS phân bón/thuốc
  shared/                 CSS chung và API client
  prototypes/             bản demo cũ, không đóng gói phục vụ người dùng
  tests/                  kiểm thử trình duyệt
src/main/resources/static/
  index.html, app.js             đăng nhập và điều hướng chính
  style.css                     CSS tương thích, nhập thiết kế chung
  features/admin/               giao diện quản trị hệ thống
  features/workspace/           giao diện nhân sự trang trại
database/                       migration và gán chủ dữ liệu cũ
scripts/                        chạy, nâng cấp và kiểm thử
docs/                           hướng dẫn
```

URL dashboard/login cũ chuyển về luồng thật ở `/`. Các bản prototype báo cáo, lịch,
hoạt động và thông báo không nằm trong luồng thật; các chức năng này chưa được chuyển
toàn bộ sang PostgreSQL. Không hiển thị số liệu demo như dữ liệu vận hành.

Thiết kế chung nằm trong `frontend/shared/design.css` và `shell.css`; các trang nghiệp vụ
dùng thêm `style.css`, `records.css`, `forms.css` và `icons.css`. Danh sách file demo ở
[DEMO_FILES.md](DEMO_FILES.md), hướng dẫn cài mới/production ở [DEPLOYMENT.md](DEPLOYMENT.md).

## Kiểm thử

```powershell
.\scripts\Test-Workspace.ps1 -Browser
```

Script tạo PostgreSQL độc lập ở cổng 55432, chạy migration và bộ kiểm thử, đóng PostgreSQL
sau khi xong; dữ liệu/log giữ trong `.test-data/`. Database thật trên cổng 5432 không bị sửa.
Các bộ integration cũ bật bằng `management.test.db` kiểm tra mô hình quyền hệ thống cũ;
luồng mới dùng `workspace.test.db` và `FarmWorkspaceIntegrationTests`.

Đã xác minh 66 kiểm thử thành công (13 kiểm thử thuộc cấu hình cũ không bật), build JAR,
và kiểm thử Chrome: đăng ký thật, từ chối mật khẩu sai, thêm nhân viên, mở thiết lập,
tạo khu đất lưu PostgreSQL, mở vật tư/chi phí/phân bón và kiểm tra giao diện nhân viên.
