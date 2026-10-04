# Checklist nghiệm thu và minh chứng tại máy Sơn

Mọi dòng dưới đây là ca cần chạy, không phải kết quả đã PASS. Kết quả kiểm thử của môi trường đóng gói được ghi riêng trong VALIDATION.md.

| Mã | Thao tác | Mong đợi | File minh chứng |
|---|---|---|---|
| AUTH-01 | Chạy migration 05 | COMMIT; có auth_session | MC01_Auth_Migration.png |
| AUTH-02 | mvnw clean test package | BUILD SUCCESS, unit tests đạt | MC02_Build_Tests.png |
| AUTH-03 | Seed bằng profile seed | SEED SUCCESS | MC03_Seed.png |
| AUTH-04 | Login sai tên/mật khẩu hoặc INACTIVE | Cùng lỗi chung 401 | MC04_Login_Error.png |
| AUTH-05 | Login đúng qua UI | Tên tài khoản và role USER, không hiện token | MC05_Login_Success.png |
| AUTH-06 | Chạy Test-Login.ps1 | Tất cả PASS | MC_API_Test_Results.csv |
| AUTH-07 | Logout qua UI | Quay lại Login, phiên thu hồi | MC07_Logout.png |
| AUTH-08 | Khóa rồi mở tài khoản đang đăng nhập | Token cũ vẫn 401; login mới thành công sau mở | MC08_Lock_Reactivation.png |
| AUTH-09 | Kiểm tra hash | is_argon2id=true; không chụp toàn hash | MC09_Password_Hash.png |

## Kiểm tra hash không lộ hash
Trong pgAdmin:
```sql
SELECT user_id, username, status,
       password_hash LIKE '$argon2id$%' AS is_argon2id
FROM public.app_user
WHERE username = 'login.demo';
```
Thay login.demo bằng tên đã seed.

## Khóa/mở để kiểm thử
Chỉ thực hiện với tài khoản thử vừa tạo; không dùng tài khoản quản lý Farm thật (task này chưa có service kiểm tra người quản lý cuối cùng).
1. Login UI, giữ nguyên trang, bấm Kiểm tra phiên -> thành công.
2. pgAdmin postgres chạy UPDATE public.app_user SET status='INACTIVE' WHERE username='login.demo';
3. Bấm Kiểm tra phiên -> 401, UI trở về Login. Login lại khi INACTIVE -> 401 thông báo chung.
4. Để kiểm chứng token cũ không phục hồi sau mở: một lần thử khác, login trước khi khóa; khóa rồi mở bằng hai câu UPDATE riêng đã commit, giữ nguyên trang chưa gọi API trong lúc khóa; sau mở bấm Kiểm tra phiên -> vẫn 401.
5. UPDATE public.app_user SET status='ACTIVE' WHERE username='login.demo'; sau đó login mới thành công.
6. Dọn: bảo đảm tài khoản test ACTIVE sau thử.

Không đưa token/mật khẩu/hash/khóa JWT vào ảnh hoặc comment Jira. Chỉ chụp màn hình UI và kết quả PASS/status.

## Điều kiện đóng task
Các ca Login/Logout/khóa/token/validation PASS tại PostgreSQL 18.4; ảnh và CSV đính kèm; code được nhóm review; các khác biệt ID/role/UI trong CONTRACT_AND_DECISIONS.md được xử lý hoặc chấp thuận có ghi nhận. Không dùng kết quả đóng gói để thay minh chứng trên môi trường dự án.
