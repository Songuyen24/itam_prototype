package com.company.itam.importbatch.service;

import com.company.itam.asset.entity.AssetEntity;
import com.company.itam.asset.entity.AssetHardwareDetailsEntity;
import com.company.itam.asset.repository.AssetHardwareDetailsRepository;
import com.company.itam.asset.repository.AssetRepository;
import com.company.itam.audit.entity.AuditLogEntity;
import com.company.itam.audit.repository.AuditLogRepository;
import com.company.itam.catalog.entity.*;
import com.company.itam.catalog.repository.*;
import com.company.itam.common.enums.AssetStatus;
import com.company.itam.common.enums.ImportBatchStatus;
import com.company.itam.common.enums.ValidationStatus;
import com.company.itam.common.exception.AppException;
import com.company.itam.common.exception.ResourceNotFoundException;
import com.company.itam.common.pagination.PageResponse;
import com.company.itam.department.entity.DepartmentEntity;
import com.company.itam.department.repository.DepartmentRepository;
import com.company.itam.importbatch.dto.request.ImportConfirmRequest;
import com.company.itam.importbatch.dto.response.*;
import com.company.itam.importbatch.entity.ImportBatchEntity;
import com.company.itam.importbatch.entity.ImportRowEntity;
import com.company.itam.importbatch.repository.ImportBatchRepository;
import com.company.itam.importbatch.repository.ImportRowRepository;
import com.company.itam.location.entity.LocationEntity;
import com.company.itam.location.repository.LocationRepository;
import com.company.itam.supplier.entity.SupplierEntity;
import com.company.itam.supplier.repository.SupplierRepository;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AssetImportService {

    private final ExcelHelperService excelHelperService;
    private final AssetImportValidator assetImportValidator;
    private final ImportBatchRepository importBatchRepository;
    private final ImportRowRepository importRowRepository;
    private final AssetRepository assetRepository;
    private final AssetHardwareDetailsRepository assetHardwareDetailsRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final AssetStatusRepository assetStatusRepository;
    private final AssetConditionRepository assetConditionRepository;
    private final ModelRepository modelRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    public AssetImportService(
            ExcelHelperService excelHelperService,
            AssetImportValidator assetImportValidator,
            ImportBatchRepository importBatchRepository,
            ImportRowRepository importRowRepository,
            AssetRepository assetRepository,
            AssetHardwareDetailsRepository assetHardwareDetailsRepository,
            AssetTypeRepository assetTypeRepository,
            AssetStatusRepository assetStatusRepository,
            AssetConditionRepository assetConditionRepository,
            ModelRepository modelRepository,
            DepartmentRepository departmentRepository,
            LocationRepository locationRepository,
            SupplierRepository supplierRepository,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository) {
        this.excelHelperService = excelHelperService;
        this.assetImportValidator = assetImportValidator;
        this.importBatchRepository = importBatchRepository;
        this.importRowRepository = importRowRepository;
        this.assetRepository = assetRepository;
        this.assetHardwareDetailsRepository = assetHardwareDetailsRepository;
        this.assetTypeRepository = assetTypeRepository;
        this.assetStatusRepository = assetStatusRepository;
        this.assetConditionRepository = assetConditionRepository;
        this.modelRepository = modelRepository;
        this.departmentRepository = departmentRepository;
        this.locationRepository = locationRepository;
        this.supplierRepository = supplierRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public byte[] getTemplate() {
        return excelHelperService.generateTemplate();
    }

    public ImportPreviewResponse preview(MultipartFile file) {
        List<Map<String, Object>> rawRows = excelHelperService.parseExcel(file);
        List<ImportRowDetailResponse> validatedRows = assetImportValidator.validateRows(rawRows);

        int total = validatedRows.size();
        int valid = (int) validatedRows.stream().filter(r -> r.getValidationStatus() == ValidationStatus.VALID).count();
        int duplicate = (int) validatedRows.stream().filter(r -> r.getValidationStatus() == ValidationStatus.DUPLICATE).count();
        int invalid = total - valid - duplicate;

        return new ImportPreviewResponse(
                file.getOriginalFilename(),
                total,
                valid,
                invalid,
                duplicate,
                validatedRows
        );
    }

    @Transactional
    public ImportBatchResponse confirmImport(ImportConfirmRequest request) {
        if (request.getValidRows() == null || request.getValidRows().isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "NO_DATA_TO_IMPORT", "Danh sách dữ liệu cần import không được để trống");
        }

        UserEntity currentUser = resolveCurrentUser();
        if (currentUser == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Không xác định được người dùng thực hiện");
        }

        // Re-validate all submitted rows against current DB state to prevent concurrency race conditions
        List<ImportRowDetailResponse> validationResults = assetImportValidator.validateRows(request.getValidRows());

        List<ImportRowDetailResponse> validRows = validationResults.stream()
                .filter(r -> r.getValidationStatus() == ValidationStatus.VALID)
                .toList();

        if (validRows.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "NO_VALID_ROWS", "Không có dòng dữ liệu hợp lệ nào để import sau khi kiểm tra lại");
        }

        int totalCount = validationResults.size();
        int validCount = validRows.size();
        int duplicateCount = (int) validationResults.stream().filter(r -> r.getValidationStatus() == ValidationStatus.DUPLICATE).count();
        int invalidCount = totalCount - validCount - duplicateCount;

        // Create Import Batch
        ImportBatchEntity batch = new ImportBatchEntity();
        batch.setFileName(request.getFileName());
        batch.setStatus(ImportBatchStatus.COMPLETED);
        batch.setTotalRows(totalCount);
        batch.setValidRows(validCount);
        batch.setInvalidRows(invalidCount);
        batch.setDuplicateRows(duplicateCount);
        batch.setImportedRows(validCount);
        batch.setUploadedBy(currentUser);
        batch.setCreatedAt(Instant.now());
        batch.setCompletedAt(Instant.now());

        ImportBatchEntity savedBatch = importBatchRepository.save(batch);

        // Preload caches for fast resolution
        AssetImportValidator.CatalogCache cache = new AssetImportValidator.CatalogCache(
                assetTypeRepository.findAll(),
                assetStatusRepository.findAll(),
                assetConditionRepository.findAll(),
                modelRepository.findAll(),
                departmentRepository.findAll(),
                locationRepository.findAll(),
                supplierRepository.findAll()
        );

        AssetStatusEntity defaultStatus = assetStatusRepository.findByCode(AssetStatus.IN_STOCK)
                .orElseGet(() -> assetStatusRepository.findAll().stream()
                        .filter(s -> s.getCode() == AssetStatus.IN_STOCK)
                        .findFirst()
                        .orElse(null));

        List<ImportRowEntity> importRowEntities = new ArrayList<>();

        // Process valid rows
        for (ImportRowDetailResponse rowDetail : validRows) {
            Map<String, Object> data = rowDetail.getRawData();

            String assetTag = AssetImportValidator.normalize(data.get("assetTag"));
            String name = AssetImportValidator.normalize(data.get("name"));
            String typeStr = AssetImportValidator.normalize(data.get("type"));
            String modelStr = AssetImportValidator.normalize(data.get("model"));
            String serialNumber = AssetImportValidator.normalize(data.get("serialNumber"));
            String statusStr = AssetImportValidator.normalize(data.get("status"));
            String conditionStr = AssetImportValidator.normalize(data.get("condition"));
            String deptStr = AssetImportValidator.normalize(data.get("department"));
            String locStr = AssetImportValidator.normalize(data.get("location"));
            String suppStr = AssetImportValidator.normalize(data.get("supplier"));
            String poNumber = AssetImportValidator.normalize(data.get("poNumber"));
            String purchaseDateStr = AssetImportValidator.normalize(data.get("purchaseDate"));
            String warrantyStr = AssetImportValidator.normalize(data.get("warrantyExpiration"));

            BigDecimal purchaseCost = AssetImportValidator.parseBigDecimal(data.get("purchaseCost"));
            LocalDate purchaseDate = AssetImportValidator.parseDate(purchaseDateStr);
            LocalDate warrantyDate = AssetImportValidator.parseDate(warrantyStr);

            AssetTypeEntity typeEntity = cache.findType(typeStr);
            AssetStatusEntity statusEntity = statusStr != null ? cache.findStatus(statusStr) : defaultStatus;
            ModelEntity modelEntity = modelStr != null ? cache.findModel(modelStr) : null;
            AssetConditionEntity conditionEntity = conditionStr != null ? cache.findCondition(conditionStr) : null;
            DepartmentEntity deptEntity = deptStr != null ? cache.findDepartment(deptStr) : null;
            LocationEntity locEntity = locStr != null ? cache.findLocation(locStr) : null;
            SupplierEntity suppEntity = suppStr != null ? cache.findSupplier(suppStr) : null;

            // 1. Create Asset
            AssetEntity asset = new AssetEntity();
            asset.setAssetTag(assetTag);
            asset.setName(name);
            asset.setType(typeEntity);
            asset.setStatus(statusEntity != null ? statusEntity : defaultStatus);
            asset.setDepartment(deptEntity);
            asset.setLocation(locEntity);
            asset.setSupplier(suppEntity);
            asset.setPoNumber(poNumber);
            asset.setPurchaseDate(purchaseDate);
            asset.setPurchaseCost(purchaseCost != null ? purchaseCost : BigDecimal.ZERO);
            asset.setCreatedBy(currentUser);
            asset.setUpdatedBy(currentUser);

            AssetEntity savedAsset = assetRepository.save(asset);

            // 2. Create Hardware Details
            AssetHardwareDetailsEntity hw = new AssetHardwareDetailsEntity();
            hw.setAssetId(savedAsset.getAssetId());
            hw.setAsset(savedAsset);
            hw.setSerialNumber(serialNumber);
            hw.setModel(modelEntity);
            hw.setCondition(conditionEntity);
            hw.setWarrantyExpiration(warrantyDate);
            hw.setActualCpu(AssetImportValidator.normalize(data.get("actualCpu")));
            hw.setActualRam(AssetImportValidator.normalize(data.get("actualRam")));
            hw.setActualStorage(AssetImportValidator.normalize(data.get("actualStorage")));
            hw.setActualGraphicsCard(AssetImportValidator.normalize(data.get("actualGraphicsCard")));

            assetHardwareDetailsRepository.save(hw);

            // 3. Create ImportRow record
            ImportRowEntity rowEntity = new ImportRowEntity();
            rowEntity.setImportBatch(savedBatch);
            rowEntity.setRowNumber(rowDetail.getRowNumber());
            rowEntity.setRawData(data);
            rowEntity.setValidationStatus(ValidationStatus.IMPORTED);
            rowEntity.setAsset(savedAsset);
            rowEntity.setCreatedAt(Instant.now());

            importRowEntities.add(rowEntity);

            // Audit log for asset
            AuditLogEntity assetAudit = new AuditLogEntity();
            assetAudit.setEntityType("ASSET");
            assetAudit.setEntityId(savedAsset.getAssetId());
            assetAudit.setActor(currentUser);
            assetAudit.setAction("CREATED_VIA_IMPORT");
            assetAudit.setDetails("Tạo tài sản từ file Excel: " + request.getFileName() + " (Lô: #" + savedBatch.getImportBatchId() + ")");
            auditLogRepository.save(assetAudit);
        }

        // Also record invalid or duplicate rows that were in the submitted request
        for (ImportRowDetailResponse nonValidRow : validationResults) {
            if (nonValidRow.getValidationStatus() != ValidationStatus.VALID) {
                ImportRowEntity rowEntity = new ImportRowEntity();
                rowEntity.setImportBatch(savedBatch);
                rowEntity.setRowNumber(nonValidRow.getRowNumber());
                rowEntity.setRawData(nonValidRow.getRawData());
                rowEntity.setValidationStatus(nonValidRow.getValidationStatus());
                rowEntity.setErrorMessage(nonValidRow.getErrorMessage() != null ? nonValidRow.getErrorMessage() : "Dòng dữ liệu không hợp lệ");
                rowEntity.setCreatedAt(Instant.now());
                importRowEntities.add(rowEntity);
            }
        }

        importRowRepository.saveAll(importRowEntities);

        // Audit log for import batch
        AuditLogEntity batchAudit = new AuditLogEntity();
        batchAudit.setEntityType("IMPORT_BATCH");
        batchAudit.setEntityId(savedBatch.getImportBatchId());
        batchAudit.setActor(currentUser);
        batchAudit.setAction("IMPORT_EXCEL");
        batchAudit.setDetails("Nhập thành công " + validCount + " tài sản từ file " + request.getFileName());
        auditLogRepository.save(batchAudit);

        return mapToBatchResponse(savedBatch);
    }

    public PageResponse<ImportBatchResponse> getImportBatches(Pageable pageable) {
        Page<ImportBatchEntity> page = importBatchRepository.findAll(pageable);
        return PageResponse.of(page.map(this::mapToBatchResponse));
    }

    public ImportBatchDetailResponse getImportBatchById(Long id) {
        ImportBatchEntity batch = importBatchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đợt import với ID: " + id));

        ImportBatchDetailResponse response = new ImportBatchDetailResponse();
        fillBatchResponse(response, batch);

        List<ImportRowEntity> rows = importRowRepository.findByImportBatchImportBatchId(id);
        List<ImportRowDetailResponse> rowResponses = rows.stream()
                .map(this::mapToRowDetailResponse)
                .sorted(Comparator.comparing(ImportRowDetailResponse::getRowNumber))
                .collect(Collectors.toList());

        response.setRows(rowResponses);
        return response;
    }

    public List<ImportRowDetailResponse> getImportBatchErrors(Long id) {
        if (!importBatchRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy đợt import với ID: " + id);
        }

        List<ImportRowEntity> rows = importRowRepository.findByImportBatchImportBatchId(id);
        return rows.stream()
                .filter(r -> r.getValidationStatus() == ValidationStatus.INVALID || r.getValidationStatus() == ValidationStatus.DUPLICATE)
                .map(this::mapToRowDetailResponse)
                .sorted(Comparator.comparing(ImportRowDetailResponse::getRowNumber))
                .collect(Collectors.toList());
    }

    private ImportBatchResponse mapToBatchResponse(ImportBatchEntity entity) {
        ImportBatchResponse res = new ImportBatchResponse();
        fillBatchResponse(res, entity);
        return res;
    }

    private void fillBatchResponse(ImportBatchResponse res, ImportBatchEntity entity) {
        res.setImportBatchId(entity.getImportBatchId());
        res.setFileName(entity.getFileName());
        res.setStatus(entity.getStatus());
        res.setTotalRows(entity.getTotalRows());
        res.setValidRows(entity.getValidRows());
        res.setInvalidRows(entity.getInvalidRows());
        res.setDuplicateRows(entity.getDuplicateRows());
        res.setImportedRows(entity.getImportedRows());
        if (entity.getUploadedBy() != null) {
            res.setUploadedByUserId(entity.getUploadedBy().getUserId());
            res.setUploadedByFullName(entity.getUploadedBy().getFullName());
        }
        res.setCreatedAt(entity.getCreatedAt());
        res.setCompletedAt(entity.getCompletedAt());
    }

    private ImportRowDetailResponse mapToRowDetailResponse(ImportRowEntity entity) {
        ImportRowDetailResponse r = new ImportRowDetailResponse();
        r.setRowNumber(entity.getRowNumber());
        r.setValidationStatus(entity.getValidationStatus());
        r.setErrorMessage(entity.getErrorMessage());
        r.setRawData(entity.getRawData());
        if (entity.getAsset() != null) {
            r.setAssetId(entity.getAsset().getAssetId());
        }
        return r;
    }

    private UserEntity resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
            Optional<UserEntity> user = userRepository.findByEmail(auth.getName());
            if (user.isPresent()) {
                return user.get();
            }
        }
        // Prototype fallback: khi chưa tích hợp auth thực, lấy user đầu tiên làm actor.
        // TODO: Khi tích hợp JWT/OAuth, xóa fallback này và ném UNAUTHORIZED.
        return userRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Không xác định được người dùng thực hiện. Hệ thống chưa có user nào."));
    }
}
