# ITAM — chuẩn thiết kế (BẢN NHÁP)

Ngày khảo sát: 02/11/2026. Chưa dùng làm chuẩn triển khai trước khi người dùng duyệt tài liệu này.

## 1. Phạm vi và mức độ xác nhận

Ưu tiên: quyết định người dùng → giao diện tham chiếu đã duyệt → token/component hiện có → khuyến nghị skill. Đây là chuẩn giao diện quản trị, không phải tài liệu nghiệp vụ. Không áp dụng hero, nội dung quảng bá hoặc phong cách landing page.

Người dùng xác nhận dashboard, tài sản, nhập kho và component thuộc các trang này đã được duyệt. Việc duyệt phạm vi không chứng minh mọi trạng thái hoặc phiên bản mã nguồn hiện tại đã được nghiệm thu. Trong tài liệu:

- **Xác nhận phạm vi**: ba nhóm giao diện trên, theo prompt hiện tại.
- **Quan sát mã nguồn**: giá trị đọc được, chưa đối chiếu ảnh chạy thực tế.
- **Đề xuất cần duyệt**: quy tắc mở rộng hoặc hành vi chưa kiểm chứng; không tự động có hiệu lực.

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

Đề xuất cần duyệt: màu trạng thái luôn kèm chữ/icon có tên; không dùng màu đơn độc. Không mặc định màu nền trạng thái đủ tương phản để dùng làm chữ. Màu chuỗi biểu đồ `--dashboard-import/handover/recovery/disposal` trong `dashboard.css` chỉ biểu thị chuỗi, không thay token success/danger toàn ứng dụng.

## 3. Typography và mật độ

Quan sát: `index.css` dùng `--font-family`: Inter, system fallback; body line-height 1.5. Không có bằng chứng Inter thực sự được tải trên máy người dùng.

- Dashboard: heading khu vực 18px/1.4; heading nhóm 15px/600; mô tả 13px; tổng số 30px/700/1.2; số liệu dùng `tabular-nums` (`dashboard.css`).
- Nhãn bộ lọc kỳ 13px/600; gap nhãn–control 6px; control tối thiểu 40px, padding 8px 12px (cùng file).
- Nhập kho: bảng danh sách có header 12px, padding 12px; ô dữ liệu 14px, height 48px, padding 4px 12px (`receiving-list.css`). Đây là cấu hình riêng, không phải chiều cao cố định cho mọi hàng nhiều dòng.
- Token radius chung `--radius: 8px`; tồn tại radius cục bộ 4/5/6px ở nhập kho. Không coi mọi giá trị cục bộ là token mới.
- Control chung tối thiểu 38px và line-height 1.4 (`index.css`, khối cuối). Không hạ control chọn kỳ từ 40 xuống 38 chỉ để đồng nhất.

Đề xuất cần duyệt: nhịp khoảng cách 4px, trong nhóm 8–12px, giữa trường 12–16px, giữa khối 20–24px; tiêu đề trang 24px, nội dung/form 14px. Đây là định hướng từ tài liệu cũ, chưa là thang token được nghiệm thu cho toàn bộ trang.

## 4. Bố cục, bộ lọc, bảng, phân trang

Quan sát `index.css`: sidebar 224px, thu gọn 64px; navbar 64px; `.app-main` padding 28px, có override responsive 24px ở 769–1100px. Dashboard gap 24px, heading/actions cho phép wrap (`dashboard.css`).

Đề xuất cần duyệt: giữ shell hiện có; tiêu đề và hành động cùng hàng khi đủ chỗ, wrap khi thiếu chỗ. Đặt bộ lọc trước kết quả, không trộn nút tạo với điều khiển phân trang. Dùng `AssetFilterBar` làm tham chiếu tài sản; không sao chép filter nghiệp vụ sang trang khác.

