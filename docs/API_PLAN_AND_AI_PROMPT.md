# ITAM Prototype — API Plan và Prompt triển khai Backend

## 1. Mục đích

File này dùng để:

- Theo dõi danh sách REST API dự kiến của ITAM Prototype.
- Ánh xạ API với task T10–T18.
- Giữ API đúng BRD và các quyết định nghiệp vụ đã chốt.
- Cung cấp prompt dùng lại cho Cursor/AI khi triển khai từng task.

Phạm vi hiện tại là Backend Spring Boot. Không yêu cầu AI triển khai Frontend nếu người dùng không nói rõ.

## 2. Tài liệu phải đọc trước khi code

AI phải đọc theo thứ tự:

1. Yêu cầu trực tiếp mới nhất của người dùng.
2. `AI_CODING_GUIDELINES.md`.
3. `CURSOR_TASK_PLAN_T10_T18.md`.
4. File này.
5. BRD ITAM và tài liệu trong `ITAM_Prototype_Documentation/` khi cần làm rõ nghiệp vụ.
6. Migration, Entity, DTO, Controller và test đang tồn tại trong repository.

Nếu nội dung file này khác `AI_CODING_GUIDELINES.md`, dùng `AI_CODING_GUIDELINES.md`.

## 3. Quyết định nghiệp vụ đã chốt

### 3.1. Role

Hệ thống có đúng bốn role:

```text
ADMIN
IT_STAFF
PUR_STAFF
USER
```

Không tạo `FIN` hoặc `ACCOUNTING`.

### 3.2. Trạng thái phiếu

```text
PENDING
COMPLETED
REJECTED
```

Không tự thêm:

- `DRAFT`
- `CANCELLED`
- `APPROVED`

### 3.3. Trạng thái tài sản

```text
IN_STOCK
IN_USE
IN_REPAIR
DAMAGED
RETIRED
```

Không tự thêm `PENDING_IMPORT` nếu người dùng chưa xác nhận.

### 3.4. Workflow

```text
Nhập kho: PUR tạo PENDING → IT duyệt COMPLETED hoặc từ chối REJECTED
Bàn giao: IT thực hiện → HANDOVER/COMPLETED
Thu hồi: IT thực hiện Smart Check → RECOVERY/COMPLETED
Thanh lý: IT tạo PENDING → chỉ Admin duyệt COMPLETED hoặc từ chối REJECTED
```

### 3.5. Ngoài phạm vi

- Không làm QR Code hoặc tem tài sản trong T17.
- Không làm chữ ký điện tử.
- Không gửi email production nếu chưa có cấu hình được xác nhận.
- Không tạo module bảo trì đầy đủ.
- Không hard-delete asset, transaction hoặc audit history.

## 4. Quy ước API

Base URL đề xuất:

```text
/api/v1
```

Nếu repository đã dùng `/api` hoặc version khác, giữ convention hiện tại. Không đổi hàng loạt route chỉ để khớp file này.

### 4.1. Quy tắc chung

- Resource dùng danh từ số nhiều.
- Danh sách lớn phải phân trang.
- Tìm kiếm và lọc dùng query parameter.
- Tạo mới trả `201 Created`.
- Cập nhật thành công trả `200 OK` hoặc `204 No Content` theo convention hiện có.
- Validation lỗi trả `400 Bad Request` hoặc `422 Unprocessable Entity`.
- Chưa đăng nhập trả `401 Unauthorized`.
- Sai quyền trả `403 Forbidden`.
- Không tìm thấy trả `404 Not Found`.
- Trùng dữ liệu hoặc sai trạng thái nghiệp vụ trả `409 Conflict`.
- Không trả JPA Entity trực tiếp.
- Không lộ lỗi PostgreSQL, stack trace hoặc đường dẫn nội bộ.
- Workflow cập nhật nhiều bảng phải dùng `@Transactional`.
- Backend phải kiểm tra role và business rule, không tin dữ liệu từ frontend.

Query phân trang chuẩn:

```text
page=0
size=20
sort=createdAt,desc
keyword=...
```

### 4.2. Response lỗi

```json
{
  "success": false,
  "message": "Asset is not available for handover",
  "code": "ASSET_NOT_AVAILABLE",
  "errors": [],
  "timestamp": "2026-07-21T09:00:00Z"
}
```

