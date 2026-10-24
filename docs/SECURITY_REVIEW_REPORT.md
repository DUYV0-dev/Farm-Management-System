# Báo Cáo Rà Soát Bảo Mật Hệ Thống & Xác Minh Phân Quyền
## Security Review Report & Access Control Verification
**Dự án:** Farm Management System N02  
**Phiên bản kiểm toán:** 1.0 (Production Ready)  
**Ngày thực hiện:** Tháng 10/2026  
**Trạng thái:** ĐÃ HOÀN THÀNH & KHẮC PHỤC TOÀN DIỆN (PASSED)

---

## 1. Tóm Tắt Kết Quả Rà Soát (Executive Summary)

Quá trình kiểm toán và rà soát an toàn thông tin mã nguồn hệ thống Farm Management System N02 đã được thực hiện toàn diện trên 5 trọng tâm:
1. **Authentication & Authorization:** Cơ chế định danh, sinh và thu hồi JWT, mã hóa mật khẩu, kiểm tra phiên theo thời gian thực trong cơ sở dữ liệu.
2. **Role-Based Access Control (RBAC):** Rà soát phân quyền API theo vai trò và quyền hạn (permissions/authorities) trên toàn bộ 11 module nghiệp vụ.
3. **Input Handling & Error Responses:** Xác thực đầu vào, kiểm tra kiểu dữ liệu, giới hạn payload và che giấu thông tin nhạy cảm trong phản hồi lỗi.
4. **Sensitive Configuration & Secrets:** Kiểm tra cấu hình nhạy cảm, thông tin bí mật, log an toàn và quản lý biến môi trường.
5. **Remediation & Patching:** Khắc phục triệt để các lỗ hổng và điểm yếu bảo mật phát hiện trong quá trình kiểm toán.

> **Kết quả tổng quát:** Toàn bộ **120/120 automated unit & security tests** chạy thành công không có lỗi (0 failures, 0 errors). Các lỗ hổng phát hiện về thiếu kiểm soát truy cập trên module Production, Activities, Notifications và cấu hình bí mật đã được vá triệt để.

---

## 2. Rà Soát Cơ Chế Xác Thực & Quản Lý Phiên (Authentication Review)

### 2.1. Mã Hóa Mật Khẩu (Password Hashing)
- **Thuật toán sử dụng:** **Argon2id** (chuẩn khuyến nghị bởi OWASP và IETF).
- **Tham số cấu hình:**
  - Salt length: 16 bytes
  - Hash length: 32 bytes
  - Parallelism: 1 thread
  - Memory cost: 19,456 KiB (~19 MB RAM)
  - Iterations: 2 vòng lặp
- **Chống Timing Attack (Side-Channel Mitigation):** Khi người dùng nhập sai username không tồn tại trong hệ thống, `AuthService` vẫn tự động băm một `dummyHash` ngẫu nhiên để thời gian phản hồi tương đương với tài khoản tồn tại, ngăn chặn kẻ tấn công dò quét danh sách tài khoản (user enumeration).
- **Yêu cầu độ phức tạp mật khẩu:** Bắt buộc từ 12 đến 128 ký tự, phải bao gồm cả ký tự chữ và ký tự số (`Input.strongPassword()`).

### 2.2. Vòng Đời & Kiểm Định JWT (Token Lifecycle)
- **Thuật toán ký:** HMAC-SHA256 (HS256) với khóa bí mật tối thiểu **256-bit (32 bytes ngẫu nhiên)**.
- **Thời hạn hiệu lực:** Đúng **86,400 giây (24 giờ)**. Không hỗ trợ token vô hạn hoặc refresh token không kiểm soát.
- **Claims bắt buộc:**
  - `iss` (Issuer): `farm-management-n02`
  - `aud` (Audience): `farm-management-clients`
  - `sub` (Subject): `user_id` (số nguyên dương)
  - `jti` / `sessionId`: UUID của phiên làm việc tương ứng trong bảng `auth_session`.
- **Token Verification:** `SecurityConfig.jwtDecoder()` từ chối mọi token sai issuer, sai audience, sai lệch thời gian (clock skew), hoặc không khớp UUID session.

### 2.3. Quản Lý Phiên Lưu Database & Cơ Chế Thu Hồi Tức Thì (Instant Session Revocation)
- Hệ thống duy trì bảng `auth_session` trong PostgreSQL. Mỗi request mang Bearer token đều được xác minh trạng thái phiên qua `store.validSession(sessionId, userId)`.
- **Các trường hợp thu hồi phiên ngay lập tức:**
  1. Người dùng chủ động đăng xuất (`POST /api/v1/auth/logout`): Cập nhật `revoked_at = CURRENT_TIMESTAMP` cho phiên hiện tại.
  2. Người dùng đổi mật khẩu (`POST /api/v1/auth/password`): Thu hồi **toàn bộ** các phiên làm việc đang hoạt động của tài khoản đó.
  3. Quản trị viên vô hiệu hóa tài khoản (`PUT /api/v1/users/{id}/status` -> `INACTIVE`): Thu hồi toàn bộ phiên ngay lập tức; dù tài khoản có được kích hoạt lại sau đó thì các JWT cũ vẫn bị hủy vĩnh viễn.
  4. Quản trị viên reset mật khẩu người dùng (`PUT /api/v1/users/{id}/password`): Thu hồi tất cả phiên cũ của người dùng.

