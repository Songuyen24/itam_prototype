# Hướng dẫn cài đặt và vận hành thử ITAM

Tài liệu dành cho người cài đặt và hỗ trợ kỹ thuật. Sau khi hoàn tất, người sử dụng có thể mở ứng dụng, đăng nhập và thực hiện kịch bản demo. Các lệnh dùng PowerShell trên Windows; chạy từ gốc repository trừ khi có ghi khác.

## 1. Chuẩn bị

- JDK 21, JAVA_HOME trỏ đúng JDK; kiểm tra java -version.

- Node.js 22 là lựa chọn môi trường demo đề xuất; kiểm tra node --version, npm --version. Dự án dùng Vite 5, không có trường engines khóa một bản Node cụ thể.

- PostgreSQL đang chạy và công cụ psql hoặc pgAdmin. Đề xuất PostgreSQL 14 trở lên; nhóm triển khai cần kiểm tra tương thích trên phiên bản được chọn trước nghiệm thu.

- Có mạng trong lần đầu để tải Maven Wrapper/dependency và npm package. Không cần cài Maven riêng nếu wrapper chạy được.

- Trình duyệt desktop Edge/Chrome; hai cửa sổ terminal để chạy backend và frontend.

## 2. Tạo database demo

Kết nối PostgreSQL bằng tài khoản có quyền tạo role/database. Chạy từng lệnh SQL ngoài transaction; thay mật khẩu ví dụ trước khi dùng:

```text
CREATE ROLE itam_user LOGIN PASSWORD 'REPLACE_WITH_LOCAL_DEMO_PASSWORD';

CREATE DATABASE itam_db OWNER itam_user;
```

Trong itam_db, xác nhận itam_user có quyền tạo bảng trong schema public. Nếu môi trường PostgreSQL đã hạn chế quyền schema, quản trị viên chạy trong đúng database:

```text
GRANT USAGE, CREATE ON SCHEMA public TO itam_user;
```

Role/database đã tồn tại thì kiểm tra và dùng lại, không chạy lệnh tạo hoặc xóa mù quáng. Flyway tự tạo/cập nhật schema khi backend khởi động; migration nằm trong backend/src/main/resources/db/migration. Đối chiếu phiên bản ứng dụng và lịch sử Flyway trước khi nâng cấp một database đã có dữ liệu.

## 3. Cấu hình backend

Nếu chưa có .env, sao chép .env.example tại gốc thành .env; không ghi đè file đang dùng:

```text
if (!(Test-Path .env)) { Copy-Item .env.example .env }
```

Điền các giá trị local:

```text
DB_URL=jdbc:postgresql://localhost:5432/itam_db

DB_USERNAME=itam_user

DB_PASSWORD=REPLACE_WITH_LOCAL_DEMO_PASSWORD

BACKEND_PORT=8080

DOCUMENT_STORAGE_ROOT=../storage
```

Thêm JWT_SECRET riêng của môi trường nếu cần. Không dùng giá trị JWT mặc định công khai cho môi trường triển khai thật. Không đưa .env vào bản bàn giao công khai.

application.yml đọc .env ở working directory và thư mục cha. Lệnh bên dưới chạy tại backend, nên đọc được .env ở gốc. DOCUMENT_STORAGE_ROOT tính từ working directory: dùng đường dẫn tuyệt đối nếu chạy JAR từ nơi khác.

Không cần kích hoạt profile dev: application-dev.yml đang ghi đè URL/user/password database bằng giá trị cố định. Kiểm tra biến môi trường SPRING_PROFILES_ACTIVE và cấu hình IDE nếu backend dùng sai kết nối.

Các tùy chọn prototype: DISPOSAL_MINIMUM_AGE_YEARS mặc định 5; DISPOSAL_PURCHASING_RECIPIENT và DISPOSAL_ACCOUNTING_RECIPIENT là địa chỉ email giả lập. Không đổi ngưỡng thanh lý tùy tiện khi demo theo nghiệp vụ đã chốt.

## 4. Chạy backend

Terminal thứ nhất:

```text
Set-Location backend

.\mvnw.cmd spring-boot:run
```

Đợi log ứng dụng khởi động thành công và không có lỗi Flyway/JPA. Backend dùng http://localhost:8080/api. Truy cập /api trực tiếp không phải màn hình ứng dụng và không phải health check. Dùng đăng nhập từ frontend để xác minh kết nối; không giả định Actuator/Swagger đã có chỉ vì security matcher cho phép đường dẫn đó.