Không bắt buộc bọc response thành công nhiều lớp nếu project đang dùng cấu trúc khác hợp lý.

## 5. Ma trận quyền chính

| Chức năng | ADMIN | IT_STAFF | PUR_STAFF | USER |
|---|:---:|:---:|:---:|:---:|
| Quản lý danh mục | Có | Theo cấu hình | Không | Không |
| Xem toàn bộ tài sản | Có | Có | Không | Không |
| Xem tài sản của mình | Có | Có | Không | Có |
| Tạo phiếu nhập kho | Có | Không | Có | Không |
| Duyệt/từ chối nhập kho | Có | Có | Không | Không |
| Bàn giao | Có | Có | Không | Không |
| Thu hồi | Có | Có | Không | Không |
| Tạo phiếu thanh lý | Có | Có | Không | Không |
| Duyệt/từ chối thanh lý | Có | Không | Không | Không |
| Xem dashboard chi tiết | Có | Có | Không | Không |

PUR chỉ xem phiếu nhập do mình tạo và document liên quan. PUR không được xem kho, danh sách user hoặc dashboard chi tiết.

USER chỉ xem asset có `assigned_to` bằng ID của mình.

## 6. Danh sách API dự kiến

### 6.1. Nền tảng — Auth và User

| Method | Endpoint | Quyền | Chức năng |
|---|---|---|---|
| POST | `/api/v1/auth/login` | Public | Đăng nhập local. |
| GET | `/api/v1/auth/me` | Đã đăng nhập | Lấy thông tin user hiện tại. |
| POST | `/api/v1/auth/logout` | Đã đăng nhập | Đăng xuất. |
| POST | `/api/v1/auth/refresh` | Đã đăng nhập | Chỉ tạo nếu cơ chế JWT hiện tại cần refresh token. |
| GET | `/api/v1/users` | ADMIN, IT_STAFF | Danh sách user, tìm kiếm và phân trang. |
| GET | `/api/v1/users/{id}` | ADMIN, IT_STAFF | Chi tiết user. |
| POST | `/api/v1/users` | ADMIN | Tạo user mẫu. |
| PUT | `/api/v1/users/{id}` | ADMIN | Cập nhật user. |
| PATCH | `/api/v1/users/{id}/active` | ADMIN | Khóa hoặc mở user. |
| GET | `/api/v1/users/me/assets` | USER, ADMIN, IT_STAFF | Xem tài sản của user hiện tại. |

Không tự tạo refresh token nếu project đang dùng session hoặc chưa có yêu cầu JWT.

### 6.2. T10 — CRUD danh mục

Các resource:

```text
departments
locations
asset-categories
asset-types
asset-statuses
asset-conditions
models
suppliers
software-license-types
software-catalog
```

Mỗi resource áp dụng bộ API phù hợp:

| Method | Endpoint mẫu | Chức năng |
|---|---|---|
| GET | `/api/v1/{resource}` | Danh sách, tìm kiếm và phân trang. |
| GET | `/api/v1/{resource}/{id}` | Chi tiết. |
| POST | `/api/v1/{resource}` | Tạo mới. |
| PUT | `/api/v1/{resource}/{id}` | Cập nhật. |
| DELETE | `/api/v1/{resource}/{id}` | Chỉ xóa khi chưa được tham chiếu. |
| PATCH | `/api/v1/{resource}/{id}/active` | Chỉ dùng nếu schema hiện tại có `is_active` và dự án chọn ngừng sử dụng thay cho xóa. |

Không bắt buộc tạo đồng thời cả `DELETE` và `/active`. Kiểm tra schema và convention hiện tại rồi chọn cách phù hợp.

Nếu danh mục đang được tham chiếu, trả:

```text
409 Conflict
CATALOG_IN_USE
```

API liên hệ nhà cung cấp:

| Method | Endpoint | Chức năng |
|---|---|---|
| GET | `/api/v1/suppliers/{supplierId}/contacts` | Danh sách liên hệ. |
| POST | `/api/v1/suppliers/{supplierId}/contacts` | Thêm liên hệ. |
| PUT | `/api/v1/supplier-contacts/{id}` | Sửa liên hệ. |
| DELETE | `/api/v1/supplier-contacts/{id}` | Xóa liên hệ chưa được sử dụng. |

