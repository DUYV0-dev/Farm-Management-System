# Farm-Management-System
# Hệ Thống Quản Lý Tổng Thể Cho Trang Trại Trồng Trọt

## 1. Giới thiệu dự án (Project Overview)
Dự án được thực hiện nhằm đáp ứng yêu cầu của môn học Quản lý dự án CNTT (chuyên ngành Công nghệ phần mềm) trong thời gian 8 tuần (từ 07/09/2026 đến 01/11/2026) với nguồn nhân lực gồm 5 sinh viên[cite: 1]. 
Mục đích là cung cấp một giải pháp phần mềm toàn diện giúp các trang trại trồng trọt số hóa và tối ưu hóa quy trình quản lý hoạt động canh tác, từ khâu quản lý vườn, mùa vụ, vật tư đến theo dõi tiến độ công việc[cite: 1].

## 2. Kiến trúc và Nền tảng (Architecture & Platforms)
- Hệ thống bao gồm nền tảng Web và Desktop[cite: 1, 2].
- Cả hai nền tảng chia sẻ chung một cơ sở dữ liệu và giao tiếp qua REST API[cite: 1, 2].

## 3. Các tính năng cốt lõi (Core Features)
Hệ thống hỗ trợ các chức năng quản lý toàn diện bao gồm:
- **Quản lý danh mục cốt lõi**: Hỗ trợ quản lý người dùng, trang trại/vườn, cây trồng, mùa vụ, công việc canh tác và vật tư[cite: 1, 2].
- **Tiện ích thao tác**: Cung cấp tính năng tìm kiếm, bộ lọc dữ liệu[cite: 1, 2].
- **Thống kê & Giám sát**: Hiển thị bảng điều khiển (dashboard) tổng quan[cite: 1, 2].
- **Báo cáo**: Tích hợp khả năng xuất báo cáo thống kê dưới định dạng PDF và Excel[cite: 1, 2].

## 4. Phương pháp luận và Quản lý dự án (Methodology)
- **Quy trình phát triển**: Áp dụng Agile Scrum trong 04 Sprint, mỗi Sprint kéo dài 02 tuần[cite: 2].
- **Công cụ quản lý**: Sử dụng Jira để quản lý Epic, backlog, task, bug và tiến độ[cite: 2].
- **Quản lý mã nguồn**: Sử dụng GitHub để quản lý branch, commit và Pull Request[cite: 2].
- **Quy trình thực hiện**: Bao gồm khảo sát và phân tích yêu cầu; thiết kế UI, kiến trúc và cơ sở dữ liệu; phát triển Web, Desktop và API; tích hợp; kiểm thử; triển khai demo; hoàn thiện báo cáo và nghiệm thu[cite: 2].

## 5. Tiêu chí nghiệm thu và Thành công (Project Success Criteria)
Để dự án được đánh giá thành công, cần đạt các yêu cầu sau:
- Hoàn thành và nghiệm thu dự án đúng thời hạn vào ngày 01/11/2026 (sau 04 Sprint)[cite: 1].
- Hệ thống trình diễn thành công toàn bộ các chức năng bắt buộc (Must have)[cite: 1, 2].
- Chất lượng phần mềm đạt tối thiểu 90% tỷ lệ chạy thành công các test case ưu tiên cao[cite: 1, 2].
- Ứng dụng không còn tồn đọng lỗi nghiêm trọng (critical bugs) trong các luồng nghiệp vụ demo chính[cite: 1, 2].
- Chi phí thực hiện kiểm soát trong ngân sách dự kiến là 2.420.000 VNĐ và tuyệt đối không vượt quá ngưỡng tối đa 2.500.000 VNĐ[cite: 1, 2].

## 6. Sản phẩm chuyển giao (Deliverables)
Ngoài mã nguồn hệ thống (Web, Desktop, API) được quản lý và lưu trữ trên GitHub[cite: 1], dự án còn bao gồm:
- Các tài liệu quản lý dự án (business case, charter, team contract, scope statement, WBS, v.v.)[cite: 1].
- Tài liệu khảo sát và phân tích yêu cầu phần mềm[cite: 1].
- Tài liệu thiết kế (Giao diện UI/UX, kiến trúc hệ thống và cơ sở dữ liệu)[cite: 1].
- Báo cáo kiểm thử và danh sách Test case[cite: 1].
- Gói triển khai phần mềm (ứng dụng demo chạy trên máy chủ)[cite: 1].

## 7. Đội ngũ phát triển (Team Members & Roles)
| Vai trò | Họ Tên | Trách nhiệm chính |
| :--- | :--- | :--- |
| **PM/Scrum Master kiêm Integration** | Nguyễn Huỳnh Thanh Phương | Điều phối, Jira/GitHub, tích hợp[cite: 2] |
| **BA/PO** | Hồng Thái Vinh | Yêu cầu, backlog, CSDL[cite: 2] |
| **UI-UX + Frontend** | Trần Văn Lượng | Thiết kế và Frontend Web[cite: 2] |
| **Backend/API + DevOps** | Võ Hà Duy | API, Desktop[cite: 2] |
| **Database + QA/Tester** | Phạm Thanh Sơn | Kiểm thử, duyệt data[cite: 2] |

---
*Ghi chú: Mọi thay đổi lớn về phạm vi, tiến độ hoặc kinh phí của dự án phải được ghi nhận trên Jira, phân tích tác động và được cả nhóm thống nhất[cite: 2].*
