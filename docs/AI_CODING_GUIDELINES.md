# AI Coding Guidelines — ITAM Prototype

## 1. Mục đích

File này là chỉ dẫn bắt buộc cho AI/Cursor khi đọc, tạo, sửa hoặc review code dự án ITAM Prototype.

AI phải đọc toàn bộ file trước khi thay đổi source code.

Mục tiêu:

- Giữ code đúng nghiệp vụ BRD ITAM.
- Không tự mở rộng phạm vi prototype.
- Không phá cấu trúc hoặc code đã tồn tại.
- Giữ backend, frontend và database nhất quán.
- Ngăn AI tự suy diễn các yêu cầu chưa được xác nhận.

## 2. Thứ tự ưu tiên thông tin

Khi có khác biệt, dùng thứ tự sau:

1. Yêu cầu trực tiếp mới nhất của người dùng.
2. Quyết định nghiệp vụ đã được xác nhận trong file này.
3. File `BRD_ITAM`.
4. File Yêu cầu nghiệp vụ — chỉ dùng để hiểu thêm bản chất quy trình.
5. Kế hoạch prototype 6 tháng.
6. Giả định kỹ thuật của AI.

AI không được dùng giả định để ghi đè quyết định đã xác nhận.

## 3. Công nghệ đã chọn

| Thành phần | Công nghệ |
|---|---|
| Backend | Java 21, Spring Boot, Maven |
| Frontend | ReactJS, TypeScript, Vite |
| Database | PostgreSQL |
| ORM | Spring Data JPA/Hibernate |
| Migration | Flyway |
| File | Lưu local trong prototype; PostgreSQL chỉ lưu metadata |

## 4. Phạm vi cấu trúc hiện tại

Project root chỉ cần:

```text
project-root/
├── backend/
├── frontend/
├── storage/
├── .env.example
├── .gitignore
└── README.md
```

Tạm thời không tạo:

- `docs/`
- `infra/`
- `docker-compose.yml`
- Dockerfile
- Kubernetes
- CI/CD
- Cấu hình deployment production

Chỉ tạo các thành phần này khi người dùng yêu cầu rõ.

## 5. Quyết định nghiệp vụ đã chốt

### 5.1. Role

Hệ thống có đúng bốn role:

```text
ADMIN
IT_STAFF
PUR_STAFF
USER
```

Quy tắc:

- Không tạo role `FIN`.
- Không tạo role `ACCOUNTING`.
- Cụm từ `PUR/Kế toán` chỉ nhóm nghiệp vụ hoặc bên nhận thông tin.
- `PUR/Kế toán` không có quyền duyệt thanh lý.
- Chỉ `ADMIN` được duyệt hoặc từ chối phiếu thanh lý.

### 5.2. Nhập kho

Luồng chuẩn:

```text
PUR tạo phiếu → IT kiểm tra → IT duyệt hoặc từ chối
```

Không thêm bước FIN cấp mã.

### 5.3. Bàn giao

Luồng chuẩn:

```text
IT chọn asset In Stock → chọn User → hoàn tất bàn giao
```

Kết quả:

- Phiếu `HANDOVER` ở trạng thái `COMPLETED`.
- Asset chuyển `In Use`.
- `assigned_to` bằng User nhận.
- Sinh một PDF cho toàn bộ asset trong phiếu.

### 5.4. Thu hồi

Luồng chuẩn:

```text
IT chọn User trả → thêm asset → Smart Check → hoàn tất thu hồi
```

Smart Check:

- Chọn asset cha: linh kiện và License OEM là bắt buộc.
- License Per-User là tùy chọn.
- Cho phép thu hồi riêng License Per-User.
- Cho phép thu hồi riêng linh kiện sau cảnh báo xác nhận.
- Cấm thu hồi riêng License OEM.
- Thu hồi riêng asset con phải gỡ đúng quan hệ tương ứng.
- Không xóa tất cả quan hệ của asset một cách máy móc.

### 5.5. Thanh lý

Luồng chuẩn:

```text
IT tạo phiếu PENDING → Admin duyệt hoặc từ chối
```

Kết quả khi duyệt:

- Phiếu chuyển `COMPLETED`.
- Asset trong phiếu chuyển `Retired`.
- Sinh PDF thanh lý.
- Gửi hoặc mô phỏng thông báo cho PUR/Kế toán.

Kết quả khi từ chối:

- Phiếu chuyển `REJECTED`.
- Trạng thái asset giữ nguyên.
- Phải lưu lý do từ chối.

