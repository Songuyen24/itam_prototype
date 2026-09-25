# Biên bản chuẩn bị bàn giao ITAM Prototype

Ngày kiểm tra: 25/09/2026. Hồ sơ này ghi kết quả kỹ thuật đã thực hiện và các điều kiện còn phải xác nhận khi tiếp nhận. Trạng thái: **đã kiểm tra luồng chính, còn lỗi lịch sử thu hồi H01 và kiểm tra bố cục Word; chưa ký nghiệm thu**.

## Phiên bản và phạm vi

- Nhánh: `develop`.
- Source được kiểm tra: `a786a3c45cd32b34c20592dcf3a2242e72cda4d5`.
- Tài liệu bàn giao được cập nhật sau commit trên, hiện chưa commit; không có release/tag mới được tạo.
- T10–T18, gồm T11B và T12B, đã merge. Đối chiếu từng task, source, test và commit tại [TRANG_THAI_TASK.md](TRANG_THAI_TASK.md).
- Có đăng nhập/phân quyền, danh mục, tài sản và license, import Excel, nhập kho, bàn giao, thu hồi, thanh lý, chứng từ/PDF, email giả lập, dashboard và export Excel.

## Môi trường kiểm tra

Windows, Java 21.0.10, Node.js 24.15.0, npm 11.12.1, PostgreSQL 18.4. Backend Spring Boot 3.3.0; frontend Vite 5.4.21 theo lockfile.

Đợt kiểm tra dùng cụm PostgreSQL riêng tại `127.0.0.1:55432`, chỉ lắng nghe loopback. Database test là `itam_handover_test`; database demo là `itam_handover_demo`; database phục hồi thử là `itam_handover_restore`. Cụm tạm dùng trust để kiểm tra local, không phải cấu hình bàn giao/production. Database và service PostgreSQL có sẵn trên máy không bị sửa.

Backend demo dùng cổng 18080, frontend 5173. Storage demo tách tại `.tmp/archive/handover-20260925/storage/`. `.env` có sẵn không bị đọc ra đầu ra, sửa hoặc đưa vào bộ bàn giao. Khi cài ở máy nhận, dùng role có mật khẩu riêng và `.env.example` theo hướng dẫn cài đặt.

Các tiến trình backend/frontend/PostgreSQL tạm đã dừng sau kiểm tra; không cài service mới. Muốn xem lại demo, phục hồi bộ snapshot và chạy theo hướng dẫn, không dùng URL kiểm tra như một dịch vụ đang hoạt động.

Flyway V1–V21 đã chạy thành công trên database demo trống; startup và JPA validate thành công. Test migration trên schema rỗng cũng đã chạy trong suite. PostgreSQL 18.4 có cảnh báo phiên bản mới hơn mức Flyway đi kèm đã kiểm chứng chính thức (16); kết quả local đạt không đồng nghĩa chứng nhận hỗ trợ mọi phiên bản PostgreSQL.

## Lệnh cài đặt và kiểm tra

Thực hiện theo [HUONG_DAN_CAI_DAT.md](HUONG_DAN_CAI_DAT.md): tạo role/database riêng, sao chép `.env.example` nếu chưa có `.env`, đặt DB_URL/DB_USERNAME/DB_PASSWORD/JWT_SECRET/DOCUMENT_STORAGE_ROOT, chạy backend từ `backend` bằng `.\mvnw.cmd spring-boot:run`; tại `frontend` chạy `npm ci` rồi `npm run dev`.

Các lệnh cuối đã chạy:

```powershell
# Tại backend; giá trị bên dưới dành riêng cho cụm tạm của lần kiểm tra này
$env:TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:55432/itam_handover_test'
$env:TEST_DB_USERNAME = 'handover_test'
$env:TEST_DB_PASSWORD = ''
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE = '4'
$env:SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE = '1'
$env:DOCUMENT_STORAGE_ROOT = '../.tmp/archive/handover-20260925/test-storage'
.\mvnw.cmd test

# Tại frontend
npm test -- --run
npm run lint
npm run build
```

Kết quả: backend **245 test, 0 failure, 0 error, 0 skipped**; frontend **24 file, 140 test đạt**; lint và build exit 0. Lần backend đầu có 34 error do PostgreSQL hết connection (`too many clients`); lần chạy lại giới hạn pool như trên đạt toàn bộ. Không sửa mã ứng dụng để làm test qua.

Đợt này dùng dependency đã có trên máy; không chạy cài dependency từ cache rỗng. Cài đặt hoàn toàn trên máy người nhận vẫn là bước xác nhận tiếp theo.

## Tài khoản demo

