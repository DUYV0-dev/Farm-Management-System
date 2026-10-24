# Hướng Dẫn Cấu Hình Môi Trường & Quy Trình Triển Khai Hệ Thống
## Deployment Environment Configuration & Operation Guide
**Dự án:** Farm Management System N02  
**Phiên bản ứng dụng:** 0.0.1-SNAPSHOT (Spring Boot 4.1.1, Java 21)  
**Tài liệu chuẩn vận hành:** Tháng 10/2026

---

## 1. Tổng Quan Kiến Trúc Triển Khai

Ứng dụng **Farm Management System N02** được thiết kế theo kiến trúc **Monolithic hiện đại, tự đóng gói (Self-contained)**:
- **Backend Runtime:** Java 21 LTS (Spring Boot 4.1.1, Spring Security 6+ OAuth2 Resource Server, Spring Data JPA / JdbcTemplate).
- **Frontend Assets:** Bộ giao diện HTML/CSS/JavaScript thuần tối ưu, được Maven Plugin đóng gói tự động vào trong file JAR tại đường dẫn classpath `static/ui/` và `static/`.
- **Database Engine:** PostgreSQL 15, 16 hoặc 17 với schema phân tầng và cơ chế xác thực phiên `auth_session`.
- **Packaging:** Một file JAR duy nhất (`target/backend-0.0.1-SNAPSHOT.jar`, dung lượng ~87 MB) chứa toàn bộ Backend, Web Server nhúng (Apache Tomcat), và toàn bộ các module giao diện (Dashboard, Farm, Material, Harvest, Purchase, Sales, Activities, Traceability).

---

## 2. Yêu Cầu Hệ Thống & Môi Trường (Prerequisites)

| Thành phần | Yêu cầu tối thiểu | Khuyến nghị Production |
| :--- | :--- | :--- |
| **Hệ điều hành** | Windows 10/11 64-bit, Ubuntu 22.04 LTS, Debian 12, RHEL 9 | Linux Server 64-bit (Ubuntu/Debian/RHEL) |
| **Java Development Kit (JDK)** | OpenJDK / Eclipse Temurin 21 (hoặc Java 22+) | Eclipse Temurin JDK 21 LTS |
| **PostgreSQL Database** | PostgreSQL 15.x | PostgreSQL 16.x / 17.x |
| **RAM** | Tối thiểu 2 GB RAM khả dụng | 4 GB – 8 GB RAM |
| **Dung lượng đĩa (Storage)** | 1 GB đĩa trống | 10 GB+ SSD cho dữ liệu và file logs |
| **Công cụ Build** | Maven Wrapper (`./mvnw` hoặc `.\mvnw.cmd`) có sẵn | Không cần cài đặt Maven rời |

---

## 3. Cấu Hình Biến Môi Trường & Bí Mật Nhạy Cảm (Environment Variables)

Hệ thống tuân thủ nguyên tắc **12-Factor App**: Mọi cấu hình kết nối và bí mật đều được đọc động qua biến môi trường.

### 3.1. Danh Sách Biến Môi Trường Bắt Buộc

| Tên biến | Giá trị mẫu | Bắt buộc | Mô tả |
| :--- | :--- | :---: | :--- |
| `SERVER_PORT` | `8080` | Không (Mặc định: 8080) | Cổng HTTP lắng nghe của ứng dụng |
| `DB_URL` | `jdbc:postgresql://localhost:5432/farm_management_erd_v1` | Có | URL kết nối JDBC đến PostgreSQL |
| `DB_USERNAME` | `farm_app` | Có | Tài khoản kết nối của ứng dụng (least privilege) |
| `DB_PASSWORD` | *(Mật khẩu an toàn)* | **CÓ** | Mật khẩu tài khoản `farm_app` |
| `JWT_SECRET` | *(Chuỗi Base64 >= 32 bytes)* | **CÓ** | Khóa bí mật dùng để ký và giải mã JWT (HS256) |

### 3.2. Cách Sinh Khóa `JWT_SECRET` Chuẩn An Toàn (256-bit Random)

Khóa bí mật JWT bắt buộc phải được mã hóa Base64 từ **tối thiểu 32 bytes ngẫu nhiên mật mã (cryptographically secure random)**:
- **Trên Windows PowerShell:**
  ```powershell
  $bytes = New-Object byte[] 32
  $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  $rng.GetBytes($bytes)
  [Convert]::ToBase64String($bytes)
  ```
