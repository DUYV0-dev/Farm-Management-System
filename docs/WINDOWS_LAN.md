# Cài đặt và vận hành Windows LAN

> Quy trình launcher bên dưới là lịch sử. Với code hiện hành, dùng [RUNBOOK](RUNBOOK.md)
> và [ARCHITECTURE](ARCHITECTURE.md). Không dùng Update-Lan/Backup-Lan để điều khiển cụm
> đang chạy bằng Windows Service. Giao diện và luồng Run IntelliJ được giữ nguyên.

> Cập nhật 31/10/2026: Run main trong IntelliJ dùng một `application.properties`
> và biến môi trường Windows User; xem [ENVIRONMENT](ENVIRONMENT.md).
> Web vẫn là HTTPS 8443, PostgreSQL 55433 chạy bằng Windows Service.
> Các hướng dẫn launcher bên dưới chỉ là lịch sử.

**Cách vận hành mới:** xem [cấu hình database thống nhất, Windows Service và sao lưu cho IntelliJ](DATABASE_CONFIGURATION.md).
Tác vụ database được thay bằng service sau khi chạy `Set-LocalDatabase.ps1 -Apply`.

**Bản cài ngày 24/10/2026 đang ở chế độ Local theo lựa chọn người dùng:**
`https://localhost:8443/`, chỉ nghe `127.0.0.1`. Mạng Windows Public được giữ nguyên.
Tài liệu dưới đây mô tả cả khả năng triển khai LAN khi được cấu hình cho phép.

Dùng Java 21, PostgreSQL 18 và trình duyệt. Giao diện đóng gói trong JAR. Database riêng ở `127.0.0.1:55433`; HTTPS cổng 8443. Database hiện có ở 5432 không bị nâng cấp hoặc ghi đè.

## Xác minh và cài mới

Từ repository:

```powershell
.\scripts\Test-Production.ps1 -Browser
.\scripts\Install-Lan.ps1 -Autostart -OpenFirewall
```

Bộ kiểm thử dùng PostgreSQL tạm ở 55432, cài migration, chạy API/integration, Chrome, sao lưu/khôi phục và đối chiếu dữ liệu. Log ở `.test-data/production-*`.

Cài vào `%LOCALAPPDATA%\FarmManagement\production`; từ chối thư mục đã tồn tại. Mở firewall cần quyền quản trị Windows. Chỉ mở HTTPS trên mạng `Private` từ `LocalSubnet`; PostgreSQL chỉ nghe loopback. Mạng Windows Public không nhận kết nối qua quy tắc này.

Mật khẩu database owner, runtime, JWT và keystore được tạo ngẫu nhiên, mã hóa DPAPI theo tài khoản Windows cài đặt. Không có mật khẩu mặc định. `farm_app` không sở hữu schema, không là superuser hoặc bypass RLS. Owner chỉ dùng cho migration/backup.

Chạy `Start-Lan.ps1` trong thư mục cài, hoặc Scheduled Task `FarmManagement-LAN`. Task tự chạy khi tài khoản Windows này đăng nhập. Đây không phải Windows Service trước màn hình đăng nhập: máy phải bật, tài khoản phải đăng nhập, Windows không ngủ trong giờ sử dụng.

Dùng `https://localhost:8443/` trên máy chủ hoặc URL IPv4 installer in ra trên máy khác. Nên giữ IP LAN cố định/DHCP reservation. Khi đổi IP, cần cấp lại chứng chỉ với SAN phù hợp.

## Tin cậy HTTPS

`farm-lan-public.crt` là chứng chỉ công khai để chép cho máy khách. Kiểm tra fingerprint trực tiếp với máy chủ trước khi nhập vào kho chứng chỉ tin cậy:

```powershell
certutil -hashfile "$env:LOCALAPPDATA\FarmManagement\production\farm-lan-public.crt" SHA256
```

