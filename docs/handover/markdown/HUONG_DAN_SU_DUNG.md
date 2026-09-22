# Hướng dẫn sử dụng ITAM Prototype

Tài liệu dành cho quản trị viên, nhân viên IT, nhân viên mua sắm và người được cấp tài sản. Các bước được trình bày theo trình tự làm việc từ dữ liệu nền đến nhập kho, bàn giao, thu hồi và thanh lý. Chuẩn bị hệ thống theo [HUONG_DAN_CAI_DAT.md](HUONG_DAN_CAI_DAT.md) và dữ liệu theo [DU_LIEU_MAU.md](DU_LIEU_MAU.md) trước khi thao tác.

## 1. Đăng nhập và bố cục

Mở http://localhost:5173, nhập email và mật khẩu rồi chọn Đăng nhập. Khi chạy Vite ở chế độ phát triển, màn hình có các nút đăng nhập nhanh; có thể bật chúng ngoài chế độ này bằng VITE_ENABLE_DEMO_LOGIN=true. Đây chỉ là tiện ích demo, không phải cơ chế đổi quyền trong môi trường thật.

Tài khoản demo công khai được tạo bởi migration, cùng mật khẩu Password@123:

| Tài khoản | Vai trò |
| --- | --- |
| admin@itam.example | ADMIN |
| it01@itam.example | IT_STAFF |
| pur01@itam.example | PUR_STAFF |
| user01@itam.example | USER |

Sau khi đăng nhập, dùng bộ chọn ngôn ngữ ở thanh trên cùng. Menu tài khoản cho phép đăng xuất; khi tiện ích demo được bật, menu này còn cho phép đăng nhập nhanh bằng tài khoản khác.

## 2. Quyền theo vai trò

| Chức năng trên giao diện | ADMIN | IT_STAFF | PUR_STAFF | USER |
| --- | --- | --- | --- | --- |
| Danh mục, tài sản, bàn giao, thu hồi, thanh lý, tổng quan | Có | Có | Không | Không |
| Xem danh sách phiếu và chứng từ | Có | Có | Có | Không |
| Tạo/sửa nháp nhập kho, tải lên hoặc gỡ chứng từ của nháp | Có | Không | Có | Không |
| Duyệt/từ chối nhập kho | Có | Có | Không | Không |
| Duyệt/từ chối thanh lý | Có | Không | Không | Không |
| Xử lý liên kết Per-User trước thanh lý | Có | Có | Không | Không |
| Xuất Excel từ Tổng quan | Có | Không | Không | Không |
| Xem tài sản được cấp | Không | Không | Không | Có |

USER có thể tải PDF hoặc chứng từ báo cáo khi backend xác định người đó là người nhận/người trả của phiếu. Giao diện hiện không có menu quản trị người dùng. Quyền cuối cùng luôn do backend kiểm tra, không dựa riêng vào việc menu có hiển thị hay không.

## 3. Danh mục

Vào Quản lý Danh mục để quản lý phòng ban, vị trí, nhà cung cấp và liên hệ, nhóm/loại/trạng thái/tình trạng tài sản, model thiết bị, phần mềm, kiểu gán và thời hạn license.

1. Chọn thẻ danh mục.

2. Tìm kiếm hoặc lọc trạng thái nếu thẻ hỗ trợ.

3. Chọn Thêm mới, nhập các trường bắt buộc và lưu.

4. Dùng Chỉnh sửa hoặc Xóa trên từng dòng. Bản ghi đang được tham chiếu có thể bị backend từ chối xóa.

Model chứa cấu hình mặc định CPU, RAM, ổ lưu trữ và GPU. Khi tài sản không có cấu hình thực tế, màn hình chi tiết dùng giá trị mặc định của model làm cấu hình hiệu lực.

## 4. Tài sản và dữ liệu tồn đầu kỳ

Trang Tài sản hỗ trợ tìm theo mã, tên, serial; lọc theo trạng thái, loại, model, phòng ban và vị trí; xem chi tiết; tạo hoặc sửa dữ liệu nền.

### Tạo một tài sản tồn đầu kỳ

1. Chọn Thêm tài sản tồn đầu kỳ.

2. Nhập tối thiểu tên và loại. Mã tài sản có thể để trống để hệ thống tự sinh.