PUR không được gọi API approve hoặc reject.

## 6. Trạng thái chuẩn

### 6.1. Trạng thái phiếu

```text
PENDING
COMPLETED
REJECTED
```

Không tự thêm `APPROVED`, `CANCELLED` hoặc `DRAFT` khi chưa được yêu cầu.

### 6.2. Trạng thái tài sản

```text
IN_STOCK
IN_USE
IN_REPAIR
DAMAGED
RETIRED
```

`PENDING_IMPORT` đang là đề xuất, chưa phải quyết định cuối. Không thêm vào migration hoặc enum nếu người dùng chưa xác nhận.

### 6.3. Loại phiếu

```text
IMPORT
HANDOVER
RECOVERY
DISPOSAL
```

### 6.4. Loại quan hệ

```text
COMPONENT_OF
INSTALLED_ON
```

### 6.5. Loại license chính

```text
OEM
PER_USER
PERPETUAL
```

## 7. Cấu trúc backend

Package gốc:

```text
com.company.itam
```

Module chính:

```text
com.company.itam
├── config
├── common
├── auth
├── user
├── catalog
├── asset
├── transaction
├── receiving
├── handover
├── recovery
├── disposal
├── document
├── audit
└── report
```

Không dùng package tên `import` vì `import` là từ khóa Java. Dùng `receiving` cho nghiệp vụ nhập kho.

### 7.1. Trách nhiệm từng layer

| Layer | Trách nhiệm |
|---|---|
| Controller | Nhận request, validation đầu vào, gọi service, trả response |
| DTO | Dữ liệu request/response của API |
| Service | Business logic và transaction boundary |
| Validator | Business rule phức tạp, kiểm tra điều kiện nghiệp vụ |
| Repository | Truy vấn database |
| Entity | Ánh xạ bảng database |
| Mapper | Chuyển Entity ↔ DTO |

### 7.2. Quy tắc backend

- Controller không chứa business logic.
- Controller không gọi trực tiếp repository.
- Không trả JPA Entity trực tiếp qua API.
- Request và response phải dùng DTO.
- Không dùng DTO làm Entity.
- Không inject repository vào Controller.
- Các workflow cập nhật nhiều bảng phải dùng `@Transactional` tại Service.
- Không bắt exception chung rồi bỏ qua lỗi.
- Không trả stack trace cho frontend.
- Không tạo `Service` interface và `ServiceImpl` nếu chỉ có một implementation.
- Không tạo abstraction chỉ để làm cấu trúc trông phức tạp.
- Không dùng static mutable state cho dữ liệu nghiệp vụ.
- Không đặt role hoặc trạng thái bằng magic string rải rác.
- Dùng enum hoặc constant tập trung.

### 7.3. Luồng backend

```text
Controller → Request DTO → Service → Repository → PostgreSQL
PostgreSQL → Entity → Mapper → Response DTO → Controller
```

## 8. Cấu trúc frontend

Frontend chia theo feature:

```text
src/
├── app/
├── layouts/
├── features/
│   ├── auth/
│   ├── assets/
│   ├── users/
│   ├── catalogs/
│   ├── receiving/
│   ├── handover/
│   ├── recovery/
│   ├── disposal/
│   ├── documents/
│   ├── history/
│   └── dashboard/
├── shared/
├── routes/
└── assets/
```

Mỗi feature có thể chứa:

```text
feature/
├── api/
├── components/
├── hooks/
├── pages/
├── schemas/
└── types/
```

### 8.1. Quy tắc frontend

- React không kết nối PostgreSQL trực tiếp.
- React chỉ gọi API Spring Boot.
- Không đặt business rule chính chỉ ở frontend.
- Backend vẫn phải kiểm tra role dù frontend đã ẩn nút.
- API của feature đặt trong chính feature đó.
- Không gom toàn bộ API vào một file `services.ts` lớn.
- Component dùng chung đặt trong `shared/components`.
- Component chỉ dùng cho một nghiệp vụ đặt trong feature tương ứng.
- Không lưu access token hoặc dữ liệu nhạy cảm tùy tiện.
- Không đưa secret backend vào biến `VITE_*`.
- Không lặp lại server state ở nhiều state khác nhau nếu có thể lấy từ một nguồn.
- Form phải hiển thị lỗi validation rõ ràng.
- Các thao tác thu hồi, thanh lý, duyệt và từ chối phải có dialog xác nhận.