### 6.3. T11 — Quản lý tài sản

| Method | Endpoint | Quyền | Chức năng |
|---|---|---|---|
| GET | `/api/v1/assets` | ADMIN, IT_STAFF | Tìm kiếm, lọc và phân trang. |
| GET | `/api/v1/assets/{id}` | ADMIN, IT_STAFF | Chi tiết asset và cấu hình Default/Actual. |
| POST | `/api/v1/assets` | ADMIN, IT_STAFF | Tạo tài sản. |
| PUT | `/api/v1/assets/{id}` | ADMIN, IT_STAFF | Cập nhật thông tin được phép sửa. |
| POST | `/api/v1/assets/validate-uniqueness` | ADMIN, IT_STAFF | Kiểm tra Asset Tag và Serial Number trùng. |
| GET | `/api/v1/assets/{id}/relationships` | ADMIN, IT_STAFF | Quan hệ cha-con của asset. |
| POST | `/api/v1/assets/{id}/relationships` | ADMIN, IT_STAFF | Tạo quan hệ. |
| DELETE | `/api/v1/asset-relationships/{id}` | ADMIN, IT_STAFF | Gỡ đúng một quan hệ. |
| GET | `/api/v1/assets/{id}/history` | ADMIN, IT_STAFF | Lịch sử asset. |
| GET | `/api/v1/assets/{id}/documents` | ADMIN, IT_STAFF | Document liên quan. |

Query cho `GET /assets`:

```text
keyword
assetTag
serialNumber
categoryId
typeId
statusId
conditionId
modelId
departmentId
locationId
supplierId
assignedTo
page
size
sort
```

Không tạo API xóa asset. Không tạo API đổi trạng thái tùy ý. Workflow là nguồn cập nhật trạng thái chính.

### 6.4. T12 — Import Excel và validation

| Method | Endpoint | Quyền | Chức năng |
|---|---|---|---|
| GET | `/api/v1/asset-imports/template` | ADMIN, IT_STAFF | Tải template Excel. |
| POST | `/api/v1/asset-imports/preview` | ADMIN, IT_STAFF | Upload, đọc và validation; không ghi asset vào database. |
| POST | `/api/v1/asset-imports/confirm` | ADMIN, IT_STAFF | Import các dòng hợp lệ đã xác nhận. |
| GET | `/api/v1/asset-imports` | ADMIN, IT_STAFF | Lịch sử import nếu schema có lưu batch. |
| GET | `/api/v1/asset-imports/{id}` | ADMIN, IT_STAFF | Chi tiết kết quả import. |
| GET | `/api/v1/asset-imports/{id}/errors` | ADMIN, IT_STAFF | Tải danh sách lỗi nếu có. |

`preview` phải trả lỗi theo dòng và cột:

```json
{
  "rowNumber": 4,
  "column": "serialNumber",
  "code": "DUPLICATE_SERIAL_NUMBER",
  "message": "Serial Number đã tồn tại"
}
```

Không tự tạo danh mục thiếu trong file Excel.

### 6.5. T13 — Document

| Method | Endpoint | Quyền | Chức năng |
|---|---|---|---|
| POST | `/api/v1/documents` | Theo transaction | Upload file bằng `multipart/form-data`. |
| GET | `/api/v1/documents` | Theo quyền dữ liệu | Tìm kiếm document. |
| GET | `/api/v1/documents/{id}` | Theo quyền dữ liệu | Xem metadata. |
| GET | `/api/v1/documents/{id}/download` | Theo quyền dữ liệu | Download file. |
| DELETE | `/api/v1/documents/{id}` | Uploader/Admin | Chỉ xóa document chưa thuộc phiếu hoàn tất. |

Document bắt buộc liên kết `transaction_id` theo BRD. `asset_id` chỉ là liên kết tùy chọn để tra cứu nhanh.

Backend phải kiểm tra extension, MIME type, kích thước file, tên file an toàn và path traversal.

### 6.6. API transaction dùng chung

| Method | Endpoint | Chức năng |
|---|---|---|
| GET | `/api/v1/transactions` | Danh sách phiếu theo type, status và actor. |
| GET | `/api/v1/transactions/{id}` | Chi tiết phiếu, asset và document. |
| GET | `/api/v1/transactions/{id}/timeline` | Dòng thời gian xử lý. |

