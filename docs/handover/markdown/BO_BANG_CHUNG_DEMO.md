# Bộ bằng chứng demo thực tế — 13/11/2026

## Phạm vi và nguồn dữ liệu

Bundle: `C:\Users\Admin\Downloads\ITAM\itam_demo_evidence_20261113`.

Dữ liệu duy nhất của lần chạy này mang tiền tố `DEMO-20261113`, được tạo bằng API hiện hành trong schema PostgreSQL riêng `demo_evidence_20261113` của `itam_db`; storage riêng trong bundle. Không dùng dữ liệu trong schema public. Bộ Excel đề xuất cũ không phải dữ liệu đã nạp của lần chạy này. Flyway đã áp dụng 40 migration; backend 8080, frontend 5173. Mọi tài sản/chứng từ là dữ liệu mô phỏng.

- `DEMO-20261113-LAP-1`: ID39, serial SN-DEMO-20261113-1, laptop tồn đầu kỳ, mua 15/01/2020, 20.000.000 đồng.
- `DEMO-20261113-LAP-2`: ID40, serial SN-DEMO-20261113-2, cùng thông số nền trên.
- `DEMO-20261113-NEW`: ID59, serial SN-DEMO-20261113-NEW, laptop nhập mới ngày 13/11/2026, 20.000.000 đồng.
- Kho `DEMO-20261113-KHO` ID38. Người nhận/trả user01 ID28. ADMIN ID27, IT ID29, PUR ID30; không suy diễn ID seed từ môi trường khác.

## Mã phiếu và kết quả đã chạy

1. Bàn giao ID41, `HO-165d477b-ea58-471c-93f6-606ef227d5c3`: ADMIN tạo, gửi, xác nhận LAP-1 → PARTIAL (1 hoàn tất/1 chờ), xác nhận LAP-2 → COMPLETED. Có JSON từng bước và ảnh UI PARTIAL.
2. Thu hồi ID48, `RC-a39a2d92-56f7-4f10-9826-8c7f7d36a857`: ADMIN Smart Check, tạo/gửi, thu LAP-1 → PARTIAL, thu LAP-2 → COMPLETED. Có ảnh UI PARTIAL và lịch sử sau đăng nhập lại bằng IT.
3. Thanh lý ID53, `DI-6075714a-d72f-45ce-ba5f-ad7d01f371cc`: IT tạo, ADMIN duyệt cả hai nhóm → APPROVED, hai tài sản vẫn IN_STOCK. IT thực hiện LAP-1 → PARTIAL, LAP-2 → COMPLETED; cả hai mới chuyển RETIRED. Ngày thực hiện 13/11/2026, chứng từ ID57.
4. Nhập kho ID58, `IMP-fd375e7a-67b3-4093-9b68-4d02e1ab4f88`: PUR tạo/thêm NEW/gửi; IT duyệt → COMPLETED, NEW IN_STOCK.

Chứng từ `disposal-evidence-simulated.pdf` là tài liệu đầu vào tự tạo, ghi rõ SIMULATED DEMO EVIDENCE, không phải bằng chứng thanh lý ngoài đời. Bốn file `*-completed.pdf` là đầu ra thật tải từ API ứng dụng.

## Đối soát và bằng chứng

- `reconciliation.json`: PASS kiểm tra bằng assert API/Excel; `reconcile.py` chứa kiểm tra tái chạy.
- `assets-final.xlsx`: 3 dòng, đúng 3 mã/ID; LAP-1 và LAP-2 Đã thanh lý, NEW Trong kho.
- `transactions-final.xlsx`: 4 dòng, đúng mã phiếu, đều Hoàn tất. Bàn giao/thu hồi/thanh lý mỗi phiếu 2 nhóm đã thực hiện, 0 chờ, 0 được duyệt chờ thực hiện. Nhập kho không sử dụng nhóm tiến độ (0).
- `allocations-final.xlsx`: 0 dòng dữ liệu; đây là bộ không có license, không phải chứng minh luồng license.
- `*-at-recovery-partial.xlsx`: snapshot báo cáo khi thu hồi mới xong một nhóm.
- Bốn PDF cuối đã render và xem trang 1 (mỗi PDF một trang), đối chiếu mã phiếu và tài sản, không thấy cắt chữ. PDF dạng raster nên trích text tự động rỗng; không dùng text rỗng để kết luận nội dung thiếu. Các PNG `*-completed-page1.png` lưu hình render.
- `screenshots/handover-partial-admin.png`, `recovery-partial-admin.png`, `disposal-approved-admin.png`, `dashboard-final-it.png`, `recovery-history-after-relogin-it.png`: ảnh giao diện thật do agent chính chụp. Thao tác ghi nghiệp vụ được thực hiện qua API, không tuyên bố đã thao tác toàn bộ bằng UI.
- `checksums.sha256`: SHA-256 các artifact tĩnh, gồm ảnh; loại logs đang chạy và chính file checksum.

## Kết quả chưa đạt, giới hạn và chưa kiểm tra

- H01: phiếu thu hồi hiện trong lịch sử ADMIN và sau đăng nhập lại IT. Reload đã chuyển về đăng nhập; không kết luận phiên đăng nhập được giữ qua reload, không kết luận bao phủ mọi vai trò.
- UI chi tiết thanh lý hiển thị người yêu cầu ADMIN trong khi danh sách/API nguồn tạo là IT; ghi nhận sai khác để điều tra, chưa sửa.
- Gọi tái phát hành PDF khi bàn giao PARTIAL bị từ chối `PUBLICATION_NOT_AVAILABLE`; PDF thành công sau COMPLETED. Không có PDF PARTIAL.
- Upload multipart mặc định không đúng MIME bị từ chối `DOCUMENT_FILE_INVALID`; gửi lại `application/pdf` đúng định dạng thành công. Không sửa validation.
- Chưa chạy RAM gắn thiết bị, OEM/Per-User, hủy/từ chối nhóm, stale version/concurrency, quá hạn, mobile/keyboard, tải file bằng mọi vai trò, UI toàn bộ PUR/USER, email thật và production. Email cấu hình simulated.
- Excel đọc được bằng thư viện và đối soát dữ liệu; chưa mở trực tiếp trong Microsoft Excel. Có cảnh báo workbook thiếu default style; không làm sai dữ liệu đối soát.
- Đây là nghiệm thu demo có phạm vi, không thay thế toàn bộ kiểm thử hay xác nhận sẵn sàng production.
