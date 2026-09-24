package com.company.itam.importbatch.service;

import com.company.itam.common.exception.AppException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class ExcelHelperService {

    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    public static final int MAX_ROWS = 1000;

    public static final String[] HEADERS = {
            "Mã tài sản (tự sinh nếu trống)",
            "Tên tài sản (*)",
            "Loại tài sản (*)",
            "Model",
            "Số Serial",
            "Trạng thái",
            "Tình trạng",
            "Phòng ban",
            "Vị trí",
            "Nhà cung cấp",
            "Số PO / Hóa đơn",
            "Ngày mua",
            "Giá mua",
            "Hạn bảo hành",
            "Actual CPU",
            "Actual RAM",
            "Actual Storage",
            "Actual GPU"
    };

    public static final String[] EN_HEADERS = {
        "Asset tag (generated if blank)", "Asset name (*)", "Asset type (*)", "Model", "Serial number",
        "Status", "Condition", "Department", "Location", "Supplier", "PO / Invoice", "Purchase date",
        "Purchase cost", "Warranty expiration", "Actual CPU", "Actual RAM", "Actual Storage", "Actual GPU"
    };

    public static final String[] FIELD_KEYS = {
            "assetTag",
            "name",
            "type",
            "model",
            "serialNumber",
            "status",
            "condition",
            "department",
            "location",
            "supplier",
            "poNumber",
            "purchaseDate",
            "purchaseCost",
            "warrantyExpiration",
            "actualCpu",
            "actualRam",
            "actualStorage",
            "actualGraphicsCard"
    };

    public byte[] generateTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Import Assets");

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            // Create header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage().equals("en") ? EN_HEADERS[i] : HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Sample row style
            CellStyle sampleStyle = workbook.createCellStyle();
            Font sampleFont = workbook.createFont();
            sampleFont.setItalic(true);
            sampleFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            sampleStyle.setFont(sampleFont);

            // Sample row 1
            Row sampleRow1 = sheet.createRow(1);
            String[] sampleData1 = {
                    "AST-NB-001",
                    "Laptop Dell Latitude 5420",
                    "LAPTOP",
                    "Latitude 5420",
                    "SN-DELL-5420-01",
                    "IN_STOCK",
                    "NEW",
                    "Phòng CNTT",
                    "Kho Tầng 2",
                    "Công ty Dell VN",
                    "PO-2026-001",
                    "2026-01-15",
                    "24500000",
                    "2029-01-15",
                    "Intel Core i7-1185G7",
                    "16GB DDR4",
                    "512GB NVMe SSD",
                    "Intel Iris Xe"
            };
            for (int i = 0; i < sampleData1.length; i++) {
                Cell cell = sampleRow1.createCell(i);
                cell.setCellValue(sampleData1[i]);
                cell.setCellStyle(sampleStyle);
            }

            // Sample row 2
            Row sampleRow2 = sheet.createRow(2);
            String[] sampleData2 = {
                    "AST-PC-002",
                    "PC HP ProDesk 400 G7",
                    "DESKTOP",
                    "ProDesk 400 G7",
                    "SN-HP-400-02",
                    "IN_STOCK",
                    "GOOD",
                    "Phòng Kế toán",
                    "Tầng 3",
                    "FPT Synnex",
                    "PO-2026-002",
                    "2026-02-10",
                    "18200000",
                    "2028-02-10",
                    "Intel Core i5-10500",
                    "8GB DDR4",
                    "256GB SSD",
                    "Integrated"
            };
            for (int i = 0; i < sampleData2.length; i++) {
                Cell cell = sampleRow2.createCell(i);
                cell.setCellValue(sampleData2[i]);
                cell.setCellStyle(sampleStyle);
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 1024, 4000));
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "TEMPLATE_GENERATION_FAILED", "Không thể tạo file template Excel");
        }
    }

    public List<Map<String, Object>> parseExcel(MultipartFile file) {
        validateFile(file);

        List<Map<String, Object>> rows = new ArrayList<>();
        DataFormatter dataFormatter = new DataFormatter();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "EMPTY_EXCEL_FILE", "File Excel không có trang tính nào");
            }

            Row header = sheet.getRow(0);
            if (header == null || header.getLastCellNum() < 3) throw new AppException(HttpStatus.BAD_REQUEST,"INVALID_EXCEL_HEADERS","Invalid columns");
            for (int c=0;c<Math.min(header.getLastCellNum(),HEADERS.length);c++) {
                String label = dataFormatter.formatCellValue(header.getCell(c)).trim();
                if (!label.equalsIgnoreCase(HEADERS[c]) && !label.equalsIgnoreCase(EN_HEADERS[c])
                        && !label.equalsIgnoreCase(FIELD_KEYS[c]) && !(c==0 && label.equals("Mã tài sản (*)")))
                    throw new AppException(HttpStatus.BAD_REQUEST,"INVALID_EXCEL_HEADERS","Unexpected column at position " + (c+1));
            }
            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum < 1) {
                throw new AppException(HttpStatus.BAD_REQUEST, "EMPTY_DATA", "File Excel không chứa dữ liệu dòng nào");
            }

            if (lastRowNum > MAX_ROWS) {
                throw new AppException(HttpStatus.BAD_REQUEST, "EXCEEDS_MAX_ROWS",
                        "File Excel chứa " + lastRowNum + " dòng, vượt quá giới hạn cho phép tối đa " + MAX_ROWS + " dòng");
            }

            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row, dataFormatter)) {
                    continue; // Skip empty rows
                }

                Map<String, Object> rowMap = new LinkedHashMap<>();
                rowMap.put("rowNumber", r + 1); // 1-based line number for user display (header is row 1)

                for (int c = 0; c < FIELD_KEYS.length; c++) {
                    String fieldKey = FIELD_KEYS[c];
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    Object cellValue = extractCellValue(cell, dataFormatter);
                    rowMap.put(fieldKey, cellValue);
                }

                rows.add(rowMap);
            }

            if (rows.isEmpty()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "EMPTY_DATA", "File Excel không chứa dữ liệu hợp lệ nào sau tiêu đề");
            }

            return rows;
        } catch (IOException e) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_EXCEL_FORMAT", "Không thể đọc định dạng file Excel: " + e.getMessage());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "FILE_EMPTY", "File tải lên không được để trống");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new AppException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "Dung lượng file vượt quá giới hạn 10MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || (!originalFilename.toLowerCase().endsWith(".xlsx") && !originalFilename.toLowerCase().endsWith(".xls"))) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "Chỉ chấp nhận định dạng file Excel (.xlsx hoặc .xls)");
        }
    }

    private boolean isRowEmpty(Row row, DataFormatter formatter) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null) {
                String text = formatter.formatCellValue(cell).trim();
                if (!text.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private Object extractCellValue(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                String s = cell.getStringCellValue().trim();
                return s.isEmpty() ? null : s;
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    Date date = cell.getDateCellValue();
                    if (date != null) {
                        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString();
                    }
                    return null;
                } else {
                    double num = cell.getNumericCellValue();
                    if (num == Math.floor(num) && !Double.isInfinite(num)) {
                        return (long) num;
                    }
                    return BigDecimal.valueOf(num);
                }
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case FORMULA:
                try {
                    String str = formatter.formatCellValue(cell).trim();
                    return str.isEmpty() ? null : str;
                } catch (Exception e) {
                    return null;
                }
            default:
                String text = formatter.formatCellValue(cell).trim();
                return text.isEmpty() ? null : text;
        }
    }
}