Query:

```text
type
status
requesterId
relatedUserId
fromDate
toDate
page
size
sort
```

Nếu module hiện tại đã có endpoint danh sách/chi tiết riêng, không tạo endpoint dùng chung gây trùng chức năng.

### 6.7. T14 — Workflow nhập kho PUR → IT

| Method | Endpoint | Quyền | Chức năng |
|---|---|---|---|
| POST | `/api/v1/receivings` | PUR_STAFF, ADMIN | Tạo `IMPORT/PENDING` với nhiều asset và document. |
| POST | `/api/v1/receivings/{id}/approve` | IT_STAFF, ADMIN | Duyệt nhập kho, chuyển phiếu `COMPLETED` và asset `IN_STOCK`. |
| POST | `/api/v1/receivings/{id}/reject` | IT_STAFF, ADMIN | Từ chối và bắt buộc lý do. |

Không thêm endpoint FIN. Không cho PUR tự duyệt.

Nếu schema chưa biểu diễn được asset trước khi IT duyệt và cần quyết định `PENDING_IMPORT` hoặc staging, đánh dấu task `BLOCKED`. Không tự chọn.

### 6.8. T15 — Workflow bàn giao

| Method | Endpoint | Quyền | Chức năng |
|---|---|---|---|
| POST | `/api/v1/handovers` | IT_STAFF, ADMIN | Tạo và hoàn tất `HANDOVER/COMPLETED`. |

Request chứa một User và ít nhất một asset.

Backend phải kiểm tra lại trong transaction:

- Asset đang `IN_STOCK`.
- `assigned_to` đang rỗng.
- User nhận đang hoạt động.
- Một lỗi phải rollback toàn bộ phiếu.

Không tự thêm asset con khi bàn giao nếu chưa được xác nhận.

### 6.9. T16 — Workflow thu hồi và Smart Check

| Method | Endpoint | Quyền | Chức năng |
|---|---|---|---|
| POST | `/api/v1/recoveries/smart-check` | IT_STAFF, ADMIN | Kiểm tra asset cha-con trước khi hoàn tất. |
| POST | `/api/v1/recoveries` | IT_STAFF, ADMIN | Tạo và hoàn tất `RECOVERY/COMPLETED`. |

Response Smart Check tối thiểu:

```json
{
  "requiredAssets": [],
  "optionalAssets": [],
  "blockedAssets": [],
  "warnings": []
}
```

Business rule:

- Chọn asset cha: linh kiện và OEM bắt buộc.
- License Per-User là tùy chọn.
- Thu hồi riêng Per-User: cho phép và gỡ đúng `INSTALLED_ON`.
- Thu hồi riêng linh kiện: cho phép sau cảnh báo và gỡ đúng `COMPONENT_OF`.
- Thu hồi riêng OEM: chặn.
- Không xóa toàn bộ quan hệ của asset.
- Hoàn tất chuyển asset được thu hồi thành `IN_STOCK`, `assigned_to = NULL`.

### 6.10. T17 — PDF và email giả lập

| Method | Endpoint | Quyền | Chức năng |
|---|---|---|---|
| GET | `/api/v1/transactions/{id}/pdf` | Theo quyền phiếu | Download PDF mới nhất. |
| POST | `/api/v1/transactions/{id}/pdf/regenerate` | ADMIN, IT_STAFF | Sinh lại PDF nếu được phép. |
| POST | `/api/v1/transactions/{id}/email/resend` | ADMIN, IT_STAFF | Gửi lại email local/sandbox. |
| GET | `/api/v1/transactions/{id}/email-logs` | ADMIN, IT_STAFF | Xem lịch sử gửi email. |

Workflow hoàn tất phải gọi PDF/email service theo quy tắc task T17.

Lỗi email không được rollback nghiệp vụ đã hoàn tất. Phải ghi log lỗi rõ ràng.

Không thêm API hoặc dependency QR Code.

### 6.11. T18 — Thanh lý và Dashboard