- **Trên Linux / macOS Terminal:**
  ```bash
  openssl rand -base64 32
  ```

### 3.3. Sử Dụng File Mẫu `.env.example`
Dự án đã chuẩn bị sẵn file template [`.env.example`](file:///d:/LIBRARY%20OF%20SUBJECTS/Farm-Management-System/.env.example). Tạo file `.env` bằng lệnh:
```bash
cp .env.example .env
```
Điền các giá trị mật khẩu cơ sở dữ liệu và khóa `JWT_SECRET` vào file `.env`. File `.env` đã được cấu hình trong `.gitignore` để tránh rủi ro rò rỉ mã nguồn.

---

## 4. Quy Trình Khởi Tạo & Migration Cơ Sở Dữ Liệu (Database Setup)

Toàn bộ database schema được quản lý qua các file SQL migration đánh số thứ tự trong thư mục [`database/`](file:///d:/LIBRARY%20OF%20SUBJECTS/Farm-Management-System/database).

### 4.1. Thứ Tự Chạy Migration Bắt Buộc

Việc khởi tạo cơ sở dữ liệu phải được thực hiện với quyền quản trị viên (`postgres`) theo đúng thứ tự sau:

1. **Tạo Database:**
   ```sql
   CREATE DATABASE farm_management_erd_v1 WITH ENCODING 'UTF8';
   ```
2. **Tạo tài khoản ứng dụng (Least Privilege):**
   ```sql
   CREATE USER farm_app WITH PASSWORD 'MatKhau_farm_app_2026';
   GRANT CONNECT ON DATABASE farm_management_erd_v1 TO farm_app;
   GRANT USAGE ON SCHEMA public TO farm_app;
   ```
3. **Chạy các file SQL tuần tự:**
   - [`05_Authentication_Sessions.sql`](file:///d:/LIBRARY%20OF%20SUBJECTS/Farm-Management-System/database/05_Authentication_Sessions.sql): Bảng `auth_session`, trigger thu hồi phiên khi đổi mật khẩu/khóa tài khoản.
   - [`06_User_Role_Management.sql`](file:///d:/LIBRARY%20OF%20SUBJECTS/Farm-Management-System/database/06_User_Role_Management.sql): Bảng `auth_permission`, `auth_role_permission`, role `SYSTEM_ADMIN`, `USER`.
   - [`07_Farm_Plot_Crop.sql`](file:///d:/LIBRARY%20OF%20SUBJECTS/Farm-Management-System/database/07_Farm_Plot_Crop.sql): Quản lý nông trại (`farm`), khu đất (`plot`), cây trồng (`crop`), mùa vụ (`crop_season`).
   - [`07_Crop_And_Product_Management.sql`](file:///d:/LIBRARY%20OF%20SUBJECTS/Farm-Management-System/database/07_Crop_And_Product_Management.sql): Bảng sản xuất mùa vụ `crop_production` và sản phẩm thu hoạch `harvested_product` (kèm cấp quyền cho `farm_app`).
   - [`08_Material_Warehouse.sql`](file:///d:/LIBRARY%20OF%20SUBJECTS/Farm-Management-System/database/08_Material_Warehouse.sql): Nhóm vật tư, vật tư nông nghiệp, kho bãi và tồn kho.
   - [`09_Management_Integrity.sql`](file:///d:/LIBRARY%20OF%20SUBJECTS/Farm-Management-System/database/09_Management_Integrity.sql): Ràng buộc toàn vẹn dữ liệu (triggers bảo vệ diện tích, kiểm tra trạng thái hoạt động).
   - [`10_Harvest_Management.sql`](file:///d:/LIBRARY%20OF%20SUBJECTS/Farm-Management-System/database/10_Harvest_Management.sql): Quản lý thu hoạch, kiểm soát chất lượng và sản lượng.
   - [`11_Advanced_Dashboard_Reports.sql`](file:///d:/LIBRARY%20OF%20SUBJECTS/Farm-Management-System/database/11_Advanced_Dashboard_Reports.sql): View thống kê, bảng snapshot báo cáo xuất PDF/Excel.
   - [`12_Supplier_Purchase_Management.sql`](file:///d:/LIBRARY%20OF%20SUBJECTS/Farm-Management-System/database/12_Supplier_Purchase_Management.sql): Nhà cung cấp và đơn mua hàng vật tư.
   - [`13_Sales_Order_Management.sql`](file:///d:/LIBRARY%20OF%20SUBJECTS/Farm-Management-System/database/13_Sales_Order_Management.sql): Khách hàng và đơn bán nông sản.
   - [`Bootstrap-Admin.sql`](file:///d:/LIBRARY%20OF%20SUBJECTS/Farm-Management-System/database/Bootstrap-Admin.sql): Gán quyền quản trị viên đầu tiên cho tài khoản quản trị.

### 4.2. Chạy Tự Động Bằng Script
- **Trên Windows PowerShell:**
  ```powershell
  .\scripts\Migrate-Database.ps1 -Host localhost -Port 5432 -Database farm_management_erd_v1 -User postgres
  ```

---

## 5. Quy Trình Build & Đóng Gói (Build & Packaging Process)

### 5.1. Cơ Chế Tự Động Đóng Gói Backend & Frontend
Trong file [`pom.xml`](file:///d:/LIBRARY%20OF%20SUBJECTS/Farm-Management-System/pom.xml), cấu hình `<resources>` tự động quét toàn bộ thư mục [`frontend/`](file:///d:/LIBRARY%20OF%20SUBJECTS/Farm-Management-System/frontend) và ánh xạ vào thư mục đích `static/ui/` của JAR:
```xml
<resource>
    <directory>frontend</directory>
    <targetPath>static/ui</targetPath>
    <includes>
        <include>advanced-dashboard.html</include><include>js/advanced-dashboard.js</include>
        <include>harvest.html</include><include>js/harvest.js</include>
        <include>farm.html</include><include>material.html</include>
        <include>purchase.html</include><include>js/purchase.js</include>
        <include>sales.html</include><include>js/sales.js</include>
        <include>css/*.css</include><include>js/farm.js</include>
        <include>js/material.js</include><include>js/api-client.js</include>
    </includes>
</resource>
```

### 5.2. Lệnh Thực Hiện Build
Chạy lệnh đóng gói không chạy unit tests:
```powershell
.\mvnw.cmd clean package -DskipTests
```
Hoặc chạy kiểm thử tự động toàn diện trước khi đóng gói:
```powershell
.\mvnw.cmd clean test package
```
Kết quả đóng gói tạo ra file thực thi:
`target/backend-0.0.1-SNAPSHOT.jar` (dung lượng ~87.2 MB).

---

## 6. Hướng Dẫn Triển Khai Thực Tế

### Phương Án A: Triển Khai Trên Windows Bằng PowerShell Script (Khuyến Nghị Local / On-Premise)

1. Mở PowerShell tại thư mục dự án.
2. Chạy script triển khai tự động:
   ```powershell
   .\scripts\Deploy-App.ps1 -Start
   ```
   Script sẽ tự động:
   - Kiểm tra phiên bản Java runtime.
   - Tự động sinh `JWT_SECRET` ngẫu nhiên an toàn nếu chưa có.
   - Build và đóng gói ứng dụng qua Maven.
   - Yêu cầu mật khẩu database một cách an toàn (ẩn ký tự).
   - Khởi động ứng dụng.

### Phương Án B: Triển Khai Trên Linux Server Bằng Shell Script

1. Cấp quyền thực thi cho các script:
   ```bash
   chmod +x ./scripts/*.sh ./mvnw
   ```
2. Thiết lập cấu hình trong `.env`:
   ```bash
   cp .env.example .env
   nano .env
   ```
3. Chạy lệnh build và khởi động:
   ```bash
   ./scripts/deploy.sh --start
   ```

### Phương Án C: Triển Khai Container Hóa Với Docker & Docker Compose (Production Ready)

Hệ thống đã được thiết kế sẵn file [`Dockerfile`](file:///d:/LIBRARY%20OF%20SUBJECTS/Farm-Management-System/Dockerfile) (Multi-stage build, người dùng không root `farmuser`) và [`docker-compose.yml`](file:///d:/LIBRARY%20OF%20SUBJECTS/Farm-Management-System/docker-compose.yml):

1. Chuẩn bị file `.env`:
   ```bash
   cp .env.example .env
   ```
2. Khởi động toàn bộ stack (PostgreSQL + Tự động chạy Migration + Backend):
   ```bash
   docker compose up -d --build
   ```
3. Kiểm tra trạng thái containers:
   ```bash
   docker compose ps
   ```
4. Xem log hoạt động theo thời gian thực:
   ```bash
   docker compose logs -f backend
   ```

---

## 7. Khởi Tạo Tài Khoản Quản Trị Đầu Tiên (Seeding Initial Account)

Để có tài khoản đăng nhập lần đầu tiên sau khi cài đặt database:

```powershell
.\scripts\Seed-Local.ps1
```
Script sẽ hỏi:
- Mật khẩu tài khoản `postgres`
- Tên đăng nhập mới (ví dụ: `admin.demo`)
- Email người dùng (ví dụ: `admin@farm.local`)
- Mật khẩu ứng dụng mới (12–128 ký tự, có chữ và số)

Sau khi tạo thành công tài khoản, chạy câu lệnh gán vai trò quản trị viên tối cao:
```sql
-- Mở pgAdmin hoặc psql:
INSERT INTO public.user_role(user_id, role_id)
SELECT u.user_id, r.role_id
FROM public.app_user u CROSS JOIN public.role r
WHERE u.username = 'admin.demo' AND r.scope = 'SYSTEM' AND r.role_name = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;
```

---

## 8. Kiểm Tra & Giám Sát Sau Triển Khai (Verification & Healthcheck)

1. **Kiểm tra Healthcheck:**
   Truy cập endpoint Actuator bằng cURL hoặc trình duyệt:
   ```bash
   curl http://127.0.0.1:8080/actuator/health
   ```
   *Kết quả mong đợi:*
   ```json
   {"status":"UP"}
   ```

2. **Truy Cập Giao Diện Ứng Dụng:**
   Mở trình duyệt web tại địa chỉ:
   ```
   http://127.0.0.1:8080/
   ```
   Đăng nhập bằng tài khoản và mật khẩu đã khởi tạo. Thử truy cập các module:
   - **Trang trại & Cây trồng:** `http://127.0.0.1:8080/#farm`
   - **Vật tư & Kho:** `http://127.0.0.1:8080/#material`
   - **Thu hoạch:** `http://127.0.0.1:8080/#harvest`
   - **Dashboard & Báo cáo nâng cao:** `http://127.0.0.1:8080/#dashboard`
   - **Quản lý người dùng & vai trò:** Chọn nút *Người dùng* hoặc *Vai trò* trên thanh điều hướng.

3. **Kiểm Tra File Logs Vận Hành:**
   Log được ghi theo chuẩn quay vòng an toàn tại:
   `logs/backend.log` (kích thước tối đa 10 MB/file, lưu trữ lịch sử 30 ngày).

---

## 9. Khắc Phục Sự Cố Thường Gặp (Troubleshooting)

| Hiện tượng | Nguyên nhân có thể | Cách khắc phục |
| :--- | :--- | :--- |
| `java.lang.IllegalStateException: JWT_SECRET must contain at least 32 random bytes` | Biến môi trường `JWT_SECRET` chưa được đặt hoặc có ít hơn 32 bytes | Sinh lại chuỗi Base64 32 bytes ngẫu nhiên theo mục 3.2 và gán vào biến môi trường. |
| `FATAL: password authentication failed for user "farm_app"` | Mật khẩu database của `farm_app` nhập sai hoặc chưa được cấp | Kiểm tra lại mật khẩu `DB_PASSWORD` hoặc đặt lại mật khẩu cho `farm_app` trong PostgreSQL. |
| `ERROR: relation "public.auth_session" does not exist` | Chưa chạy script migration `05_Authentication_Sessions.sql` | Thực hiện chạy lại tuần tự các file SQL migration trong thư mục `database/` theo mục 4.1. |
| `Port 8080 is already in use` | Cổng 8080 bị tiến trình khác chiếm dụng | Thay đổi `SERVER_PORT=8081` trong biến môi trường hoặc dừng tiến trình cũ đang chiếm cổng. |
| Không tải được iframe các module con (`Refused to display ... in a frame`) | CSP hoặc X-Frame-Options bị sai cấu hình | Ứng dụng đã cấu hình `frameOptions(sameOrigin)`. Đảm bảo truy cập các trang qua cùng Origin `http://127.0.0.1:8080`. |
