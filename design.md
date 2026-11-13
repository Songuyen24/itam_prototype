# ITAM — chuẩn thiết kế chính thức

Ngày khảo sát ban đầu: 02/11/2026. Chính thức hóa ngày 03/11/2026 theo yêu cầu người dùng. Tài liệu này là chuẩn triển khai UI; việc ban hành chuẩn không đồng nghĩa mọi màn hình hiện tại đã đáp ứng hoặc đã được nghiệm thu.

## 1. Phạm vi và mức độ xác nhận

Ưu tiên: quyết định người dùng → giao diện tham chiếu đã duyệt → token/component hiện có → khuyến nghị skill. Đây là chuẩn giao diện quản trị, không phải tài liệu nghiệp vụ. Không áp dụng hero, nội dung quảng bá hoặc phong cách landing page.

Người dùng xác nhận dashboard, tài sản, nhập kho và component thuộc các trang này đã được duyệt. Việc duyệt phạm vi không chứng minh mọi trạng thái hoặc phiên bản mã nguồn hiện tại đã được nghiệm thu. Trong tài liệu:

- **Mẫu tham chiếu đã duyệt**: dashboard, tài sản, nhập kho và các component thuộc phạm vi đó.
- **Quan sát mã nguồn**: giá trị đọc được, chưa đối chiếu ảnh chạy thực tế.
- **Quy tắc thiết kế**: yêu cầu áp dụng khi triển khai hoặc cải thiện UI. Các ghi chú quan sát và giới hạn kiểm chứng không phải bằng chứng nghiệm thu.

Ánh xạ đã được người dùng duyệt cho Bàn giao: dùng trang Tài sản làm mẫu danh sách/bộ lọc/bảng; form và chi tiết tài sản làm mẫu modal; giữ picker bàn giao hiện có và chỉnh typography, khoảng cách, control theo các mẫu đó. Giữ nguyên hành vi Escape, dữ liệu chưa lưu và nghiệp vụ. Không coi toàn bộ phiên bản Bàn giao hiện tại là mẫu đã nghiệm thu.

Nguồn tham chiếu (đường dẫn tương đối từ gốc repo):

- Dashboard: `frontend/src/features/dashboard/pages/DashboardPage.tsx`, `dashboard.css`; modal chọn kỳ `.dashboard-period-dialog`.
- Tài sản: `frontend/src/features/assets/pages/AssetsPage.tsx`, `assets.css`; `components/AssetFilterBar.tsx`, `AssetFormModal.tsx`, `AssetDetailModal.tsx`, `AssetImportModal.tsx`, `asset-workflow.css` trong cùng feature.
- Nhập kho: `frontend/src/features/documents/pages/DocumentsPage.tsx`, `receiving-list.css`; `frontend/src/features/documents/components/ImportDraftPanel.tsx`, `ReceiptSubModal.tsx`, `ReceivingDateRangePicker.tsx`; `frontend/src/features/receiving/components/ReceivingAssetForm.tsx`, `ReceivingAssetDetails.tsx`.
- Dùng chung: `frontend/src/index.css`, `frontend/src/shared/components/PaginatedContent.tsx`, `frontend/src/features/documents/components/DocumentPagination.tsx`.
- `design.md` trước đợt này và báo cáo `project_sources/docs/task_reports/HALLMARK_DESKTOP_UI.md` chỉ là tài liệu tham khảo, không thay thế lời duyệt.

Ảnh: chưa có ảnh được đối chiếu hoặc gắn với phiên bản đã duyệt. Chưa chốt ánh xạ “nhập kho” với toàn bộ component liệt kê trên; cần duyệt danh sách cụ thể trước khi áp dụng chéo.

## 2. Màu semantic — quan sát mã nguồn

Nguồn duy nhất là `frontend/src/index.css`, kể cả khối override cuối file; không tạo hệ token song song.