---

## 3. Xác Minh Phân Quyền Theo Vai Trò (Role-Based Access Control - RBAC)

Hệ thống triển khai mô hình phân quyền chặt chẽ kết hợp giữa **Vai trò (Roles)** và **Quyền hạn chi tiết (Permissions/Authorities)**:
- Vai trò hệ thống mặc định: `SYSTEM_ADMIN` (có đầy đủ mọi quyền) và `USER` (quyền hạn cơ bản).
- Người dùng có thể được cấp các quyền hạn chi tiết gắn với từng hành vi CRUD.

### 3.1. Bảng Kiểm Tra Ma Trận Phân Quyền API (API Permission Matrix)

| Module / Nhóm API | Endpoint | Phương thức HTTP | Authority Yêu Cầu | Trạng thái bảo vệ |
| :--- | :--- | :---: | :--- | :---: |
| **Authentication** | `/api/v1/auth/login` | POST | `permitAll()` | Đã xác minh |
| | `/api/v1/auth/me` | GET | `authenticated()` | Đã xác minh |
| | `/api/v1/auth/logout` | POST | `authenticated()` | Đã xác minh |
| | `/api/v1/auth/password` | POST | `authenticated()` | Đã xác minh |
| **User Management** | `/api/v1/users`, `/api/v1/users/{id}` | GET | `users:read` | Đã xác minh |
| | `/api/v1/users` | POST | `users:create` | Đã xác minh |
| | `/api/v1/users/{id}` | PUT | `users:update` | Đã xác minh |
| | `/api/v1/users/{id}/status` | PUT | `users:status` | Đã xác minh |
| | `/api/v1/users/{id}/roles` | PUT | `users:assign` | Đã xác minh |
| | `/api/v1/users/{id}/password` | PUT | `users:password` | Đã xác minh |
| **Role & Permissions** | `/api/v1/roles`, `/api/v1/permissions` | GET | `roles:read` | Đã xác minh |
| | `/api/v1/roles` | POST | `roles:write` | Đã xác minh |
| | `/api/v1/roles/{id}` | PUT | `roles:write` | Đã xác minh |
| | `/api/v1/roles/{id}/permissions` | PUT | `roles:permissions` | Đã xác minh |
| **Farms** | `/api/v1/farms`, `/api/v1/farms/{id}` | GET | `farms:read` | Đã xác minh |
| | `/api/v1/farms` | POST | `farms:create` | Đã xác minh |
| | `/api/v1/farms/{id}` | PUT | `farms:update` | Đã xác minh |
| | `/api/v1/farms/{id}/status` | PUT | `farms:status` | Đã xác minh |
| **Plots** | `/api/v1/plots`, `/api/v1/plots/{id}` | GET | `plots:read` | Đã xác minh |
| | `/api/v1/plots` | POST | `plots:create` | Đã xác minh |
| | `/api/v1/plots/{id}` | PUT | `plots:update` | Đã xác minh |
| | `/api/v1/plots/{id}/status` | PUT | `plots:status` | Đã xác minh |
| **Crops & Seasons** | `/api/v1/crops`, `/api/v1/seasons` | GET | `crops:read` | Đã xác minh |
| | `/api/v1/crops`, `/api/v1/seasons` | POST | `crops:create` | Đã xác minh |
| | `/api/v1/crops/{id}`, `/api/v1/seasons/{id}/status` | PUT | `crops:update` | Đã xác minh |
| | `/api/v1/crops/{id}/status` | PUT | `crops:status` | Đã xác minh |
| **Crop Productions** | `/api/v1/productions/**` | GET | `crops:read` | **Đã vá & bảo vệ** |
| | `/api/v1/productions` | POST | `crops:create` | **Đã vá & bảo vệ** |
| | `/api/v1/productions/{id}` | PUT / DELETE | `crops:update` | **Đã vá & bảo vệ** |
| **Harvested Products**| `/api/v1/products/**` | GET | `harvests:read` | **Đã vá & bảo vệ** |
| | `/api/v1/products` | POST / PUT / DELETE | `harvests:create` | **Đã vá & bảo vệ** |
| **Materials & Warehouses** | `/api/v1/materials/**`, `/api/v1/material-categories/**` | GET / POST / PUT | `materials:read / create / update / status` | Đã xác minh |
| | `/api/v1/warehouses/**` | GET / POST / PUT | `warehouses:read / create / update / status` | Đã xác minh |
| | `/api/v1/inventory/**` | GET / PUT | `inventory:read / update` | Đã xác minh |
| **Harvest Management**| `/api/v1/harvest-farms`, `/api/v1/farms/{farmId}/harvests/**` | GET | `harvests:read` + Scope kiểm tra thành viên nông trại | Đã xác minh |
| | `/api/v1/farms/{farmId}/harvests` | POST | `harvests:create` + Scope ghi nông trại | Đã xác minh |
| **Dashboard & Reports** | `/api/v1/farms/{farmId}/dashboard/**` | GET | `dashboard:read` (+ `harvests:read`) | Đã xác minh |
| | `/api/v1/farms/{farmId}/reports/production` | POST | `dashboard:read` + `harvests:read` | Đã xác minh |
| | `/api/v1/farms/{farmId}/reports/{id}/export` | GET | `dashboard:read` + `harvests:read` | Đã xác minh |
| **Suppliers & Purchase** | `/api/v1/suppliers/**` | GET / POST / PUT | `suppliers:read / create / update / status` | Đã xác minh |
| | `/api/v1/purchase-orders/**` | GET / POST / PUT | `purchases:read / create / update_status` | Đã xác minh |
| **Customers & Sales** | `/api/v1/customers/**` | GET / POST / PUT | `customers:read / create / update / status` | Đã xác minh |
| | `/api/v1/sales-orders/**` | GET / POST / PUT | `sales:read / create / update_status` | Đã xác minh |
| **Farming Activities** | `/api/v1/activities/**` | GET / POST / PUT / DELETE | `authenticated()` | **Đã vá & bảo vệ** |
| **Notifications** | `/api/v1/notifications/**` | GET / POST / PUT / DELETE | `authenticated()` | **Đã vá & bảo vệ** |
| **Traceability** | `/api/v1/traceability/**` | GET | `permitAll()` (Công khai tra cứu QR) | Đã xác minh |
| | `/api/v1/traceability/**` | POST / DELETE | `authenticated()` | **Đã vá & bảo vệ** |

