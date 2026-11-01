# Kiểm tra trước khi public Internet — 31/10/2026

## Cập nhật sau khắc phục

Đã chuyển datasource sang biến môi trường, thêm cấu hình Docker/Caddy với proxy
tin cậy và đóng gói font Noto Sans/OFL vào JAR. Toàn bộ file runtime được chuẩn bị
trong Git staging, không gồm log, `.env`, database hoặc bản build.

Pipeline mới: **164 test, 149 đạt, 15 bỏ qua, 0 failure/error**; browser,
backup/restore và restart/persistence đều đạt. Image Linux build thành công;
kiểm thử container không có mạng, filesystem chỉ đọc và user không phải root
đã xuất/đọc được PDF tiếng Việt từ font trong chính JAR. Compose và Caddy hợp lệ.
Chưa triển khai public hoặc xác minh DNS/chứng chỉ trên domain thật.

Hướng dẫn hiện hành: [PUBLIC_DEPLOYMENT](PUBLIC_DEPLOYMENT.md).
Kết quả pipeline mới ở `target/production-verification.json`, log ở
`.test-data/production-fd82b7c48da041cbaf0e68b13a393b06/`.

**Các phần bên dưới ghi lại lần audit trước khi khắc phục**, không phải trạng thái
cấu hình sau sửa. Không sử dụng các JAR/ZIP cũ còn chứa mật khẩu local.

## Kết quả kiểm tra mới

Đã chạy `scripts/Test-Production.ps1 -Browser` trên mã nguồn hiện tại:

- Clean package Java 21: thành công; frontend và migration trong JAR khớp source.
- PostgreSQL 18: cài 18 migration trên database trống, migrate lần hai và validate thành công.
- Maven: 158 test được phát hiện, 143 đạt, 15 bỏ qua, 0 failure, 0 error.
  Các test bỏ qua thuộc context/legacy có điều kiện riêng; không coi là đã kiểm chứng.
- Chrome: đăng ký, tạo trang trại, trang tổng, công việc/lịch/thông báo, các module,
  lưu hồ sơ, đặt hàng công khai, chủ nhận đơn, bảo vệ thông tin biên nhận và responsive: đạt.
- Backup/restore: đạt; đối chiếu 41 bảng và 29 sequence, không có khác biệt.
- Khởi động lại backend/PostgreSQL rồi đăng nhập mới: đọc lại được công việc tạo từ UI.
- PostgreSQL tạm đã dừng sau kiểm tra; không migrate hoặc seed database đang dùng.

Kết quả máy đọc được: `target/production-verification.json`.
Log: `.test-data/production-f8937c476d62493698849d4afa5414d3/`.
JAR SHA256: `0630F2A2158718832A9D0490E1FFCE434CA8D0F60A5EC994132FE2FC23F5938D`.
Kết quả này xác nhận bản build được kiểm thử, chưa xác nhận triển khai Internet,
hiệu năng tải thực tế hoặc bảo mật toàn bộ ứng dụng.

## Những việc cần xử lý trước phát hành

| Mức độ | Phát hiện | Việc cần làm |
| --- | --- | --- |
| Bắt buộc | `src/main/resources/application.properties` chứa mật khẩu datasource trực tiếp; Maven đưa file này vào JAR. | Loại bí mật khỏi source trước khi commit/publish; dùng biến môi trường. Đổi mật khẩu nếu source/JAR đã được chia sẻ. Build và kiểm chứng lại sau thay đổi. |
| Bắt buộc | Ba thuộc tính datasource cố định ở database local; `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` trong tài liệu không điều khiển ba giá trị này. | Nối lại placeholder tương ứng; hoặc dùng các biến chuẩn `SPRING_DATASOURCE_*` để ghi đè. Ghi đè khi chạy không loại mật khẩu đã nằm trong JAR. |
| Bắt buộc khi cài máy mới | `spring.flyway.enabled=false`; ứng dụng yêu cầu schema và role runtime không sở hữu bảng/không bypass RLS. | Chạy `DatabaseMigrations migrate` và `validate` riêng bằng role migration trước khi khởi động. Migration tạo role `farm_app`; xác minh dịch vụ PostgreSQL cho phép cấp quyền cần thiết. Không dùng tài khoản owner để chạy API. |
| Cần cấu hình theo hạ tầng | Mặc định web nghe `127.0.0.1:8443`, TLS mặc định tắt. Chưa có cấu hình domain/reverse proxy public trong repo. | Với proxy cùng máy, có thể giữ loopback và kết thúc HTTPS ở proxy. Với container/PaaS, đặt địa chỉ/cổng theo hạ tầng. Chỉ mở truy cập database trong mạng riêng cần thiết. |
| Cần xử lý khi có proxy | `LoginThrottle.java` dùng `getRemoteAddr()`, giới hạn chung 30 POST/phút cho login/register/đặt hàng theo IP. | Cấu hình nhận IP qua proxy được tin cậy và kiểm thử nhiều client. Nếu app chỉ thấy IP proxy, người dùng sẽ dùng chung hạn mức. Không tin tùy ý header client gửi lên. |
| Cần xử lý nếu chạy Linux | `ReportExportService.java` mặc định dùng `C:/Windows/Fonts/arial.ttf`. | Cấp font TTF hỗ trợ tiếng Việt trên máy chủ và đặt `reports.pdf.font`; kiểm tra PDF trong môi trường đích. |
| Cần hoàn tất khi deploy từ Git | Nhiều file runtime hiện chưa được Git theo dõi, gồm migration, frontend/app và các lớp cấu hình mới. | Rà soát, thêm đủ source và commit một phiên bản sạch sau khi bỏ bí mật. Bản clone từ commit cũ không đại diện workspace vừa kiểm thử. |

Cấu hình datasource dự kiến, chưa áp dụng vào máy local:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME:farm_app}
spring.datasource.password=${DB_PASSWORD}
```

JWT cần khóa Base64 giải mã được ít nhất 32 byte ngẫu nhiên, được giữ ổn định và
cấp riêng cho production. Ứng dụng đã có kiểm tra khóa khi khởi động.

## Phương án triển khai để chuẩn bị

Frontend đang được đóng gói cùng Spring Boot và gọi API bằng đường dẫn tương đối.
Có thể phục vụ frontend/backend cùng domain từ một JAR; không cần tách frontend
sang một dịch vụ hosting khác chỉ để public ứng dụng.

Một phương án là máy chủ chạy Java 21, reverse proxy HTTPS và PostgreSQL trong
mạng riêng. Cần bổ sung cấu hình khởi động lại ứng dụng, lưu log, backup ngoài máy,
domain/chứng chỉ và quy trình cập nhật/khôi phục phù hợp hạ tầng được chọn.
Các script cài đặt Windows/DPAPI hiện có không thay thế quy trình vận hành Linux.

Trình tự tiếp theo: bỏ bí mật và sửa cấu hình, chọn hạ tầng, kiểm tra role/migration
trên staging, kiểm chứng toàn bộ bản build mới, rồi kiểm tra HTTPS, IP client,
đăng nhập, các luồng nghiệp vụ và PDF qua domain thật trước khi mở sử dụng.

Tham chiếu chính thức:

- [Spring Boot: Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Spring Boot: Running Behind a Front-end Proxy Server](https://docs.spring.io/spring-boot/how-to/webserver.html#howto.webserver.use-behind-a-proxy-server)

Lần kiểm tra này chỉ thêm báo cáo; không thay đổi cấu hình local, mật khẩu, firewall,
database vận hành hoặc mã nghiệp vụ. `target/` và `.test-data/` được cập nhật bởi bộ test.
