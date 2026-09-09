package com.company.itam.importbatch.service;

import com.company.itam.asset.repository.AssetHardwareDetailsRepository;
import com.company.itam.asset.repository.AssetRepository;
import com.company.itam.catalog.entity.*;
import com.company.itam.catalog.repository.*;
import com.company.itam.common.enums.AssetStatus;
import com.company.itam.common.enums.ValidationStatus;
import com.company.itam.department.entity.DepartmentEntity;
import com.company.itam.department.repository.DepartmentRepository;
import com.company.itam.importbatch.dto.response.ImportRowDetailResponse;
import com.company.itam.importbatch.dto.response.ImportRowErrorResponse;
import com.company.itam.location.entity.LocationEntity;
import com.company.itam.location.repository.LocationRepository;
import com.company.itam.supplier.entity.SupplierEntity;
import com.company.itam.supplier.repository.SupplierRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class AssetImportValidator {

    private final com.company.itam.common.util.MessageHelper messages;
    private final AssetRepository assetRepository;
    private final AssetHardwareDetailsRepository assetHardwareDetailsRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final AssetStatusRepository assetStatusRepository;
    private final AssetConditionRepository assetConditionRepository;
    private final ModelRepository modelRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final SupplierRepository supplierRepository;

    public AssetImportValidator(
            AssetRepository assetRepository,
            AssetHardwareDetailsRepository assetHardwareDetailsRepository,
            AssetTypeRepository assetTypeRepository,
            AssetStatusRepository assetStatusRepository,
            AssetConditionRepository assetConditionRepository,
            ModelRepository modelRepository,
            DepartmentRepository departmentRepository,
            LocationRepository locationRepository,
            SupplierRepository supplierRepository, com.company.itam.common.util.MessageHelper messages) {
        this.messages = messages;
        this.assetRepository = assetRepository;
        this.assetHardwareDetailsRepository = assetHardwareDetailsRepository;
        this.assetTypeRepository = assetTypeRepository;
        this.assetStatusRepository = assetStatusRepository;
        this.assetConditionRepository = assetConditionRepository;
        this.modelRepository = modelRepository;
        this.departmentRepository = departmentRepository;
        this.locationRepository = locationRepository;
        this.supplierRepository = supplierRepository;
    }

    public List<ImportRowDetailResponse> validateRows(List<Map<String, Object>> rawRows) {
        if (rawRows == null || rawRows.size() > ExcelHelperService.MAX_ROWS) throw new com.company.itam.common.exception.AppException(
                org.springframework.http.HttpStatus.BAD_REQUEST,"EXCEEDS_MAX_ROWS","Too many rows");
        Set<Integer> rowNumbers = new HashSet<>();
        for (Map<String,Object> row : rawRows) {
            if (row == null || !(row.get("rowNumber") instanceof Integer number) || number < 2 || !rowNumbers.add(number))
                throw new com.company.itam.common.exception.AppException(org.springframework.http.HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Invalid row number");
        }
        // Preload reference catalogs to in-memory lookup cache
        CatalogCache cache = new CatalogCache(
                assetTypeRepository.findAll(),
                assetStatusRepository.findAll(),
                assetConditionRepository.findAll(),
                modelRepository.findAll(),
                departmentRepository.findAll(),
                locationRepository.findAll(),
                supplierRepository.findAll()
        );

        // Track seen keys in the current batch to detect internal file duplicates
        Map<String, Integer> seenAssetTags = new HashMap<>(); // tag -> first rowNumber
        Map<String, Integer> seenSerialNumbers = new HashMap<>(); // serial -> first rowNumber

        // First pass: identify duplicates inside file
        Set<String> internalDuplicateTags = new HashSet<>();
        Set<String> internalDuplicateSerials = new HashSet<>();

        for (Map<String, Object> row : rawRows) {
            String assetTag = normalize(row.get("assetTag"));
            if (assetTag != null) {
                String lowerTag = assetTag.toLowerCase(Locale.ROOT);
                if (seenAssetTags.containsKey(lowerTag)) {
                    internalDuplicateTags.add(lowerTag);
                } else {
                    seenAssetTags.put(lowerTag, (Integer) row.get("rowNumber"));
                }
            }

            String serial = normalize(row.get("serialNumber"));
            if (serial != null) {
                String lowerSerial = serial.toLowerCase(Locale.ROOT);
                if (seenSerialNumbers.containsKey(lowerSerial)) {
                    internalDuplicateSerials.add(lowerSerial);
                } else {
                    seenSerialNumbers.put(lowerSerial, (Integer) row.get("rowNumber"));
                }
            }
        }

        List<ImportRowDetailResponse> validatedRows = new ArrayList<>();

        for (Map<String, Object> row : rawRows) {
            Integer rowNumber = (Integer) row.get("rowNumber");
            List<ImportRowErrorResponse> errors = new ArrayList<>();

            // 1. Validate Asset Tag
            String assetTag = normalize(row.get("assetTag"));
            if (assetTag != null) {
                if (assetTag.length() > 100) {
                    errors.add(rowError(rowNumber, "assetTag", "MAX_LENGTH", "Mã tài sản không được vượt quá 100 ký tự"));
                }
                // Check internal duplicate
                if (internalDuplicateTags.contains(assetTag.toLowerCase(Locale.ROOT))) {
                    errors.add(rowError(rowNumber, "assetTag", "DUPLICATE",
                            "Mã tài sản trùng lặp với dòng khác trong cùng file"));
                }
                // Check database duplicate
                if (assetRepository.existsByAssetTag(assetTag)) {
                    errors.add(rowError(rowNumber, "assetTag", "DUPLICATE",
                            "Mã tài sản đã tồn tại trong hệ thống: " + assetTag));
                }
            }

            // 2. Validate Name
            String name = normalize(row.get("name"));
            if (name == null || name.isEmpty()) {
                errors.add(rowError(rowNumber, "name", "REQUIRED", "Tên tài sản là bắt buộc"));
            } else if (name.length() > 255) {
                errors.add(rowError(rowNumber, "name", "MAX_LENGTH", "Tên tài sản không được vượt quá 255 ký tự"));
            }

            // 3. Validate Serial Number
            String serial = normalize(row.get("serialNumber"));
            if (serial != null) {
                if (serial.length() > 255) {
                    errors.add(rowError(rowNumber, "serialNumber", "MAX_LENGTH", "Số Serial không được vượt quá 255 ký tự"));
                }
                // Check internal duplicate
                if (internalDuplicateSerials.contains(serial.toLowerCase(Locale.ROOT))) {
                    errors.add(rowError(rowNumber, "serialNumber", "DUPLICATE",
                            "Số Serial trùng lặp với dòng khác trong cùng file"));
                }
                // Check database duplicate
                if (assetHardwareDetailsRepository.existsBySerialNumber(serial)) {
                    errors.add(rowError(rowNumber, "serialNumber", "DUPLICATE",
                            "Số Serial Number đã tồn tại trong hệ thống: " + serial));
                }
            }

            // 4. Validate Asset Type
            String typeVal = normalize(row.get("type"));
            if (typeVal == null || typeVal.isEmpty()) {
                errors.add(rowError(rowNumber, "type", "REQUIRED", "Loại tài sản là bắt buộc"));
            } else {
                AssetTypeEntity typeEntity = cache.findType(typeVal);
                if (typeEntity == null || (typeEntity.getCategory()!=null && typeEntity.getCategory().getCode()!=com.company.itam.common.enums.AssetCategory.DEVICE)) {
                    errors.add(rowError(rowNumber, "type", "INVALID_REFERENCE",
                            "Loại tài sản không tồn tại trong danh mục: " + typeVal));
                }
            }

            // 5. Validate Model
            String modelVal = normalize(row.get("model"));
            if (modelVal != null && cache.findModel(modelVal) == null) {
                errors.add(rowError(rowNumber, "model", "INVALID_REFERENCE",
                        "Model không tồn tại trong danh mục: " + modelVal));
            }

            if (modelVal!=null && typeVal!=null && cache.findModel(modelVal)!=null && cache.findType(typeVal)!=null
                    && cache.findModel(modelVal).getType()!=null
                    && !Objects.equals(cache.findModel(modelVal).getType().getTypeId(),cache.findType(typeVal).getTypeId())) {
                errors.add(rowError(rowNumber,"model","INVALID_REFERENCE","Model does not match asset type"));
            }
            // 6. Validate Condition
            String conditionVal = normalize(row.get("condition"));
            if (conditionVal != null && cache.findCondition(conditionVal) == null) {
                errors.add(rowError(rowNumber, "condition", "INVALID_REFERENCE",
                        "Tình trạng tài sản không tồn tại: " + conditionVal));
            }

            // 7. Validate Status
            String statusVal = normalize(row.get("status"));
            if (statusVal != null && cache.findStatus(statusVal) == null) {
                errors.add(rowError(rowNumber, "status", "INVALID_REFERENCE",
                        "Trạng thái tài sản không hợp lệ: " + statusVal));
            }

            if (statusVal != null && cache.findStatus(statusVal) != null
                    && (cache.findStatus(statusVal).getCode()==AssetStatus.PENDING_IMPORT || cache.findStatus(statusVal).getCode()==AssetStatus.IN_USE)) {
                errors.add(rowError(rowNumber,"status","INVALID_REFERENCE","Use opening inventory status; receiving and assignment require their workflow"));
            }
            // 8. Validate Department
            String deptVal = normalize(row.get("department"));
            if (deptVal != null && cache.findDepartment(deptVal) == null) {
                errors.add(rowError(rowNumber, "department", "INVALID_REFERENCE",
                        "Phòng ban không tồn tại: " + deptVal));
            }

            // 9. Validate Location
            String locVal = normalize(row.get("location"));
            if (locVal != null && cache.findLocation(locVal) == null) {
                errors.add(rowError(rowNumber, "location", "INVALID_REFERENCE",
                        "Vị trí không tồn tại: " + locVal));
            }

            // 10. Validate Supplier
            String suppVal = normalize(row.get("supplier"));
            if (suppVal != null && cache.findSupplier(suppVal) == null) {
                errors.add(rowError(rowNumber, "supplier", "INVALID_REFERENCE",
                        "Nhà cung cấp không tồn tại: " + suppVal));
            }

            // 11. Validate Purchase Cost
            Object costObj = row.get("purchaseCost");
            if (costObj != null) {
                try {
                    BigDecimal cost = parseBigDecimal(costObj);
                    if (cost.compareTo(BigDecimal.ZERO) < 0) {
                        errors.add(rowError(rowNumber, "purchaseCost", "INVALID_VALUE", "Giá mua không được âm"));
                    }
                } catch (Exception e) {
                    errors.add(rowError(rowNumber, "purchaseCost", "INVALID_FORMAT", "Giá mua không đúng định dạng số"));
                }
            }

            // 12. Validate Purchase Date
            String purchaseDateStr = normalize(row.get("purchaseDate"));
            if (purchaseDateStr != null && parseDate(purchaseDateStr) == null) {
                errors.add(rowError(rowNumber, "purchaseDate", "INVALID_FORMAT",
                        "Ngày mua không đúng định dạng (YYYY-MM-DD hoặc DD/MM/YYYY)"));
            }

            // 13. Validate Warranty Expiration
            String warrantyStr = normalize(row.get("warrantyExpiration"));
            if (warrantyStr != null && parseDate(warrantyStr) == null) {
                errors.add(rowError(rowNumber, "warrantyExpiration", "INVALID_FORMAT",
                        "Hạn bảo hành không đúng định dạng (YYYY-MM-DD hoặc DD/MM/YYYY)"));
            }

            // Determine status
            ValidationStatus status;
            String errorMessage = null;
            if (errors.isEmpty()) {
                status = ValidationStatus.VALID;
            } else {
                boolean hasDuplicate = errors.stream().anyMatch(e -> "DUPLICATE".equals(e.getCode()));
                status = hasDuplicate ? ValidationStatus.DUPLICATE : ValidationStatus.INVALID;
                errorMessage = errors.get(0).getMessage();
                if (errors.size() > 1) {
                    errorMessage += messages.getMessage("import.row.more", errors.size() - 1);
                }
            }

            validatedRows.add(new ImportRowDetailResponse(rowNumber, status, errorMessage, errors, row));
        }

        return validatedRows;
    }

    private ImportRowErrorResponse rowError(Integer row, String field, String code, String fallback) {
        return new ImportRowErrorResponse(row, field, code,
                messages.getMessageWithDefault("import.row." + field + "." + code, fallback));
    }

    public static String normalize(Object val) {
        if (val == null) {
            return null;
        }
        String s = val.toString().trim();
        return s.isEmpty() ? null : s;
    }

    public static BigDecimal parseBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof Number) return BigDecimal.valueOf(((Number) val).doubleValue());
        String s = val.toString().trim().replace(",", "");
        if (s.isEmpty()) return BigDecimal.ZERO;
        return new BigDecimal(s);
    }

    public static LocalDate parseDate(String val) {
        if (val == null || val.trim().isEmpty()) {
            return null;
        }
        String clean = val.trim();
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/uuuu"),
                DateTimeFormatter.ofPattern("d/M/uuuu"),
                DateTimeFormatter.ofPattern("dd-MM/uuuu"),
                DateTimeFormatter.ofPattern("uuuu/MM/dd")
        );

        for (DateTimeFormatter fmt : formatters) {
            try {
                return LocalDate.parse(clean, fmt.withResolverStyle(java.time.format.ResolverStyle.STRICT));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    // Reference Catalog Cache
    public static class CatalogCache {
        private final Map<String, AssetTypeEntity> typeByCodeOrName = new HashMap<>();
        private final Map<String, AssetStatusEntity> statusByCodeOrName = new HashMap<>();
        private final Map<String, AssetConditionEntity> conditionByCodeOrName = new HashMap<>();
        private final Map<String, ModelEntity> modelByName = new HashMap<>();
        private final Map<String, DepartmentEntity> deptByCodeOrName = new HashMap<>();
        private final Map<String, LocationEntity> locByCodeOrName = new HashMap<>();
        private final Map<String, SupplierEntity> supplierByCodeOrName = new HashMap<>();

        public CatalogCache(
                List<AssetTypeEntity> types,
                List<AssetStatusEntity> statuses,
                List<AssetConditionEntity> conditions,
                List<ModelEntity> models,
                List<DepartmentEntity> depts,
                List<LocationEntity> locs,
                List<SupplierEntity> suppliers) {
            for (AssetTypeEntity t : types) {
                if (t.getCode() != null) typeByCodeOrName.put(t.getCode().toLowerCase(Locale.ROOT), t);
                if (t.getName() != null) typeByCodeOrName.put(t.getName().toLowerCase(Locale.ROOT), t);
            }
            for (AssetStatusEntity s : statuses) {
                if (s.getCode() != null) statusByCodeOrName.put(s.getCode().name().toLowerCase(Locale.ROOT), s);
                if (s.getName() != null) statusByCodeOrName.put(s.getName().toLowerCase(Locale.ROOT), s);
            }
            for (AssetConditionEntity c : conditions) {
                if (c.getCode() != null) conditionByCodeOrName.put(c.getCode().name().toLowerCase(Locale.ROOT), c);
                if (c.getName() != null) conditionByCodeOrName.put(c.getName().toLowerCase(Locale.ROOT), c);
            }
            for (ModelEntity m : models) {
                if (m.getName() != null) modelByName.put(m.getName().toLowerCase(Locale.ROOT), m);
            }
            for (DepartmentEntity d : depts) {
                if (d.getCode() != null) deptByCodeOrName.put(d.getCode().toLowerCase(Locale.ROOT), d);
                if (d.getName() != null) deptByCodeOrName.put(d.getName().toLowerCase(Locale.ROOT), d);
            }
            for (LocationEntity l : locs) {
                if (l.getCode() != null) locByCodeOrName.put(l.getCode().toLowerCase(Locale.ROOT), l);
                if (l.getName() != null) locByCodeOrName.put(l.getName().toLowerCase(Locale.ROOT), l);
            }
            for (SupplierEntity s : suppliers) {
                if (s.getCode() != null) supplierByCodeOrName.put(s.getCode().toLowerCase(Locale.ROOT), s);
                if (s.getName() != null) supplierByCodeOrName.put(s.getName().toLowerCase(Locale.ROOT), s);
            }
        }

        public AssetTypeEntity findType(String val) {
            return typeByCodeOrName.get(val.toLowerCase(Locale.ROOT));
        }

        public AssetStatusEntity findStatus(String val) {
            return statusByCodeOrName.get(val.toLowerCase(Locale.ROOT));
        }

        public AssetConditionEntity findCondition(String val) {
            return conditionByCodeOrName.get(val.toLowerCase(Locale.ROOT));
        }

        public ModelEntity findModel(String val) {
            return modelByName.get(val.toLowerCase(Locale.ROOT));
        }

        public DepartmentEntity findDepartment(String val) {
            return deptByCodeOrName.get(val.toLowerCase(Locale.ROOT));
        }

        public LocationEntity findLocation(String val) {
            return locByCodeOrName.get(val.toLowerCase(Locale.ROOT));
        }

        public SupplierEntity findSupplier(String val) {
            return supplierByCodeOrName.get(val.toLowerCase(Locale.ROOT));
        }
    }
}
