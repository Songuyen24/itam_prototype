# Dữ liệu mẫu cho ITAM Prototype

Tài liệu dành cho người chuẩn bị môi trường demo và kiểm thử. Phần tài khoản và danh mục hệ thống mô tả dữ liệu do migration tạo; các phòng ban, vị trí, tài sản và chứng từ ví dụ cần được tạo thêm. Các ví dụ không phải script nạp dữ liệu và không xác nhận rằng bản ghi đã tồn tại trong database đang dùng.

## 1. Dữ liệu được tạo bởi migration

### Tài khoản demo

| Email | Họ tên | Vai trò | Mật khẩu demo |
| --- | --- | --- | --- |
| admin@itam.example | Quản trị viên hệ thống | ADMIN | Password@123 |
| it01@itam.example | IT Support 01 | IT_STAFF | Password@123 |
| pur01@itam.example | Mua sắm 01 | PUR_STAFF | Password@123 |
| user01@itam.example | Nguyễn Văn A | USER | Password@123 |

Đây là thông tin demo công khai trong migration và màn hình đăng nhập, không phải bí mật môi trường. Không dùng các tài khoản hoặc mật khẩu này cho triển khai thật.

### Danh mục hệ thống seed sẵn

- Vai trò hiệu lực: ADMIN, IT_STAFF, PUR_STAFF, USER.

- Nhóm tài sản: DEVICE, LICENSE, COMPONENT (COMPONENT ban đầu bị đặt inactive nhưng các loại RAM/STORAGE vẫn được tạo).

- Loại tài sản: LAPTOP, DESKTOP, MONITOR, SERVER, PRINTER, RAM, STORAGE, SOFTWARE_LICENSE.

- Trạng thái: IN_STOCK, IN_USE, IN_REPAIR, DAMAGED, RETIRED, PENDING_IMPORT.

- Tình trạng: NEW, USED, DAMAGED.

- Kiểu gán license: OEM, PER_USER.

- Thời hạn license: PERPETUAL, SUBSCRIPTION.

Migration không seed phòng ban, vị trí, nhà cung cấp, model, phần mềm hoặc tài sản nghiệp vụ. Cần tạo các mục này qua UI trước khi chạy kịch bản đầy đủ.

## 2. Bộ danh mục tổng hợp đề xuất

Vào Quản lý Danh mục và tạo các bản ghi sau. Có thể đổi tên hiển thị, nhưng nên giữ mã ngắn, duy nhất và dễ nhận biết.

### Phòng ban

| Mã | Tên |
| --- | --- |
| IT | Phòng Công nghệ thông tin |
| HR | Phòng Nhân sự |
| PUR | Phòng Mua sắm |

### Vị trí

| Mã | Tên | Địa chỉ |
| --- | --- | --- |
| KHO-HCM | Kho IT TP.HCM | Tầng 1, văn phòng TP.HCM |
| VP-HCM | Văn phòng TP.HCM | Tầng 5, văn phòng TP.HCM |

### Nhà cung cấp và liên hệ

| Mã | Tên | Mã số thuế | Liên hệ |
| --- | --- | --- | --- |
| NCC-DEMO | Công ty Thiết bị Demo | 0000000000 | Demo Support, demo-support@example.invalid |

Tên miền .invalid được dành cho dữ liệu không gửi thật.

### Model

Bản ghi 1

| Trường | Giá trị |
| --- | --- |
| Loại | LAPTOP |
| Hãng | Lenovo |
| Model | ThinkPad T14 Demo |
| CPU mặc định | Intel Core i5 |
| RAM | 16 GB |
| Ổ lưu trữ | 512 GB SSD |
| GPU | Intel Integrated |

Bản ghi 2

| Trường | Giá trị |
| --- | --- |
| Loại | MONITOR |
| Hãng | Dell |
| Model | P2422H Demo |
| CPU mặc định |  |
| RAM |  |
| Ổ lưu trữ |  |
| GPU |  |

Bản ghi 3

| Trường | Giá trị |
| --- | --- |
| Loại | RAM |
| Hãng | Kingston |
| Model | DDR4 16GB Demo |
| CPU mặc định |  |
| RAM | 16 GB |
| Ổ lưu trữ |  |
| GPU |  |

### Phần mềm

| Hãng | Tên | Phiên bản |
| --- | --- | --- |
| Microsoft | Microsoft 365 Demo | 2026 |
| Microsoft | Windows Pro Demo | 11 |

## 3. Trường dữ liệu tài sản

Các trường chung được UI/backend hiện tại sử dụng:

| Nhóm | Trường | Ghi chú |
| --- | --- | --- |
| Định danh | Mã tài sản | Có thể để trống khi tạo/import để tự sinh; phải duy nhất nếu nhập |
| Định danh | Tên tài sản, loại | Bắt buộc |
| Trạng thái | Trạng thái, phòng ban, vị trí, người dùng | IN_STOCK không được gán người dùng |
| Mua sắm | Nhà cung cấp, PO/hóa đơn, ngày mua, giá mua | Giá không âm; ngày mua quyết định tuổi thanh lý |
| Phần cứng | Serial, model, tình trạng, hạn bảo hành | Serial nếu có phải duy nhất |
| Cấu hình | CPU, RAM, ổ lưu trữ, GPU thực tế | Để trống thì cấu hình hiệu lực kế thừa model |
| License | Phần mềm, kiểu gán, thời hạn, số suất, ngày hết hạn | Số suất tối thiểu 1; thuê bao cần ngày hết hạn |