Tạo JAR khi cần bàn giao bản build:

```text
.\mvnw.cmd package -DskipTests

java -jar target/itam-backend-0.0.1-SNAPSHOT.jar
```

Lệnh -DskipTests chỉ build, không chứng minh test pass. Dừng server đang chạy trước khi chạy JAR cùng port. Nếu đầu ra build đã được chuyển sang archive, chạy build lại, không cần lấy JAR cũ.

## 5. Cấu hình và chạy frontend

Terminal thứ hai, từ gốc repository:

```text
Set-Location frontend

if (!(Test-Path .env)) { Copy-Item .env.example .env }

npm ci

npm run dev
```

Trong frontend/.env:

```text
VITE_API_BASE_URL=http://localhost:8080/api
```

Frontend không tự dùng .env ở gốc theo cấu hình Vite hiện tại. API client tự thêm các đường dẫn /v1/...; không thêm /v1 vào biến base URL. Sau khi đổi .env, khởi động lại Vite; với bản build phải build lại.

Port mặc định là 5173. Muốn đổi port, dùng npm run dev -- --port 5174 hoặc biến tiến trình VITE_FRONTEND_PORT. FRONTEND_PORT trong .env.example ở gốc không được vite.config.ts hiện tại đọc. Xem URL thực tế Vite in ra vì port bận có thể khiến nó chọn port kế tiếp.

Đăng nhập bằng tài khoản demo ở Dữ liệu mẫu ([DU_LIEU_MAU.md](DU_LIEU_MAU.md)). Nút đăng nhập nhanh bật trong Vite dev; bản build chỉ bật nếu VITE_ENABLE_DEMO_LOGIN=true lúc build. Đăng nhập thủ công vẫn dùng được khi không bật shortcut.

Bản frontend build local:

```text
npm run build

npm run preview -- --port 4173
```

Mở URL preview được in ra; backend vẫn phải chạy. Preview là công cụ kiểm tra bản build local, không phải cấu hình triển khai production.

## 6. Kiểm tra trước bàn giao

Frontend, tại frontend:

```text
npm test -- --run

npm run build

npm run lint
```

Backend integration test cần PostgreSQL riêng. Tạo itam_test trên máy local dành riêng cho test, cấp quyền cho tài khoản test; không trỏ TEST_DB_URL vào database demo đang cần giữ hoặc database thật. Tại backend:

```text
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/itam_test'

$env:TEST_DB_USERNAME = 'YOUR_TEST_USER'

$env:TEST_DB_PASSWORD = 'YOUR_TEST_PASSWORD'

.\mvnw.cmd test
```

Source integration test dùng profile test; biến TEST_DB_* nên được đặt rõ thay vì phụ thuộc password mặc định. Lưu kết quả chạy kiểm tra vào hồ sơ nghiệm thu, kèm phiên bản ứng dụng và môi trường sử dụng. Chạy demo desktop và resize theo Kịch bản demo ([KICH_BAN_DEMO.md](KICH_BAN_DEMO.md)); không cần test điện thoại.

## 7. Xử lý lỗi thường gặp

- Kết nối DB lỗi: kiểm tra PostgreSQL, port, database/role, quyền schema, biến môi trường và profile IDE.

- Flyway checksum/validate lỗi: giữ log, xác định migration và database bị ảnh hưởng; đối chiếu lịch sử, sao lưu trước khi xử lý. Không sửa migration cũ, xóa lịch sử hoặc repair tùy tiện.

- Login 401: kiểm tra email/mật khẩu seed, tài khoản active và database backend đang dùng. 403: kiểm tra role/phạm vi giao dịch; không bỏ kiểm tra quyền để demo.

- Frontend gọi sai host: kiểm tra frontend/.env, tab Network và khởi động lại/build lại frontend.

- Upload lỗi: multipart tối đa 10 MB/file, request 11 MB theo cấu hình; kiểm tra loại file UI/API cho phép và quyền ghi storage.

- PDF/file không tồn tại: kiểm tra DOCUMENT_STORAGE_ROOT và working directory; database và storage cần khớp nhau.

- Không có email trong hộp thư: đúng với LocalEmailGateway, xem lịch sử email giả lập trong ứng dụng/log.