3. Chọn trạng thái, vị trí, phòng ban, nhà cung cấp và thông tin mua sắm nếu có.

4. Với thiết bị/linh kiện, có thể nhập serial, model, tình trạng và cấu hình thực tế.

5. Với license, chọn phần mềm, kiểu gán, thời hạn, số suất và ngày hết hạn nếu là thuê bao.

6. Lưu và kiểm tra dòng mới trong danh sách.

Tạo trực tiếp và import Excel ở trang này chỉ dành cho dữ liệu nền/tồn đầu kỳ. Tài sản mua mới phải đi qua quy trình Nhập kho.

### Import Excel tồn đầu kỳ

1. Chọn Import Excel rồi Tải file Excel mẫu chuẩn. Giao diện gọi GET /api/v1/asset-imports/template và tải itam_asset_import_template.xlsx.

2. Điền file .xlsx hoặc .xls, tối đa 10 MB và 1.000 dòng dữ liệu.

3. Tải file lên để xem trước. Bước xem trước chưa ghi dữ liệu.

4. Sửa các dòng lỗi/trùng trong file, tải lại, rồi chọn Xác nhận Import.

Chỉ dòng hợp lệ được tạo; mã và serial phải duy nhất trong file và cơ sở dữ liệu. Nếu bỏ trống trạng thái, hệ thống dùng IN_STOCK.

### Quan hệ và license

Trong chi tiết tài sản, có thể gắn linh kiện hoặc license OEM vào thiết bị đang ở kho. Gắn OEM giữ một suất ngay; bàn giao không trừ suất lần hai. License Per-User được cấp qua phiếu bàn giao. Các thay đổi trạng thái, người nhận hoặc quan hệ của tài sản đang sử dụng phải đi qua workflow tương ứng.

## 5. Nhập kho tài sản mua mới

Quy trình có ba trạng thái chính: DRAFT → PENDING → COMPLETED hoặc REJECTED.

### PUR_STAFF hoặc ADMIN lập phiếu

1. Vào Nhập kho, chọn Tạo nháp nhập kho.

2. Thêm tài sản mới hoặc chọn lại tài sản PENDING_IMPORT chưa thuộc phiếu đang chờ/đã hoàn tất.

3. Điền thông tin mua sắm, tình trạng phần cứng và dữ liệu license phù hợp.

4. Tải lên ít nhất một chứng từ mẫu hợp lệ. Hệ thống nhận PDF, PNG, JPEG; tối đa 10 MB; nội dung file phải khớp phần mở rộng.

5. Lưu ghi chú nếu đã thay đổi, rồi chọn Gửi IT kiểm tra.

Khi gửi, hệ thống tạo một revision bất biến và khóa nội dung phiếu. PUR_STAFF hoặc ADMIN có thể Rút về nháp, chỉnh sửa rồi gửi lại; revision cũ vẫn được giữ. Phạm vi prototype cho phép PUR cùng xử lý các phiếu nhập, không giới hạn vào người lập ban đầu.

### IT_STAFF hoặc ADMIN xử lý

1. Mở phiếu PENDING trên trang Nhập kho.

2. Kiểm tra danh sách tài sản, cấu hình, tình trạng, ghi chú và chứng từ.

3. Chọn Duyệt và nhập kho toàn bộ để chuyển tất cả tài sản sang IN_STOCK, hoặc Từ chối và nhập lý do.

Prototype không hỗ trợ duyệt một phần. Phiếu bị từ chối không mở lại; PUR/ADMIN dùng Lập nháp mới từ phiếu bị từ chối.

## 6. Bàn giao

Bàn giao được hoàn tất ngay trong một thao tác của ADMIN/IT_STAFF, không có bước duyệt riêng.

1. Vào Bàn giao, chọn người nhận, vị trí nhận và ngày bàn giao.

2. Chọn thiết bị/linh kiện IN_STOCK; chọn gói Per-User và số suất nếu cần.

3. Chọn Xem trước bộ tài sản. Linh kiện và OEM đang gắn được thêm vào bản xem trước.

4. Kiểm tra toàn bộ danh sách rồi chọn Hoàn tất bàn giao.