## 9. Database PostgreSQL

### 9.1. Nguyên tắc

- Dùng database quan hệ và khóa ngoại.
- Dùng primary key, foreign key, unique constraint và check constraint.
- Dùng Flyway quản lý mọi thay đổi schema.
- Không sửa database thủ công rồi bỏ qua migration.
- Không dùng `spring.jpa.hibernate.ddl-auto=update`.
- Dùng `ddl-auto=validate` sau khi có migration.
- Migration đã chạy không được sửa tùy tiện; tạo migration mới.
- Không lưu file PDF hoặc chứng từ dạng binary trong database ở prototype.
- Database chỉ lưu metadata và đường dẫn file.

### 9.2. Mô hình cốt lõi

Các bảng chính dự kiến:

```text
roles
users
departments
locations
suppliers
asset_types
asset_statuses
asset_conditions
models
assets
hardware_details
software_details
software_catalog
software_license_types
asset_relationships
transactions
transaction_assets
documents
asset_history
```

Không tạo tất cả bảng trong một migration khổng lồ. Chia migration theo module và thứ tự phụ thuộc.

### 9.3. Ràng buộc quan trọng

- `asset_tag` phải unique khi có giá trị.
- Serial number phải unique khi có giá trị.
- Không cho asset làm cha của chính nó.
- Không cho một asset xuất hiện hai lần trong cùng phiếu.
- `assigned_to` phải có giá trị khi asset ở `IN_USE`.
- `assigned_to` phải rỗng khi asset ở `IN_STOCK` hoặc `RETIRED`.
- Asset đã có lịch sử hoặc giao dịch không được hard-delete.
- `purchase_cost` và `maintenance_cost` không được âm.

## 10. Transaction và tính toàn vẹn

Các thao tác sau phải nguyên tử:

- Duyệt nhập kho.
- Bàn giao nhiều asset.
- Thu hồi nhiều asset.
- Gỡ quan hệ khi thu hồi riêng.
- Admin duyệt thanh lý.

Nếu một bước thất bại, toàn bộ thay đổi phải rollback.

Không được để trạng thái như:

- Phiếu `COMPLETED` nhưng chỉ một phần asset được cập nhật.
- Asset `IN_USE` nhưng không có `assigned_to`.
- Asset `RETIRED` nhưng phiếu thanh lý vẫn `PENDING`.
- License OEM đã về kho nhưng vẫn gắn với thiết bị đang sử dụng do xử lý sai workflow.

## 11. API

### 11.1. Nguyên tắc

- Dùng REST API nhất quán.
- Dùng danh từ số nhiều cho resource.
- Dùng HTTP status code phù hợp.
- Danh sách lớn phải phân trang.
- Tìm kiếm và lọc truyền bằng query parameter.
- Không dùng HTTP `200` cho mọi trường hợp lỗi.
- Không đưa thông tin lỗi database trực tiếp cho client.

Ví dụ endpoint:

```text
GET    /api/assets
GET    /api/assets/{id}
POST   /api/assets
PUT    /api/assets/{id}

POST   /api/receivings
POST   /api/receivings/{id}/approve
POST   /api/receivings/{id}/reject

POST   /api/handovers
POST   /api/recoveries

POST   /api/disposals
POST   /api/disposals/{id}/approve
POST   /api/disposals/{id}/reject
```

Endpoint chỉ là quy ước định hướng. AI phải kiểm tra code hiện tại trước khi thêm để tránh tạo route trùng hoặc đổi API đang dùng.

### 11.2. Response

Response lỗi nên có cấu trúc nhất quán:

```json
{
  "success": false,
  "message": "Asset is not available for handover",
  "code": "ASSET_NOT_AVAILABLE",
  "errors": [],
  "timestamp": "2026-07-15T10:00:00Z"
}
```

Không bắt buộc bọc mọi response thành nhiều lớp nếu không tạo giá trị thực tế.

## 12. Phân quyền

### 12.1. Ma trận chính

| Hành động | Admin | IT Staff | PUR Staff | User |
|---|:---:|:---:|:---:|:---:|
| Xem toàn bộ tài sản | Có | Có | Không | Không |
| Xem tài sản của mình | Có | Có | Không | Có |
| Tạo phiếu nhập | Có | Không | Có | Không |
| Duyệt/từ chối nhập | Có | Có | Không | Không |
| Bàn giao | Có | Có | Không | Không |
| Thu hồi | Có | Có | Không | Không |
| Tạo thanh lý | Có | Có | Không | Không |
| Duyệt/từ chối thanh lý | Có | Không | Không | Không |
| Xem dashboard chi tiết | Có | Có | Không | Không |

