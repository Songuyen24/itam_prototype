# Mẫu đầu ra prototype — nghiệm thu 26/10/2026

Các file được xuất từ ứng dụng chạy với dữ liệu giả trong PostgreSQL schema `m104_live_20261026`. Không phải dữ liệu doanh nghiệp hay mẫu đã được nghiệp vụ ký duyệt.

- `assets-filtered-en.xlsx`: 23 thiết bị đúng bộ lọc; xuất từ trang 2 vẫn chứa toàn bộ 23 dòng.
- `assets-all-vi.xlsx`: 24 tài sản/gói, nhãn tiếng Việt.
- `alloc-holder-a-en.xlsx`, `alloc-holder-b-en.xlsx`: lần lượt 2 và 3 suất ACTIVE theo người được lọc; toàn gói 10 tổng, 5 ACTIVE, 2 RESERVED, 3 khả dụng.
- `transactions-completed-en.xlsx`: chỉ phiếu COMPLETED trong kỳ ngày hoàn tất, có tên người tạo/người xử lý. Ngày năm 2035 là dữ liệu test chủ ý để tách khỏi dữ liệu khác.
- `handover-prototype.pdf`, `recovery-prototype.pdf`: PDF song ngữ tải từ phiếu hoàn tất thật trên schema workflow riêng; đã render và kiểm tra trang A4, chữ/dấu, lề và nội dung không bị cắt. Chỉ dữ liệu fixture.

Đã kiểm dữ liệu, header, freeze pane, không xuất license key/password/token/cost, đối chiếu SQL và render ảnh 5 workbook. Ảnh render chỉ dùng để rà bố cục, không thay nội dung file và không phải kiểm trong Microsoft Excel. Bộ render có lỗi đọc ô chuỗi rỗng; bước QA thay giá trị ô trong bộ nhớ bằng dữ liệu openpyxl trước render, không lưu đè XLSX. File gốc ở đây là bytes tải từ API.

Hồi quy và giới hạn đầy đủ: `project_sources/docs/task_reports/M10_4_KHEP_NGHIEM_THU.md`. Không dùng các file này để suy ra đã nghiệm thu toàn bộ TC01–TC39 hoặc production.