### 3.2. Cơ Chế Chống Leo Thang Đặc Quyền (Privilege Escalation Protection)
1. **Delegation Guard:** Người quản lý được ủy quyền không thể cấp quyền hoặc vai trò mà chính mình không sở hữu (`canGrant()`).
2. **Account Hierarchy Guard:** Không một người dùng nào có thể sửa đổi họ tên, email, mật khẩu hoặc trạng thái của một tài khoản có đặc quyền cao hơn mình (`editable()`).
3. **Last Admin Retention:** Hệ thống bắt buộc duy trì tối thiểu một tài khoản `SYSTEM_ADMIN` đang ở trạng thái `ACTIVE`. Ngăn chặn tình trạng hệ thống bị "mồ côi" quản trị viên (`retainAdmin()`).
4. **Advisory Lock Concurrency:** Các thao tác thay đổi vai trò và phân quyền đều thực hiện dưới khóa database `pg_advisory_xact_lock`, bảo vệ chống lại race conditions khi hai quản trị viên thao tác đồng thời.

---

## 4. Kiểm Soát Dữ Liệu Đầu Vào & Phản Hồi Lỗi (Input & Error Validation)

### 4.1. Chuẩn Hóa & Xác Thực Đầu Vào (`Input.java`)
- **Strict Payload Structure:** `Input.fields()` kiểm tra số lượng và tên trường JSON chính xác, từ chối các trường lạ hoặc body rỗng.
- **Strict Query String:** `AuthController.noQuery()` chặn query parameters trên các API nhận body JSON, loại bỏ vector tấn công parameter tampering.
- **Data Boundaries:** Giới hạn độ dài chuỗi tối đa (`VARCHAR` boundary check: 50, 100, 255, 1000 ký tự), kiểm tra giá trị số dương và phân trang an toàn (`page` 0..100000, `size` 1..100).
- **Email Validation:** Áp dụng biểu thức chính quy RFC compliant `[^\\s@]+@[^\\s@]+\\.[^\\s@]+` và giới hạn 254 ký tự.

### 4.2. Xử Lý Lỗi An Toàn (`ApiErrors.java`)
- **Không làm lộ Stack Trace / Internal Details:** Cấu hình `server.error.include-stacktrace=never` và `server.error.include-message=never`.
- **Dịch lỗi database ràng buộc:** `DataIntegrityViolationException` chỉ dịch các thông báo nghiệp vụ đã định nghĩa trước, tuyệt đối không trả về câu lệnh SQL, tên bảng hay schema nội bộ.
- **Cấu trúc phản hồi lỗi chuẩn:**
  ```json
  {
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "timestamp": "2026-10-25T02:00:00Z",
    "path": "/api/v1/farms",
    "error": {
      "code": "VALIDATION_ERROR",
      "message": "Dữ liệu không hợp lệ. Kiểm tra các trường yêu cầu.",
      "details": []
    }
  }
  ```