- Nền trang `--bg-main: #f8fafc`; bề mặt `--bg-card: #ffffff`; sidebar `--bg-sidebar: #0f172a`.
- Chữ chính `--text-main: #0f172a`; phụ `--text-muted: #64748b`; chữ sáng `--text-white: #f8fafc`.
- Viền phân cách `--border-color: #e2e8f0`; viền control `--control-border: #94a3b8`; hover bề mặt `--surface-hover: #f1f5f9`; focus `--focus-ring: #1e40af`.
- Primary `--primary: #2563eb`, hover `--primary-hover: #1d4ed8`, nền nhẹ `--primary-light: #eff6ff`.
- Success `--success: #10b981`, nền `--success-light: #ecfdf5`.
- Warning `--warning: #f59e0b`, nền `--warning-light: #fffbeb`.
- Danger có khai báo gốc `--danger: #ef4444`, `--danger-hover: #dc2626`, nền `--danger-light: #fef2f2`; khi triển khai phải đọc giá trị cuối cascade, không chép khai báo gốc làm màu chữ mặc định.

Quy tắc: màu trạng thái luôn kèm chữ/icon có tên; không dùng màu đơn độc. Không mặc định màu nền trạng thái đủ tương phản để dùng làm chữ. Màu chuỗi biểu đồ `--dashboard-import/handover/recovery/disposal` trong `dashboard.css` chỉ biểu thị chuỗi, không thay token success/danger toàn ứng dụng.

## 3. Typography và mật độ

Quan sát: `index.css` dùng `--font-family`: Inter, system fallback; body line-height 1.5. Không có bằng chứng Inter thực sự được tải trên máy người dùng.

- Dashboard: heading khu vực 18px/1.4; heading nhóm 15px/600; mô tả 13px; tổng số 30px/700/1.2; số liệu dùng `tabular-nums` (`dashboard.css`).
- Nhãn bộ lọc kỳ 13px/600; gap nhãn–control 6px; control tối thiểu 40px, padding 8px 12px (cùng file).
- Nhập kho: bảng danh sách có header 12px, padding 12px; ô dữ liệu 14px, height 48px, padding 4px 12px (`receiving-list.css`). Đây là cấu hình riêng, không phải chiều cao cố định cho mọi hàng nhiều dòng.
- Token radius chung `--radius: 8px`; tồn tại radius cục bộ 4/5/6px ở nhập kho. Không coi mọi giá trị cục bộ là token mới.
- Control chung tối thiểu 38px và line-height 1.4 (`index.css`, khối cuối). Không hạ control chọn kỳ từ 40 xuống 38 chỉ để đồng nhất.

Quy tắc: nhịp khoảng cách 4px, trong nhóm 8–12px, giữa trường 12–16px, giữa khối 20–24px; tiêu đề trang 24px, nội dung/form 14px. Đây là nhịp thiết kế mặc định; ưu tiên mẫu đã duyệt tương ứng và không ép các cấu hình riêng thành một giá trị duy nhất.

## 4. Bố cục, bộ lọc, bảng, phân trang

Quan sát `index.css`: sidebar 224px, thu gọn 64px; navbar 64px; `.app-main` padding 28px, có override responsive 24px ở 769–1100px. Dashboard gap 24px, heading/actions cho phép wrap (`dashboard.css`).

Quy tắc: giữ shell hiện có; tiêu đề và hành động cùng hàng khi đủ chỗ, wrap khi thiếu chỗ. Đặt bộ lọc trước kết quả, không trộn nút tạo với điều khiển phân trang. Dùng `AssetFilterBar` làm tham chiếu tài sản; không sao chép filter nghiệp vụ sang trang khác.

Quan sát: `.asset-table` min-width 1000px (`index.css`); bảng dashboard min-width 980px và wrapper overflow-x auto (`dashboard.css`); bảng nhập kho có min-width 650px (`receiving-list.css`). Giữ cuộn ngang trong vùng bảng, không ép bảng làm tràn toàn trang. Không chuẩn hóa ba min-width thành một số duy nhất.

Quy tắc: tái sử dụng `PaginatedContent` hoặc `DocumentPagination` theo feature hiện hữu; chỉ rõ trang hiện tại, tổng kết quả khi có, vô hiệu hóa trước/sau ở biên. Không tự quy định page-size mới khi chưa kiểm tra hợp đồng hiện tại.

## 5. Modal và modal con

Quan sát:

