# Authentication & User/Role Management — Farm Management N02

## Quản lý trang trại, cây trồng, vật tư và kho

Các module đã nối giao diện với API và PostgreSQL. Xem [hướng dẫn cài đặt, chức năng và kiểm thử](docs/MANAGEMENT.md). Chạy migration **07 → 08 → 09**, build JAR rồi đăng nhập tại `http://127.0.0.1:8080/`; chọn **Trang trại & Cây trồng** hoặc **Vật tư & Kho**. Kiểm thử đầy đủ trên database riêng: `scripts/Test-Management.ps1 -Browser`.

## Bản cập nhật module quản trị

Đã bổ sung đổi/reset mật khẩu, CRUD người dùng (không xóa), kích hoạt/vô hiệu hóa, gán vai trò SYSTEM, quản lý quyền và bảo vệ trang/API. Xem [hướng dẫn hiện hành](docs/AUTH_USER_ROLE_MANAGEMENT.md) để chạy migration 06, cấp quản trị viên đầu tiên và dùng API/giao diện.

**Phần dưới là hướng dẫn nền Authentication v1**, giữ lại cho cấu hình môi trường/seed. Các mô tả giới hạn “chưa có CRUD User/RBAC” chỉ áp dụng bản v1; phạm vi hiện tại được mô tả trong tài liệu module ở trên.

Bản triển khai cho src.zip và pom.xml nhận ngày 28/09/2026. Java 21, Spring Boot 4.1.1; PostgreSQL schema ERD v1 đã triển khai.

## 1. Nội dung và giới hạn

Có POST /api/v1/auth/login, GET /api/v1/auth/me, POST /api/v1/auth/logout; Login UI tiếng Việt; Argon2id; JWT HS256 hết hạn đúng 86400 giây; phiên lưu PostgreSQL; logout thu hồi phiên hiện tại; khóa tài khoản hoặc đổi password_hash thu hồi tất cả phiên kể cả sau khi mở khóa lại. Không có refresh token, đăng ký, quên mật khẩu, CRUD User/Farm hoặc RBAC Farm trong gói này. Mọi URL khác bị từ chối mặc định.

Trang Login được phục vụ từ Spring Boot để kiểm thử cùng origin. Đây là UI demo tích hợp, chưa phải module React/JavaFX theo kiến trúc tổng thể; có thể dùng các API này từ các client đó ở bước tích hợp tiếp theo. Không bật CORS rộng.

## 2. Cài vào project trên Windows

1. Dừng Backend bằng Ctrl+C.
2. Sao lưu src và pom.xml hiện tại (hoặc commit vào nhánh riêng).
3. Giải nén ZIP này ở ngoài project. Chép **nội dung** thư mục Auth_Login_v1 vào thư mục chứa pom.xml và mvnw.cmd của bạn. Chọn ghi đè pom.xml, src/main/resources/application.properties và test context cũ. Các thư mục database, scripts, docs, Evidence ở cùng cấp pom.xml. Không đặt thêm một thư mục src lồng trong src.
4. Gói không chứa Maven Wrapper; giữ mvnw.cmd và .mvn của project gốc.
5. Trong pgAdmin mở Query Tool bằng postgres trên farm_management_erd_v1. Mở database/05_Authentication_Sessions.sql, chạy toàn bộ đúng một lần. Mong đợi COMMIT. Nếu báo lỗi, lưu lỗi và ROLLBACK; không tiếp tục giả định thành công. Nếu bảng đã tồn tại, kiểm tra lần chạy trước; không xóa bảng phiên tùy tiện.
6. Sau migration có **27 bảng**: 26 bảng nghiệp vụ cũ và auth_session. Quan hệ PLOT–CROP vẫn chỉ qua CROP_SEASON. Trigger app_user là thay đổi bổ sung để thu hồi phiên. Đây không phải chạy lại V1 hoặc xóa Database.
7. Mở Terminal ở thư mục project, chạy `./mvnw.cmd clean test package` (trên PowerShell dùng `.\mvnw.cmd clean test package`). Chờ BUILD SUCCESS. Test context có DB được bật riêng; các test bảo mật không cần DB.

## 3. Tạo tài khoản thử

Chạy trong PowerShell tại project:

```powershell
.\scripts\Seed-Local.ps1
```

Nhập mật khẩu postgres, tên đăng nhập mới (vd login.demo), email riêng và mật khẩu **ứng dụng** dài 12–128 ký tự có chữ và số. Đây là tài khoản để đăng nhập web, khác farm_app là tài khoản kết nối database. Script dùng profile seed, tạo một User ACTIVE và role SYSTEM USER trong cùng transaction, ghi audit và tự dừng sau SEED SUCCESS. Không có mật khẩu mặc định; không ghi đè/reset tài khoản đã tồn tại. Chỉ dùng profile seed trong phát triển trên localhost.

