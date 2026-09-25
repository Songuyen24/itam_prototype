# Trạng thái triển khai ITAM ngày 25 tháng 9 năm 2026

Đối chiếu trên nhánh `develop`, commit `a786a3c45cd32b34c20592dcf3a2242e72cda4d5`. Đây là trạng thái source và tích hợp Git, không thay cho chữ ký nghiệm thu nghiệp vụ. Kết quả chạy mới nằm trong [biên bản bàn giao](BIEN_BAN_BAN_GIAO.md).

## Quy ước

- `DONE`: phần triển khai của task đã có trong source hiện tại và PR đã merge vào lịch sử của HEAD.
- `READY_FOR_REVIEW`: code và kiểm tra đã xong nhưng chưa có bằng chứng merge. Không còn task T10–T18/T11B/T12B thuộc nhóm này tại HEAD đã đối chiếu.
- Thiếu kiểm chứng hoặc đầu việc nghiệm thu được ghi riêng; không đổi một tính năng đã merge thành “chưa triển khai”.
- Backlog là phần mở rộng hoặc vận hành sau prototype, không được trình bày như chức năng đã hoàn thành.

## Các task đã merge

### T10 Danh mục — DONE

Merge triển khai `c8d6ccf` và merge review `37e3b01`. Source: `backend/src/main/java/com/company/itam/catalog/`, các module department/location/supplier và `frontend/src/features/catalogs/`. Bằng chứng kiểm tra: CatalogControllerIntegrationTest, CatalogServiceTest, DepartmentServiceTest, ChecklistCompletionIntegrationTest; danh mục demo đã tạo qua API.

### T11 Tài sản phần cứng — DONE

Merge triển khai `760e7aa` và merge review `be660d3`. Source: `backend/src/main/java/com/company/itam/asset/` và `frontend/src/features/assets/`. AssetServiceTest, ChecklistCompletionIntegrationTest và kiểm tra quyền bao phủ tạo/sửa/mã tài sản; demo đã xác nhận vòng đời laptop. API tạo tồn đầu kỳ yêu cầu `creationPurpose=BASELINE`; mua mới dùng phiếu nhập.

### T11B Linh kiện và license nhiều suất — DONE

Merge triển khai `2b17ddb`, merge review `6aab9ba`. Source: asset/relationship, migration V14 và phần license trong assets. T11BAssetsIntegrationTest, T15HandoverIntegrationTest và T16RecoveryIntegrationTest đã chạy; demo xác nhận RAM đi cùng laptop, OEM giữ suất và Per-User cấp/thu hồi một suất. Tích hợp T15/T16 không còn là phần chưa làm.

### T12 Import Excel — DONE

Merge triển khai `457ced2`, merge review `d2094e3`. Source: `backend/src/main/java/com/company/itam/importbatch/`. AssetImportServiceTest, ChecklistCompletionIntegrationTest và MigrationAndConcurrencyIntegrationTest đã chạy. Luồng Excel preview/confirm được kiểm tra tự động; chưa chạy lại riêng bằng thao tác trình duyệt trong buổi demo này.

### T12B Đăng nhập và phân quyền — DONE

Merge `6b2de4a`, bổ sung checklist qua `eb49c9a`. Source: auth, security và frontend auth. AuthControllerIntegrationTest, AuthorizationControllerTest và các test quyền workflow đã chạy. Đã đăng nhập bốn tài khoản seed trên trình duyệt; USER chỉ thấy tài sản của mình, PUR không có menu kho/quản trị.

### T13 Chứng từ — DONE

Merge `d7e9cba`, bổ sung `eb49c9a`, merge review `91674f3`. Source: document, workflow/receiving và frontend documents. DocumentControllerIntegrationTest, LocalDocumentStorageTest và ChecklistCompletionIntegrationTest đã chạy. Upload hóa đơn giả lập, khóa chứng từ sau submit, lịch sử revision và tải PDF đã được kiểm tra. Không còn bị chặn bởi T14.

### T14 Nhập kho — DONE

Merge `caacf4e`, bổ sung kiểm thử qua `b1aa306`. Source: workflow/receiving và frontend receiving. T14ReceivingIntegrationTest bao phủ duyệt nguyên tử, rollback, revision cũ, từ chối/lập lại và cạnh tranh. Demo PUR submit → IT approve đạt; PUR approve bị chặn 403. Không tìm thấy merge nhánh review riêng mang tên T14; trạng thái DONE dựa trên các PR triển khai đã merge, không suy diễn có thêm một đợt review độc lập.

