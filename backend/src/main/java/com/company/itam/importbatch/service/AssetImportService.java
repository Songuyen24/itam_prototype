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

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired
    private com.company.itam.common.util.MessageHelper messages;
    @org.springframework.beans.factory.annotation.Autowired
    private com.company.itam.workflow.receiving.service.ImportDraftService drafts;
    @org.springframework.beans.factory.annotation.Autowired
    private com.company.itam.asset.service.LicenseDetailsService licenses;
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
    public ImportBatchDetailResponse confirmImport(ImportConfirmRequest request) {
        requireRole("ADMIN", "IT_STAFF");
        return importRows(request, null);
    }

    @Transactional
    public Map<String, Object> confirmReceiving(Long id, Long version, ImportConfirmRequest request) {
        requireRole("ADMIN", "PUR_STAFF");
        // Serialize retries on the same draft before checking request identity/version.
        jdbc.queryForList("SELECT transaction_id FROM transactions WHERE transaction_id=? FOR UPDATE", Long.class, id);
        var replay = findReplay(request, id, resolveCurrentUser());
        var draft = drafts.lockDraft(id, replay == null ? version : jdbc.queryForObject(
                "SELECT content_version FROM transactions WHERE transaction_id=?", Long.class, id));
        if (replay != null) return Map.of("transaction", com.company.itam.workflow.core.dto.TransactionSummaryResponse.fromEntity(draft), "batch", replay);
        var batch = importRows(request, id);
        return Map.of("transaction", com.company.itam.workflow.core.dto.TransactionSummaryResponse.fromEntity(draft), "batch", batch);
    }

    private void requireRole(String... roles) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> Set.of(roles).contains(a.getAuthority())))
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
    }

    private ImportBatchDetailResponse importRows(ImportConfirmRequest request, Long receivingId) {
        // Same database lock as generated/manual tags, acquired before revalidation.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(74101301)");
        if (request.getValidRows() == null || request.getValidRows().isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "NO_DATA_TO_IMPORT", "Danh sách dữ liệu cần import không được để trống");
        }

        UserEntity currentUser = resolveCurrentUser();
        if (currentUser == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Không xác định được người dùng thực hiện");
        }

        var replay = findReplay(request, receivingId, currentUser);
        if (replay != null) return replay;
        // Re-validate all submitted rows against current DB state to prevent concurrency race conditions
        List<ImportRowDetailResponse> validationResults = assetImportValidator.validateRows(request.getValidRows());

        applyRetry(request, receivingId, currentUser, validationResults);
        List<ImportRowDetailResponse> validRows = validationResults.stream()
                .filter(r -> r.getValidationStatus() == ValidationStatus.VALID)
                .toList();

        if (validRows.isEmpty()) {
            throw new ImportValidationException(validationResults);
        }

        int totalCount = validationResults.size();
        int validCount = validRows.size();
        int duplicateCount = (int) validationResults.stream().filter(r -> r.getValidationStatus() == ValidationStatus.DUPLICATE).count();
        int invalidCount = totalCount - validCount - duplicateCount;

        // Create Import Batch
        ImportBatchEntity batch = new ImportBatchEntity();
        batch.setFileName(request.getFileName());
        batch.setRequestId(request.getRequestId());
        batch.setRetryBatchId(request.getRetryBatchId());
        batch.setReceivingId(receivingId);
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

        Set<String> submittedTags = request.getValidRows().stream()
                .map(row -> AssetImportValidator.normalize(row.get("assetTag"))).filter(Objects::nonNull)
                .map(tag -> tag.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        // Process valid rows
        for (ImportRowDetailResponse rowDetail : validRows) {
            Map<String, Object> data = rowDetail.getRawData();

            String assetTag = AssetImportValidator.normalize(data.get("assetTag"));
            if (assetTag == null) {
                do { assetTag = assetRepository.nextGeneratedAssetTag(); }
                while (submittedTags.contains(assetTag.toLowerCase(Locale.ROOT)));
            }
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

            if (receivingId != null) {
                var input = assetImportValidator.toRequest(data, cache);
                input.setAssetTag(assetTag);
                Long version = jdbc.queryForObject("SELECT content_version FROM transactions WHERE transaction_id=?", Long.class, receivingId);
                drafts.addHardware(receivingId, version, input);
                AssetEntity received = assetRepository.findByAssetTag(assetTag).orElseThrow();
                ImportRowEntity imported = new ImportRowEntity();
                imported.setImportBatch(savedBatch); imported.setRowNumber(rowDetail.getRowNumber());
                imported.setRawData(data); imported.setValidationStatus(ValidationStatus.IMPORTED);
                imported.setAsset(received); imported.setCreatedAt(Instant.now()); importRowEntities.add(imported);
                continue;
            }
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

            hw.setAsset(savedAsset);
            hw.setSerialNumber(serialNumber);
            hw.setModel(modelEntity);
            hw.setCondition(conditionEntity);
            hw.setWarrantyExpiration(warrantyDate);
            hw.setActualCpu(AssetImportValidator.normalize(data.get("actualCpu")));
            hw.setActualRam(AssetImportValidator.normalize(data.get("actualRam")));
            hw.setActualStorage(AssetImportValidator.normalize(data.get("actualStorage")));
            hw.setActualGraphicsCard(AssetImportValidator.normalize(data.get("actualGraphicsCard")));

            if (typeEntity.getCategory() != null && typeEntity.getCategory().getCode() == com.company.itam.common.enums.AssetCategory.LICENSE)
                licenses.save(savedAsset, assetImportValidator.toRequest(data, cache).getLicense());
            else assetHardwareDetailsRepository.save(hw);

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
                rowEntity.setValidationErrors(nonValidRow.getErrors());
                if (nonValidRow.getAssetId() != null) rowEntity.setAsset(assetRepository.getReferenceById(nonValidRow.getAssetId()));
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

        ImportBatchDetailResponse response = new ImportBatchDetailResponse();
        fillBatchResponse(response, savedBatch);
        response.setRows(importRowEntities.stream().map(this::mapToRowDetailResponse)
                .sorted(Comparator.comparing(ImportRowDetailResponse::getRowNumber)).toList());
        return response;
    }

    private ImportBatchDetailResponse findReplay(ImportConfirmRequest request, Long receivingId, UserEntity user) {
        if (request.getRequestId() == null) return null;
        var existing = importBatchRepository.findByRequestId(request.getRequestId());
        if (existing.isEmpty()) return null;
        var batch = existing.get();
        if (user == null || !Objects.equals(batch.getUploadedBy().getUserId(), user.getUserId())
                || !Objects.equals(batch.getReceivingId(), receivingId))
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        var previousRows = importRowRepository.findByImportBatchImportBatchIdWithAsset(batch.getImportBatchId());
        if (!Objects.equals(batch.getFileName(), request.getFileName())
                || !Objects.equals(batch.getRetryBatchId(), request.getRetryBatchId())
                || request.getValidRows() == null || previousRows.size() != request.getValidRows().size()
                || previousRows.stream().anyMatch(old -> request.getValidRows().stream().noneMatch(row ->
                    row != null && Objects.equals(old.getRowNumber(), row.get("rowNumber")) && sameInput(old.getRawData(), row))))
            throw new AppException(HttpStatus.CONFLICT, "IMPORT_REQUEST_CHANGED", "IMPORT_REQUEST_CHANGED");
        return getImportBatchById(batch.getImportBatchId());
    }

    private void applyRetry(ImportConfirmRequest request, Long receivingId, UserEntity user,
            List<ImportRowDetailResponse> rows) {
        Long previous = request.getRetryBatchId();
        Set<Long> visited = new HashSet<>();
        Set<Integer> matchedRows = new HashSet<>();
        while (previous != null && visited.add(previous)) {
            var batch = importBatchRepository.findByIdWithUploadedBy(previous)
                    .orElseThrow(() -> new ResourceNotFoundException("IMPORT_RETRY_NOT_FOUND"));
            if (!Objects.equals(batch.getUploadedBy().getUserId(), user.getUserId())
                    || !Objects.equals(batch.getReceivingId(), receivingId))
                throw new org.springframework.security.access.AccessDeniedException("Access denied");
            for (var prior : importRowRepository.findByImportBatchImportBatchIdWithAsset(previous)) {
                if (prior.getValidationStatus() != ValidationStatus.IMPORTED) continue;
                for (var row : rows) {
                    // Match a multiset: repaired files may reorder rows; identical purchases retain their count.
                    if (!matchedRows.contains(row.getRowNumber()) && sameInput(prior.getRawData(), row.getRawData())) {
                        matchedRows.add(row.getRowNumber());
                        row.setValidationStatus(ValidationStatus.DUPLICATE);
                        row.setAssetId(prior.getAsset().getAssetId());
                        row.setErrorMessage(messages.getMessage("ALREADY_IMPORTED"));
                        row.setErrors(List.of(new ImportRowErrorResponse(row.getRowNumber(), "rowNumber", "ALREADY_IMPORTED", messages.getMessage("ALREADY_IMPORTED"))));
                        break;
                    }
                }
            }
            previous = batch.getRetryBatchId();
        }
    }

    private boolean sameInput(Map<String, Object> first, Map<String, Object> second) {
        return Arrays.stream(ExcelHelperService.FIELD_KEYS).allMatch(key -> Objects.equals(
                AssetImportValidator.normalize(first.get(key)), AssetImportValidator.normalize(second.get(key))));
    }

    public PageResponse<ImportBatchResponse> getImportBatches(Pageable pageable) {
        // Dùng query có JOIN FETCH uploadedBy để tránh LazyInit + N+1 trong fillBatchResponse().
        Page<ImportBatchEntity> page = importBatchRepository.findAllWithUploadedBy(pageable);
        return PageResponse.of(page.map(this::mapToBatchResponse));
    }

    public ImportBatchDetailResponse getImportBatchById(Long id) {
        // Dùng query có JOIN FETCH uploadedBy để tránh LazyInit trong fillBatchResponse().
        ImportBatchEntity batch = importBatchRepository.findByIdWithUploadedBy(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đợt import với ID: " + id));

        ImportBatchDetailResponse response = new ImportBatchDetailResponse();
        fillBatchResponse(response, batch);

        List<ImportRowEntity> rows = importRowRepository.findByImportBatchImportBatchIdWithAsset(id);
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

        List<ImportRowEntity> rows = importRowRepository.findByImportBatchImportBatchIdWithAsset(id);
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
        r.setErrors(entity.getValidationErrors());
        r.setRawData(entity.getRawData());
        if (entity.getAsset() != null) {
            r.setAssetId(entity.getAsset().getAssetId());
            r.setRawData(new LinkedHashMap<>(entity.getRawData()));
            r.getRawData().put("assetTag", entity.getAsset().getAssetTag());
        }
        return r;
    }

    private UserEntity resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
            Optional<UserEntity> user = userRepository.findByPrincipal(auth.getName());
            if (user.isPresent()) {
                return user.get();
            }
        }
        throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authenticated user is required");
    }
}
