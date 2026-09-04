package com.company.itam.importbatch.service;

import com.company.itam.asset.entity.AssetEntity;
import com.company.itam.asset.entity.AssetHardwareDetailsEntity;
import com.company.itam.asset.repository.AssetHardwareDetailsRepository;
import com.company.itam.asset.repository.AssetRepository;
import com.company.itam.audit.repository.AuditLogRepository;
import com.company.itam.catalog.entity.AssetStatusEntity;
import com.company.itam.catalog.entity.AssetTypeEntity;
import com.company.itam.catalog.repository.*;
import com.company.itam.common.enums.AssetStatus;
import com.company.itam.common.enums.ImportBatchStatus;
import com.company.itam.common.enums.ValidationStatus;
import com.company.itam.common.exception.AppException;
import com.company.itam.department.repository.DepartmentRepository;
import com.company.itam.importbatch.dto.request.ImportConfirmRequest;
import com.company.itam.importbatch.dto.response.ImportBatchResponse;
import com.company.itam.importbatch.dto.response.ImportPreviewResponse;
import com.company.itam.importbatch.entity.ImportBatchEntity;
import com.company.itam.importbatch.repository.ImportBatchRepository;
import com.company.itam.importbatch.repository.ImportRowRepository;
import com.company.itam.location.repository.LocationRepository;
import com.company.itam.supplier.repository.SupplierRepository;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.user.repository.UserRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetImportServiceTest {

    @Mock
    private ImportBatchRepository importBatchRepository;

    @Mock
    private ImportRowRepository importRowRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetHardwareDetailsRepository assetHardwareDetailsRepository;

    @Mock
    private AssetTypeRepository assetTypeRepository;

    @Mock
    private AssetStatusRepository assetStatusRepository;

    @Mock
    private AssetConditionRepository assetConditionRepository;

    @Mock
    private ModelRepository modelRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    private ExcelHelperService excelHelperService;
    private AssetImportValidator assetImportValidator;
    private AssetImportService assetImportService;

    private UserEntity sampleUser;
    private AssetTypeEntity sampleType;
    private AssetStatusEntity sampleStatus;

    @BeforeEach
    void setUp() {
        excelHelperService = new ExcelHelperService();
        assetImportValidator = new AssetImportValidator(
                assetRepository,
                assetHardwareDetailsRepository,
                assetTypeRepository,
                assetStatusRepository,
                assetConditionRepository,
                modelRepository,
                departmentRepository,
                locationRepository,
                supplierRepository
        );

        assetImportService = new AssetImportService(
                excelHelperService,
                assetImportValidator,
                importBatchRepository,
                importRowRepository,
                assetRepository,
                assetHardwareDetailsRepository,
                assetTypeRepository,
                assetStatusRepository,
                assetConditionRepository,
                modelRepository,
                departmentRepository,
                locationRepository,
                supplierRepository,
                userRepository,
                auditLogRepository
        );

        sampleUser = new UserEntity();
        sampleUser.setUserId(1L);
        sampleUser.setEmail("it01@itam.example");
        sampleUser.setFullName("IT Specialist");

        sampleType = new AssetTypeEntity();
        sampleType.setTypeId(1L);
        sampleType.setCode("LAPTOP");
        sampleType.setName("Laptop");

        sampleStatus = new AssetStatusEntity();
        sampleStatus.setStatusId(1L);
        sampleStatus.setCode(AssetStatus.IN_STOCK);
        sampleStatus.setName("In Stock");
    }

    private byte[] createTestExcel(List<String[]> dataRows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            for (int i = 0; i < ExcelHelperService.HEADERS.length; i++) {
                header.createCell(i).setCellValue(ExcelHelperService.HEADERS[i]);
            }

            for (int r = 0; r < dataRows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                String[] rowData = dataRows.get(r);
                for (int c = 0; c < rowData.length; c++) {
                    row.createCell(c).setCellValue(rowData[c]);
                }
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("Tải template Excel thành công và có đầy đủ cột tiêu đề")
    void testGetTemplate_Success() throws IOException {
        byte[] bytes = assetImportService.getTemplate();
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertNotNull(sheet);
            Row header = sheet.getRow(0);
            assertNotNull(header);
            assertEquals(ExcelHelperService.HEADERS.length, header.getLastCellNum());
            assertEquals("Mã tài sản (*)", header.getCell(0).getStringCellValue());
        }
    }

    @Test
    @DisplayName("Preview file Excel hợp lệ — không lưu dữ liệu vào database")
    void testPreview_ValidRows_Success() throws IOException {
        List<String[]> rows = List.of(
                new String[]{"AST-NB-101", "Laptop Dell 14", "LAPTOP", "", "SN-001", "IN_STOCK", "", "", "", "", "", "2026-01-01", "20000000", "", "", "", "", ""},
                new String[]{"AST-NB-102", "Laptop Dell 15", "LAPTOP", "", "SN-002", "IN_STOCK", "", "", "", "", "", "2026-01-02", "22000000", "", "", "", "", ""}
        );

        byte[] excelBytes = createTestExcel(rows);
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(assetTypeRepository.findAll()).thenReturn(List.of(sampleType));
        when(assetStatusRepository.findAll()).thenReturn(List.of(sampleStatus));
        when(assetConditionRepository.findAll()).thenReturn(Collections.emptyList());
        when(modelRepository.findAll()).thenReturn(Collections.emptyList());
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());
        when(locationRepository.findAll()).thenReturn(Collections.emptyList());
        when(supplierRepository.findAll()).thenReturn(Collections.emptyList());

        when(assetRepository.existsByAssetTag("AST-NB-101")).thenReturn(false);
        when(assetRepository.existsByAssetTag("AST-NB-102")).thenReturn(false);
        when(assetHardwareDetailsRepository.existsBySerialNumber("SN-001")).thenReturn(false);
        when(assetHardwareDetailsRepository.existsBySerialNumber("SN-002")).thenReturn(false);

        ImportPreviewResponse preview = assetImportService.preview(file);

        assertNotNull(preview);
        assertEquals(2, preview.getTotalRows());
        assertEquals(2, preview.getValidRows());
        assertEquals(0, preview.getInvalidRows());
        assertEquals(0, preview.getDuplicateRows());
        assertEquals(ValidationStatus.VALID, preview.getRows().get(0).getValidationStatus());

        // CRITICAL: Preview MUST NOT write to database
        verify(assetRepository, never()).save(any());
        verify(importBatchRepository, never()).save(any());
    }

    @Test
    @DisplayName("Preview phát hiện dòng thiếu cột bắt buộc và dòng lỗi danh mục")
    void testPreview_InvalidRows_Detected() throws IOException {
        List<String[]> rows = List.of(
                // Missing assetTag and name
                new String[]{"", "", "LAPTOP", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""},
                // Unknown type
                new String[]{"AST-999", "Unknown Device", "UNKNOWN_TYPE", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""}
        );

        byte[] excelBytes = createTestExcel(rows);
        MockMultipartFile file = new MockMultipartFile("file", "invalid.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(assetTypeRepository.findAll()).thenReturn(List.of(sampleType));
        when(assetStatusRepository.findAll()).thenReturn(List.of(sampleStatus));
        when(assetConditionRepository.findAll()).thenReturn(Collections.emptyList());
        when(modelRepository.findAll()).thenReturn(Collections.emptyList());
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());
        when(locationRepository.findAll()).thenReturn(Collections.emptyList());
        when(supplierRepository.findAll()).thenReturn(Collections.emptyList());

        ImportPreviewResponse preview = assetImportService.preview(file);

        assertNotNull(preview);
        assertEquals(2, preview.getTotalRows());
        assertEquals(0, preview.getValidRows());
        assertEquals(2, preview.getInvalidRows());
        assertEquals(ValidationStatus.INVALID, preview.getRows().get(0).getValidationStatus());
        assertFalse(preview.getRows().get(0).getErrors().isEmpty());
    }

    @Test
    @DisplayName("Preview phát hiện trùng lặp trong nội bộ file và trùng với database")
    void testPreview_DuplicateDetection() throws IOException {
        List<String[]> rows = List.of(
                new String[]{"AST-DUP-01", "Device 1", "LAPTOP", "", "SN-SAME", "", "", "", "", "", "", "", "", "", "", "", "", ""},
                new String[]{"AST-DUP-01", "Device 2", "LAPTOP", "", "SN-SAME", "", "", "", "", "", "", "", "", "", "", "", "", ""},
                new String[]{"AST-EXISTING", "Device 3", "LAPTOP", "", "SN-DB", "", "", "", "", "", "", "", "", "", "", "", "", ""}
        );

        byte[] excelBytes = createTestExcel(rows);
        MockMultipartFile file = new MockMultipartFile("file", "dup.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(assetTypeRepository.findAll()).thenReturn(List.of(sampleType));
        when(assetStatusRepository.findAll()).thenReturn(List.of(sampleStatus));
        when(assetConditionRepository.findAll()).thenReturn(Collections.emptyList());
        when(modelRepository.findAll()).thenReturn(Collections.emptyList());
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());
        when(locationRepository.findAll()).thenReturn(Collections.emptyList());
        when(supplierRepository.findAll()).thenReturn(Collections.emptyList());

        // AST-DUP-01 does not exist in DB
        lenient().when(assetRepository.existsByAssetTag("AST-DUP-01")).thenReturn(false);
        // AST-EXISTING exists in DB
        lenient().when(assetRepository.existsByAssetTag("AST-EXISTING")).thenReturn(true);
        lenient().when(assetHardwareDetailsRepository.existsBySerialNumber(any())).thenReturn(false);

        ImportPreviewResponse preview = assetImportService.preview(file);

        assertNotNull(preview);
        assertEquals(3, preview.getTotalRows());
        assertEquals(0, preview.getValidRows());
        assertEquals(3, preview.getDuplicateRows());
    }

    @Test
    @DisplayName("Confirm import thành công các dòng hợp lệ và tạo đúng entities")
    void testConfirmImport_Success() {
        Map<String, Object> row1 = new HashMap<>();
        row1.put("rowNumber", 2);
        row1.put("assetTag", "AST-NB-201");
        row1.put("name", "Laptop ThinkPad");
        row1.put("type", "LAPTOP");
        row1.put("serialNumber", "SN-TP-201");
        row1.put("status", "IN_STOCK");
        row1.put("purchaseCost", "25000000");

        ImportConfirmRequest request = new ImportConfirmRequest("import_test.xlsx", List.of(row1));

        when(userRepository.findAll()).thenReturn(List.of(sampleUser));
        when(assetTypeRepository.findAll()).thenReturn(List.of(sampleType));
        when(assetStatusRepository.findAll()).thenReturn(List.of(sampleStatus));
        when(assetStatusRepository.findByCode(AssetStatus.IN_STOCK)).thenReturn(Optional.of(sampleStatus));
        when(assetConditionRepository.findAll()).thenReturn(Collections.emptyList());
        when(modelRepository.findAll()).thenReturn(Collections.emptyList());
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());
        when(locationRepository.findAll()).thenReturn(Collections.emptyList());
        when(supplierRepository.findAll()).thenReturn(Collections.emptyList());

        when(assetRepository.existsByAssetTag("AST-NB-201")).thenReturn(false);
        when(assetHardwareDetailsRepository.existsBySerialNumber("SN-TP-201")).thenReturn(false);

        ImportBatchEntity savedBatch = new ImportBatchEntity();
        savedBatch.setImportBatchId(10L);
        savedBatch.setFileName("import_test.xlsx");
        savedBatch.setStatus(ImportBatchStatus.COMPLETED);
        savedBatch.setTotalRows(1);
        savedBatch.setValidRows(1);
        savedBatch.setImportedRows(1);
        savedBatch.setUploadedBy(sampleUser);

        when(importBatchRepository.save(any(ImportBatchEntity.class))).thenReturn(savedBatch);

        AssetEntity savedAsset = new AssetEntity();
        savedAsset.setAssetId(100L);
        savedAsset.setAssetTag("AST-NB-201");
        when(assetRepository.save(any(AssetEntity.class))).thenReturn(savedAsset);

        ImportBatchResponse response = assetImportService.confirmImport(request);

        assertNotNull(response);
        assertEquals(10L, response.getImportBatchId());
        assertEquals(1, response.getImportedRows());
        assertEquals(ImportBatchStatus.COMPLETED, response.getStatus());

        verify(assetRepository, times(1)).save(any(AssetEntity.class));
        verify(assetHardwareDetailsRepository, times(1)).save(any(AssetHardwareDetailsEntity.class));
        verify(importRowRepository, times(1)).saveAll(any());
        verify(auditLogRepository, atLeast(1)).save(any());
    }

    @Test
    @DisplayName("Confirm import từ chối khi không có dòng hợp lệ nào")
    void testConfirmImport_NoValidRows_ThrowsException() {
        Map<String, Object> rowInvalid = new HashMap<>();
        rowInvalid.put("rowNumber", 2);
        rowInvalid.put("assetTag", ""); // empty asset tag

        ImportConfirmRequest request = new ImportConfirmRequest("empty.xlsx", List.of(rowInvalid));

        when(userRepository.findAll()).thenReturn(List.of(sampleUser));
        when(assetTypeRepository.findAll()).thenReturn(List.of(sampleType));
        when(assetStatusRepository.findAll()).thenReturn(List.of(sampleStatus));
        when(assetConditionRepository.findAll()).thenReturn(Collections.emptyList());
        when(modelRepository.findAll()).thenReturn(Collections.emptyList());
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());
        when(locationRepository.findAll()).thenReturn(Collections.emptyList());
        when(supplierRepository.findAll()).thenReturn(Collections.emptyList());

        assertThrows(AppException.class, () -> assetImportService.confirmImport(request));
        verify(assetRepository, never()).save(any());
    }
}