### T15 Bàn giao — DONE

Merge `aa549bc`, merge review `e181667`. Source: workflow/handover và frontend handover. T15HandoverIntegrationTest và HandoverPublicationIntegrationTest đã chạy. Đã hoàn tất phiếu trên trình duyệt bằng IT, gồm laptop/RAM/OEM/Per-User; USER nhìn thấy cả bốn bản ghi.

### T16 Thu hồi và Smart Check — DONE

Merge `f91be77`, merge review `ee81381`. Source: workflow/recovery và frontend recovery. T16RecoveryIntegrationTest hiện có 12 test, gồm thu hồi bộ, Per-User/linh kiện riêng, chặn OEM riêng, fingerprint, rollback, cạnh tranh và phân quyền. Không còn đúng khi ghi “chưa có test riêng T16”. Demo đã thu hồi bộ, giữ RAM theo máy, trả suất Per-User và đưa OEM về giữ chỗ.

Không có lớp unit test riêng cho RecoveryService; bằng chứng hiện tại là integration test. Popup preview riêng cho từng asset cha chưa được nghiệm thu như một tính năng riêng; giao diện hiện dùng Smart Check chung. Không đánh dấu tiêu chí unit test/popup riêng hoàn thành chỉ dựa trên test tích hợp.

**Lỗi còn mở H01:** `frontend/src/features/recovery/pages/RecoveriesPage.tsx:9` khởi tạo lịch sử bằng state rỗng, chỉ thêm kết quả trong phiên trang tại handleCompleted; không tải phiếu cũ từ backend. Mở lại trang và chọn Lịch sử không thấy phiếu RC-F5B0572E, trong khi Chứng từ vẫn đọc được phiếu/PDF. DONE ở đây chỉ xác nhận merge, không đóng lỗi này. Xem hành động và tiêu chí kiểm tra tại BACKLOG.

### T17 PDF và email giả lập — DONE

Merge `f164da2`, merge review `ff2d917`. Source: publication, migration liên quan và BilingualPdfGenerator. ImportPublicationIntegrationTest, HandoverPublicationIntegrationTest, TransactionPublicationIntegrationTest, BilingualPdfGeneratorTest đã chạy. Demo tải và kiểm tra trực quan bốn PDF; email cho nhập/bàn giao/thu hồi/thanh lý đều có log giả lập. Tích hợp DISPOSAL đã có ở T18, không còn chờ triển khai.

### T18 Thanh lý và dashboard — DONE

Merge `bbbbf98`, merge review `1a89cbd`. Source: workflow/disposal, dashboard và frontend disposal/dashboard. T18DisposalDashboardIntegrationTest đã chạy. Demo IT lập → ADMIN duyệt; IT tự duyệt bị chặn 403; laptop/RAM RETIRED, OEM giữ suất; ADMIN export Excel đối soát đạt. Dashboard được ADMIN và IT xem, chỉ ADMIN xuất Excel.

## Phần nghiệm thu còn lại và backlog

- H01: sửa lịch sử thu hồi tải từ dữ liệu lưu bền trước nghiệm thu đầy đủ; đây là lỗi chức năng hiện có, không phải đề xuất mở rộng tùy chọn.
- Bộ test cuối đạt nhưng chưa thay cho nghiệm thu trên máy của bên nhận và xác nhận nghiệp vụ.
- Word chưa kiểm tra trực quan được do thiếu LibreOffice; cần render và rà từng trang trước ký nhận.
- Chưa chạy thủ công mọi nhánh từ chối, rút/gửi lại, retry và mọi tổ hợp màn hình/kích thước. Các test tự động tương ứng đã chạy; không gọi đó là kiểm tra UI toàn diện.
- Không kiểm tra mobile/tablet, đúng phạm vi yêu cầu desktop.
- T19: đã có kết quả regression và demo chính; phần nghiệm thu bên nhận còn mở. T20: bộ bàn giao được chuẩn bị, chưa có tên/chữ ký người nhận và người xác nhận.
- QR/tem, thay linh kiện như một quy trình liên kết hoàn chỉnh, SMTP thật, ký số, CI và vận hành production nằm ở [BACKLOG.md](BACKLOG.md).

## Cách kiểm tra lại lịch sử

Từ gốc repository: `git show --stat <commit>` và `git merge-base --is-ancestor <commit> HEAD`. Mỗi mã merge nêu trên đã được kiểm tra là tổ tiên của HEAD. Không commit, push, tạo PR hay merge mới trong đợt bàn giao này.
