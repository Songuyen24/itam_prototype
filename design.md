# ITAM — giao diện desktop

<!-- Hallmark · pre-emit critique: P4 H4 E3 S4 R5 V3 -->

Phạm vi: tinh chỉnh giao diện hiện có theo yêu cầu ngày 27/10/2026. Không thay điều hướng, workflow, quyền, hợp đồng API hay phép tính. Không thiết kế mobile.

- Phong cách: modern-minimal; cấu trúc workbench hiện có, sidebar tối và nội dung sáng. Không thêm hero, hình trang trí, số liệu mẫu hoặc animation xuất hiện theo cuộn.
- Giữ font stack `--font-family` và màu thương hiệu `--primary` trong `frontend/src/index.css`. Không tải font hoặc thêm dependency.
- Nguồn token: `:root` trong `frontend/src/index.css`; tái sử dụng `--bg-main`, `--bg-card`, `--text-main`, `--text-muted`, `--border-color`, `--radius`. Token mới `--control-border`, `--focus-ring`, `--surface-hover` bổ sung trạng thái tương tác. Không nhân bản sang hệ token song song.
- Tiêu đề trang 24px, tiêu đề khu vực 18–20px, nội dung/form 14px, bảng 13.5px; tiêu đề đứng, số liệu tabular khi phù hợp.
- Khoảng cách nhịp 4px: trong nhóm 8–12px, giữa trường 12–16px, giữa khối 20–24px. Điều khiển thường cao tối thiểu 38px, nút nhỏ 30px cho desktop.
- Bảng cuộn ngang trong container; không ép các cột dài làm trang tràn. Nhãn form hiện rõ, liên kết với control. Nút chính xanh, nút phụ nền trắng, nút nguy hiểm đỏ đậm.
- Focus bàn phím tức thời, outline có khoảng cách; disabled có độ mờ và cursor. Tôn trọng reduced motion. Dùng native dialog cho form tài sản, theo pattern modal sẵn có.
- Trang Report dùng cùng tiêu đề/control/table; nhóm bộ lọc, kết quả và phân quyền tách bằng khoảng cách/đường viền. Dashboard giữ riêng tồn hiện tại và phát sinh trong kỳ, giữ nguyên biểu đồ và đơn vị.

Kiểm tra và giới hạn chi tiết: `project_sources/docs/task_reports/HALLMARK_DESKTOP_UI.md`. Không coi điểm tự đánh giá Hallmark là chứng nhận WCAG hoặc nghiệm thu nghiệp vụ.
