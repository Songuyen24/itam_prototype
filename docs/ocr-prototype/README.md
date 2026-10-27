# PDF nhập liệu prototype — M10.3

Các PDF tự tạo, không chứa dữ liệu công ty. Đây là mẫu kiểm tra kỹ thuật, không phải biểu mẫu nghiệp vụ đã được chốt.

- `asset-list-text.pdf`: PDF text, 2 trang, 7 tài sản.
- `asset-list-scan.pdf`: cùng nội dung nhưng mỗi trang chỉ chứa ảnh raster, không có lớp text.
- Trang 1: 2 dòng hợp lệ với tag để trống. Trang 2: 2 dòng trùng tag `OCR-DUP`, 1 dòng thiếu tên, 1 loại `UNKNOWN`, 1 giá mua âm. Tổng dự kiến trên database chưa có serial mẫu: 2 hợp lệ, 2 trùng, 3 lỗi. Chạy lại sau nhập sẽ xuất hiện lỗi serial tồn tại, đúng cơ chế Excel.
- Generator: `backend/src/test/java/com/company/itam/importbatch/service/PdfAssetReaderTest.java`; chạy test tạo lại hai PDF. Không chỉnh PDF thủ công rồi chạy lại generator nếu muốn giữ bản chỉnh.

## Bố cục được hỗ trợ

Mỗi trang có một bảng với tiêu đề gồm tên cột Excel tiếng Việt/Anh hoặc field key, bắt buộc có `name` và `type`. Dấu `|` phân cột; `-` biểu thị ô trống. Có hỗ trợ tab hoặc ít nhất hai dấu cách, nhưng PDF scan nên dùng `|`. Mỗi tài sản ở một dòng, không gộp ô/không xuống dòng trong ô. Ví dụ:

```text
assetTag | name | type | condition | serialNumber | purchaseCost
- | Laptop prototype | LAPTOP | NEW | SAMPLE-SERIAL | 12000000
```

23 trường dùng cùng hợp đồng Excel: assetTag, name, type, model, serialNumber, status, condition, department, location, supplier, poNumber, purchaseDate, purchaseCost, warrantyExpiration, actualCpu, actualRam, actualStorage, actualGraphicsCard, software, licenseAssignment, licenseTerm, seatCount, licenseExpiry. Ngày `YYYY-MM-DD`; danh mục phải tồn tại và đang hoạt động. Không tự tạo danh mục hoặc đoán mã.

Mở **Excel / PDF** trên Thêm tài sản (ADMIN/IT) hoặc phiếu Nhập kho nháp (ADMIN/PUR). Chọn PDF → xem preview → Sửa từng dòng nếu cần → Kiểm tra lại → Xác nhận nhập. Dòng sai vẫn hiển thị, không nhập; lịch sử batch và retry/idempotency dùng cơ chế Excel hiện có. Dòng không đủ số cột được giữ với text gốc để sửa, không đoán vị trí ô. Trang không nhận được header trả lỗi, không nhập phần trang còn lại.

## OCR nội bộ

PDFBox 3.0.3 có sẵn được dùng cho text và render. Scan gọi Tesseract CLI cài trên máy backend, không gọi API ngoài. Cần bộ ngôn ngữ `eng` và `vie` ở deployment. Tesseract nhận file tạm có tên do server tạo, không nhận đường dẫn từ tên file upload.

Ví dụ PowerShell trước khi khởi động backend trên máy kiểm tra:

```powershell
$env:ITAM_OCR_EXECUTABLE = 'C:\Program Files\Tesseract-OCR\tesseract.exe'
$env:ITAM_OCR_LANGUAGES = 'eng+vie'
$env:TESSDATA_PREFIX = 'C:\Users\Admin\.cache\itam-ocr\tessdata'
```

Máy khác: cài Tesseract và language packs từ nguồn tin cậy của hệ điều hành/Tesseract, đặt đường dẫn executable và tessdata tương ứng. Không cần Python hoặc Poppler khi chạy ứng dụng. Thiếu công cụ/gói ngôn ngữ trả thông báo rõ; PDF text vẫn dùng được. Không tải ngôn ngữ hoặc gửi tài liệu qua Internet lúc xử lý upload.

Giới hạn: 10 MB, 10 trang, 1.000 dòng, 500.000 ký tự, render 200 DPI tối đa 12 megapixel/trang, tối đa 2 yêu cầu PDF đồng thời. Tesseract tối đa 30 giây/trang, ngân sách toàn yêu cầu 120 giây được kiểm giữa các bước. PDFBox chạy trong JVM ứng dụng nên đây **không phải hard timeout/sandbox** cho PDF độc hại; trước triển khai rộng cần worker cô lập tiến trình/tài nguyên. Không lưu PDF nguồn; ảnh/OCR text tạm được dọn sau xử lý; rawData của dòng nhập chứa trang/text nhận diện để truy vết kết quả đã xác nhận. Chính sách lưu nguồn thật chưa chốt, không tự thêm kho tài liệu.

Chưa nhận diện chữ viết tay, chữ ký, bảng bất kỳ/ô gộp, danh sách nhiều cột độc lập hoặc ảnh chất lượng thấp. Chưa có mẫu thật IT/PUR; cần thử lại và chốt ánh xạ/lưu nguồn với nghiệp vụ trước áp dụng rộng. OCR không dành cho bàn giao hoặc thu hồi.
