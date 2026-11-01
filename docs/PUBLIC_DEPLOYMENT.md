# Chuẩn bị triển khai public Internet

Bộ cấu hình ở `deploy/linux/` dành cho máy chủ Linux có Docker Engine và Docker
Compose. Việc sửa/kiểm chứng repository không khởi chạy website public hoặc thay
đổi DNS/firewall. Domain thật và máy chủ được cấu hình ở bước triển khai sau.

## Những thay đổi đã áp dụng

- Datasource đọc `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; không đóng gói mật khẩu local.
- PDF dùng Noto Sans có tiếng Việt và giấy phép OFL, nằm ngay trong JAR.
- Mặc định bỏ qua forwarded headers. Bộ Docker bật native và chỉ tin IP của Caddy.
- PostgreSQL, migration và web là các service riêng; chỉ proxy publish cổng 80/443.
- App chạy bằng user không phải root; database dùng volume bền vững.
- Release kiểm tra các resource đóng gói và từ chối credential dạng literal.

## Cấu trúc

```text
Internet -> Caddy (HTTPS, 80/443) -> Spring Boot (8080) -> PostgreSQL (5432)
                                             ^
                                      migrate hoàn tất trước
```

Frontend và API cùng domain. Không cần build/deploy frontend riêng. App không
nhận mật khẩu owner của database; chỉ service migration nhận thông tin đó.
PostgreSQL 18 dùng volume gắn tại `/var/lib/postgresql`.

## Chuẩn bị bản build

Trên máy phát triển Windows, chạy pipeline kiểm chứng và đóng gói:

```powershell
.\scripts\Test-Production.ps1 -Browser
.\scripts\Build-Release.ps1 -UseVerifiedBuild
```

Chỉ sử dụng release mới được kiểm chứng sau thay đổi cấu hình. Các JAR/ZIP cũ
đã chứa mật khẩu local không được phát hành. Nếu đã chia sẻ một bản cũ, đổi mật
khẩu tương ứng; bỏ mật khẩu khỏi bản build mới không thu hồi bản đã chia sẻ.

Gói ZIP mới chứa `backend.jar`, manifest, `.dockerignore`, `deploy/linux/` và tài
liệu. Giải nén nguyên cấu trúc. Nếu dùng repository, phải có đủ source đã commit.

## Thiết lập môi trường đích (chỉ thực hiện khi bắt đầu deploy)

Trong thư mục `deploy/linux/`, sao chép `.env.example` thành `.env`, cấp quyền đọc
cho người quản trị (`chmod 600 .env`) và điền:

- `APP_DOMAIN`: tên miền thật, không gồm scheme/path. DNS A/AAAA phải trỏ đúng VPS.
- `ACME_EMAIL`: email nhận thông báo chứng chỉ.
- `DB_OWNER_PASSWORD` và `DB_PASSWORD`: hai mật khẩu khác nhau, tạo bằng
  `openssl rand -hex 32`, không dùng lại mật khẩu local.
- `JWT_SECRET`: tạo một lần bằng `openssl rand -base64 32`, giữ ổn định khi cập nhật.
- `JAR_FILE=backend.jar` nếu dùng ZIP release; giữ giá trị mặc định nếu build từ repo.
- `RELEASE_TAG`: tên phiên bản riêng để giữ image cũ khi cần phục hồi.

Không dùng `source .env`; Docker Compose đọc file này trực tiếp. `.env` không được
commit và không được COPY vào image. Người có quyền Docker có thể đọc môi trường
container, vì vậy chỉ cấp quyền Docker cho người quản trị máy chủ.

Kiểm tra cấu hình trước khi chạy (không in bí mật):

```sh
docker compose config --quiet
docker compose build
```

Khi đã đến bước triển khai, mở 80/TCP, 443/TCP và tùy chọn 443/UDP; giữ PostgreSQL
và cổng 8080 không public. Caddy xin/gia hạn chứng chỉ và chuyển HTTP sang HTTPS.

```sh
docker compose up -d
docker compose ps -a
docker compose logs --tail 100 migrate app
```

Compose chờ database healthy, chạy migration bằng owner và cấp mật khẩu role
`farm_app`, rồi mới chạy app; Caddy chờ app healthy. Migration chạy lại an toàn
trên schema đã được Flyway quản lý. Không dùng quy trình này để tự chuyển đổi một
database legacy không có lịch sử Flyway.

Truy cập `https://<domain>/actuator/health` phải có `status=UP`. Kiểm tra đăng ký,
đăng nhập, hai người dùng có hạn mức riêng, đặt hàng và xuất PDF qua domain thật.
Kết quả test local chưa chứng minh DNS/ACME/firewall của môi trường đích.