- Chọn kỳ dashboard: width min(420px, 100% - 48px), padding 24px, max-height viewport - 48px, overflow auto; backdrop đen xanh opacity .45 (`dashboard.css`).
- Form tài sản: `.asset-form-dialog` width min(850px, 100% - 32px), max-width 850px (`index.css`); native dialog trong `AssetFormModal.tsx`.
- Chi tiết tài sản: nội dung max-width 1000px, max-height 90vh, flex column (`AssetDetailModal.tsx`); chưa xác minh kích thước hộp ngoài.
- Nhập kho có các cấu hình 1200px/92vh và 960px/90vh trong `receiving-list.css`; chưa chốt cấu hình nào là chuẩn cho từng loại modal.

Quy tắc: header chứa tên tác vụ và đóng; body cuộn, footer hành động luôn tìm được. Chỉ chọn width theo loại đã đối chiếu, không lấy 1200px áp dụng mọi modal. Modal con chỉ phục vụ tác vụ phụ cụ thể; đóng modal con trở lại modal cha, bảo toàn dữ liệu đang nhập. Không mở nhiều tầng bằng cách cộng z-index tùy ý.

## 6. Picker và form

Nguồn cần đối chiếu tiếp: `ReceivingDateRangePicker`, `ReceiptSubModal`, `ReceivingAssetForm`, `AssetFormModal`; chưa xác nhận picker nào được dùng làm mẫu chung.

Quy tắc: tìm kiếm có nhãn rõ; mỗi dòng kết quả có định danh chính và mô tả phụ đủ phân biệt, trạng thái đã chọn hiển thị bằng dấu chọn và chữ. Danh sách dài cuộn trong vùng riêng; không tự thêm virtualization hoặc phân trang nếu chưa có nhu cầu đo được. Không lấy global search trên navbar làm picker chuẩn.

Form: tái sử dụng `.form-input`, `.form-error` trong `index.css` và cấu trúc feature hiện hữu. Quy tắc: nhóm trường theo tác vụ, nhãn luôn hiện và liên kết control, trường bắt buộc có giải thích, lỗi đặt sát trường và giữ giá trị đã nhập; chỉ một hành động chính trong mỗi cấp modal. Không thay validation nghiệp vụ bằng quy tắc thiết kế.

## 7. Trạng thái và accessibility

Quan sát: `index.css` có focus outline 2px qua `--focus-ring`, disabled dùng nền hover/chữ muted/cursor not-allowed; `.form-error` 13px. `AssetFormModal.tsx` gọi showModal và trả focus về opener còn tồn tại; modal lỗi trả focus về nút submit. `AssetDetailModal.tsx` có onCancel và trả focus. Chưa kiểm chứng thao tác bàn phím thực tế.

Quy tắc áp dụng qua component hiện hữu khi phù hợp:

- Loading ghi đang tải trong vùng kết quả; empty chỉ xuất hiện sau khi tải thành công, phân biệt không có dữ liệu với không khớp bộ lọc.
- Error có thông báo hữu ích và thử lại khi phù hợp; không ghi lỗi thành empty hoặc xóa dữ liệu đã nhập.
- Busy khóa hành động đang gửi để tránh gửi lặp, kèm nhãn trạng thái; disabled phải có lý do truy cập được khi người dùng cần hiểu.
- Success thông báo kết quả bằng chữ, không chỉ màu; dùng vùng thông báo phù hợp để trình đọc màn hình nhận biết.
- Mọi thao tác có thể dùng keyboard; focus nhìn rõ; dialog có tên truy cập được, focus ở trong modal hoạt động, Escape chỉ đóng lớp trên cùng khi được phép, trả focus về nút mở hoặc vị trí thay thế hợp lý.
- Không suy ra native dialog đã giải quyết đầy đủ focus/modal con. Cần kiểm tra Tab/Shift+Tab, Escape, đóng khi busy và dữ liệu chưa lưu riêng; hành vi mất dữ liệu cần người dùng quyết định.

## 8. VI/EN, dữ liệu dài và thiếu

