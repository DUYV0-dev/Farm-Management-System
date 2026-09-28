# Contract triển khai Authentication v1 (theo ERD)

## Căn cứ
SRS v1 đã sửa FR-01–03; NFR-04,05; UC-01,02; ERD/schema v1 đã chạy; API Specification v1 ngày 27/09/2026; User/Role Domain v1.2 (đang review).

## API
- POST /api/v1/auth/login: JSON {username,password}; 200 data TokenSession. username trim, regex [a-z0-9._-]{3,50}; password không trim, 1–128 ký tự. Sai tên/mật khẩu/INACTIVE/không có role SYSTEM: cùng 401 INVALID_CREDENTIALS.
- GET /api/v1/auth/me: Bearer JWT; 200 data {user,memberships}.
- POST /api/v1/auth/logout: Bearer JWT, không body/query; 204, thu hồi đúng sessionId.
- Trường lạ/kiểu sai: 422; JSON lỗi: 400; media type sai: 415. Thiếu JWT/JWT hết hạn/phiên bị thu hồi: 401. Các đường dẫn ngoài scope mặc định deny. DB lỗi: 503 nếu được phát hiện trong lớp dữ liệu.
- Thành công {data,requestId}. Lỗi {requestId,timestamp,path,error:{code,message,details:[]}}. X-Request-Id do server sinh UUID; Cache-Control no-store.
- Login TokenSession có accessToken, tokenType=Bearer, expiresIn=86400, expiresAt UTC, sessionId UUID, user.
- User chỉ gồm id INTEGER, username, fullName, email bắt buộc, status, createdAt, systemRoles string[]. Không password/hash, updatedAt/version giả.
- Membership gồm farmId INTEGER, farmName, farmStatus, status membership, joinedAt, roles string[]. Chỉ truy vấn membership của người gọi; không trao quyền truy cập Farm chỉ từ thông tin này.

## Khác biệt với API Spec v1 cần review
1. User/Farm ID INTEGER theo schema thay UUID; sessionId và requestId vẫn UUID.
2. systemRoles là mảng theo user_role 0..N của ERD; chưa ép systemRole đơn lẻ. Login yêu cầu ít nhất một SYSTEM role hiện hành.
3. Không giả lập updatedAt/version/Member.id vì schema chưa có. Email vẫn bắt buộc theo ERD.
4. Seed duy nhất SYSTEM USER cho kiểm thử Login, không seed SYSTEM_ADMIN/OWNER/MANAGER/WORKER và không tự chốt actor K. Không cấp quyền Farm.
5. Thêm bảng kỹ thuật auth_session và trigger thu hồi trên app_user. JWT vẫn kiểm tra tài khoản/phiên/role hiện tại mỗi request. Không nhúng danh sách quyền Farm cũ vào JWT.
6. Trang HTML/JS dùng để demo cùng Backend; React và JavaFX cần tích hợp riêng. Đây là khác biệt phạm vi UI với kiến trúc, không phải đã hoàn tất tích hợp toàn hệ thống.

## Thiết kế
Controller -> AuthService -> AuthStore (JdbcTemplate, SQL tham số hóa) -> PostgreSQL. Gói Authentication dùng JDBC rõ ràng để bám schema có sẵn, không tự sinh DDL bằng ORM. Dependency JPA hiện hữu giữ nguyên; không có JPA entity mới trong task này.

Spring Security BearerTokenAuthenticationFilter/Nimbus kiểm tra chữ ký HS256, issuer, audience, exp/iat, TTL=24h, subject ID, sessionId khớp jti; converter đọc auth_session/user/role trước xác thực. Tất cả request ngoài Login và tài nguyên tĩnh đều bị bảo vệ; chưa có API nghiệp vụ mở bằng authenticated() chung.

Đăng nhập khóa row User trong transaction để tuần tự với thao tác khóa tài khoản; insert phiên và cấp token chỉ trả sau commit. Logout đánh revoked_at. Trigger SECURITY DEFINER được cố định search_path, dùng tên bảng đầy đủ, không dynamic SQL; thu hồi phiên khi status sang INACTIVE hoặc password_hash đổi. Re-activate không phục hồi phiên cũ.

## Nguồn kỹ thuật
- https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html
- https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- https://www.postgresql.org/docs/18/sql-grant.html

Các quyết định triển khai trên phục vụ bản chạy thử. Nhóm cần review/addendum API trước khi chốt tính tương thích của client.