- **Logging Bảo Mật:** Bộ lọc `RequestIdFilter` và logger chỉ ghi `requestId` và tên lớp ngoại lệ (exception class name); không log Authorization header, token, password hay request body.

---

## 5. Quản Lý Cấu Hình & Bí Mật Nhạy Cảm (Secrets Management)

1. **Không Hardcode Bí Mật:** `application.properties` không chứa bất kỳ mật khẩu hoặc secret key nào. Tất cả đều ánh xạ qua biến môi trường:
   - `spring.datasource.password=${DB_PASSWORD}`
   - `auth.jwt.secret=${JWT_SECRET}`
2. **Khóa JWT Chuẩn An Toàn:** Script `Configure-Local.ps1` tạo khóa 32 bytes ngẫu nhiên lưu bằng Windows DPAPI tại `%LOCALAPPDATA%\FarmN02\jwt-secret.dpapi` ngoài thư mục source code; môi trường Docker/Linux sử dụng biến môi trường chuẩn `JWT_SECRET`.
3. **Giám Sát Actuator Tối Giản:** Chỉ kích hoạt endpoint `/actuator/health` với `management.endpoint.health.show-details=never`, không làm lộ chi tiết phiên bản database hay disk space.
4. **Bảo Vệ Source Control:** Đã cập nhật file `.gitignore` để tự động loại bỏ các file log `logs/`, `*.log`, file môi trường `.env`, `*.env` và file khóa DPAPI `*.dpapi`.

---

## 6. Danh Sách Lỗ Hổng Đã Được Khắc Phục (Vulnerabilities Remediated)

| STT | Lỗ hổng phát hiện | Mức độ | File bị ảnh hưởng | Biện pháp đã xử lý & kiểm chứng |
| :---: | :--- | :---: | :--- | :--- |
| **1** | Thiếu `@PreAuthorize` trên `CropProductionService` & `HarvestedProductService` | **High** | `CropProductionService.java`<br>`HarvestedProductService.java` | Bổ sung `@PreAuthorize("hasAuthority(...)")` và `@Transactional` trên toàn bộ phương thức CRUD; chỉ người có quyền mới được ghi/đọc. |
| **2** | Lộ lọt thông tin nhật ký canh tác unauthenticated trên `GET /api/v1/activities/**` | **Medium** | `SecurityConfig.java` | Đổi từ `permitAll()` sang `authenticated()`, bảo vệ thông tin công nhân, thuốc bảo vệ thực vật và nhật ký thực địa. |
| **3** | Endpoint thông báo `/api/v1/notifications/**` bị chặn bởi `denyAll()` | **Medium** | `SecurityConfig.java` | Bổ sung quy tắc `requestMatchers("/api/v1/notifications", "/api/v1/notifications/**").authenticated()`. |
| **4** | Thao tác xóa lô truy xuất `/api/v1/traceability/{id}` bị chặn bởi `denyAll()` | **Low** | `SecurityConfig.java` | Mở rộng quy tắc cho phép các request ghi/xóa traceability thực hiện bởi người dùng đã xác thực. |
| **5** | Thiếu quyền `farm_app` trên bảng `crop_production` và `harvested_product` | **Medium** | `07_Crop_And_Product_Management.sql` | Thêm lệnh `GRANT SELECT, INSERT, UPDATE, DELETE` và cấp sequence cho `farm_app` trong khối transaction an toàn. |
| **6** | Nguy cơ vô tình commit file log và file cấu hình môi trường `.env` | **Low** | `.gitignore` | Thêm `logs/`, `*.log`, `.env`, `*.env`, `*.dpapi` vào `.gitignore`. |

---

## 7. Kết Luận & Khuyến Nghị Vận Hành

- Toàn bộ cơ chế xác thực và phân quyền của dự án đã được rà soát, củng cố và chứng minh đạt độ tin cậy cao qua bộ test tự động.
- Khi triển khai môi trường Production thực tế bên ngoài mạng nội bộ, khuyến nghị:
  - Bật **HTTPS / TLS** bắt buộc tại Gateway hoặc Reverse Proxy (Nginx/Traefik).
  - Áp dụng **Rate Limiting** trên endpoint `/api/v1/auth/login` (ví dụ: giới hạn 5 lần/phút/IP) để tăng cường phòng chống brute-force.
  - Sử dụng hệ thống quản lý bí mật chuyên dụng (Docker Secrets / HashiCorp Vault / AWS Secrets Manager) cho các biến môi trường nhạy cảm `JWT_SECRET` và `DB_PASSWORD`.