Bốn tài khoản được migration seed, cùng mật khẩu demo công khai `Password@123`:

- ADMIN: `admin@itam.example`.
- IT_STAFF: `it01@itam.example`.
- PUR_STAFF: `pur01@itam.example`.
- USER: `user01@itam.example`.

Không bàn giao mật khẩu database, JWT secret, token đăng nhập hay tài khoản thật. Các tài khoản trên chỉ dành cho prototype.

## Dữ liệu và kết quả demo thực tế

Đã tạo phòng ban HO-IT, vị trí HO-KHO, nhà cung cấp HO-NCC, model Laptop demo handover và phần mềm Office demo handover. Nhóm COMPONENT được bật rõ ràng sau seed. Tài sản gồm HO-20260925-LAP, HO-20260925-RAM, HO-20260925-OEM và HO-20260925-PER_USER. Hai gói license có 10 suất/gói. Hóa đơn demo một trang ghi rõ dữ liệu giả lập; không có license key thật.

1. **Nhập kho**: `IMP-c8dab367-3034-4ad3-8a79-0de5bc0c0ea1` (ID 43). PUR tạo nháp, thêm laptop, upload hóa đơn, submit; IT approve. Phiếu COMPLETED, revision 1, laptop IN_STOCK. PUR tự approve trả 403. Thao tác nghiệp vụ thực hiện qua API thật; PUR đã mở phiếu và đối chiếu giao diện chỉ đọc trên trình duyệt.
2. **Bàn giao**: `HO-abe92c7b-887e-4b44-a284-560313235df1` (ID 55). IT thực hiện chọn tài sản, người nhận, vị trí, xem trước và hoàn tất trên trình duyệt. Bộ gồm laptop, RAM, OEM và một suất Per-User gắn laptop. USER xem được bốn bản ghi; không có menu quản trị. USER gọi API chi tiết phiếu bàn giao trả 403.
3. **Thu hồi**: `RC-F5B0572E`. IT Smart Check và complete qua API thật; giữ RAM theo máy, thu hồi suất Per-User. Laptop về IN_STOCK, Per-User còn 10/10 suất, USER không còn tài sản. OEM vẫn giữ một suất theo laptop.
4. **Thanh lý**: `DI-3d242be0-8a77-4879-98b0-ca68c84d8503`. IT tạo phiếu từ laptop đã thu hồi có ngày mua 01/01/2020; RAM tự đi cùng. IT tự approve trả 403; ADMIN approve thành COMPLETED. Laptop/RAM RETIRED, không còn người dùng; OEM giữ một suất.
5. **Chứng từ và báo cáo**: bốn PDF download trả 200, có signature PDF, mỗi tệp một trang; đã render và xem trực quan cả bốn trang. Email log có 7 lần gửi giả lập: nhập chờ IT, nhập hoàn tất cho PUR, bàn giao/thu hồi cho USER, thanh lý chờ ADMIN và hoàn tất cho PUR/kế toán cấu hình. `SENT` ở đây là giả lập qua LocalEmailGateway, không có SMTP thật.
6. **Đối soát**: dashboard 2 IN_STOCK (hai gói license), 2 RETIRED, 0 phiếu nhập/0 phiếu thanh lý chờ. Excel có 4 dòng tài sản; OEM tổng/còn/đã cấp = 10/9/1; Per-User = 10/10/0. PUR xem dashboard và IT export Excel bị chặn 403.

## Kiểm tra giao diện desktop

Đã đăng nhập ADMIN, IT, PUR và USER trên trình duyệt local. Đã xem bàn giao hoàn tất ở 1440×900, tài sản USER ở 1280×800, nhập kho PUR ở 1024×768, và dashboard ADMIN tại cả ba kích thước. Dashboard không tràn ngang document; nhập kho 1024 px chuyển danh sách/chi tiết thành bố cục dọc. Đã đổi Việt/Anh trên phiếu nhập.

Đây là kiểm tra các màn hình đại diện và luồng chính, không phải kiểm tra mọi màn hình ở mọi kích thước hay mọi nhánh nghiệp vụ bằng UI. Không kiểm tra mobile/tablet.

Kiểm tra thêm phát hiện **H01**: mở Thu hồi → Lịch sử trong phiên trang mới báo không có phiếu, dù RC-F5B0572E đã lưu. Source `frontend/src/features/recovery/pages/RecoveriesPage.tsx:9–16` chỉ giữ history trong useState và không tải từ API. Đã kiểm tra cách tra cứu tạm bằng Chứng từ: phiếu RC-F5B0572E, trạng thái COMPLETED và PDF phiên bản 1 vẫn hiển thị. Không mất dữ liệu backend; lỗi nằm ở danh sách lịch sử của màn hình Thu hồi. Chưa sửa mã trong đợt này.