| Method | Endpoint | Quyền | Chức năng |
|---|---|---|---|
| POST | `/api/v1/disposals/smart-check` | IT_STAFF, ADMIN | Tự thêm linh kiện/OEM và cảnh báo Per-User. |
| POST | `/api/v1/disposals` | IT_STAFF, ADMIN | Tạo `DISPOSAL/PENDING`. |
| POST | `/api/v1/disposals/{id}/approve` | ADMIN | Chuyển phiếu `COMPLETED`, asset `RETIRED`. |
| POST | `/api/v1/disposals/{id}/reject` | ADMIN | Chuyển phiếu `REJECTED`, giữ nguyên asset. |
| GET | `/api/v1/dashboard/summary` | ADMIN, IT_STAFF | Thống kê prototype. |

Dashboard trả tối thiểu:

- Số asset theo trạng thái.
- Số asset theo loại.
- Số asset theo vị trí.
- Số asset theo phòng ban.
- Số phiếu nhập kho `PENDING`.
- Số phiếu thanh lý `PENDING`.

Dashboard phải dùng query tổng hợp. Không tải toàn bộ Entity rồi đếm trong Java.

### 6.12. Audit

| Method | Endpoint | Quyền | Chức năng |
|---|---|---|---|
| GET | `/api/v1/asset-history` | ADMIN, IT_STAFF | Lọc lịch sử theo asset, transaction, actor và thời gian. |
| GET | `/api/v1/assets/{id}/history` | ADMIN, IT_STAFF | Lịch sử một asset. |
| GET | `/api/v1/transactions/{id}/timeline` | Theo quyền phiếu | Lịch sử một phiếu. |

Không tạo API sửa hoặc xóa audit history.

## 7. Mã lỗi nghiệp vụ đề xuất

| Code | Trường hợp |
|---|---|
| `VALIDATION_ERROR` | Request không hợp lệ. |
| `RESOURCE_NOT_FOUND` | Không tìm thấy bản ghi. |
| `DUPLICATE_CODE` | Trùng mã danh mục. |
| `DUPLICATE_ASSET_TAG` | Trùng Asset Tag. |
| `DUPLICATE_SERIAL_NUMBER` | Trùng Serial Number. |
| `CATALOG_IN_USE` | Danh mục đang được tham chiếu. |
| `ASSET_NOT_AVAILABLE` | Asset không ở trạng thái cho phép. |
| `ASSET_ALREADY_ASSIGNED` | Asset đã có người sử dụng. |
| `TRANSACTION_ALREADY_FINISHED` | Phiếu đã `COMPLETED` hoặc `REJECTED`. |
| `EMPTY_TRANSACTION` | Phiếu không có asset. |
| `REJECTION_REASON_REQUIRED` | Thiếu lý do từ chối. |
| `DISPOSAL_REASON_REQUIRED` | Thiếu lý do thanh lý. |
| `OEM_RECOVERY_NOT_ALLOWED` | Thu hồi riêng License OEM. |
| `FORBIDDEN_OPERATION` | Role không được thực hiện hành động. |
| `INVALID_FILE_TYPE` | File không đúng loại. |
| `FILE_TOO_LARGE` | File vượt giới hạn. |
| `FILE_STORAGE_ERROR` | Lỗi lưu hoặc đọc file. |

AI phải tái sử dụng error code hiện có nếu repository đã định nghĩa tương đương.

## 8. Thứ tự triển khai

| Giai đoạn | Task | Branch |
|---|---|---|
| 1 | T10 — CRUD danh mục | `feature/T10-catalog-crud` |
| 2 | T11 — Tài sản phần cứng | `feature/T11-hardware-assets` |
| 3 | T12 — Import Excel | `feature/T12-excel-import` |
| 4 | T13 — Document | `feature/T13-documents` |
| 5 | T14 — Nhập kho | `feature/T14-receiving-workflow` |
| 6 | T15 — Bàn giao | `feature/T15-handover-workflow` |
| 7 | T16 — Thu hồi | `feature/T16-recovery-workflow` |
| 8 | T17 — PDF và email | `feature/T17-pdf-email` |
| 9 | T18 — Thanh lý và dashboard | `feature/T18-disposal-dashboard` |

Quy tắc:

```text
1 task = 1 branch = 1 Pull Request
```

Không triển khai task tiếp theo khi task hiện tại chưa được người dùng yêu cầu.

## 9. Test bắt buộc theo task