Thiết bị/linh kiện được chuyển sang IN_USE và gán người nhận/vị trí. Gói license vẫn là IN_STOCK; hệ thống tạo hoặc kích hoạt các lượt cấp phát. Kết quả phiếu đã hoàn tất được đọc từ snapshot đã chốt.

## 7. Thu hồi

1. Vào Thu hồi, chọn người trả và vị trí nhận.

2. Chọn thiết bị IN_USE thuộc người trả, ngày và lý do.

3. Chọn Kiểm tra để chạy Smart Check.

4. Xem các tài sản bắt buộc đi kèm, quyết định giữ/tách linh kiện và có thu hồi từng license Per-User hay không.

5. Khi không còn mục bị chặn, chọn Hoàn tất thu hồi.

OEM đi cùng thiết bị và không thể thu hồi riêng. Nếu không thu hồi một cấp phát Per-User, cấp phát đó được gỡ khỏi thiết bị nhưng vẫn thuộc người dùng. Tài sản thu hồi hợp lệ trở về kho theo kết quả Smart Check.

## 8. Thanh lý

Ứng viên thanh lý là tài sản DAMAGED, hoặc IN_STOCK có purchase_date không muộn hơn ngày hiện tại trừ ngưỡng cấu hình (mặc định 5 năm). Tài sản IN_USE phải thu hồi trước; tuổi được tính theo ngày mua, không theo ngày tạo bản ghi.

1. ADMIN/IT_STAFF vào Thanh lý, chọn tài sản đủ điều kiện, ngày và lý do, rồi tạo phiếu PENDING.

2. Linh kiện COMPONENT_OF còn hoạt động được tự động thêm cùng thiết bị cha.

3. Nếu có liên kết Per-User, ADMIN/IT_STAFF phải chọn giải phóng suất hoặc giữ cấp phát hoạt động nhưng gỡ khỏi thiết bị.

4. ADMIN mở phiếu chờ, kiểm tra lại rồi Duyệt hoặc Từ chối với lý do.

Duyệt chuyển tài sản sang RETIRED và bỏ người sử dụng. OEM vẫn gắn với thiết bị, trở về trạng thái giữ suất; license Per-User đang được cấp trực tiếp không thể đem thanh lý như một tài sản license.

## 9. Chứng từ, PDF và email giả lập

Trang Chứng từ cho ADMIN, IT_STAFF và PUR_STAFF lọc phiếu, xem/tải chứng từ. Chỉ ADMIN/PUR_STAFF được thay đổi chứng từ khi phiếu nhập còn DRAFT; phiếu đã gửi là chỉ đọc.

Với phiếu hoàn tất, panel Biên bản PDF và email giả lập hiển thị trạng thái phát hành, cho phép tải PDF. ADMIN/IT_STAFF có thể tạo phiên bản PDF mới hoặc gửi lại email. PDF là tệp thật được ghi vào storage; gateway email mặc định chỉ ghi log giả lập, không gửi thư ra ngoài. Lỗi PDF/email không đảo ngược trạng thái nghiệp vụ đã hoàn tất.

## 10. Tổng quan và xuất báo cáo

Trang Tổng quan hiển thị số lượng tài sản theo trạng thái, loại, vị trí, phòng ban; số phiếu nhập và thanh lý đang chờ. Mỗi bản ghi tài sản được tính một lần, bao gồm PENDING_IMPORT, RETIRED, linh kiện và gói license; đây không phải số bộ thiết bị hoặc số suất license. Nhóm phòng ban dùng trực tiếp assets.department_id, không suy ra từ phòng ban của người nhận.

ADMIN có nút Xuất Excel; IT_STAFF chỉ xem số liệu. File xuất là ảnh chụp toàn bộ danh sách tài sản hiện tại, không áp dụng bộ lọc từ trang Tài sản.

## 11. Giới hạn cần biết

- Prototype dùng PostgreSQL và storage cục bộ; không phải cấu hình production.

- Không có tự đăng ký, quản trị người dùng trên UI, quy trình phê duyệt nhiều cấp, chữ ký số hoặc gửi email thật.

- Các mã phiếu bàn giao/thu hồi/thanh lý chứa UUID nên không dự đoán trước được.

- Chỉ kiểm tra trên bố cục desktop. Khi trình chiếu, thay đổi kích thước cửa sổ desktop nếu cần; không dùng viewport điện thoại làm tiêu chí bàn giao.