## Tệp và dữ liệu chuyển giao

- Bộ tài liệu Markdown nằm trong `docs/handover/markdown/`; Word trong `docs/handover/word/`.
- Đã kiểm tra 54 liên kết nội bộ Markdown/Word và cấu trúc OOXML của 8 file Word. Bộ tài liệu cũ không có ảnh nhúng; không có đường dẫn ảnh minh họa để xác minh. Ảnh render PDF dùng riêng cho kiểm tra, không coi là ảnh UI hướng dẫn.
- Log kiểm tra, JSON kết quả demo, PDF/Excel mẫu và snapshot lưu local tại `.tmp/archive/handover-20260925/`, được Git ignore.
- `itam-handover-demo.dump`: snapshot chỉ chứa dữ liệu demo của database riêng. `demo-storage.zip`: storage tương ứng gồm hóa đơn và bốn PDF. Chuyển cả hai cùng nhau nếu bên nhận cần xem lại lần demo này; không chuyển thư mục pgdata, log/token hoặc `.env` của máy phát triển.
- Gói local `ITAM-demo-20260925.zip` chứa hai file trên, hướng dẫn phục hồi và SHA256SUMS; không chứa log hoặc cấu hình bí mật.
- Đã thử `pg_restore --no-owner --no-privileges --exit-on-error` vào database mới: exit 0, 4 assets, 4 transactions, 5 documents. Kiểm tra tệp storage trong bộ zip phải đi cùng khi phục hồi ở máy nhận; không coi phục hồi database riêng là đủ cho file.
- Snapshot là trạng thái sau demo, không phải bộ seed trước giao dịch. Để trình diễn lại vòng đời, tạo mã tài sản mới theo Dữ liệu mẫu; không ép asset RETIRED về IN_STOCK bằng SQL.
- `.env`, storage nghiệp vụ, target, dist, node_modules và .tmp không nằm trong danh sách tệp được Git theo dõi cho bộ thay đổi này. Các tệp cũ không rõ nguồn được giữ nguyên.
- Ngoài tài liệu, chỉ thêm placeholder JWT_SECRET và chú thích vào `.env.example`; không đổi source backend/frontend. Task plan trong project_sources bị Git ignore nên không xuất hiện trong diff thông thường; bản trạng thái công khai trong docs/handover được đưa vào bộ thay đổi.

## Giới hạn và điều kiện còn mở

- H01 cần tải danh sách thu hồi từ backend khi mở trang, có phân trang và trạng thái loading/error; kiểm tra phiếu vẫn hiển thị sau reload/đăng nhập lại bằng ADMIN và IT. Chi tiết phạm vi và tiêu chí chấp nhận ở BACKLOG. Đây là lỗi cần sửa, không phải tính năng mới tùy chọn.
- Word đã đồng bộ nội dung từ Markdown và kiểm tra cấu trúc; **chưa đạt kiểm tra hình thức từng trang** vì renderer thiếu `soffice.exe`. Cần mở/render lại cả bộ ở môi trường có LibreOffice trước ký nhận. Không dùng trạng thái này để kết luận Word không có lỗi bố cục.
- Chưa có tên người nhận, người bàn giao thực tế và người xác nhận nghiệp vụ. Không tự điền hoặc ký thay.
- Chưa nghiệm thu trên máy bên nhận, chưa cài dependency từ môi trường hoàn toàn trống, chưa xác nhận production.
- Các nhánh UI từ chối, rút/gửi lại, retry và import Excel chưa chạy thủ công đầy đủ trong buổi này; suite tự động đã chạy.
- Email giả lập, PDF/chứng từ lưu local; chưa có ký số, QR/tem, SMTP thật, mobile, CI hoặc cấu hình production hoàn chỉnh.
- Backlog chi tiết, gồm quy trình thay linh kiện đang dùng và vận hành, nằm ở [BACKLOG.md](BACKLOG.md). Không tự mở rộng nghiệp vụ trong đợt chuẩn bị bàn giao.

## Xác nhận tiếp nhận

- Người bàn giao: ........................................................
- Người nhận: ............................................................
- Người xác nhận nghiệp vụ: ..............................................
- Ngày tiếp nhận: ........................................................
- Phiên bản/commit tài liệu sau khi tự commit: ............................
- Kết quả chạy lại trên máy nhận: .........................................
- Xác nhận đã xem Word và các giới hạn còn mở: ............................
- Ý kiến và chữ ký: ......................................................
