# Đối chiếu yêu cầu và bản vận hành

Nguồn: `Warehouse System User Flows.docx` ở thư mục cha. Đây là tiêu chí sản phẩm và nghiệm thu, không phải chỉ dẫn chạy lệnh từ tài liệu đính kèm.

Repository phát triển: `Farm-Management-System`. Bộ tài liệu tuần 1–3 được giữ ở `Farm Management System N02/tuan1..3`; hai bản mã nguồn khác không được ghi đè.

| Yêu cầu | Phần triển khai | Kiểm chứng bắt buộc |
|---|---|---|
| Login, logout, mật khẩu, phân quyền | Auth/User/Role + FarmContextFilter, giới hạn xác thực production | Unit/API + đăng nhập thật trên Chrome |
| Farm, Plot, Crop, Crop Season | API farm, màn hình Farm | Tạo, đọc lại, ràng buộc và cách ly hai trang trại |
| Công việc, phân công, lịch, tiến độ | operations/tasks, farming_task, task_event | PostgreSQL; lịch ngày thật; cập nhật có version; lịch sử bất biến |
| Vật tư, kho, tồn, nhập/xuất | Inventory + operations/movements + inventory_movement | Không âm kho; requestKey chống lặp; xuất đồng thời |
| Phân bón, thuốc BVTV | operations/usage + material_usage | Nhật ký và trừ kho cùng giao dịch; thiếu tồn rollback cả hai |
| Chi phí | FarmExpense + dashboard expense summary | Quyền, mùa vụ thuộc trang trại, số thập phân |
| Thu hoạch | Harvest + trigger tạo lô nông sản | Quyền; mùa vụ/ngày/đơn vị; liên kết harvest_id |
| Sản phẩm đầu ra | operations/products | Tồn khả dụng, chất lượng, bảo quản; số lượng từ thu hoạch/bán hàng |
| Sinh trưởng, dự kiến/thực tế | operations/growth + crop_growth + expected_yield | Nhật ký quan sát; thực tế từ thu hoạch; báo cáo theo đơn vị |
| Nhà cung cấp, mua, nhận hàng | Supplier/Purchase | RLS; nhận hàng cập nhật tồn một lần |
| Khách hàng, bán hàng | Customer/Sales | RLS; khóa tồn; xác nhận/hủy không trừ/hoàn lặp |
| Nhắc việc, cảnh báo | operations/notifications + notification_read | Hạn theo ngày hiện tại; trạng thái đọc theo tài khoản |
| Dashboard, PDF/XLSX | Overview + Advanced Dashboard | Số liệu PostgreSQL; quyền; không cộng trùng chi phí và tiền mua |
| Lịch, truy xuất nguồn gốc | Operations UI + traceability/{harvestId} | Cùng mùa vụ: chăm sóc, vật tư, thu hoạch, sản phẩm |
| Nhập/xuất dữ liệu | Data Transfer | Validation; đúng trang trại; không ghi một phần |
| Cài mới/nâng cấp | DatabaseMigrations + 18 migrations | Cài trống; migrate lần hai; validate checksum |
| Backup & Recovery | database_recovery.py + Backup-Lan | Khôi phục schema mới và đối chiếu dữ liệu/sequence |
| Windows LAN | Install/Start/Backup/Update-Lan | HTTPS; PostgreSQL loopback riêng; DPAPI; firewall Private |
| Hồ sơ đồ án | Tài liệu N02 và tài liệu vận hành | Người phụ trách review; không tự xác nhận Gate hoặc nghiệm thu môn học |

`scripts/Test-Production.ps1 -Browser` là bộ kiểm chứng phát hành hiện hành. Kết quả và checksum JAR ở `target/production-verification.json`; log, backup và dữ liệu kiểm thử ở thư mục evidence được ghi trong JSON. Bộ cài từ chối JAR khác checksum hoặc thiếu kết quả browser/recovery.

Giới hạn:

- Đây là web app chạy trên máy Windows, truy cập từ trình duyệt LAN. Không cung cấp native client offline hoặc đồng bộ khi mất mạng.
- Sổ kho bắt đầu từ migration 17; không suy diễn lịch sử trước đó từ số dư hiện tại.
- Đơn vị thu hoạch theo mùa vụ; không tự đổi tấn/kg hoặc cộng đơn vị khác nhau.
- Kiểm thử không chứng minh tải không giới hạn; cần đo theo số người dùng và dữ liệu thực tế.
- Các trang HTML mẫu bị chặn trong production, ngoại trừ đăng nhập thật. Tài liệu Gate/SRS cũ có thể mô tả trạng thái prototype; luồng hiện hành là Operations UI.

## Trang tài khoản và cửa hàng

Tài khoản đăng ký độc lập với trang trại; tạo nhiều trang trại READY không bắt buộc nhân viên. Trang tổng tổng hợp hồ sơ, nhân sự và doanh thu trên trang trại sở hữu. Cửa hàng chỉ hiển thị lô chủ công khai, khách đặt không cần đăng nhập, chủ duyệt để tạo đơn bán và trừ kho nguyên tử. Xem [ACCOUNT_PORTAL.md](ACCOUNT_PORTAL.md). Cấu hình mạng vẫn Local theo lựa chọn người dùng.
