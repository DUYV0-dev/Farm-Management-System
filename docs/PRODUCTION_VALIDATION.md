# Kết quả kiểm chứng và bàn giao — 24/10/2026

Mã nguồn chính: `Farm-Management-System`. Không sửa hai bản mã nguồn còn lại hoặc database phát triển cũ.

- Maven/package: thành công. 144 ca kiểm thử được phát hiện, **129 đạt**, không failure/error; 15 ca thuộc cấu hình legacy/context riêng được bỏ qua. Bộ PostgreSQL hiện hành đã được bật và chạy, không chỉ chạy mock.
- PostgreSQL 18 riêng: cài đầy đủ 18 migration, chạy migrate lần hai và validate checksum thành công.
- Tích hợp: đăng ký độc lập, tổng hợp nhiều trang trại chỉ cho OWNER, chống đơn cửa hàng lặp với 6 yêu cầu đồng thời, hai yêu cầu duyệt tranh tồn kho chỉ một thành công; cách ly trang trại; công việc/lịch sử/version; nhập/xuất kho chống lặp; 12 yêu cầu xuất đồng thời; sử dụng vật tư kèm trừ kho; thu hoạch tạo lô; mua/nhận; bán/hủy; từ chối bán lô bị tạm giữ; nhập/xuất dữ liệu có validation và rollback.
- Chrome: đăng ký 4 trường và tự đăng nhập, tạo trang trại không bắt buộc nhân viên, trang tổng nhiều trang trại, lưu hồ sơ, công việc/lịch/thông báo và các module cũ. Đăng bán từ lô thu hoạch thật, khách đặt không đăng nhập, chủ xác nhận, kiểm tra biên nhận không lộ liên hệ. Viewport 1440px và 390px thành công. Ảnh được giữ trong `Evidence/Production/`.
- Sao lưu và phục hồi toàn bộ schema trên database kiểm thử riêng: thành công, đối chiếu dữ liệu và sequence. Backup của bản cài Windows cũng đã tạo và kiểm tra toàn vẹn archive; không khôi phục đè database vận hành.
- Windows: đã cài PostgreSQL riêng, DPAPI, HTTPS, tác vụ khởi động theo đăng nhập và tác vụ backup 02:00. Đã chạy thử quy trình backup–nâng cấp và kiểm tra health sau nâng cấp.

Theo lựa chọn cuối của người dùng, giữ nguyên mạng Windows **Public**, ứng dụng chỉ nghe **127.0.0.1:8443**. Truy cập **https://localhost:8443/**. PostgreSQL chỉ nghe **127.0.0.1:55433**. Không công khai ứng dụng cho máy khác trong mạng.

Checksum và bằng chứng kiểm thử: [validation.json](../Evidence/Production/validation.json). Trạng thái bản cài: `scripts/Get-DeploymentStatus.ps1`. Hướng dẫn vận hành: [WINDOWS_LAN.md](WINDOWS_LAN.md).

Các giới hạn vận hành cần duy trì:

- Máy phải bật, Windows không ngủ trong giờ sử dụng; tác vụ hiện chạy theo tài khoản cài đặt khi đã đăng nhập.
- Cần sao chép backup sang nơi lưu trữ ngoài ổ máy chủ và gia hạn chứng chỉ trước khi hết hạn 365 ngày.
- Chưa xác nhận tải lớn, máy khách từ xa hoặc chế độ offline/native; không suy diễn các kết quả này từ bộ kiểm thử hiện tại.
- Chứng chỉ được tin cậy theo xác nhận của người dùng trong Windows. Không có mật khẩu mặc định; đăng ký tài khoản chủ bằng giao diện.

Trang tổng và cửa hàng: [ACCOUNT_PORTAL.md](ACCOUNT_PORTAL.md). Bản cập nhật không tạo dữ liệu mẫu trong database vận hành.