| Task | Test chính |
|---|---|
| T10 | CRUD, trùng code/name, danh mục đang được dùng, phân quyền. |
| T11 | Asset Tag/Serial unique, Default/Actual, search/filter, không N+1 rõ ràng. |
| T12 | File hợp lệ, file sai, trùng trong file, trùng database, preview không ghi asset. |
| T13 | MIME/extension/size, path traversal, quyền download, document thuộc phiếu hoàn tất. |
| T14 | PUR tạo, IT approve/reject, lý do bắt buộc, rollback toàn bộ. |
| T15 | Bàn giao `IN_STOCK`, chặn `IN_USE`, assigned_to, rollback nhiều asset. |
| T16 | Đủ bốn Smart Check, gỡ đúng quan hệ, chặn OEM riêng. |
| T17 | PDF đúng asset, metadata đúng transaction, email lỗi không sai trạng thái nghiệp vụ. |
| T18 | Chỉ Admin approve/reject, từ chối giữ asset, duyệt chuyển `RETIRED`, dashboard đúng. |

## 10. Definition of Done cho Backend API

Task chỉ chuyển `READY_FOR_REVIEW` khi:

- API đúng phạm vi task.
- Không tạo endpoint trùng.
- Không thêm role FIN.
- Không thêm trạng thái chưa được duyệt.
- Controller không chứa business logic.
- Không trả Entity trực tiếp.
- Validation và authorization đầy đủ.
- Workflow dùng transaction đúng chỗ.
- Migration mới chạy được trên database trống nếu task có thay đổi schema.
- Unit test, repository test hoặc integration test phù hợp đã chạy.
- `./mvnw test` thành công.
- `git diff` chỉ chứa thay đổi liên quan task.
- Checklist task được cập nhật thành `READY_FOR_REVIEW`, không tự đánh dấu `DONE`.
- Không tự commit, push, tạo PR hoặc merge nếu người dùng chưa yêu cầu.

## 11. Prompt dùng cho Cursor/AI

Đổi `CURRENT_TASK` trước khi dùng. Ví dụ bắt đầu CRUD danh mục thì giữ `T10`.

