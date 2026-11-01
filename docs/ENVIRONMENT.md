# Một file cấu hình ứng dụng

Ứng dụng chỉ dùng **`src/main/resources/application.properties`** cho thiết lập chạy.
Không còn file development/production hoặc cơ chế tự tìm file cấu hình IDE.
File trong `src/test/resources` chỉ dành cho JUnit, không đi vào JAR phát hành.

`application.properties` chứa thiết lập chung và tham chiếu biến môi trường.
URL database, tài khoản và mật khẩu nhận từ `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`;
không có địa chỉ database hoặc mật khẩu local trong source/JAR. JWT/TLS cũng được
cấp qua môi trường. Không commit file `.env` hay chứng chỉ/khóa riêng.

| Biến môi trường | Ý nghĩa |
| --- | --- |
| `DB_URL` | JDBC URL PostgreSQL, bắt buộc chọn rõ database |
| `DB_USERNAME` | Tài khoản runtime, mặc định `farm_app` |
| `DB_PASSWORD` | Mật khẩu database |
| `JWT_SECRET` | Khóa xác thực đăng nhập; giữ ổn định khi chuyển cấu hình |
| `SERVER_ADDRESS` | Địa chỉ web lắng nghe, mặc định `127.0.0.1` |
| `SERVER_PORT` | Cổng web, mặc định `8443` |
| `SERVER_SSL_ENABLED` | `true` để bật HTTPS, mặc định `false` |
| `TLS_KEY_STORE` | URI/path chứng chỉ máy chủ khi bật HTTPS |
| `TLS_KEY_STORE_PASSWORD` | Mật khẩu kho chứng chỉ |
| `LOG_FILE` | Vị trí log, mặc định `logs/backend.log` |
| `DB_POOL_SIZE` | Số kết nối tối đa, mặc định 10 |
| `REPORTS_PDF_FONT` | Mặc định `classpath:fonts/NotoSans-Regular.ttf` đóng gói trong JAR; có thể thay bằng đường dẫn TTF |
| `FORWARD_HEADERS_STRATEGY` | Mặc định `none`; bộ Docker dùng `native` để Tomcat nhận IP/HTTPS từ proxy được tin cậy |
| `TRUSTED_PROXY_REGEX` | Regex IP proxy được tin cậy; Docker chỉ tin Caddy `172.30.40.10` |
| `APP_OPEN_BROWSER` | Tự mở web khi chạy source, mặc định `true` |
| `APP_FRONTEND_WORKING_TREE` | Đọc frontend từ source khi chạy source, mặc định `true` |

JAR luôn dùng frontend đóng gói và không tự mở trình duyệt, kể cả khi hai tùy chọn
cuối được bật. Kiểm tra quyền/schema nghiêm ngặt áp dụng cho cả source và JAR.
Ứng dụng không tự migrate, seed hoặc chuyển sang database khác khi kết nối lỗi.

## Máy Windows hiện tại

Thông tin trước đây trong file IDE đã chuyển sang **biến môi trường của tài khoản
Windows hiện tại**. Kết nối vẫn là PostgreSQL `127.0.0.1:55433/farm_management`,
tài khoản `farm_app`; web vẫn ở `https://localhost:8443/`.

Run `FarmManagementSystemN02Application` như bình thường. Khi chạy source trên Windows,
launcher đọc lại các biến User còn thiếu trong tiến trình để xử lý trường hợp IntelliJ
được mở trước khi biến Windows thay đổi. Chỉ các tên DB/JWT/TLS và tùy chọn chạy đã
khai báo mới được đọc; không tạo khóa JWT mới và không thêm file cấu hình.
Các giá trị đọc lại là giá trị dự phòng: thiết lập trực tiếp trong properties,
Run Configuration, tham số JVM hoặc dòng lệnh vẫn được ưu tiên.
JAR phát hành không đọc môi trường User của máy cá nhân bằng cơ chế này.

Để chạy JAR từ PowerShell đang mở sẵn:

```powershell
.\scripts\Start-Local.ps1
```

Script nạp biến User còn thiếu vào tiến trình và chạy JAR; không đọc file Spring khác.
Không in các biến mật khẩu/JWT khi chụp màn hình, gửi log hoặc trao đổi trong Git.
Biến môi trường là thông tin riêng của tài khoản Windows, không phải kho bí mật mã hóa.
Trên máy chủ thật, cung cấp các biến tương ứng qua cơ chế quản lý bí mật của môi trường triển khai.

## Phát hành

```powershell
.\scripts\Test-Production.ps1 -Browser
.\scripts\Build-Release.ps1 -UseVerifiedBuild
```

Trên máy đích, cung cấp các biến DB/JWT/TLS rồi chạy:

```powershell
.\Start-Production.ps1 -ReleaseJar .\backend.jar
```

Không cần `-ConfigurationFile`, profile development/production hay một file properties
thứ hai. PostgreSQL có cổng riêng trong `DB_URL`, độc lập với cổng web `SERVER_PORT`.

Metadata cài đặt PostgreSQL (`config.json`, thư mục pgdata, khóa owner dùng cho backup)
vẫn phục vụ Windows service và sao lưu. Chúng không thay thế hoặc ghi đè cấu hình Spring.

Triển khai Linux/domain/HTTPS: [PUBLIC_DEPLOYMENT](PUBLIC_DEPLOYMENT.md).
