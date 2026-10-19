# Database Backup & Recovery Test Report

Kết quả: **PASS — 12/12 kiểm tra**, hoàn tất lúc **09:03:37 ngày 19/10/2026 (UTC+07)**.
Dữ liệu sau khôi phục khớp baseline trên **21 bảng và 14 sequence**, không có sai lệch.
Kết quả này áp dụng cho bộ dữ liệu tổng hợp trên PostgreSQL riêng; chưa xác nhận
khả năng phục hồi hoặc hiệu năng trên môi trường production.

## 1. Mục tiêu và yêu cầu sao lưu

| Hạng mục | Yêu cầu / phạm vi |
|---|---|
| Dữ liệu | Schema ứng dụng, dữ liệu bảng, sequence, large object, quyền và chủ sở hữu đối tượng |
| Lịch sao lưu đề xuất | Hằng ngày và trước khi nâng cấp database; cần lập lịch bên ngoài công cụ |
| RPO mục tiêu | 24 giờ |
| RTO mục tiêu | 60 phút cho khôi phục và đối chiếu tự động |
| Thời hạn lưu đề xuất | 30 ngày; không có tác vụ tự động xóa bản sao |
| Định dạng | PostgreSQL custom archive `database.dump`, kèm `manifest.json` |
| Toàn vẹn tệp | Kiểm tra kích thước, SHA-256, danh mục archive và đọc toàn bộ archive |
| Tính nhất quán | Tạm dừng ghi dữ liệu và DDL; dùng chung exported MVCC snapshot cho dump và baseline |
| Đích khôi phục | Database mới; từ chối database đã tồn tại |
| Bảo mật lưu trữ | Thư mục giới hạn quyền truy cập, mã hóa và có bản sao ở vị trí khác; cấu hình ở môi trường triển khai |

Archive có thể chứa tài khoản ứng dụng, password hash và lịch sử nghiệp vụ. Không lưu
archive thật vào Git. SHA-256 phát hiện hỏng dữ liệu, không xác thực nguồn gốc nếu
kẻ khác có thể sửa cả archive và manifest; chỉ khôi phục archive từ nguồn tin cậy.

Role/mật khẩu PostgreSQL cấp cluster, tablespace, cấu hình server/database, quyền
cấp database, JWT secret và tệp bên ngoài phải được quản lý riêng. Foreign table
chưa được hỗ trợ và sẽ làm lệnh backup từ chối thực hiện.

## 2. Môi trường và bằng chứng

- PostgreSQL 18.4 trên Windows, chỉ lắng nghe `127.0.0.1:59710` cho lần chạy này.
- Cluster riêng được tạo bởi `scripts/Test-DatabaseRecovery.py`; không truy cập database vận hành.
- Database nguồn: `recovery_source`; database khôi phục: `recovery_restored`.
- Schema: các migration `00`, `05`, `06`, `07`, `08`, `09`, `10`, `11a`, `12`, `13`.
- Dữ liệu: [fixture tổng hợp](../src/test/resources/database-recovery-fixture.sql), gồm hai trang trại,
  tài khoản/thành viên, lô đất, cây trồng, mùa vụ, vật tư, tồn kho, sử dụng vật tư, chi phí;
  thêm dữ liệu thử Unicode, xuống dòng/tab, JSONB, BYTEA, NULL, timestamp, số thập phân và large object.
- Mã lần chạy: `f5c1821796314411b8f9532d863b8a80`.
- Máy chủ kiểm thử đã dừng sau khi hoàn tất; log xác nhận `database system is shut down`.

Bằng chứng được lưu cố định trong
[Evidence/Database_Backup_Recovery/2026-10-19-f5c18217](../Evidence/Database_Backup_Recovery/2026-10-19-f5c18217):

| Tệp | Nội dung |
|---|---|
| [test-report.json](../Evidence/Database_Backup_Recovery/2026-10-19-f5c18217/test-report.json) | Kết quả máy đọc được của toàn bộ 12 kiểm tra |
| [test-report.md](../Evidence/Database_Backup_Recovery/2026-10-19-f5c18217/test-report.md) | Bảng kết quả tự sinh |
| [backup-report.json](../Evidence/Database_Backup_Recovery/2026-10-19-f5c18217/backup-report.json) | Thời gian sao lưu và checksum |
| [restore-report.json](../Evidence/Database_Backup_Recovery/2026-10-19-f5c18217/restore-report.json) | Khôi phục, đối chiếu, đánh giá RPO/RTO của lần thử |
| [consistency-report.json](../Evidence/Database_Backup_Recovery/2026-10-19-f5c18217/consistency-report.json) | Kết quả đối chiếu độc lập sau khôi phục |
| [postgres.log](../Evidence/Database_Backup_Recovery/2026-10-19-f5c18217/postgres.log) | Phiên bản, khởi động, lỗi được chủ động tạo và dừng cluster |