Kiểm tra riêng image Linux trên máy phát triển (không mở cổng, không dùng database):

```powershell
docker build -f deploy/linux/Dockerfile -t farm-management:deployment-check .
.\scripts\testing\Verify-LinuxImage.ps1
```

Smoke test dùng JAR bên trong image, xuất/đọc PDF tiếng Việt với font classpath,
và yêu cầu Linux/user không phải root. Container bị tắt mạng và filesystem chỉ đọc.

## IP người dùng và reverse proxy

Caddy có IP cố định `172.30.40.10` trong mạng `172.30.40.0/24`. Tomcat chỉ tin IP
này, và Caddy ghi đè `X-Forwarded-For/Proto/Host` từ socket kết nối thực. Header giả
từ client không thể đổi IP được dùng bởi `LoginThrottle`. Hạn mức hiện là 30
POST/phút/IP, dùng chung cho đăng nhập, đăng ký và tạo đơn; người cùng NAT có thể
dùng chung hạn mức. Hạn mức nằm trong RAM từng instance, phù hợp bộ triển khai
một instance này.

Nếu subnet trùng mạng hiện tại, đổi subnet, địa chỉ Caddy và `TRUSTED_PROXY_REGEX`
cùng lúc. Không đặt regex `.*`. Không bật `framework` để tin mọi header client.
Nếu thêm CDN/load balancer trước Caddy, cần thiết kế lại danh sách proxy tin cậy;
bộ này mặc định Caddy là proxy đầu tiên nhận kết nối Internet.

## Dữ liệu và cập nhật

Các volume giữ dữ liệu PostgreSQL, log app và chứng chỉ Caddy qua restart/rebuild.
`docker compose down` giữ volume; không chạy `down -v` trên dữ liệu vận hành.
Thay `DB_OWNER_PASSWORD` trong `.env` không tự đổi mật khẩu của database đã khởi tạo.
Không xoay mật khẩu bằng cách xóa volume.

Trước cập nhật: tạo backup bằng `pg_dump -Fc`, kiểm tra và lưu thêm ngoài VPS;
dừng app/writer, build image release mới, chạy lại migrate và validate, rồi khởi
động app/proxy. Giữ release/image cũ. Nếu migration đã thay schema, chỉ đổi image
không đảm bảo rollback an toàn: cần đánh giá tương thích hoặc phục hồi backup
vào database mới. Kiểm chứng restore định kỳ bằng công cụ recovery trong `scripts/`.

Ví dụ bước migrate/validate khi app đã dừng, từ thư mục `deploy/linux/`:

```sh
docker compose run --rm --no-deps migrate migrate
docker compose run --rm --no-deps migrate validate
```

Không tự chuyển dữ liệu local sang VPS trong lần chuẩn bị này. Chọn backup/restore
hoặc cài trống theo yêu cầu sử dụng ở bước triển khai.

## Tài liệu chính thức

- [Spring Boot: web server và proxy](https://docs.spring.io/spring-boot/how-to/webserver.html)
- [Caddy reverse proxy](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy)
- [Compose startup order](https://docs.docker.com/compose/how-tos/startup-order/)
- [PostgreSQL official image](https://hub.docker.com/_/postgres)
- [Noto fonts và giấy phép](https://github.com/notofonts/noto-fonts)