Quan sát: `.asset-table` min-width 1000px (`index.css`); bảng dashboard min-width 980px và wrapper overflow-x auto (`dashboard.css`); bảng nhập kho có min-width 650px (`receiving-list.css`). Giữ cuộn ngang trong vùng bảng, không ép bảng làm tràn toàn trang. Không chuẩn hóa ba min-width thành một số duy nhất.

Đề xuất cần duyệt: tái sử dụng `PaginatedContent` hoặc `DocumentPagination` theo feature hiện hữu; chỉ rõ trang hiện tại, tổng kết quả khi có, vô hiệu hóa trước/sau ở biên. Không tự quy định page-size mới khi chưa kiểm tra hợp đồng hiện tại.

## 5. Modal và modal con

Quan sát:

- Chọn kỳ dashboard: width min(420px, 100% - 48px), padding 24px, max-height viewport - 48px, overflow auto; backdrop đen xanh opacity .45 (`dashboard.css`).
- Form tài sản: `.asset-form-dialog` width min(850px, 100% - 32px), max-width 850px (`index.css`); native dialog trong `AssetFormModal.tsx`.
- Chi tiết tài sản: nội dung max-width 1000px, max-height 90vh, flex column (`AssetDetailModal.tsx`); chưa xác minh kích thước hộp ngoài.
- Nhập kho có các cấu hình 1200px/92vh và 960px/90vh trong `receiving-list.css`; chưa chốt cấu hình nào là chuẩn cho từng loại modal.

Đề xuất cần duyệt: header chứa tên tác vụ và đóng; body cuộn, footer hành động luôn tìm được. Chỉ chọn width theo loại đã đối chiếu, không lấy 1200px áp dụng mọi modal. Modal con chỉ phục vụ tác vụ phụ cụ thể; đóng modal con trở lại modal cha, bảo toàn dữ liệu đang nhập. Không mở nhiều tầng bằng cách cộng z-index tùy ý.

## 6. Picker và form

Nguồn cần đối chiếu tiếp: `ReceivingDateRangePicker`, `ReceiptSubModal`, `ReceivingAssetForm`, `AssetFormModal`; chưa xác nhận picker nào được dùng làm mẫu chung.

Đề xuất cần duyệt: tìm kiếm có nhãn rõ; mỗi dòng kết quả có định danh chính và mô tả phụ đủ phân biệt, trạng thái đã chọn hiển thị bằng dấu chọn và chữ. Danh sách dài cuộn trong vùng riêng; không tự thêm virtualization hoặc phân trang nếu chưa có nhu cầu đo được. Không lấy global search trên navbar làm picker chuẩn.

Form: tái sử dụng `.form-input`, `.form-error` trong `index.css` và cấu trúc feature hiện hữu. Đề xuất cần duyệt: nhóm trường theo tác vụ, nhãn luôn hiện và liên kết control, trường bắt buộc có giải thích, lỗi đặt sát trường và giữ giá trị đã nhập; chỉ một hành động chính trong mỗi cấp modal. Không thay validation nghiệp vụ bằng quy tắc thiết kế.

## 7. Trạng thái và accessibility

Quan sát: `index.css` có focus outline 2px qua `--focus-ring`, disabled dùng nền hover/chữ muted/cursor not-allowed; `.form-error` 13px. `AssetFormModal.tsx` gọi showModal và trả focus về opener còn tồn tại; modal lỗi trả focus về nút submit. `AssetDetailModal.tsx` có onCancel và trả focus. Chưa kiểm chứng thao tác bàn phím thực tế.

Đề xuất cần duyệt, áp dụng qua component hiện hữu khi được chấp thuận:

