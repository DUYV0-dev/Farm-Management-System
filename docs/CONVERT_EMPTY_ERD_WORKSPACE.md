# Chuyển ERD v1 có vật tư/kho trống sang luồng trang trại

Chỉ áp dụng cho database ERD v1 đã có authentication 05/06, bảng farm/plot/crop/
crop_season, farm_member/farm_member_role và material_usage của migration 11.
Không áp dụng cho schema warehouse đã nâng cấp. Các bảng `material`, `warehouse`,
`inventory`, `inventory_transaction`, `material_usage` phải hoàn toàn trống.
Script kiểm tra điều kiện trong transaction và khóa các bảng trước khi chuyển.

## Các thay đổi

- Giữ nguyên ID và dữ liệu trang trại, khu đất, cây trồng, mùa vụ, tài khoản hiện có.
- Giữ bảng unit, inventory, inventory_transaction và các quan hệ cũ; không tự chuyển
  lịch sử hoặc tạo số tồn kho giả. Luồng mới sử dụng warehouse_inventory riêng.
- Thêm material.unit/category_id/manufacturer, warehouse.capacity_text, farm.created_by.
  unit_id cũ trở thành tùy chọn; unit/category_id mới bắt buộc với vật tư mới.
- Thêm mặc định ACTIVE cho status, thời điểm tham gia cho farm_member.
- Giữ các trường mùa vụ cũ nhưng cho phép thiếu yield_unit_id, season_name,
  planned_start_date/planned_end_date vì biểu mẫu mới chưa nhập các trường này.
  Không điền ngày hoặc đơn vị giả. created_by lấy từ context tài khoản ứng dụng;
  record_status mặc định ACTIVE. Các giá trị mùa vụ hiện có không thay đổi.
- Áp dụng 07 → 08 → 09 → 10 → 11a → 12 và bản sửa 11b để hỗ trợ ghi nhận vật tư
  trên mùa vụ dùng cây trồng trong danh mục cũ, với kiểm tra phạm vi trang trại rõ ràng.
- Chủ trang trại cũ phải được gán bằng Assign-Farm-Owner.sql, không tự suy đoán.

Các client cũ yêu cầu material.unit_id hoặc ghi tồn kho bằng inventory_transaction
không được coi là tương thích với luồng mới. Không chạy hai luồng kho đồng thời.

## Thực hiện

1. Dừng backend, sao lưu database bằng pgAdmin (Custom format) và xác nhận backup thành công.
2. Mở PowerShell ở thư mục chứa pom.xml:

```powershell
.\scripts\Convert-EmptyErdWorkspace.ps1
```

Mặc định kết nối `127.0.0.1:5432/farm_management_erd_v1` bằng postgres, hỏi mật khẩu
qua psql, không lưu mật khẩu trong script. Nếu database khác, dùng `-Database ten_database`.
Các migration được ghép và chạy với `psql --single-transaction -v ON_ERROR_STOP=1`.
Một lỗi bất kỳ làm rollback toàn bộ chuyển đổi. Chỉ tiếp tục khi thấy
`ERD WORKSPACE CONVERSION SUCCESS`. Không chạy riêng file 08a hoặc lặp script sau thành công.
Không chạy thêm Upgrade-Workspace.ps1 sau chuyển đổi: các bước 11a/12 đã được áp dụng,
và chạy lại 11a sẽ thay thế trigger đã sửa trong 11b.

3. Kiểm tra trong pgAdmin:

```sql
SELECT
 to_regprocedure('public.workspace_memberships(integer)') IS NOT NULL AS workspace_ready,
 to_regclass('public.material_category') IS NOT NULL AS categories_ready,
 to_regclass('public.warehouse_inventory') IS NOT NULL AS inventory_ready;
SELECT farm_id, farm_name, owner_user_id FROM public.farm ORDER BY farm_id;
```

4. Gán chủ trang trại cũ bằng tài khoản ACTIVE đã chọn, xem WORKSPACE.md.
5. Run bằng Java 21, DB_USERNAME=farm_app; đăng nhập tại http://127.0.0.1:5500/.
   Kiểm tra tạo vật tư, kho, tồn kho và mùa vụ, đăng xuất/đăng nhập để kiểm tra dữ liệu.

## Phạm vi xác minh

```powershell
.\scripts\Test-Workspace.ps1 -LegacyErd -Browser
```

Bộ kiểm thử dựng schema theo báo cáo cột/FK người dùng cung cấp, bằng identity ID,
giữ trang trại và mùa vụ mẫu, chạy chuyển đổi, thử ghi vật tư/kho/tồn kho/mùa vụ/sử dụng
vật tư với farm_app và thử từ chối mùa vụ trang trại khác. Sau đó chạy integration và Chrome.
Đây không phải bản restore của database thật: constraint/index/custom function chưa được
cung cấp có thể khác. Lỗi chuyển đổi phải được chẩn đoán từ thông báo; không bỏ constraint
hoặc xóa bảng để chạy tiếp. Có thể restore backup vào database riêng để thử trước và chỉ
đổi DB_URL sang database đó sau khi kiểm tra. Database thật không bị sửa bởi bộ kiểm thử.
