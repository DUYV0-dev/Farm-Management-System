# Kiểm tra chức năng cuối — 31/10/2026

**Chưa xanh toàn bộ bộ test:** backend 148/159 đạt, 11 thất bại, 0 lỗi chạy và 0 bỏ qua. Luồng trình duyệt chuẩn và 16/16 nhóm kiểm tra bổ sung trên giao diện/API đều đạt sau khi chuẩn bị dữ liệu thử hợp lệ.

Theo yêu cầu **chỉ test**, không sửa code chức năng. Fingerprint các tệp runtime trước/sau kiểm thử khớp nhau. Kịch bản bổ sung và dữ liệu thử nằm trong `.test-data`; báo cáo nằm tại đây. Không tạo bản phát hành mới.

## Kết quả theo lớp kiểm tra

| Kiểm tra | Kết quả |
| --- | --- |
| Backend: unit, API giả lập và PostgreSQL thực | 159 bài; 148 đạt, 11 thất bại, không bỏ qua |
| Trình duyệt chuẩn | Đăng ký, trang trại, nhân viên, công việc, lịch, nhắc việc, hồ sơ, shop, đặt mua và chủ xác nhận đều đạt |
| Trình duyệt/API bổ sung | 16/16 nhóm đạt; không ghi nhận lỗi JavaScript trong lượt hợp lệ cuối |
| Khởi động source với môi trường IDE chưa cập nhật | Đạt: đọc biến Windows User, JWT, HTTPS, đúng PostgreSQL 55433; chỉ đọc health/trang chủ |
| Lưu dữ liệu sau restart | Đạt: UI tạo công việc → có trong PostgreSQL → restart backend và PostgreSQL → đăng nhập mới đọc lại được |
| Backup/restore toàn schema và dữ liệu | Đạt: khôi phục sang database mới và đối chiếu dữ liệu/sequence |
| Backup online qua script vận hành | Đạt: startup farm_app, backup/checksum, restore thực và health vẫn tốt |
| Kiểm tra cú pháp | 20 tệp JavaScript frontend và 24 script PowerShell đang dùng đều hợp lệ |

## Các chức năng đã thao tác

| Nhóm | Những thao tác được kiểm tra |
| --- | --- |
| Tài khoản | Đăng ký/đăng nhập, hồ sơ, đổi mật khẩu, mật khẩu cũ bị từ chối, đăng nhập lại |
| Trang trại, khu đất, cây trồng, mùa vụ | Tạo và đọc lại bản ghi, cấu hình ngày/đơn vị, chuyển trang trại, cách ly dữ liệu |
| Nhân sự/phân quyền | Tạo bằng hộp thoại, quyền nhân viên, quản trị tạo user/role, cấp và thu hồi quyền trên token hiện có, reset mật khẩu, khóa user |
| Công việc/lịch/thông báo | Tạo công việc bằng form và trong lịch; tháng/tuần/chi tiết; API tiến độ, version, lịch sử; nhắc việc và đánh dấu đọc |
| Vật tư/kho | Tạo nhóm/vật tư/kho; nhập bằng giao diện, đọc lại tồn; API không âm kho, chống gửi lặp và xuất đồng thời |
| Mua hàng | Tạo nhà cung cấp, tạo đơn, đặt hàng, nhận hàng và xác nhận tồn tăng đúng |
| Phân bón/thuốc | Ghi nhận sử dụng từ kho, nhật ký và tồn kho cập nhật cùng giao dịch |
| Sinh trưởng | Ghi giai đoạn, ngày quan sát, ghi chú và sản lượng dự kiến |
| Thu hoạch/truy xuất | Ghi thu hoạch qua hộp thoại, tạo lô nông sản, xem liên kết vật tư và mùa vụ |
| Chi phí | Ghi chi phí qua form và đối chiếu số tiền thập phân qua API |
| Bán trực tiếp | Tạo khách, tạo đơn CONFIRMED, giao hàng, xác nhận đã giao, tồn nông sản giảm đúng |
| Cửa hàng công khai | Đăng sản phẩm, tách sản phẩm của tôi/khám phá, giỏ, khách đặt, chủ duyệt, quyền riêng tư đường dẫn đơn, chống bán vượt tồn |
| Tổng quan/báo cáo | Biểu đồ có dữ liệu, đúng trang trại; tạo báo cáo và tải PDF/XLSX thực, kiểm tra định dạng file |
| Nhập/xuất dữ liệu | Mở UI, validate trước nhập, nhập và xuất JSON; API kiểm tra dữ liệu sai/cách ly/rollback |
| Giao diện nhỏ | Trang tài khoản và các module trên 390×844; không tràn ngang khung trang |