Các đường dẫn `.test-data` trong JSON là đường dẫn lịch sử của lần chạy, không phải
địa chỉ lưu trữ production. Archive thử nằm trong thư mục đó và không được đưa vào bộ bằng chứng Git.

## 3. Quy trình đã thực hiện

1. Tạo cluster PostgreSQL riêng, áp dụng schema hiện hành và nạp fixture tổng hợp.
2. Chạy `backup` với xác nhận `--writers-paused`, tạo archive và baseline từ cùng snapshot.
   Các ứng dụng ghi dữ liệu không được khởi động trong cluster thử.
3. Đọc danh mục và toàn bộ archive; ghi SHA-256, kích thước và manifest sau khi hoàn tất.
4. Chạy `check-backup` với cổng kết nối đặt thành `1`: xác minh archive không cần database.
5. Khôi phục vào database mới bằng `pg_restore --exit-on-error --single-transaction`,
   giữ encoding và locale provider của nguồn.
6. Đối chiếu tự động trong lệnh restore, rồi chạy `verify` độc lập.
7. Kiểm tra truy vấn nghiệp vụ và RLS; chủ động tạo lỗi dữ liệu, sequence, schema,
   archive và thiếu role để kiểm tra phát hiện sai lệch, bảo vệ dữ liệu và rollback.
8. Ghi báo cáo, dừng cluster thử và lưu bằng chứng của lần chạy đạt.

## 4. Kết quả kiểm tra

| ID | Tình huống và kết quả quan sát | Kết quả |
|---|---|---|
| BR-01 | Sao lưu database có dữ liệu trang trại, xuất archive và manifest hoàn tất | PASS |
| BR-02 | Xác minh manifest, kích thước, SHA-256, danh mục và toàn bộ archive khi không có kết nối DB | PASS |
| BR-03 | Khôi phục vào database mới; 21 bảng, 14 sequence khớp baseline | PASS |
| BR-04 | Chạy đối chiếu độc lập sau khôi phục; `differences: []` | PASS |
| BR-05 | Truy vấn giữ đúng tiếng Việt, hai trang trại, tồn kho `125.375`, chi phí `123456.78`, quan hệ mùa vụ/trang trại, BYTEA/NULL, large object 5.000 byte; RLS giới hạn chủ trang trại thứ nhất thấy một trang trại | PASS |
| BR-06 | Từ chối khôi phục vào database đã tồn tại; đối chiếu lại xác nhận dữ liệu không đổi | PASS |
| BR-07 | Sửa một byte archive; checksum không khớp, từ chối trước khi tạo database | PASS |
| BR-08 | Sửa giá trị trong một bản ghi, giữ nguyên số dòng; phát hiện `recovery_probe (data)` | PASS |
| BR-09 | Tăng sequence identity; phát hiện `recovery_probe_id_seq (state)` | PASS |
| BR-10 | Thêm CHECK thực sự khác; phát hiện `recovery_probe (constraints)` | PASS |
| BR-11 | Xóa bảng thử; phát hiện thiếu bảng và sequence thuộc bảng | PASS |
| BR-12 | Cố ý thiếu role sở hữu khi restore; lệnh thất bại, toàn bộ bảng/sequence trong schema public của đích được rollback; database đích trống vẫn được giữ để kiểm tra | PASS |

Trong các tình huống lỗi có chủ đích, lệnh trả `status: FAIL` là hành vi mong đợi.
Trạng thái **PASS của ca kiểm tra** xác nhận lỗi đó đã được phát hiện hoặc ngăn chặn đúng.

## 5. Chỉ số và tính nhất quán

