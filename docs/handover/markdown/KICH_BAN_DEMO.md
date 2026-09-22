# Kịch bản demo ITAM Prototype

Tài liệu dành cho người trình diễn và người theo dõi nghiệm thu ITAM. Kịch bản đi qua phân quyền, nhập kho, bàn giao, thu hồi, thanh lý và báo cáo bằng dữ liệu giả lập. Thời lượng dự kiến 20–30 phút sau khi đã chuẩn bị môi trường. Mỗi bước nêu vai trò và kết quả để người theo dõi có thể đối chiếu.

## 1. Chuẩn bị

- Cơ sở dữ liệu đã chạy đủ migration và đăng nhập được bốn tài khoản seed.

- Đã tạo phòng ban, vị trí, nhà cung cấp, model và phần mềm tổng hợp.

- Có ít nhất một PDF giả lập dưới 10 MB.

- Có một laptop, RAM, gói OEM và gói Per-User ở IN_STOCK để bàn giao.

- Có một tài sản DAMAGED hoặc một tài sản IN_STOCK đủ tuổi theo purchase_date để thanh lý.

- Dùng cửa sổ desktop. Có thể thu/phóng hoặc thay đổi kích thước cửa sổ desktop; không chuyển sang viewport điện thoại.

Nếu dữ liệu đã được dùng ở lần demo trước, tạo mã mới thay vì cố khôi phục thủ công trạng thái workflow.

## 2. Mở đầu  phân quyền và tổng quan

Vai trò: ADMIN

1. Đăng nhập admin@itam.example / Password@123.

2. Mở Tổng quan.

3. Chỉ ra bốn nhóm thống kê và hai số phiếu đang chờ.

4. Chọn Xuất Excel và kiểm tra trình duyệt tải file.

Kết quả mong đợi: tổng quan phản ánh dữ liệu hiện tại; nút xuất chỉ hiện cho ADMIN.

Đổi sang USER bằng đăng nhập lại (hoặc bộ chuyển tài khoản khi tiện ích demo được bật), mở Tài sản của tôi.

Kết quả mong đợi: USER chỉ thấy tài sản được cấp và trang tài khoản; không thấy menu quản trị kho/workflow.

## 3. Nhập kho tài sản mua mới

### 3.1 PUR lập và gửi phiếu

Vai trò: PUR_STAFF

1. Đăng nhập pur01@itam.example.

2. Vào Nhập kho → Tạo nháp nhập kho.

3. Thêm một laptop mua mới, ví dụ DEMO-NEW-001, chọn loại, tình trạng, model, vị trí, nhà cung cấp, ngày/giá mua.

4. Tải lên PDF giả lập, chọn loại chứng từ phù hợp.

5. Lưu ghi chú nếu có, rồi chọn Gửi IT kiểm tra.

6. Mở phần lịch sử phiên bản.

Kết quả mong đợi: phiếu chuyển DRAFT → PENDING; revision 1 chỉ đọc; nội dung và chứng từ hiện tại bị khóa.

### 3.2 IT duyệt toàn bộ

Vai trò: IT_STAFF

1. Đăng nhập it01@itam.example.

2. Vào Nhập kho, mở phiếu vừa gửi.

3. Kiểm tra tài sản, tình trạng, cấu hình và chứng từ.

4. Chọn Duyệt và nhập kho toàn bộ và xác nhận.

Kết quả mong đợi: phiếu thành COMPLETED; tài sản chuyển từ PENDING_IMPORT sang IN_STOCK; panel phát hành hiển thị PDF/email giả lập sau khi xử lý thành công.

Điểm cần nói rõ: đây là luồng mua mới. Nút Import Excel trên trang Tài sản là luồng tồn đầu kỳ riêng.

### Nhánh từ chối tùy chọn

Nếu cần minh họa từ chối, lập phiếu thứ hai. IT chọn Từ chối nhập kho, nhập lý do; PUR mở phiếu REJECTED và chọn Lập nháp mới từ phiếu bị từ chối.

Kết quả mong đợi: phiếu cũ giữ nguyên REJECTED; hệ thống tạo một nháp mới, không mở lại phiếu cũ.

## 4. Bàn giao thiết bị, linh kiện và license

Tiền điều kiện: laptop và RAM ở IN_STOCK, RAM đã gắn với laptop; OEM đã gắn và giữ suất; gói Per-User còn suất.

Vai trò: IT_STAFF

1. Vào Bàn giao.

2. Chọn người nhận user01@itam.example, vị trí nhận và ngày hiện tại.

3. Chọn laptop; thêm một suất từ gói Per-User và có thể chọn gắn vào laptop trong phiếu.

4. Chọn Xem trước bộ tài sản.

5. Chỉ ra RAM và OEM được đưa vào bộ đi kèm; kiểm tra số suất Per-User.

6. Chọn Hoàn tất bàn giao.

Kết quả mong đợi: laptop/RAM chuyển sang IN_USE và thuộc USER; OEM được kích hoạt từ suất đã giữ mà không trừ thêm; một lượt cấp phát Per-User được tạo; phiếu COMPLETED có snapshot.

Vai trò: USER

Đăng nhập user01@itam.example, mở Tài sản của tôi.