## 4. Tài sản tổng hợp đề xuất

Tạo trực tiếp ở trang Tài sản chỉ để làm tồn đầu kỳ. Dữ liệu mua mới trong demo nên tạo từ phiếu Nhập kho.

| Mã đề xuất | Tên | Loại | Trạng thái đầu | Ngày mua | Mục đích |
| --- | --- | --- | --- | --- | --- |
| DEMO-LAP-001 | Laptop demo bàn giao | LAPTOP | IN_STOCK | 2026-09-01 | Bàn giao rồi thu hồi |
| DEMO-RAM-001 | RAM demo đi kèm | RAM | IN_STOCK | 2026-09-01 | Gắn COMPONENT_OF với laptop |
| DEMO-LIC-OEM-001 | Windows OEM demo | SOFTWARE_LICENSE | IN_STOCK | 2026-09-01 | License OEM, tối thiểu 2 suất |
| DEMO-LIC-USR-001 | Microsoft 365 demo | SOFTWARE_LICENSE | IN_STOCK | 2026-09-01 | License Per-User, tối thiểu 3 suất |
| DEMO-DMG-001 | Laptop hỏng chờ thanh lý | LAPTOP | DAMAGED | 2025-01-15 | Ứng viên thanh lý do hỏng |
| DEMO-OLD-001 | Màn hình cũ chờ thanh lý | MONITOR | IN_STOCK | 2020-01-15 | Ứng viên thanh lý theo tuổi với ngưỡng 5 năm |

Ngày 2020-01-15 chỉ là ví dụ ổn định cho buổi demo năm 2026. Nếu đổi ngưỡng DISPOSAL_MINIMUM_AGE_YEARS hoặc demo vào thời điểm khác, chọn ngày mua không muộn hơn ngày hiện tại trừ đúng ngưỡng.

Sau khi tạo:

1. Mở DEMO-LAP-001, gắn DEMO-RAM-001 theo quan hệ linh kiện.

2. Gắn một suất DEMO-LIC-OEM-001 vào laptop; hệ thống giữ suất OEM.

3. Không gắn Per-User thủ công; cấp nó trong phiếu bàn giao.

## 5. File Excel tồn đầu kỳ

Tải template chính xác từ Tài sản → Import Excel → Tải file Excel mẫu chuẩn. API tương ứng là GET /api/v1/asset-imports/template; tên file trả về là itam_asset_import_template.xlsx.

Template hiện có 18 cột theo thứ tự:

1. Mã tài sản (tự sinh nếu trống)

2. Tên tài sản (*)

3. Loại tài sản (*)

4. Model

5. Số Serial

6. Trạng thái

7. Tình trạng

8. Phòng ban

9. Vị trí

10. Nhà cung cấp

11. Số PO / Hóa đơn

12. Ngày mua

13. Giá mua

14. Hạn bảo hành

15. Actual CPU

16. Actual RAM

17. Actual Storage

18. Actual GPU

Quy tắc chính:

- File .xlsx hoặc .xls, tối đa 10 MB, tối đa 1.000 dòng dữ liệu.

- Tên tài sản và loại tài sản bắt buộc; mã có thể trống để tự sinh.

- Mã/serial không được trùng trong file hoặc cơ sở dữ liệu.

- Danh mục tham chiếu nhập bằng mã hoặc tên đang tồn tại và active.

- Ngày dùng định dạng ngày Excel hoặc chuỗi ISO YYYY-MM-DD; giá mua không âm.

- Preview chỉ kiểm tra; dữ liệu chỉ được ghi sau khi xác nhận.

Ví dụ một dòng tổng hợp:

Bản ghi 1

| Trường | Giá trị |
| --- | --- |
| Mã tài sản | DEMO-IMP-001 |
| Tên tài sản | Laptop tồn đầu kỳ demo |
| Loại tài sản | LAPTOP |
| Model | ThinkPad T14 Demo |
| Số Serial | SN-DEMO-IMP-001 |
| Trạng thái | IN_STOCK |
| Tình trạng | NEW |
| Phòng ban | IT |
| Vị trí | KHO-HCM |
| Nhà cung cấp | NCC-DEMO |
| Số PO / Hóa đơn | PO-DEMO-001 |
| Ngày mua | 2026-01-15 |
| Giá mua | 25000000 |

## 6. Chứng từ demo

Tạo các file giả lập nhỏ, không dùng hóa đơn, hợp đồng, thông tin người thật hoặc dữ liệu tài chính thật. Định dạng được hỗ trợ: PDF, PNG, JPEG; tối đa 10 MB và nội dung phải khớp đuôi file.

Tên gợi ý:

- PO-DEMO-001.pdf — loại Đơn đặt hàng.

- INVOICE-DEMO-001.pdf — loại Hóa đơn.

- IMPORT-RECEIPT-DEMO-001.pdf — loại Biên bản nhập kho.

Các file này là đầu vào do người demo tự tạo; repository không chứa bộ chứng từ demo chuẩn được seed tự động.
