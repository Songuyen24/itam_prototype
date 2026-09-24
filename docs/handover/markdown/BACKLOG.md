# Kế hoạch phát triển tiếp theo của ITAM

Tài liệu dành cho quản lý dự án, nhóm phát triển và đại diện nghiệp vụ để lựa chọn công việc sau giai đoạn prototype. Các mức ưu tiên là đề xuất phục vụ lập kế hoạch, không phải cam kết tiến độ hoặc xác nhận đã được phê duyệt.

## Nguyên tắc lựa chọn

Ưu tiên hoàn tất nghiệm thu các luồng hiện có trước khi mở rộng. Với mỗi hạng mục, xác định người phụ trách, dữ liệu đầu vào, quyết định nghiệp vụ còn thiếu và tiêu chí hoàn thành. Không đưa các chức năng đã có vào danh sách chưa triển khai chỉ vì chưa có kết quả nghiệm thu.

## Ưu tiên 1 Nghiệm thu môi trường bàn giao

Mục tiêu là xác nhận frontend, backend, PostgreSQL và storage làm việc cùng nhau trên môi trường nhóm tiếp nhận sử dụng.

- Chạy kịch bản demo với đủ bốn vai trò, cả thao tác thành công và thao tác bị chặn đúng quyền hoặc trạng thái.

- Đối chiếu tài sản, lịch sử phiếu, PDF, email giả lập và Excel.

- Ghi phiên bản ứng dụng, cấu hình không nhạy cảm, mã phiếu và kết quả thực tế.

- Hoàn thành khi các bước trong phạm vi nghiệm thu có bằng chứng; mọi lỗi và trường hợp chưa kiểm tra có người phụ trách xử lý.

## Ưu tiên 1 Chuẩn hóa bộ dữ liệu demo

Mục tiêu là chuẩn bị lại dữ liệu một cách nhất quán trên môi trường mới.

- Chốt bộ danh mục, tài sản, license và chứng từ giả lập theo [DU_LIEU_MAU.md](DU_LIEU_MAU.md).

- Xác định phần migration tạo sẵn và phần cần nhập bổ sung.

- Nếu làm công cụ nạp dữ liệu, giới hạn vào database demo, xử lý chạy lại không nhân bản dữ liệu và không sửa migration đã phát hành.

- Hoàn thành khi người mới có thể chuẩn bị và chạy kịch bản từ tài liệu mà không phụ thuộc dữ liệu trên máy người phát triển.

## Ưu tiên 2 Thay linh kiện trên thiết bị đang sử dụng

Đây là hạng mục được để lại cho giai đoạn sau. Quan hệ linh kiện và thu hồi riêng hiện có không đồng nghĩa đã có toàn bộ quy trình thay linh kiện.

- Thu hồi linh kiện cũ, gỡ quan hệ và ghi nhận tình trạng.

- Cấp/gắn linh kiện mới cho thiết bị, giữ đúng người sử dụng và cập nhật cấu hình thực tế.

- Liên kết các giao dịch của cùng lần thay, giữ phiếu bàn giao ban đầu và lịch sử.

- Cần chốt cách liên kết hai phiếu, xử lý linh kiện hỏng và tình huống tháo nhưng chưa lắp xong. OEM không được xử lý chuyển tự do như RAM hoặc SSD.

- Hoàn thành khi không cấp trùng linh kiện, lịch sử truy vết được và cấu hình không ghi nhận thay xong trước khi thao tác thực sự hoàn tất.

## Ưu tiên 2 Hoàn thiện vận hành và kiểm thử tự động

- Thiết lập CI chạy test backend với database riêng, test/lint/build frontend và kiểm tra migration.

- Đóng gói artifact theo phiên bản; mô tả cấu hình môi trường và quy trình nâng cấp.

- Thử sao lưu và khôi phục đồng bộ database với storage.

- Hoàn thành khi build tái lập, test không tác động dữ liệu thật, bản sao lưu được khôi phục thử và tài liệu vận hành dùng được trên máy khác.

## Trước production Quản lý chứng từ thật

Phạm vi cần được nghiệp vụ xác nhận trước khi triển khai.

- Chốt loại chứng từ bắt buộc trước duyệt, biên bản đã ký, thời hạn lưu và quyền truy cập.

- Xác định kiểm tra file, giới hạn dung lượng, phiên bản và xử lý file thiếu/hỏng.

- Hoàn thành khi quyền tải file được kiểm tra ở backend, lịch sử thay đổi rõ ràng và dữ liệu phục hồi đồng bộ.

## Trước production Gửi email thật

- Chọn nhà cung cấp hoặc SMTP, địa chỉ gửi/nhận và cấu hình bí mật.

- Bổ sung xử lý lỗi, retry và chống gửi lặp; giữ lịch sử từng lần phát hành.

- Hoàn thành khi gửi được qua môi trường thử của nhà cung cấp, xử lý mất kết nối có kiểm soát và không đảo ngược giao dịch đã hoàn tất khi email lỗi.

- Gateway giả lập hiện tại không được xem là tích hợp email thật.

## Trước production Tài khoản và bảo mật cấu hình

- Chốt nguồn tài khoản, quản trị nội bộ hay SSO, khóa tài khoản và chính sách token/session.

- Loại tài khoản demo khỏi môi trường thật; dùng secret riêng, HTTPS và giới hạn CORS phù hợp.

- Kiểm tra vai trò, quyền trên từng bản ghi và đường dẫn tải chứng từ.

- Hoàn thành khi không sử dụng mật khẩu/secret demo, quyền được kiểm tra ở backend và cấu hình nhạy cảm không nằm trong mã nguồn hoặc log chia sẻ.

## Theo nhu cầu Báo cáo nâng cao

Dashboard và Excel cơ bản đã có. Mọi mở rộng cần chốt mẫu, bộ lọc, phạm vi quyền và cách tính trước khi phát triển.

- Phân biệt số bản ghi tài sản, bộ thiết bị và số suất license; quy định rõ cách tính tài sản chờ nhập/đã thanh lý.

- Chỉ xem xét khấu hao hoặc tích hợp kế toán khi có yêu cầu riêng.

- Hoàn thành khi số liệu đối soát được với dữ liệu gốc và bộ lọc/quyền không làm lộ dữ liệu ngoài phạm vi.

## Ngoài phạm vi mặc định

Không phát triển ứng dụng mobile hoặc thiết kế riêng cho điện thoại. Vẫn bảo đảm thao tác trên cửa sổ desktop thay đổi kích thước và dùng được bằng bàn phím. Lỗi mới phải được xác minh trước khi ghi thành công việc; không mở lại một lỗi lịch sử đã được xử lý chỉ vì còn xuất hiện trong báo cáo cũ.

## Thông tin cần có khi giao việc

Mỗi hạng mục cần có mục tiêu, phạm vi, người phụ trách, người xác nhận nghiệp vụ, phụ thuộc, dữ liệu kiểm thử, tiêu chí nghiệm thu và kế hoạch kiểm tra. Ước lượng thời gian chỉ thực hiện sau khi các thông tin này đã rõ.