Khóa JWT được tạo bằng bộ sinh số ngẫu nhiên 32 byte, lưu mã hóa bằng DPAPI cho Windows user hiện tại tại %LOCALAPPDATA%/FarmN02/jwt-secret.dpapi, ngoài repo. Không chia sẻ file khóa, không chụp token/mật khẩu. Nếu xóa/đổi khóa, JWT cũ mất hiệu lực. Triển khai thật cần secret manager và HTTPS.

Nếu PowerShell chặn chạy .ps1 theo chính sách của máy, không thay đổi chính sách toàn máy. Mở script, thực hiện các lệnh đã đọc trong Terminal được phép hoặc nhờ quản trị viên cấu hình chính sách phù hợp.

## 4. Khởi động và thử UI

```powershell
.\scripts\Start-Local.ps1
```

Nhập mật khẩu PostgreSQL của farm_app. Chờ Started FarmManagementSystemN02Application. Mở http://127.0.0.1:8080. Đăng nhập bằng tên/mật khẩu **ứng dụng** vừa seed. Thử sai mật khẩu, đúng mật khẩu, Kiểm tra phiên, Đăng xuất.

Token chỉ giữ trong bộ nhớ trang, không lưu localStorage/cookie. Reload trang yêu cầu login lại; phiên cũ vẫn hết hạn theo TTL nếu chưa logout. UI không tự nhận đã logout thành công khi request logout lỗi mạng. API là Bearer-only, không dùng cookie/Basic nên CSRF token không áp dụng cho cơ chế này. Nếu chuyển sang cookie phải bổ sung CSRF.

## 5. Kiểm thử API và minh chứng

Giữ Backend chạy, mở Terminal thứ hai tại project:

```powershell
.\scripts\Test-Login.ps1
```

Script hỏi tài khoản ứng dụng, không in mật khẩu/token; lưu kết quả vào Evidence/Task03_Authentication/MC_API_Test_Results.csv. Bao gồm sai mật khẩu, đúng mật khẩu, token thiếu, trường lạ, me, logout, token đã logout và phiên thứ hai vẫn hoạt động. Chỉ coi PASS khi script thực tế chạy đạt. Xem docs/TEST_CHECKLIST.md để kiểm thử khóa tài khoản và lưu ảnh.

Test context trên Database đã chuẩn bị (tuỳ chọn, dùng đúng môi trường kiểm thử):
```powershell
. .\scripts\Configure-Local.ps1
$env:DB_USERNAME = "farm_app"
$dbSecret = Read-Host "Mat khau farm_app" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $dbSecret).Password
$env:RUN_DB_CONTEXT_TEST = "true"
.\mvnw.cmd test
Remove-Item Env:RUN_DB_CONTEXT_TEST, Env:DB_PASSWORD, Env:JWT_SECRET
```

## 6. Khác biệt cần nhóm review

Đọc docs/CONTRACT_AND_DECISIONS.md: ID số/systemRoles/membership bám ERD đã chạy, khác API Specification v1 dùng UUID/systemRole. Không tự tuyên bố đã khớp toàn bộ contract hoặc Gate đã được duyệt.

## 7. Vận hành

Flyway giữ disabled vì V1 đã chạy thủ công. Script 05 là migration thủ công có transaction. Chưa tự baseline Flyway; khi nhóm chuyển sang Flyway, đăng ký lịch sử theo quy trình riêng. Không bật Hibernate ddl-auto=create/update.

Runtime farm_app có SELECT/INSERT/UPDATE trên auth_session, không cấp DELETE; migration thu hẹp quyền sửa/xóa audit_log. Chủ DB chạy migration/seed. Định kỳ chủ DB có thể dọn phiên hết hạn sau thời gian lưu đã thống nhất. Không xóa audit trong tác vụ này.

Thông số Argon2id: salt=16 byte, hash=32 byte, parallelism=1, memory=19456 KiB, iterations=2. Cần đo thời gian trên máy triển khai và điều chỉnh theo khả năng tài nguyên. Log kỹ thuật tại logs/backend.log, xoay 10MB, giữ lịch sử 30 ngày; API chỉ trả lỗi an toàn và requestId. Không bật log body/Authorization/JDBC bind trên môi trường chứa dữ liệu thật.

Bản demo chỉ bind loopback 127.0.0.1. Trước khi công khai cần HTTPS, rate limiting đăng nhập ở gateway/app, quản lý secrets và kiểm thử tải/bảo mật theo NFR. Gói này không bổ sung quy tắc khóa tài khoản theo số lần thử sai khi tài liệu chưa quy định.