| Chỉ số | Giá trị đo được |
|---|---|
| Toàn bộ bộ kiểm thử | 28,589 giây |
| Sao lưu và tạo baseline | 2,584 giây |
| Đọc/xác minh archive độc lập | 0,040 giây |
| Khôi phục và đối chiếu tự động | 3,269 giây |
| Kích thước archive | 101.369 byte |
| Bảng / sequence được đối chiếu | 21 / 14 |
| Sai lệch ở database ngay sau khôi phục | 0 |
| RTO trong lần thử | `rto_met: true`, so với mục tiêu 60 phút |
| Tuổi bản sao khi hoàn tất khôi phục | 6,063 giây; `rpo_met: true`, so với mục tiêu 24 giờ |

SHA-256 của archive thử:

```text
4c55963df901beddb51463b1ed9db2359cbe5389379627fecf7631b3aae52d79
```

Phép đối chiếu so sánh số dòng và SHA-256 của dữ liệu đã sắp xếp theo biểu diễn JSON,
cấu trúc cột, ràng buộc, cấu hình/trạng thái sequence, dữ liệu và metadata large object.
Các trường hợp dữ liệu thay đổi nhưng giữ nguyên số dòng vẫn được phát hiện.

## 6. Vấn đề đã khắc phục trong quá trình kiểm thử

- Sandbox Windows chặn việc khởi động `pg_ctl`; lần chạy đạt được thực hiện với
  quyền chạy cluster thử ngoài sandbox đã được cho phép.
- Tiến trình PostgreSQL trên Windows có thể giữ pipe kế thừa làm chương trình chờ
  vô hạn. Bộ kiểm thử chuyển stdout/stderr của `pg_ctl` sang tệp, thêm giới hạn chờ;
  tiến trình và cluster bị treo ở lần trước đã được dừng.
- PostgreSQL 18 có thể chuyển cast `varchar[]` sang `text[]` thành cast từng phần tử
  khi đọc lại biểu thức CHECK, làm phép so sánh chuỗi báo sai lệch giả. Mã đối chiếu
  chuẩn hóa riêng dạng tương đương này bằng token SQL, giữ nguyên nội dung literal.
  BR-10 xác nhận ràng buộc thực sự thay đổi vẫn bị phát hiện.

## 7. Giới hạn và công việc vận hành còn lại

Kết quả xác nhận khả năng sao lưu/khôi phục và phát hiện các sai lệch nêu trên với
dữ liệu tổng hợp nhỏ. Chưa kiểm chứng tải dữ liệu production, đủ dung lượng đĩa,
mạng chậm, sự cố ổ đĩa/máy chủ, phục hồi sang máy khác, lịch sao lưu, mã hóa,
sao lưu ngoài máy hoặc chính sách lưu 30 ngày. Thời gian đo được không bảo đảm RTO production.

Chỉ số RPO hiện phản ánh tuổi bản sao tại thời điểm hoàn tất khôi phục thử, không
chứng minh lịch sao lưu thực tế hoặc lượng giao dịch mất khi có sự cố. Chưa có WAL
archiving/PITR để phục hồi tới một thời điểm nằm giữa hai bản sao toàn phần.

Cờ `--writers-paused` là xác nhận của người vận hành; công cụ không tự chặn ứng dụng
ghi dữ liệu. Phải giữ việc tạm dừng trong suốt backup để sequence và DDL nhất quán.
Inventory chưa đối chiếu toàn bộ định nghĩa view, hàm, trigger, index, mọi quyền và
mọi chính sách RLS; archive chứa các đối tượng được pg_dump hỗ trợ, nhưng phép kiểm
tra quyền trực tiếp hiện chỉ bao phủ tình huống RLS trong BR-05.

## 8. Chạy lại

Từ thư mục gốc dự án, với Python 3.10+ và PostgreSQL 18 client/server tools:

```powershell
python scripts/Test-DatabaseRecovery.py
```

Đường dẫn PostgreSQL khác mặc định:

```powershell
python scripts/Test-DatabaseRecovery.py --postgres-bin 'C:\Program Files\PostgreSQL\18\bin'
```

Mỗi lần chạy tạo cluster, cổng và thư mục kết quả riêng dưới `.test-data`, không dùng
database hiện có. Chạy bằng môi trường cho phép PostgreSQL khởi động tiến trình.
Lệnh in đường dẫn báo cáo mới; bộ bằng chứng được liên kết trong báo cáo này là
bản cố định của lần chạy ngày 19/10/2026 và không bị thay thế bởi lần chạy tiếp theo.
