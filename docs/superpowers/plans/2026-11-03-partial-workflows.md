# Partial workflows implementation plan

**Goal:** Ghi nhận thực hiện từng phần, phê duyệt từng dòng và ngăn thao tác xung đột theo các quyết định đã chốt trong cuộc trao đổi ngày 03/11/2026.

**Architecture:** Tái sử dụng transaction, allocation, reservation, audit và publication hiện tại. Lưu kết quả từng dòng riêng với nội dung đề nghị; kiểm tra phiên bản và giữ chỗ trong giao dịch DB. Không khóa theo màn hình đang mở.

**Tech stack:** Spring Boot/JPA/JDBC/PostgreSQL/Flyway; React/TypeScript; JUnit và Vitest.

## Quyết định nghiệp vụ

- Bàn giao/thu hồi xác nhận từng phần; không rút toàn phiếu về nháp sau khi có phần đã thực hiện.
- Hủy dòng chưa thực hiện theo quyền xử lý hiện hành, bắt buộc lý do, người thao tác và thời gian; giải phóng đúng phần đang giữ. Đổi tài sản/người nhận bằng cách hủy phần chưa thực hiện và lập phiếu mới.
- Khi hết dòng chờ, kết thúc phiếu và thể hiện số dòng thực hiện/hủy, không đánh đồng hủy với thực hiện.
- ADMIN duyệt/từ chối thanh lý từng dòng; từ chối có lý do và giữ nội dung đề nghị.
- ADMIN hoặc IT_STAFF xác nhận thực hiện thanh lý; bắt buộc chứng từ và ngày thực hiện. Chỉ lúc này tài sản mới chuyển đã thanh lý.
- Phần được duyệt thanh lý tiếp tục giữ đến khi thực hiện hoặc hủy có lý do. Không được duyệt thì giải phóng.
- Sau 7 ngày làm việc kể từ duyệt, đánh dấu quá hạn và thông báo ADMIN/người phụ trách, không tự giải phóng. ADMIN/IT_STAFF được gia hạn có lý do và lịch sử.
- Linh kiện đang lắp và OEM đi cùng thiết bị; muốn xử lý riêng linh kiện phải tháo/tách quan hệ trước. Tài sản độc lập và suất theo người dùng xử lý riêng.
- Suất khả dụng = tổng - đã cấp - đang giữ. Mở màn hình không giữ chỗ. Kiểm tra lại khi gửi/xác nhận và ngăn tranh cùng thiết bị/suất cuối.
- Lưu cần đối chiếu phiên bản đã đọc, không ghi đè âm thầm. Thông báo xung đột có nguyên nhân và hướng xử lý.
- Report/chứng từ phản ánh kết quả thực tế, đang chờ và đang giữ.

## Phạm vi và bảo tồn

Không thay nhánh, stage, stash, commit hoặc push. Giữ nguyên `design.md` và `.ui-evidence/` có sẵn. Không thêm dependency hoặc sửa thiết kế UI ngoài nhu cầu hiển thị/thao tác mới. Không đổi quyền đăng nhập hay vai trò thành quản lý suất license.

## Trình tự thực hiện

- [ ] Backend: DTO, migration, trạng thái dòng/phiếu, version guards, reservation liên luồng và thông báo lỗi.
- [ ] Backend: bàn giao/thu hồi từng phần và hủy dòng còn lại; kiểm tra nhóm và retry.
- [ ] Backend: thanh lý từng dòng, thực hiện riêng, chứng từ, hạn và nhắc quá hạn.
- [ ] Backend: Report/publication và dữ liệu phiếu cũ bảo tồn lịch sử.
- [ ] Frontend: API/types và các màn hình chi tiết/form hiện tại, quyền và VI/EN.
- [ ] Kiểm tra: focused backend/frontend tests, compile/build/lint và diff.
- [ ] Kiểm tra tích hợp: dữ liệu thật/migration, hai người sửa cùng phiếu, tranh asset/seat cuối, rollback và retry; ghi rõ phần chưa kiểm chứng.

## Tiêu chí nghiệm thu

Phiếu 3 tài sản thực hiện 2 còn 1 chờ; hủy phần dư không đổi kết quả cũ. Thanh lý 5 duyệt 3/từ chối 2 không retire trước thực hiện. Hủy/rút/từ chối giải phóng đúng phần chưa thực hiện. Hai phiên không ghi đè; hai phiếu không tranh được asset/seat cuối; gửi/xác nhận lặp không gây tác động lặp. Quá hạn không tự thả giữ chỗ. Report không tính pending/draft là completed.

## Bằng chứng

Chưa có kết quả kiểm thử cho thay đổi mới tại thời điểm tạo kế hoạch. Các ô trên chỉ được đánh dấu sau khi có bằng chứng.

## Kết quả triển khai và kiểm tra

- Đã triển khai tiến độ nhóm, xác nhận/hủy từng phần, giữ lịch sử và nhãn đề nghị, bảo toàn nhóm linh kiện/OEM, xử lý riêng suất Per-User.
- Đã tách duyệt và thực hiện thanh lý, chứng từ/ngày thực hiện, hạn 7 ngày làm việc, gia hạn có lý do, nhắc quá hạn không giải phóng.
- Đã nối API/UI VI/EN, version guards, giữ chỗ liên luồng, Report và PDF kết quả thực tế.
- Backend: 131 kiểm tra thuộc 13 lớp liên quan đạt trên PostgreSQL test; T18 chạy lại riêng sau sửa kỳ vọng mã lỗi, 15/15 đạt. Email dùng simulated, không xác nhận SMTP thật.
- Frontend: kiểm tra tập trung đạt, build/lint đạt. Bộ đầy đủ ghi nhận một lỗi test documentsLibrary về số select không thuộc thay đổi này; giữ nguyên theo yêu cầu bỏ qua việc không cần thiết.
- git diff --check đạt. Không stage/commit/push; giữ thay đổi design.md và .ui-evidence có sẵn.

### Giới hạn chưa nghiệm thu

- Chưa kiểm tra tương tác trên trình duyệt và chưa triển khai/migrate database ứng dụng đang chạy.
- Ngày làm việc hiện bỏ thứ Bảy/Chủ nhật; chưa có danh mục ngày nghỉ lễ của doanh nghiệp.
- Nhắc quá hạn chạy 09:00 Asia/Ho_Chi_Minh khi backend hoạt động; email theo cấu hình môi trường.
- Không coi kiểm thử tập trung là bằng chứng toàn bộ bộ backend hoặc sẵn sàng production.