Không phải tất cả 159 bài đều dùng PostgreSQL thực: các lớp `*ApiTests` có phần mock Store/Service. Bộ `FarmWorkspaceIntegrationTests`, các lớp integration, các luồng browser và kiểm tra vận hành dùng PostgreSQL thật **tách biệt để thử**.

## 11 bài chưa đạt

Các bài này trước đây bị bỏ qua bởi cờ `management.test.db`. Lần này đã bật để kiểm tra và giữ nguyên kết quả thất bại. 9 bài thuộc `ManagementIntegrationTests`, 2 bài thuộc `RbacIntegrationTests`.

Phần lớn dừng tại fixture/kỳ vọng cho rằng tài khoản có quyền hệ thống được truy cập/tạo dữ liệu trang trại mà không chọn ngữ cảnh trang trại. Code hiện tại yêu cầu thành viên/ngữ cảnh trang trại READY, nên trả 403. Bài tài nguyên còn kỳ vọng HTTP 200 trên đường dẫn cũ và nhận 404. Các luồng chủ trại/nhân viên hiện hành đã được kiểm tra riêng và đạt; không sửa phân quyền để làm các bài cũ xanh.

| Bộ test | Bài thất bại |
| --- | --- |
| ManagementIntegrationTests | `everyModuleRequiresExactPermissionsAndRevocationIsImmediate` |
| ManagementIntegrationTests | `concurrentInventoryUpsertsKeepOneRow` |
| ManagementIntegrationTests | `concurrentPlotWritesCannotOverbookFarm` |
| ManagementIntegrationTests | `packagedUiAndProtectedFarmRouteAreAvailable` |
| ManagementIntegrationTests | `farmPlotCropLifecyclePersistsAndEnforcesRelationships` |
| ManagementIntegrationTests | `updatesDuplicatesAndInputValidation` |
| ManagementIntegrationTests | `dashboardAggregatesConfirmedUnitsAndExportsSameStoredSnapshot` |
| ManagementIntegrationTests | `inventoryLifecycleIsAtomicAndBlocksInvalidDeactivation` |
| ManagementIntegrationTests | `harvestPersistsAndLocksSeasonMetadata` |
| RbacIntegrationTests | `roleLifecycleUpdatesExistingTokenAndRecordsAudit` |
| RbacIntegrationTests | `deactivationAndPasswordResetPermanentlyRevokeOldSessions` |

Chi tiết mong đợi/thực tế nằm trong [backend-tests.json](backend-tests.json). Những bài lỗi ngay từ fixture chưa kiểm tra được các nhánh nghiệp vụ phía sau (ví dụ tranh chấp diện tích khu đất). Vì vậy không kết luận mọi nhánh/chức năng đã được chứng minh hoàn toàn.

## Quan sát và giới hạn

- Thao tác Đặt hàng → Nhận hàng liên tiếp trước khi UI tải lại có thể hiện cảnh báo 409: đơn đã nhận không được đổi trạng thái. Lượng tồn vẫn chỉ tăng một lần. Lượt tuần tự chờ đúng trạng thái đạt; đây là điểm UX cần xem lại nếu muốn chặn thao tác khi đang xử lý.
- Dữ liệu thử ban đầu đặt ngày kết thúc bằng ngày bắt đầu và dùng lại tên trang trại; hệ thống từ chối đúng quy tắc. Đã điều chỉnh **dữ liệu/kịch bản thử**, giữ nguyên code chức năng, rồi chạy lại các luồng hợp lệ.
- Chưa kiểm thử tải lớn, hạ tầng Internet/domain/proxy hoặc đánh giá bảo mật xâm nhập. Các bài thử thao tác đồng thời không thay thế đo tải.
- Demo/mã lịch sử không thuộc giao diện runtime đang chạy. Các bài kiểm tra cũ không được dùng làm lý do khôi phục hành vi đã bỏ.
- PostgreSQL thật 55433 được giữ chạy; dữ liệu ghi thử nằm trong các cụm `.test-data` riêng (55432 và cổng tạm). Kiểm tra source với 55433 chỉ gọi health/trang chủ/tài nguyên, không đăng nhập hoặc ghi dữ liệu nghiệp vụ.

## Bằng chứng

- [Tổng hợp máy đọc được](summary.json), [16 nhóm giao diện/API](browser-groups.json), [khởi động source](source-startup.json).
- [PDF tải từ luồng thử](sample-report.pdf), [Excel tải từ luồng thử](sample-report.xlsx) — toàn bộ là dữ liệu kiểm thử.
- Log chi tiết: `.test-data/production-4e15f498dc014096b03ecf1ab8226745/` và `.test-data/database-configuration-1c676fc257f84d2fa728d5f3d7cc9dbb/`.