Nguồn: `frontend/src/shared/i18n/locales/vi/` và `en/`; `LanguageSwitcher`. Dashboard có overflow-wrap anywhere cho nhãn và giữ số liệu không co (`dashboard.css`).

Quy tắc: dùng khóa dịch trong namespace feature, không hardcode thêm nhãn; thử cả VI và EN. Nhãn dài wrap không đè control; mã định danh dài cho phép đọc đầy đủ ở chi tiết hoặc cơ chế truy cập được, không chỉ tooltip chuột. Giá trị thiếu hiển thị “—”; số 0 giữ là 0, không thay bằng dấu thiếu. Không đưa dữ liệu thật vào ảnh mẫu hoặc tài liệu.

## 9. Mở rộng và ngoại lệ

Trước màn hình mới: chọn một mẫu đã duyệt tương ứng → ánh xạ token/component sẵn có → ghi khác biệt cần duyệt → kiểm tra VI/EN, bảng dài, các trạng thái và keyboard → người dùng duyệt ảnh/trạng thái mới → cập nhật nguồn chuẩn. Chưa duyệt thì chỉ tham khảo, không lan truyền thành quy chuẩn.

Anti-pattern: lấy “đã code” làm “đã duyệt”; chép màu literal thay token; thêm hệ token hoặc font mới; gradient/hero trang trí cho tác vụ; mọi trạng thái cùng một màu; fixed-height làm cắt nhãn; lỗi tải hiển thị như không có dữ liệu; modal con mất focus/dữ liệu.

Ngoại lệ còn tồn tại từ khảo sát mã: nhiều width/radius cục bộ và CSS override; chi tiết tài sản có màu literal inline; chưa đối chiếu contrast, cascade đầy đủ và hành vi responsive. Đây là điểm cần kiểm chứng, không phải yêu cầu sửa code trong đợt này.

## 10. Áp dụng chuẩn và giới hạn kiểm chứng

Áp dụng trực tiếp các quy tắc trong tài liệu và ánh xạ mẫu đã duyệt; không yêu cầu duyệt lại chỉ vì tài liệu từng là bản nháp. Chỉ hỏi khi thiếu quyết định quan trọng, có mâu thuẫn giữa các mẫu, cần thay đổi nghiệp vụ hoặc đi khác đáng kể so với chuẩn. Không tự đổi API, dữ liệu, phân quyền, validation, thứ tự xử lý hoặc VI/EN để đạt hình thức mong muốn.

Các kích thước được ghi là quan sát mã nguồn vẫn cần đối chiếu cascade và giao diện chạy thực tế; chúng không tự trở thành token chung. Khi chưa có ánh xạ mẫu nhập kho hoặc phân loại width/picker phù hợp cho một màn hình mới, dùng mẫu đã duyệt gần nhất và hỏi nếu khác biệt có ảnh hưởng quan trọng. Giữ hành vi Escape và dữ liệu chưa lưu hiện có, trừ khi người dùng quyết định thay đổi.

Giới hạn khảo sát ban đầu ngày 02/11/2026: chưa mở được giao diện tham chiếu, chưa có ảnh đối chiếu, chưa kiểm keyboard, contrast hoặc responsive trực tiếp. Không coi quan sát nguồn là chứng nhận WCAG.

Cập nhật Bàn giao ngày 03/11/2026: đã triển khai theo ánh xạ trên và kiểm tra API thật ở trang VI/EN, picker chọn/bỏ chọn, xem trước có dữ liệu, quay lại giữ form, modal người giữ, chi tiết phiếu hoàn tất và khu vực chứng từ. Test bàn giao 14/14, build, lint và diff-check đạt; build còn cảnh báo chunk lớn. Chưa nghiệm thu đủ sửa nháp/xác nhận, các biến thể quyền, loading/error/validation/busy, keyboard và viewport hẹp. Một phiếu lịch sử trả lỗi API khi mở chi tiết. Ảnh trước chưa lưu thành tệp. Các giới hạn này phải được kiểm tra trước khi kết luận toàn luồng hoàn tất.

Việc chính thức hóa tài liệu không tự mở rộng phạm vi sửa mã nguồn và không thay thế nghiệm thu thị giác hoặc kiểm tra nghiệp vụ.
