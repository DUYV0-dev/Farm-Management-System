# Kết quả kiểm tra bản đóng gói

- Build/package đã thành công với Java 21 và Spring Boot 4.1.1.
- 10 unit test bảo mật PASS, 0 failures/errors. Bản kết quả Surefire được kèm theo.
- Test context mặc định được skip; chỉ bật khi RUN_DB_CONTEXT_TEST=true và Database đã chuẩn bị.
- Migration schema v1 + auth_session đã chạy trên PostgreSQL 18.3 nhúng (PGlite 0.5.8).
- Kiểm tra HTTP đã PASS: seed và hash Argon2id; trang Login trả 200; thiếu token 401; sai mật khẩu 401; trường lạ/kiểu JSON sai 422; JSON hỏng 400; media type sai 415; login 200; requestId/no-store; không trả hash; expiresIn 86400; me 200; token sửa 401; logout 204; token đã logout 401; phiên khác vẫn hợp lệ; khóa tài khoản từ chối token/login; mở lại không phục hồi token cũ; thu hồi role hệ thống có hiệu lực; đường dẫn nghiệp vụ ngoài scope 403; FK phiên tồn tại.

## Giới hạn bằng chứng

PGlite là PostgreSQL nhúng, không thay thế PostgreSQL 18.4 trên Windows của người dùng. Kiểm thử tích hợp dùng tài khoản postgres và pool 1, prepareThreshold=0 để tương thích bộ giả lập kết nối; chưa kiểm chứng native authentication, concurrency hay quyền runtime farm_app trong môi trường này. Cấu hình PostgreSQL của sản phẩm không thay đổi theo các tùy chỉnh test này.

Trình duyệt kiểm thử không tải được executable, nên chưa hoàn tất test tương tác hoặc kiểm tra hình ảnh UI. Không có screenshot UI được giả lập làm minh chứng. Script PowerShell đã soạn nhưng chưa chạy trên Windows. Người dùng cần thực hiện README_VI.md và TEST_CHECKLIST.md để hoàn tất nghiệm thu.

Các kết quả này thuộc môi trường đóng gói; không phải kết quả đã chạy trên máy Sơn. API contract theo ERD có khác biệt UUID/role/UI cần nhóm review như CONTRACT_AND_DECISIONS.md.