Windows: nhập chứng chỉ đã kiểm tra vào Trusted Root Certification Authorities của người dùng. Thiết bị khác cần cấu hình tương ứng. Chứng chỉ có hạn 365 ngày, cần gia hạn trước khi hết hạn. Không chia sẻ `lan.p12`, file `.dpapi` hoặc `pgdata`; không tắt xác minh HTTPS.

## Luồng sử dụng

1. Đăng ký tài khoản cá nhân → tự đăng nhập vào trang tổng; không có tài khoản hoặc dữ liệu canh tác mẫu.
2. Tạo trang trại từ trang tổng → vào trang trại để quản lý. Nhân viên có thể thêm sau, không bắt buộc trong thiết lập.
3. Tạo khu đất, cây trồng, mùa vụ; cấu hình ngày/đơn vị trong Thu hoạch.
4. Tạo vật tư/kho; nhập tồn đầu kỳ bằng phiếu kho hoặc nhận hàng từ đơn mua.
5. Phân công, theo dõi lịch và ghi tiến độ/kết quả công việc.
6. Ghi phân bón/thuốc và chọn kho xuất: nhật ký và tồn kho thay đổi cùng giao dịch.
7. Ghi thu hoạch để tạo lô nông sản; quản lý bảo quản và bán từ lượng khả dụng.
8. Trang tổng → Bán sản phẩm → thiết lập công khai, giá của lô; khách đặt tại `/shop/`. Chủ xác nhận để tạo đơn bán và trừ kho.
9. Đối chiếu mùa vụ, chăm sóc, vật tư, thu hoạch bằng báo cáo/truy xuất. Báo cáo tài khoản tổng hợp các trang trại sở hữu.

Chi tiết giao diện và cửa hàng: [ACCOUNT_PORTAL.md](ACCOUNT_PORTAL.md). Cửa hàng hiện chỉ truy cập trên máy này theo cấu hình Local.

## Sao lưu và phục hồi

Task `FarmManagement-Backup` chạy 02:00 khi tài khoản cài đặt còn đăng nhập. Có thể chạy `Backup-Lan.ps1` thủ công. Script dừng ứng dụng, chờ hết ghi, tạo archive/baseline, kiểm tra toàn vẹn rồi mở lại. Khi backup lỗi, giữ ứng dụng dừng để kiểm tra.

Mặc định backup nằm cùng thư mục cài, chưa bảo vệ khỏi hỏng ổ. Cần sao chép sang ổ/máy khác có mã hóa và giới hạn quyền. Không tự xóa backup. Lưu riêng cấu hình, releases và bí mật theo quy trình quản trị; DPAPI không tự chuyển sang máy/tài khoản khác.

Khôi phục bằng `database_recovery.py restore` vào database **mới**, rồi `verify`. Kiểm tra ứng dụng với database phục hồi trước khi đổi cấu hình. Role/mật khẩu phải được cấp lại riêng khi đổi cluster/máy. Không phục hồi đè database đang phục vụ.

## Nâng cấp

```powershell
& "$env:LOCALAPPDATA\FarmManagement\production\Update-Lan.ps1" `
 -ReleaseJar '.\target\backend-0.0.1-SNAPSHOT.jar' `
 -VerificationFile '.\target\production-verification.json'
```

Script kiểm tra checksum, sao lưu, migrate và chuyển JAR. Giữ JAR/cấu hình trước. Không tự hạ schema; quay lại bằng JAR tương thích hoặc khôi phục backup vào database mới.

## Database cũ

Không chạy cài mới lên dữ liệu hiện có. `DatabaseMigrations upgrade-legacy` chỉ dùng trên bản sao đã phục hồi và kiểm tra, với `ALLOW_LEGACY_BASELINE=true`. Công cụ kiểm tra nền auth trước khi baseline 3 rồi chạy các bản tiếp theo. ERD-v1 `material.unit_id` bị từ chối, cần chuyển đổi theo tài liệu riêng. Supplier/customer cũ thiếu tenant_farm_id làm migration dừng để quản trị viên xác nhận quyền sở hữu; không tự gán dữ liệu cũ cho người đăng ký mới.
