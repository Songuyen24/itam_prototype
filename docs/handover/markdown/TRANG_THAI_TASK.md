# Trạng thái kiểm tra bàn giao

Ngày cập nhật: 13/11/2026. Đây là trạng thái kiểm tra mã nguồn hiện tại, không thay cho nghiệm thu trên máy bên nhận. Kết quả demo và mã phiếu ngày 25/09/2026 được lưu riêng trong [biên bản bàn giao](BIEN_BAN_BAN_GIAO.md) như bằng chứng lịch sử.

- H01: màn hình Thu hồi hiện tải lịch sử `RECOVERY` qua API giao dịch, có phân trang và trạng thái tải/lỗi. Chưa kiểm tra lại thao tác reload và đăng nhập bằng trình duyệt thật.
- Backend: `mvn test` chạy 371 test, 0 failure, 0 error, 1 skipped (370 đạt); frontend: 252/252 test trong 46 file đạt. Lint, TypeScript và build đạt.
- Các kiểm tra tự động không thay thế kiểm tra trên máy nhận, SMTP thật, toàn bộ nhánh giao diện hoặc xác nhận chứng từ nghiệp vụ.

Không suy ra trạng thái từng task T10–T18/T11B/T12B từ kết quả kiểm thử tổng hợp này.
