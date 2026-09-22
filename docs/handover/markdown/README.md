# Tổng quan hệ thống ITAM

ITAM Prototype hỗ trợ quản lý vòng đời tài sản công nghệ thông tin từ khi tiếp nhận đến khi bàn giao, thu hồi và thanh lý. Tài liệu này giúp nhóm tiếp nhận hiểu phạm vi hệ thống, trách nhiệm từng vai trò và cách sử dụng bộ tài liệu đi kèm.

## Đối tượng sử dụng

- Nhóm kỹ thuật: cài đặt, cấu hình, kiểm thử và hỗ trợ vận hành.

- Nhóm IT và mua sắm: quản lý tài sản, chứng từ và các phiếu nghiệp vụ.

- Người được cấp tài sản: xem tài sản thuộc phạm vi được phân quyền.

- Người tổ chức demo hoặc nghiệm thu: chuẩn bị dữ liệu và đối chiếu kết quả từng quy trình.

## Bộ tài liệu

- [HUONG_DAN_CAI_DAT.md](HUONG_DAN_CAI_DAT.md): chuẩn bị máy, database, cấu hình, chạy ứng dụng và xử lý lỗi.

- [HUONG_DAN_SU_DUNG.md](HUONG_DAN_SU_DUNG.md): các bước thao tác theo vai trò, trạng thái và kết quả mong đợi.

- [DU_LIEU_MAU.md](DU_LIEU_MAU.md): tài khoản có sẵn, danh mục và bộ tài sản đề xuất để chuẩn bị demo.

- [KICH_BAN_DEMO.md](KICH_BAN_DEMO.md): trình tự trình diễn, điều kiện đầu vào và các điểm cần xác nhận.

- [BACKLOG.md](BACKLOG.md): các hạng mục phát triển tiếp theo, mức ưu tiên đề xuất và tiêu chí hoàn thành.

## Phạm vi chức năng

Hệ thống có đăng nhập JWT và phân quyền, danh mục, tài sản, quan hệ thiết bị và linh kiện, quản lý gói license cùng các lượt cấp phát. Dữ liệu tồn đầu kỳ có thể nhập từ Excel qua bước xem trước và xác nhận.

Tài sản mua mới được tiếp nhận bằng phiếu nhập kho có chứng từ và bước IT duyệt. Các quy trình bàn giao, thu hồi và thanh lý cập nhật trạng thái tài sản và lưu thông tin giao dịch. Biên bản được xuất PDF; dashboard cung cấp số lượng tài sản và xuất danh sách ra Excel.

## Vai trò và trách nhiệm

- ADMIN: quản trị nghiệp vụ, xử lý các quy trình, duyệt thanh lý và xuất báo cáo.

- IT_STAFF: quản lý kho, kiểm tra nhập kho, bàn giao, thu hồi, lập thanh lý và xem dashboard.

- PUR_STAFF: chuẩn bị phiếu nhập kho và chứng từ, gửi IT kiểm tra và theo dõi kết quả.

- USER: xem tài sản được cấp; phạm vi truy cập được kiểm tra tại backend.

Menu hiển thị theo vai trò. Có menu không đồng nghĩa được thực hiện mọi hành động trên mọi phiếu; trạng thái và phạm vi dữ liệu tiếp tục được kiểm tra khi thao tác.

## Kiến trúc và dữ liệu

Backend dùng Java 21, Spring Boot 3.3.0, JPA, Spring Security và Flyway; frontend dùng React 18, TypeScript và Vite 5. PostgreSQL lưu dữ liệu nghiệp vụ. Chứng từ và PDF được lưu trên filesystem của máy chạy backend.

- backend/: mã API, quy tắc nghiệp vụ, migration và test.

- frontend/: màn hình, bản dịch, API client và test.

- storage/: chứng từ và biên bản; cần sao lưu đồng bộ với database.

- docs/handover/: bộ tài liệu bàn giao.

- .tmp/: log và artifact kiểm tra, không phải dữ liệu nghiệp vụ chính.

## Điều kiện sử dụng

Sử dụng trình duyệt desktop; giao diện thích ứng với việc thay đổi kích thước cửa sổ. Không có yêu cầu sử dụng trên điện thoại. Hỗ trợ ngôn ngữ Việt và Anh.

Frontend mặc định tại http://localhost:5173; API tại http://localhost:8080/api. Nhóm kỹ thuật có thể đổi địa chỉ trong cấu hình theo hướng dẫn cài đặt.

## Giới hạn prototype

Email là thông báo giả lập được ghi nhận trong hệ thống, không gửi SMTP thật. Chứng từ và PDF là file được lưu thật; chỉ đưa nội dung giả lập vào môi trường demo. Prototype chưa có chữ ký số, phê duyệt nhiều cấp hoặc cấu hình vận hành production hoàn chỉnh.

Dữ liệu của các lần demo trước không mặc nhiên có khi cài mới. Người tổ chức cần chuẩn bị tài khoản, danh mục, tài sản và chứng từ theo tài liệu dữ liệu mẫu. Không sửa trực tiếp database để ép trạng thái giao dịch khi trình diễn.

## Tiếp nhận và nghiệm thu

1. Cài đặt hệ thống và xác nhận đăng nhập đúng vai trò.

2. Chuẩn bị dữ liệu riêng cho lần kiểm tra.

3. Thực hiện kịch bản demo, ghi mã phiếu và kết quả quan sát được.

4. Đối chiếu trạng thái tài sản, PDF, email giả lập và báo cáo.

5. Ghi riêng lỗi, phần chưa kiểm tra và người phụ trách xử lý; không suy ra đã nghiệm thu chỉ từ việc ứng dụng khởi động được.

Khi báo lỗi, cung cấp màn hình, vai trò, bước tái hiện và thông báo. Không gửi kèm mật khẩu, token hoặc chứng từ thật. Trước khi chuyển môi trường, sao lưu cả database và storage và kiểm tra đường dẫn lưu file ở máy đích.