- Loading ghi đang tải trong vùng kết quả; empty chỉ xuất hiện sau khi tải thành công, phân biệt không có dữ liệu với không khớp bộ lọc.
- Error có thông báo hữu ích và thử lại khi phù hợp; không ghi lỗi thành empty hoặc xóa dữ liệu đã nhập.
- Busy khóa hành động đang gửi để tránh gửi lặp, kèm nhãn trạng thái; disabled phải có lý do truy cập được khi người dùng cần hiểu.
- Success thông báo kết quả bằng chữ, không chỉ màu; dùng vùng thông báo phù hợp để trình đọc màn hình nhận biết.
- Mọi thao tác có thể dùng keyboard; focus nhìn rõ; dialog có tên truy cập được, focus ở trong modal hoạt động, Escape chỉ đóng lớp trên cùng khi được phép, trả focus về nút mở hoặc vị trí thay thế hợp lý.
- Không suy ra native dialog đã giải quyết đầy đủ focus/modal con. Cần kiểm tra Tab/Shift+Tab, Escape, đóng khi busy và dữ liệu chưa lưu riêng; hành vi mất dữ liệu cần người dùng quyết định.

## 8. VI/EN, dữ liệu dài và thiếu

Nguồn: `frontend/src/shared/i18n/locales/vi/` và `en/`; `LanguageSwitcher`. Dashboard có overflow-wrap anywhere cho nhãn và giữ số liệu không co (`dashboard.css`).

Đề xuất cần duyệt: dùng khóa dịch trong namespace feature, không hardcode thêm nhãn; thử cả VI và EN. Nhãn dài wrap không đè control; mã định danh dài cho phép đọc đầy đủ ở chi tiết hoặc cơ chế truy cập được, không chỉ tooltip chuột. Giá trị thiếu hiển thị “—”; số 0 giữ là 0, không thay bằng dấu thiếu. Không đưa dữ liệu thật vào ảnh mẫu hoặc tài liệu.

## 9. Mở rộng và ngoại lệ

Trước màn hình mới: chọn một mẫu đã duyệt tương ứng → ánh xạ token/component sẵn có → ghi khác biệt cần duyệt → kiểm tra VI/EN, bảng dài, các trạng thái và keyboard → người dùng duyệt ảnh/trạng thái mới → cập nhật nguồn chuẩn. Chưa duyệt thì chỉ tham khảo, không lan truyền thành quy chuẩn.

Anti-pattern: lấy “đã code” làm “đã duyệt”; chép màu literal thay token; thêm hệ token hoặc font mới; gradient/hero trang trí cho tác vụ; mọi trạng thái cùng một màu; fixed-height làm cắt nhãn; lỗi tải hiển thị như không có dữ liệu; modal con mất focus/dữ liệu.

Ngoại lệ còn tồn tại từ khảo sát mã: nhiều width/radius cục bộ và CSS override; chi tiết tài sản có màu literal inline; chưa đối chiếu contrast, cascade đầy đủ và hành vi responsive. Đây là điểm cần kiểm chứng, không phải yêu cầu sửa code trong đợt này.

## 10. Cần duyệt và giới hạn khảo sát

Cần người dùng duyệt: ánh xạ component nhập kho; phiên bản/ảnh tham chiếu của ba trang; thang typography/spacing dùng chung; phân loại width modal; mẫu picker; các đề xuất trạng thái/accessibility và xử lý Escape khi có dữ liệu chưa lưu.

Đã kiểm tra branch `feature/ui-improvements`, Git ban đầu sạch; không thấy AGENTS.md được track qua danh sách Git. Có kiểm tra trình duyệt: không có tab ITAM đang mở; không thấy server nghe ở 5173/4173/8080 tại thời điểm khảo sát. Chưa mở được trang/modal tham chiếu, chưa có ảnh, chưa thử keyboard, VI/EN, contrast hoặc responsive trực tiếp. Không coi khảo sát nguồn là kiểm thử UI hay chứng nhận WCAG. Không có kết luận thành phần nào “đã sửa nhưng chưa duyệt” nếu thiếu lịch sử xác nhận.

Chỉ cập nhật tài liệu; không sửa UI, không stage/commit/push. Dừng ở bản nháp để người dùng duyệt; không dùng tài liệu này triển khai trước bước đó.
