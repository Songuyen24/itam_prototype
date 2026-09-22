# ITAM Prototype

Ứng dụng quản lý vòng đời tài sản công nghệ thông tin dành cho nhóm IT, bộ phận mua sắm và người sử dụng thiết bị. Hệ thống hỗ trợ quản lý tồn kho, nhập kho, bàn giao, thu hồi, thanh lý và chứng từ.

Đây là prototype phục vụ trình diễn và kiểm tra nghiệp vụ nội bộ. Giao diện hỗ trợ tiếng Việt/Anh, sử dụng trên trình duyệt desktop và thích ứng khi thay đổi kích thước cửa sổ.

## Chức năng

- Đăng nhập JWT và phân quyền ADMIN, IT_STAFF, PUR_STAFF, USER.
- Quản lý danh mục, thiết bị, linh kiện, gói license và lượt cấp phát.
- Import Excel tồn đầu kỳ với xem trước, kiểm tra lỗi và xác nhận.
- Nhập kho mua mới qua phiếu nháp, chứng từ, gửi duyệt và lịch sử phiên bản.
- Bàn giao, thu hồi, thanh lý theo quyền và trạng thái nghiệp vụ.
- Sinh biên bản PDF, lưu chứng từ và theo dõi email giả lập.
- Dashboard thống kê và xuất danh sách tài sản ra Excel.

Email hiện được mô phỏng, không gửi SMTP thật. Chứng từ và PDF được lưu thành file cục bộ. Dùng dữ liệu giả lập khi trình diễn.

## Tài liệu Word

- [Tổng quan và bàn giao](docs/handover/README.docx)
- [Hướng dẫn cài đặt](docs/handover/HUONG_DAN_CAI_DAT.docx)
- [Hướng dẫn sử dụng](docs/handover/HUONG_DAN_SU_DUNG.docx)
- [Dữ liệu mẫu](docs/handover/DU_LIEU_MAU.docx)
- [Kịch bản demo](docs/handover/KICH_BAN_DEMO.docx)
- [Backlog phát triển tiếp theo](docs/handover/BACKLOG.docx)

Người cài đặt bắt đầu với hướng dẫn cài đặt và dữ liệu mẫu; người dùng nghiệp vụ đọc hướng dẫn sử dụng; người trình diễn dùng kịch bản demo.

## Công nghệ

Java 21, Spring Boot 3.3.0, PostgreSQL, Flyway, Maven Wrapper; React 18, TypeScript, Vite 5 và npm.

## Khởi chạy trên Windows

Chuẩn bị JDK 21, Node.js tương thích Vite 5, npm và PostgreSQL. Các lệnh dưới đây dùng PowerShell từ gốc repository. Lần đầu cần mạng để tải dependency Maven/npm.

### Tạo database

Kết nối PostgreSQL bằng tài khoản quản trị, chạy từng lệnh SQL ngoài transaction và thay mật khẩu ví dụ:

```sql
CREATE ROLE itam_user LOGIN PASSWORD 'REPLACE_WITH_LOCAL_PASSWORD';
CREATE DATABASE itam_db OWNER itam_user;
```

Nếu role/database đã có, kiểm tra để dùng lại. Tài khoản ứng dụng cần quyền tạo bảng trong schema public của itam_db. Flyway tự chạy migration khi khởi động; không xóa database hoặc sửa migration đã áp dụng để giải quyết lỗi khởi động.

### Backend

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
```

Điền `.env` ở gốc repository:

```dotenv
DB_URL=jdbc:postgresql://localhost:5432/itam_db
DB_USERNAME=itam_user
DB_PASSWORD=REPLACE_WITH_LOCAL_PASSWORD
BACKEND_PORT=8080
DOCUMENT_STORAGE_ROOT=../storage
```

Đặt JWT_SECRET riêng cho môi trường triển khai; không dùng secret mặc định công khai ngoài môi trường thử nghiệm. Không commit file .env.

Terminal thứ nhất:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Backend mặc định tại http://localhost:8080/api. Khi chạy từ backend, ứng dụng đọc được .env ở thư mục cha; đường dẫn storage tương đối cũng tính từ backend. Không cần bật profile dev vì profile đó có cấu hình database cố định, có thể ghi đè giá trị mong muốn.

### Frontend

Terminal thứ hai, từ gốc repository:

```powershell
cd frontend
if (!(Test-Path .env)) { Copy-Item .env.example .env }
npm ci
npm run dev
```

Trong frontend/.env:

```dotenv
VITE_API_BASE_URL=http://localhost:8080/api
```

Mở URL Vite in ra, mặc định http://localhost:5173. Frontend không tự đọc .env ở gốc. Đổi biến frontend cần khởi động lại Vite hoặc build lại. Đổi port bằng `npm run dev -- --port 5174`; biến FRONTEND_PORT trong file mẫu gốc không được vite.config.ts hiện tại đọc.

### Đăng nhập demo

Migration tạo bốn tài khoản `admin@itam.example`, `it01@itam.example`, `pur01@itam.example`, `user01@itam.example`, cùng mật khẩu demo công khai `Password@123`.

Shortcut đăng nhập bật khi chạy Vite dev; bản build chỉ bật nếu VITE_ENABLE_DEMO_LOGIN=true tại thời điểm build. Không sử dụng tài khoản demo cho môi trường thật. Chuẩn bị danh mục và tài sản theo tài liệu dữ liệu mẫu; cài mới không mặc nhiên có toàn bộ dữ liệu của các lần trình diễn trước.

## Test và build

Tại frontend:

```powershell
npm test -- --run
npm run lint
npm run build
```

Backend integration test cần database PostgreSQL riêng dành cho test. Tại backend:

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/itam_test'
$env:TEST_DB_USERNAME = 'YOUR_TEST_USER'
$env:TEST_DB_PASSWORD = 'YOUR_TEST_PASSWORD'
.\mvnw.cmd test
```

Không trỏ test vào database có dữ liệu cần giữ. Chạy `.\mvnw.cmd package` tại backend để tạo JAR trong target. Frontend build nằm trong dist; `npm run preview` dùng để xem bản build local, không thay thế web server production.

## Cấu trúc và lưu trữ

- backend/: API, nghiệp vụ, migration, kiểm thử.
- frontend/: màn hình, API client, bản dịch, kiểm thử.
- storage/: chứng từ và biên bản; sao lưu cùng database để bảo toàn liên kết file.
- docs/handover/: tài liệu Word dành cho người tiếp nhận.
- .tmp/: log, ảnh kiểm tra và artifact tạm.
- project_sources/: tài liệu tham chiếu nội bộ, bị Git ignore; chuyển giao riêng nếu người nhận có quyền sử dụng.

## Giới hạn và hỗ trợ

Prototype chưa bao gồm gửi email thật, chữ ký số, ứng dụng mobile hoặc cấu hình production hoàn chỉnh. Quy trình thay linh kiện trên thiết bị đang sử dụng thuộc backlog, khác với chức năng quản lý quan hệ/thu hồi linh kiện đã có. Các mở rộng cần chốt phạm vi và tiêu chí nghiệm thu.

Khi gặp lỗi, ghi bước tái hiện, vai trò, mã phiếu/tài sản và thông báo; không đính kèm mật khẩu, token hoặc chứng từ thật. Xem hướng dẫn cài đặt để xử lý lỗi database, Flyway, storage và kết nối frontend.