### 12.2. Quy tắc

- Backend là nguồn kiểm soát quyền cuối cùng.
- Route guard frontend chỉ hỗ trợ UX.
- PUR không được xem danh sách kho hoặc user.
- User chỉ xem asset có `assigned_to` bằng ID của mình.
- Không role nào được xóa audit log qua API nghiệp vụ.

## 13. File, PDF và storage

Thư mục local:

```text
storage/
├── import/
├── handover/
├── recovery/
└── disposal/
```

Quy tắc:

- Không commit file runtime.
- Kiểm tra loại file và kích thước.
- Không dùng tên file người dùng gửi làm đường dẫn trực tiếp.
- Chống path traversal.
- Tạo tên file an toàn và duy nhất.
- Mỗi document phải liên kết với `transaction_id`.
- DB lưu tên, đường dẫn, loại, người upload và thời gian.
- Không sử dụng chứng từ thật trong prototype.

## 14. Audit log

Phải ghi tối thiểu:

- Actor.
- Thời gian.
- Hành động.
- Asset hoặc phiếu liên quan.
- Trạng thái trước và sau nếu có.
- Lý do duyệt, từ chối, thu hồi hoặc thanh lý khi cần.

Không dùng log ứng dụng thay thế hoàn toàn lịch sử nghiệp vụ trong database.

## 15. Validation bắt buộc

- Không hoàn tất phiếu rỗng.
- Không bàn giao asset khác `IN_STOCK`.
- Không bàn giao asset đã có người sử dụng.
- Không thu hồi asset không thuộc User đã chọn, trừ asset con hợp lệ.
- Không thu hồi riêng License OEM.
- Không duyệt hoặc từ chối phiếu đã kết thúc.
- Chỉ Admin duyệt thanh lý.
- Lý do từ chối bắt buộc.
- Lý do thanh lý bắt buộc.
- Email đúng định dạng.
- Giá trị tiền không âm.
- Foreign key phải tồn tại.
- File upload đúng loại và giới hạn.

## 16. Testing

### 16.1. Backend

Ưu tiên test:

- Unit test business rule.
- Repository test cho truy vấn quan trọng.
- Integration test workflow.
- Authorization test.
- Transaction rollback test.

Các trường hợp bắt buộc:

1. PUR tạo phiếu nhập thành công.
2. IT duyệt và từ chối nhập.
3. Bàn giao asset `IN_STOCK` thành công.
4. Chặn bàn giao asset `IN_USE`.
5. Thu hồi asset cha hiển thị đúng asset con.
6. Thu hồi riêng Per-User thành công.
7. Thu hồi riêng linh kiện thành công.
8. Chặn thu hồi riêng OEM.
9. Chỉ Admin duyệt thanh lý.
10. PUR và IT bị chặn khi gọi API duyệt thanh lý.
11. Từ chối thanh lý không đổi trạng thái asset.
12. Lỗi giữa workflow phải rollback toàn bộ.

### 16.2. Frontend

- Test form validation.
- Test route guard.
- Test hiển thị theo role.
- Test trạng thái loading, empty và error.
- Test dialog Smart Check.
- Không chỉ test happy path.

## 17. Quy tắc code

### Java

- Class dùng PascalCase.
- Method và biến dùng camelCase.
- Constant dùng UPPER_SNAKE_CASE.
- Package dùng chữ thường.
- Không dùng field injection; ưu tiên constructor injection.
- Không dùng `Optional` làm field Entity.
- Không dùng Lombok `@Data` tùy tiện trên Entity.
- Tránh quan hệ JPA hai chiều nếu không cần.
- Không serialize Entity có quan hệ vòng lặp.
- Không dùng eager loading cho mọi quan hệ.
- Không viết query N+1 không kiểm soát.

### TypeScript/React

- Component dùng PascalCase.
- Hook bắt đầu bằng `use`.
- API function và biến dùng camelCase.
- Không dùng `any` nếu có thể định nghĩa type.
- Không gọi API trực tiếp rải rác trong component.
- Không lưu dữ liệu dẫn xuất thành state trùng lặp.
- Không tạo component quá lớn chứa toàn bộ trang và logic.

## 18. Các vấn đề AI không được tự quyết

Nếu công việc liên quan các mục sau, AI phải hỏi hoặc ghi rõ giả định trước khi code:

1. Có thêm trạng thái `PENDING_IMPORT` hay không.
2. Phiếu bị từ chối có được sửa và gửi lại hay không.
3. Bàn giao asset cha có tự thêm linh kiện/OEM hay không.
4. Thu hồi cha cùng linh kiện/OEM có giữ quan hệ không.
5. Asset thu hồi bị hỏng có chuyển `DAMAGED` thay vì `IN_STOCK` không.
6. Điều kiện xác định asset `In Stock quá cũ`.
7. License Per-User khi thanh lý cha chỉ cảnh báo hay phải chặn.
8. Asset tag được nhập thủ công hay hệ thống tự sinh.
9. Cách quản lý license nhiều seat.
10. Có làm giao diện song ngữ Việt/Anh không.
11. Có làm module bảo trì đầy đủ không.
12. Có làm cảnh báo hết bảo hành/license không.
13. Có thêm chữ ký điện tử không.
14. Có gửi email thật không.

Không được coi nội dung trong danh sách này là yêu cầu đã duyệt.

## 19. Quy trình làm việc bắt buộc cho AI

Trước khi sửa code:

1. Đọc yêu cầu hiện tại.
2. Đọc file này.
3. Kiểm tra cấu trúc repository.
4. Chạy `git status --short`.
5. Tìm code liên quan bằng tên feature, class và endpoint.
6. Xác định file sẽ sửa.
7. Không sửa file ngoài phạm vi nếu không cần.

Trong khi sửa:

1. Thay đổi nhỏ và có mục đích rõ.
2. Giữ naming và style đang tồn tại.
3. Không refactor diện rộng khi chỉ sửa một lỗi.
4. Không tạo duplicate Entity, DTO, Repository hoặc endpoint.
5. Không xóa code của người dùng để thay bằng cách khác mà chưa được yêu cầu.
6. Không thay version dependency tùy tiện.

Sau khi sửa:

1. Format code.
2. Chạy test liên quan.
3. Chạy backend build nếu sửa Java.
4. Chạy frontend build nếu sửa React.
5. Chạy lại `git status --short`.
6. Báo file đã sửa, logic thay đổi và kết quả kiểm tra.
7. Không tự commit hoặc push.

## 20. Hành động bị cấm

- Không tự thêm role FIN.
- Không cho PUR duyệt thanh lý.
- Không cho React kết nối PostgreSQL.
- Không hard-delete asset có lịch sử.
- Không sửa migration đã chạy để che lỗi.
- Không dùng `ddl-auto=update` thay Flyway.
- Không đặt business rule chỉ ở frontend.
- Không trả Entity trực tiếp qua API.
- Không hard-code password hoặc secret.
- Không bỏ qua test thất bại.
- Không xóa file hoặc thay đổi Git history.
- Không tự tạo `docs`, `infra`, Docker hoặc deployment khi chưa được yêu cầu.
- Không triển khai tính năng Future Scope như yêu cầu bắt buộc.

## 21. Definition of Done cho mỗi thay đổi

Một thay đổi chỉ hoàn tất khi:

- Đúng nghiệp vụ đã chốt.
- Đúng role và quyền.
- Có validation cần thiết.
- Không phá API hoặc dữ liệu hiện có.
- Có test hoặc bằng chứng kiểm tra phù hợp.
- Backend/frontend build thành công nếu bị ảnh hưởng.
- Không còn secret hoặc file runtime bị đưa vào Git.
- File liên quan được liệt kê rõ trong báo cáo.
- Vấn đề chưa xác nhận được ghi lại, không bị AI tự quyết.

## 22. Checklist nhanh

Trước khi kết thúc, AI phải tự kiểm tra:

- [ ] Có vô tình thêm FIN không?
- [ ] Có vô tình cho PUR duyệt thanh lý không?
- [ ] Business rule có nằm trong backend không?
- [ ] Workflow nhiều bước có `@Transactional` không?
- [ ] API có dùng DTO không?
- [ ] Entity có bị trả trực tiếp không?
- [ ] Validation role và trạng thái đủ chưa?
- [ ] Quan hệ OEM/Per-User xử lý đúng chưa?
- [ ] Có migration Flyway nếu schema thay đổi không?
- [ ] Có test trường hợp lỗi không?
- [ ] Có sửa file ngoài phạm vi không?
- [ ] Có tạo Docker/infra/docs ngoài yêu cầu không?
- [ ] Build và test đã chạy chưa?