Kết quả mong đợi: người dùng thấy bộ tài sản vừa nhận; không truy cập được danh sách phiếu quản trị.

## 5. Thu hồi có Smart Check

Vai trò: IT_STAFF

1. Vào Thu hồi, chọn người trả là Nguyễn Văn A và vị trí kho nhận.

2. Chọn laptop vừa bàn giao, nhập ngày và lý do.

3. Chọn Kiểm tra.

4. Xem danh sách bắt buộc: thiết bị, linh kiện và OEM; chọn giữ hoặc tách linh kiện.

5. Với Per-User, chọn thu hồi để giải phóng suất, hoặc bỏ chọn để chỉ gỡ khỏi thiết bị nhưng vẫn giữ cấp cho người dùng.

6. Chọn Hoàn tất thu hồi.

Kết quả mong đợi: không còn mục bị chặn; tài sản hợp lệ trở về IN_STOCK; quan hệ và lượt cấp phát được xử lý đúng lựa chọn; phiếu thu hồi COMPLETED.

Để minh họa bảo vệ nghiệp vụ, có thể bắt đầu một lần Smart Check mới và chỉ chọn OEM riêng.

Kết quả mong đợi: hệ thống đánh dấu mục bị chặn và không cho hoàn tất; không ghi giao dịch thu hồi.

## 6. Thanh lý và xử lý license

### 6.1 IT lập phiếu

Vai trò: IT_STAFF

1. Vào Thanh lý.

2. Tìm DEMO-DMG-001 hoặc DEMO-OLD-001, chọn tài sản, ngày và lý do.

3. Chọn Kiểm tra và tạo phiếu rồi xác nhận.

Kết quả mong đợi: tài sản DAMAGED xuất hiện dù chưa đủ 5 năm; tài sản IN_STOCK chỉ xuất hiện khi ngày mua đã đủ ngưỡng; phiếu mới là PENDING.

Nếu chọn thiết bị có linh kiện COMPONENT_OF, linh kiện còn hoạt động được tự động thêm. Nếu thiết bị còn liên kết Per-User, xử lý tất cả quyết định được yêu cầu trước khi duyệt.

### 6.2 ADMIN duyệt

Vai trò: ADMIN

1. Mở phiếu trong danh sách Phiếu chờ duyệt.

2. Kiểm tra dấu Tự động thêm, cảnh báo OEM và các quyết định Per-User.

3. Chọn Duyệt và xác nhận.

Kết quả mong đợi: phiếu thành COMPLETED; tài sản chuyển RETIRED, không còn người sử dụng; OEM vẫn giữ suất và liên kết với thiết bị đã thanh lý.

Nhánh tùy chọn: tạo phiếu khác rồi chọn Từ chối, nhập lý do. Kết quả là REJECTED, tài sản không đổi trạng thái.

## 7. Chứng từ và phát hành

Vai trò: ADMIN hoặc IT_STAFF

1. Vào Chứng từ, lọc lần lượt IMPORT, HANDOVER, RECOVERY, DISPOSAL.

2. Mở phiếu đã hoàn tất và tải PDF.

3. Quan sát trạng thái PDF, phiên bản, thời gian phát hành và email gần nhất.

4. Với ADMIN/IT_STAFF, thử Tạo phiên bản PDF mới hoặc Gửi lại email.

Kết quả mong đợi: PDF tải được và phiên bản tăng khi sinh lại; email chuyển trạng thái theo gateway giả lập. Nêu rõ email chỉ được ghi log, còn PDF/tệp chứng từ được lưu thật trong thư mục storage cục bộ.

## 8. Kết thúc và đối chiếu

Quay lại Tổng quan bằng ADMIN:

1. Kiểm tra số tài sản theo IN_STOCK, IN_USE, RETIRED.

2. Kiểm tra số phiếu chờ đã giảm sau khi duyệt/từ chối.

3. Xuất Excel và đối chiếu mã, trạng thái, người dùng, số suất license và ngày mua của vài dòng vừa thao tác.

Không tuyên bố prototype đã gửi email thật, có chữ ký số, có phê duyệt nhiều cấp hoặc đã được nghiệm thu production. Kết quả buổi demo nên ghi riêng với mã phiếu thực tế, người thực hiện và các lỗi quan sát được.

## 9. Xử lý nhanh khi demo

| Hiện tượng | Cách xử lý |
| --- | --- |
| Không thấy nút đăng nhập nhanh | Đang không chạy Vite dev và chưa bật VITE_ENABLE_DEMO_LOGIN; nhập tài khoản seed thủ công |
| Không gửi được phiếu nhập | Kiểm tra có ít nhất một tài sản hợp lệ, tình trạng phần cứng/dữ liệu license đầy đủ và ít nhất một chứng từ |
| Phiếu báo đã thay đổi/khóa | Tải lại phiếu; thao tác đang dùng version hoặc fingerprint cũ |
| Không thấy tài sản thanh lý | Kiểm tra trạng thái và purchase_date; IN_USE phải thu hồi trước |
| Không duyệt được thanh lý | Chỉ ADMIN duyệt; xử lý hết liên kết Per-User trước |
| Email không đến hộp thư | Đúng với cấu hình prototype: gateway chỉ ghi log giả lập |