```text
CURRENT_TASK: T10
SCOPE: BACKEND_API_ONLY

Hãy triển khai Backend API cho task CURRENT_TASK của dự án ITAM Prototype.

Trước khi sửa code, bắt buộc:

1. Đọc toàn bộ các file:
   - AI_CODING_GUIDELINES.md
   - CURSOR_TASK_PLAN_T10_T18.md
   - API_PLAN_AND_AI_PROMPT.md
2. Đọc phần CURRENT_TASK trong CURSOR_TASK_PLAN_T10_T18.md.
3. Chạy và báo kết quả:
   - git status --short
   - git branch --show-current
4. Kiểm tra cấu trúc backend, pom.xml, application configuration, migration, Entity, DTO, Repository, Service, Controller, Security và test đang tồn tại.
5. Tìm endpoint hoặc class tương đương trước khi tạo file mới. Không tạo duplicate code.
6. Xác định dependency của CURRENT_TASK đã hoàn thành hay chưa.
7. Xác định branch đúng theo bảng trong API_PLAN_AND_AI_PROMPT.md.

Quy tắc Git:

- Một task chỉ làm trên một branch riêng.
- Nếu đang ở main hoặc develop và worktree sạch, tạo/chuyển sang branch của CURRENT_TASK từ develop, trừ khi repository có convention khác.
- Nếu worktree đang có thay đổi, không tự stash, reset hoặc bỏ thay đổi. Báo rõ file đang thay đổi và tiếp tục chỉ khi không xung đột.
- Không trộn task khác vào branch hiện tại.
- Không commit, push, mở Pull Request hoặc merge nếu chưa được yêu cầu.

Sau khi kiểm tra:

1. Tóm tắt code hiện có liên quan CURRENT_TASK.
2. Liệt kê endpoint sẽ tạo hoặc sửa.
3. Liệt kê file dự kiến thay đổi.
4. Nêu dependency, migration và business rule liên quan.
5. Nếu không có blocker, tiếp tục triển khai ngay trong phạm vi CURRENT_TASK.

Yêu cầu implementation:

- Giữ package root và cấu trúc hiện có. Package định hướng là com.company.itam.
- Dùng Controller → DTO → Service → Repository → PostgreSQL.
- Controller không gọi Repository và không chứa business logic.
- Không trả JPA Entity trực tiếp.
- Dùng request/response DTO riêng.
- Dùng Mapper phù hợp convention hiện tại; không thêm thư viện mapping mới nếu chưa cần.
- Không tạo Service interface và ServiceImpl nếu chỉ có một implementation.
- Dùng constructor injection.
- Dùng Bean Validation cho input cơ bản.
- Business rule phức tạp đặt trong Service hoặc Validator.
- Workflow cập nhật nhiều bảng dùng @Transactional.
- Kiểm tra lại trạng thái asset/transaction trong transaction trước khi cập nhật.
- Dùng phân trang cho danh sách lớn.
- Tránh N+1 ở API danh sách và chi tiết.
- Dùng enum/constant cho role, trạng thái, loại phiếu và loại quan hệ.
- Không dùng magic string rải rác.
- Giữ response/error format hiện có. Nếu chưa có, dùng format trong API_PLAN_AND_AI_PROMPT.md.
- Dùng HTTP status code đúng.
- Không lộ lỗi database hoặc stack trace.
- Không hard-delete asset, transaction, document thuộc phiếu hoàn tất hoặc audit history.
- Không sửa migration đã chạy; tạo migration mới.
- Sau khi có Flyway, dùng spring.jpa.hibernate.ddl-auto=validate.
- Không thêm dependency nếu Java/Spring hiện tại đã giải quyết được.
- Không thêm Docker, docs, infra, CI/CD hoặc cấu hình production.

Quyết định nghiệp vụ bắt buộc:

- Role chỉ gồm ADMIN, IT_STAFF, PUR_STAFF, USER.
- Không có FIN hoặc ACCOUNTING.
- Trạng thái phiếu chỉ gồm PENDING, COMPLETED, REJECTED.
- Không thêm DRAFT, CANCELLED hoặc APPROVED.
- Không thêm PENDING_IMPORT nếu chưa được xác nhận.
- Nhập kho: PUR tạo, IT/Admin approve hoặc reject.
- Bàn giao: IT/Admin thực hiện, tạo HANDOVER/COMPLETED.
- Thu hồi: IT/Admin thực hiện Smart Check, tạo RECOVERY/COMPLETED.
- Thanh lý: IT/Admin tạo; chỉ ADMIN approve hoặc reject.
- PUR/Kế toán chỉ nhận thông báo thanh lý.
- Không làm QR Code trong T17.

Nếu CURRENT_TASK gặp một trong các vấn đề chưa được quyết định trong AI_CODING_GUIDELINES.md, không tự suy diễn. Đánh dấu BLOCKED, mô tả chính xác điểm thiếu và đề xuất tối đa ba lựa chọn.

Testing:

- Thêm unit test cho business rule.
- Thêm repository test cho query quan trọng.
- Thêm integration test cho workflow và authorization.
- Có test failure path, không chỉ happy path.
- Kiểm tra rollback nếu workflow cập nhật nhiều asset.
- Chạy:
  cd backend
  ./mvnw test
- Nếu không có mvnw, dùng lệnh Maven hiện tại của repository.
- Báo nguyên văn lỗi chính nếu test thất bại.
- Không đánh dấu hoàn tất khi test liên quan còn lỗi.

Khi hoàn thành, báo cáo ngắn theo mẫu:

1. Task và branch.
2. Endpoint đã tạo/sửa.
3. Migration và file đã thay đổi.
4. Business rule đã xử lý.
5. Test đã chạy và kết quả.
6. Phần chưa làm hoặc blocker.
7. Git diff --stat.

Cập nhật CURRENT_TASK trong CURSOR_TASK_PLAN_T10_T18.md thành READY_FOR_REVIEW khi code và test đã xong. Không tự đánh dấu DONE.
Không triển khai task tiếp theo.
```

## 12. Cách dùng prompt

Ví dụ triển khai T11:

```text
CURRENT_TASK: T11
SCOPE: BACKEND_API_ONLY
```

Giữ nguyên phần còn lại của prompt.

Nếu chỉ muốn AI kiểm tra mà chưa code, đổi:

```text
SCOPE: REVIEW_ONLY
```

Với `REVIEW_ONLY`, AI chỉ kiểm tra repository, đối chiếu API và báo thiếu. Không sửa file.

