package com.company.itam.catalog.service;

import com.company.itam.asset.repository.AssetHardwareDetailsRepository;
import com.company.itam.asset.repository.AssetLicenseDetailsRepository;
import com.company.itam.asset.repository.AssetRepository;
import com.company.itam.catalog.dto.*;
import com.company.itam.catalog.entity.*;
import com.company.itam.catalog.repository.*;
import com.company.itam.common.exception.CatalogInUseException;
import com.company.itam.common.exception.DuplicateResourceException;
import com.company.itam.common.exception.ResourceNotFoundException;
import com.company.itam.common.pagination.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final AssetCategoryRepository categoryRepository;
    private final AssetTypeRepository typeRepository;
    private final AssetStatusRepository statusRepository;
    private final AssetConditionRepository conditionRepository;
    private final ModelRepository modelRepository;
    private final SoftwareCatalogRepository softwareCatalogRepository;
    private final LicenseAssignmentTypeRepository assignmentTypeRepository;
    private final LicenseTermTypeRepository termTypeRepository;
    private final AssetRepository assetRepository;
    private final AssetHardwareDetailsRepository hardwareDetailsRepository;
    private final AssetLicenseDetailsRepository licenseDetailsRepository;

    public CatalogService(AssetCategoryRepository categoryRepository,
                          AssetTypeRepository typeRepository,
                          AssetStatusRepository statusRepository,
                          AssetConditionRepository conditionRepository,
                          ModelRepository modelRepository,
                          SoftwareCatalogRepository softwareCatalogRepository,
                          LicenseAssignmentTypeRepository assignmentTypeRepository,
                          LicenseTermTypeRepository termTypeRepository,
                          AssetRepository assetRepository,
                          AssetHardwareDetailsRepository hardwareDetailsRepository,
                          AssetLicenseDetailsRepository licenseDetailsRepository) {
        this.categoryRepository = categoryRepository;
        this.typeRepository = typeRepository;
        this.statusRepository = statusRepository;
        this.conditionRepository = conditionRepository;
        this.modelRepository = modelRepository;
        this.softwareCatalogRepository = softwareCatalogRepository;
        this.assignmentTypeRepository = assignmentTypeRepository;
        this.termTypeRepository = termTypeRepository;
        this.assetRepository = assetRepository;
        this.hardwareDetailsRepository = hardwareDetailsRepository;
        this.licenseDetailsRepository = licenseDetailsRepository;
    }

    // ==========================================
    // 1. ASSET CATEGORIES
    // ==========================================
    @Transactional(readOnly = true)
    public PageResponse<AssetCategoryResponse> getAssetCategories(Pageable pageable) {
        Page<AssetCategoryEntity> page = categoryRepository.findAll(pageable);
        return PageResponse.of(page.map(this::toCategoryResponse));
    }

    @Transactional(readOnly = true)
    public AssetCategoryResponse getAssetCategoryById(Long id) {
        AssetCategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm tài sản với ID: " + id));
        return toCategoryResponse(entity);
    }

    @Transactional
    public AssetCategoryResponse createAssetCategory(AssetCategoryRequest request) {
        if (categoryRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Mã nhóm tài sản đã tồn tại: " + request.getCode());
        }
        AssetCategoryEntity entity = new AssetCategoryEntity();
        entity.setCode(request.getCode());
        entity.setName(request.getName().trim());
        entity.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        return toCategoryResponse(categoryRepository.save(entity));
    }

    @Transactional
    public AssetCategoryResponse updateAssetCategory(Long id, AssetCategoryRequest request) {
        AssetCategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm tài sản với ID: " + id));
        if (entity.getCode() != request.getCode() && categoryRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Mã nhóm tài sản đã tồn tại: " + request.getCode());
        }
        entity.setCode(request.getCode());
        entity.setName(request.getName().trim());
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        return toCategoryResponse(categoryRepository.save(entity));
    }

    @Transactional
    public void deleteAssetCategory(Long id) {
        AssetCategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm tài sản với ID: " + id));
        if (typeRepository.existsByCategoryCategoryId(id)) {
            throw new CatalogInUseException("Nhóm tài sản đang chứa loại tài sản, không thể xóa");
        }
        categoryRepository.delete(entity);
    }

    @Transactional
    public AssetCategoryResponse toggleActiveCategory(Long id, Boolean active) {
        AssetCategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm tài sản với ID: " + id));
        entity.setIsActive(active != null ? active : !Boolean.TRUE.equals(entity.getIsActive()));
        return toCategoryResponse(categoryRepository.save(entity));
    }

    // ==========================================
    // 2. ASSET TYPES
    // ==========================================
    @Transactional(readOnly = true)
    public PageResponse<AssetTypeResponse> getAssetTypes(String search, Boolean isActive, Pageable pageable) {
        Page<AssetTypeEntity> page;
        if (search != null && !search.isBlank()) {
            page = typeRepository.searchActive(search.trim(), isActive, pageable);
        } else if (isActive != null) {
            page = typeRepository.findByIsActive(isActive, pageable);
        } else {
            page = typeRepository.findAll(pageable);
        }
        return PageResponse.of(page.map(this::toTypeResponse));
    }

    @Transactional(readOnly = true)
    public AssetTypeResponse getAssetTypeById(Long id) {
        AssetTypeEntity entity = typeRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại tài sản với ID: " + id));
        return toTypeResponse(entity);
    }

    @Transactional
    public AssetTypeResponse createAssetType(AssetTypeRequest request) {
        if (typeRepository.existsByCodeIgnoreCase(request.getCode().trim())) {
            throw new DuplicateResourceException("Mã loại tài sản đã tồn tại: " + request.getCode());
        }
        AssetCategoryEntity category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm tài sản với ID: " + request.getCategoryId()));

        AssetTypeEntity entity = new AssetTypeEntity();
        entity.setCode(request.getCode().trim().toUpperCase());
        entity.setCategory(category);
        entity.setName(request.getName().trim());
        entity.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        return toTypeResponse(typeRepository.save(entity));
    }

    @Transactional
    public AssetTypeResponse updateAssetType(Long id, AssetTypeRequest request) {
        AssetTypeEntity entity = typeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại tài sản với ID: " + id));

        if (!java.util.Objects.equals(entity.getCategory().getCategoryId(), request.getCategoryId())
                && assetRepository.existsByTypeTypeId(id)) {
            throw new com.company.itam.common.exception.AppException(org.springframework.http.HttpStatus.CONFLICT,
                    "ASSET_WORKFLOW_REQUIRED", "ASSET_WORKFLOW_REQUIRED");
        }

        if (!entity.getCode().equalsIgnoreCase(request.getCode().trim())
                && typeRepository.existsByCodeIgnoreCase(request.getCode().trim())) {
            throw new DuplicateResourceException("Mã loại tài sản đã tồn tại: " + request.getCode());
        }
        AssetCategoryEntity category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm tài sản với ID: " + request.getCategoryId()));

        entity.setCode(request.getCode().trim().toUpperCase());
        entity.setCategory(category);
        entity.setName(request.getName().trim());
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        return toTypeResponse(typeRepository.save(entity));
    }

    @Transactional
    public void deleteAssetType(Long id) {
        AssetTypeEntity entity = typeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại tài sản với ID: " + id));
        if (assetRepository.existsByTypeTypeId(id)) {
            throw new CatalogInUseException("Loại tài sản đang được sử dụng bởi tài sản, không thể xóa");
        }
        if (modelRepository.existsByTypeTypeId(id)) {
            throw new CatalogInUseException("Loại tài sản đang được sử dụng bởi model thiết bị, không thể xóa");
        }
        typeRepository.delete(entity);
    }

    @Transactional
    public AssetTypeResponse toggleActiveType(Long id, Boolean active) {
        AssetTypeEntity entity = typeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại tài sản với ID: " + id));
        entity.setIsActive(active != null ? active : !Boolean.TRUE.equals(entity.getIsActive()));
        return toTypeResponse(typeRepository.save(entity));
    }

    // ==========================================
    // 3. ASSET STATUSES
    // ==========================================
    @Transactional(readOnly = true)
    public PageResponse<AssetStatusResponse> getAssetStatuses(Pageable pageable) {
        Page<AssetStatusEntity> page = statusRepository.findAll(pageable);
        return PageResponse.of(page.map(this::toStatusResponse));
    }

    @Transactional(readOnly = true)
    public AssetStatusResponse getAssetStatusById(Long id) {
        AssetStatusEntity entity = statusRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trạng thái với ID: " + id));
        return toStatusResponse(entity);
    }

    @Transactional
    public AssetStatusResponse createAssetStatus(AssetStatusRequest request) {
        if (statusRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Mã trạng thái đã tồn tại: " + request.getCode());
        }
        AssetStatusEntity entity = new AssetStatusEntity();
        entity.setCode(request.getCode());
        entity.setName(request.getName().trim());
        entity.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        return toStatusResponse(statusRepository.save(entity));
    }

    @Transactional
    public AssetStatusResponse updateAssetStatus(Long id, AssetStatusRequest request) {
        AssetStatusEntity entity = statusRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trạng thái với ID: " + id));
        if (entity.getCode()!=request.getCode() || Boolean.FALSE.equals(request.getIsActive())) throw new com.company.itam.common.exception.AppException(org.springframework.http.HttpStatus.CONFLICT,"SYSTEM_CATALOG_LOCKED","System catalog code cannot be changed, disabled or deleted");
        if (entity.getCode() != request.getCode() && statusRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Mã trạng thái đã tồn tại: " + request.getCode());
        }
        entity.setCode(request.getCode());
        entity.setName(request.getName().trim());
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        return toStatusResponse(statusRepository.save(entity));
    }

    @Transactional
    public void deleteAssetStatus(Long id) {
        AssetStatusEntity entity = statusRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trạng thái với ID: " + id));
        if (entity.getCode()!=null) throw new com.company.itam.common.exception.AppException(org.springframework.http.HttpStatus.CONFLICT,"SYSTEM_CATALOG_LOCKED","System catalog code cannot be changed, disabled or deleted");
        if (assetRepository.existsByStatusCode(entity.getCode())) {
            throw new CatalogInUseException("Trạng thái đang được sử dụng bởi tài sản, không thể xóa");
        }
        statusRepository.delete(entity);
    }

    @Transactional
    public AssetStatusResponse toggleActiveStatus(Long id, Boolean active) {
        AssetStatusEntity entity = statusRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trạng thái với ID: " + id));
        if (!Boolean.TRUE.equals(active)) throw new com.company.itam.common.exception.AppException(org.springframework.http.HttpStatus.CONFLICT,"SYSTEM_CATALOG_LOCKED","System catalog code cannot be changed, disabled or deleted");
        entity.setIsActive(active != null ? active : !Boolean.TRUE.equals(entity.getIsActive()));
        return toStatusResponse(statusRepository.save(entity));
    }

    // ==========================================
    // 4. ASSET CONDITIONS
    // ==========================================
    @Transactional(readOnly = true)
    public PageResponse<AssetConditionResponse> getAssetConditions(Pageable pageable) {
        Page<AssetConditionEntity> page = conditionRepository.findAll(pageable);
        return PageResponse.of(page.map(this::toConditionResponse));
    }

    @Transactional(readOnly = true)
    public AssetConditionResponse getAssetConditionById(Long id) {
        AssetConditionEntity entity = conditionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tình trạng với ID: " + id));
        return toConditionResponse(entity);
    }

    @Transactional
    public AssetConditionResponse createAssetCondition(AssetConditionRequest request) {
        if (conditionRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Mã tình trạng đã tồn tại: " + request.getCode());
        }
        AssetConditionEntity entity = new AssetConditionEntity();
        entity.setCode(request.getCode());
        entity.setName(request.getName().trim());
        entity.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        return toConditionResponse(conditionRepository.save(entity));
    }

    @Transactional
    public AssetConditionResponse updateAssetCondition(Long id, AssetConditionRequest request) {
        AssetConditionEntity entity = conditionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tình trạng với ID: " + id));
        if (entity.getCode() != request.getCode() && conditionRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Mã tình trạng đã tồn tại: " + request.getCode());
        }
        entity.setCode(request.getCode());
        entity.setName(request.getName().trim());
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        return toConditionResponse(conditionRepository.save(entity));
    }

    @Transactional
    public void deleteAssetCondition(Long id) {
        AssetConditionEntity entity = conditionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tình trạng với ID: " + id));
        if (hardwareDetailsRepository.existsByConditionCode(entity.getCode())) {
            throw new CatalogInUseException("Tình trạng đang được sử dụng bởi tài sản, không thể xóa");
        }
        conditionRepository.delete(entity);
    }

    @Transactional
    public AssetConditionResponse toggleActiveCondition(Long id, Boolean active) {
        AssetConditionEntity entity = conditionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tình trạng với ID: " + id));
        entity.setIsActive(active != null ? active : !Boolean.TRUE.equals(entity.getIsActive()));
        return toConditionResponse(conditionRepository.save(entity));
    }

    // ==========================================
    // 5. MODELS
    // ==========================================
    @Transactional(readOnly = true)
    public PageResponse<ModelResponse> getModels(String search, Boolean isActive, Pageable pageable) {
        Page<ModelEntity> page;
        if (search != null && !search.isBlank()) {
            page = modelRepository.searchActive(search.trim(), isActive, pageable);
        } else if (isActive != null) {
            page = modelRepository.findByIsActive(isActive, pageable);
        } else {
            page = modelRepository.findAll(pageable);
        }
        return PageResponse.of(page.map(this::toModelResponse));
    }

    @Transactional(readOnly = true)
    public ModelResponse getModelById(Long id) {
        ModelEntity entity = modelRepository.findByIdWithType(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy model với ID: " + id));
        return toModelResponse(entity);
    }

    @Transactional
    public ModelResponse createModel(ModelRequest request) {
        if (modelRepository.existsByTypeTypeIdAndBrandIgnoreCaseAndNameIgnoreCase(
                request.getTypeId(), request.getBrand().trim(), request.getName().trim())) {
            throw new DuplicateResourceException("Model với tên và thương hiệu này đã tồn tại cho loại tài sản");
        }
        AssetTypeEntity type = typeRepository.findById(request.getTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại tài sản với ID: " + request.getTypeId()));

        ModelEntity entity = new ModelEntity();
        entity.setName(request.getName().trim());
        entity.setBrand(request.getBrand().trim());
        entity.setType(type);
        entity.setDefaultCpu(request.getDefaultCpu());
        entity.setDefaultRam(request.getDefaultRam());
        entity.setDefaultStorage(request.getDefaultStorage());
        entity.setDefaultGraphicsCard(request.getDefaultGraphicsCard());
        entity.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        return toModelResponse(modelRepository.save(entity));
    }

    @Transactional
    public ModelResponse updateModel(Long id, ModelRequest request) {
        ModelEntity entity = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy model với ID: " + id));

        boolean changed = !entity.getName().equalsIgnoreCase(request.getName().trim())
                || !entity.getBrand().equalsIgnoreCase(request.getBrand().trim())
                || !entity.getType().getTypeId().equals(request.getTypeId());

        if (changed && modelRepository.existsByTypeTypeIdAndBrandIgnoreCaseAndNameIgnoreCase(
                request.getTypeId(), request.getBrand().trim(), request.getName().trim())) {
            throw new DuplicateResourceException("Model với tên và thương hiệu này đã tồn tại cho loại tài sản");
        }

        AssetTypeEntity type = typeRepository.findById(request.getTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại tài sản với ID: " + request.getTypeId()));

        entity.setName(request.getName().trim());
        entity.setBrand(request.getBrand().trim());
        entity.setType(type);
        entity.setDefaultCpu(request.getDefaultCpu());
        entity.setDefaultRam(request.getDefaultRam());
        entity.setDefaultStorage(request.getDefaultStorage());
        entity.setDefaultGraphicsCard(request.getDefaultGraphicsCard());
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }

        return toModelResponse(modelRepository.save(entity));
    }

    @Transactional
    public void deleteModel(Long id) {
        ModelEntity entity = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy model với ID: " + id));
        if (hardwareDetailsRepository.existsByModelModelId(id)) {
            throw new CatalogInUseException("Model đang được sử dụng bởi tài sản, không thể xóa");
        }
        modelRepository.delete(entity);
    }

    @Transactional
    public ModelResponse toggleActiveModel(Long id, Boolean active) {
        ModelEntity entity = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy model với ID: " + id));
        entity.setIsActive(active != null ? active : !Boolean.TRUE.equals(entity.getIsActive()));
        return toModelResponse(modelRepository.save(entity));
    }

    // ==========================================
    // 6. SOFTWARE CATALOG
    // ==========================================
    @Transactional(readOnly = true)
    public PageResponse<SoftwareCatalogResponse> getSoftwareCatalog(String search, Boolean isActive, Pageable pageable) {
        Page<SoftwareCatalogEntity> page;
        if (search != null && !search.isBlank()) {
            page = softwareCatalogRepository.searchActive(search.trim(), isActive, pageable);
        } else if (isActive != null) {
            page = softwareCatalogRepository.findByIsActive(isActive, pageable);
        } else {
            page = softwareCatalogRepository.findAll(pageable);
        }
        return PageResponse.of(page.map(this::toSoftwareResponse));
    }

    @Transactional(readOnly = true)
    public SoftwareCatalogResponse getSoftwareCatalogById(Long id) {
        SoftwareCatalogEntity entity = softwareCatalogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phần mềm với ID: " + id));
        return toSoftwareResponse(entity);
    }

    @Transactional
    public SoftwareCatalogResponse createSoftwareCatalog(SoftwareCatalogRequest request) {
        if (softwareCatalogRepository.existsByManufacturerIgnoreCaseAndNameIgnoreCaseAndVersionIgnoreCase(
                request.getManufacturer().trim(), request.getName().trim(), request.getVersion() != null ? request.getVersion().trim() : "")) {
            throw new DuplicateResourceException("Phần mềm với thông tin này đã tồn tại trong danh mục");
        }

        SoftwareCatalogEntity entity = new SoftwareCatalogEntity();
        entity.setName(request.getName().trim());
        entity.setManufacturer(request.getManufacturer().trim());
        entity.setVersion(request.getVersion() != null ? request.getVersion().trim() : null);
        entity.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        return toSoftwareResponse(softwareCatalogRepository.save(entity));
    }

    @Transactional
    public SoftwareCatalogResponse updateSoftwareCatalog(Long id, SoftwareCatalogRequest request) {
        SoftwareCatalogEntity entity = softwareCatalogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phần mềm với ID: " + id));

        entity.setName(request.getName().trim());
        entity.setManufacturer(request.getManufacturer().trim());
        entity.setVersion(request.getVersion() != null ? request.getVersion().trim() : null);
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }

        return toSoftwareResponse(softwareCatalogRepository.save(entity));
    }

    @Transactional
    public void deleteSoftwareCatalog(Long id) {
        SoftwareCatalogEntity entity = softwareCatalogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phần mềm với ID: " + id));
        if (licenseDetailsRepository.existsBySoftwareCatalogSoftwareCatalogId(id)) {
            throw new CatalogInUseException("Phần mềm đang được sử dụng trong bản quyền tài sản, không thể xóa");
        }
        softwareCatalogRepository.delete(entity);
    }

    @Transactional
    public SoftwareCatalogResponse toggleActiveSoftware(Long id, Boolean active) {
        SoftwareCatalogEntity entity = softwareCatalogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phần mềm với ID: " + id));
        entity.setIsActive(active != null ? active : !Boolean.TRUE.equals(entity.getIsActive()));
        return toSoftwareResponse(softwareCatalogRepository.save(entity));
    }

    // ==========================================
    // 7. LICENSE ASSIGNMENT TYPES
    // ==========================================
    @Transactional(readOnly = true)
    public PageResponse<LicenseAssignmentTypeResponse> getLicenseAssignmentTypes(Pageable pageable) {
        Page<LicenseAssignmentTypeEntity> page = assignmentTypeRepository.findAll(pageable);
        return PageResponse.of(page.map(this::toAssignmentTypeResponse));
    }

    @Transactional(readOnly = true)
    public LicenseAssignmentTypeResponse getLicenseAssignmentTypeById(Long id) {
        LicenseAssignmentTypeEntity entity = assignmentTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại gán license với ID: " + id));
        return toAssignmentTypeResponse(entity);
    }

    @Transactional
    public LicenseAssignmentTypeResponse createLicenseAssignmentType(LicenseAssignmentTypeRequest request) {
        if (assignmentTypeRepository.existsByCode(request.getCode().trim())) {
            throw new DuplicateResourceException("Mã loại gán license đã tồn tại: " + request.getCode());
        }
        LicenseAssignmentTypeEntity entity = new LicenseAssignmentTypeEntity();
        entity.setCode(request.getCode().trim());
        entity.setName(request.getName().trim());
        entity.setActive(request.getActive() != null ? request.getActive() : true);
        return toAssignmentTypeResponse(assignmentTypeRepository.save(entity));
    }

    @Transactional
    public LicenseAssignmentTypeResponse updateLicenseAssignmentType(Long id, LicenseAssignmentTypeRequest request) {
        LicenseAssignmentTypeEntity entity = assignmentTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại gán license với ID: " + id));
        if (java.util.Set.of("OEM","PER_USER").contains(entity.getCode()) && (!entity.getCode().equals(request.getCode()) || Boolean.FALSE.equals(request.getActive()))) throw new com.company.itam.common.exception.AppException(org.springframework.http.HttpStatus.CONFLICT,"SYSTEM_CATALOG_LOCKED","System catalog code cannot be changed, disabled or deleted");
        if (!entity.getCode().equalsIgnoreCase(request.getCode().trim())
                && assignmentTypeRepository.existsByCode(request.getCode().trim())) {
            throw new DuplicateResourceException("Mã loại gán license đã tồn tại: " + request.getCode());
        }
        entity.setCode(request.getCode().trim());
        entity.setName(request.getName().trim());
        if (request.getActive() != null) {
            entity.setActive(request.getActive());
        }
        return toAssignmentTypeResponse(assignmentTypeRepository.save(entity));
    }

    @Transactional
    public void deleteLicenseAssignmentType(Long id) {
        LicenseAssignmentTypeEntity entity = assignmentTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại gán license với ID: " + id));
        if (java.util.Set.of("OEM","PER_USER").contains(entity.getCode())) throw new com.company.itam.common.exception.AppException(org.springframework.http.HttpStatus.CONFLICT,"SYSTEM_CATALOG_LOCKED","System catalog code cannot be changed, disabled or deleted");
        if (licenseDetailsRepository.existsByAssignmentTypeId(id)) {
            throw new CatalogInUseException("Loại gán license đang được sử dụng, không thể xóa");
        }
        assignmentTypeRepository.delete(entity);
    }

    @Transactional
    public LicenseAssignmentTypeResponse toggleActiveAssignmentType(Long id, Boolean active) {
        LicenseAssignmentTypeEntity entity = assignmentTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại gán license với ID: " + id));
        if (java.util.Set.of("OEM","PER_USER").contains(entity.getCode()) && !Boolean.TRUE.equals(active)) throw new com.company.itam.common.exception.AppException(org.springframework.http.HttpStatus.CONFLICT,"SYSTEM_CATALOG_LOCKED","System catalog code cannot be changed, disabled or deleted");
        entity.setActive(active != null ? active : !Boolean.TRUE.equals(entity.getActive()));
        return toAssignmentTypeResponse(assignmentTypeRepository.save(entity));
    }

    // ==========================================
    // 8. LICENSE TERM TYPES
    // ==========================================
    @Transactional(readOnly = true)
    public PageResponse<LicenseTermTypeResponse> getLicenseTermTypes(Pageable pageable) {
        Page<LicenseTermTypeEntity> page = termTypeRepository.findAll(pageable);
        return PageResponse.of(page.map(this::toTermTypeResponse));
    }

    @Transactional(readOnly = true)
    public LicenseTermTypeResponse getLicenseTermTypeById(Long id) {
        LicenseTermTypeEntity entity = termTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại thời hạn license với ID: " + id));
        return toTermTypeResponse(entity);
    }

    @Transactional
    public LicenseTermTypeResponse createLicenseTermType(LicenseTermTypeRequest request) {
        if (termTypeRepository.existsByCode(request.getCode().trim())) {
            throw new DuplicateResourceException("Mã loại thời hạn license đã tồn tại: " + request.getCode());
        }
        LicenseTermTypeEntity entity = new LicenseTermTypeEntity();
        entity.setCode(request.getCode().trim());
        entity.setName(request.getName().trim());
        entity.setActive(request.getActive() != null ? request.getActive() : true);
        return toTermTypeResponse(termTypeRepository.save(entity));
    }

    @Transactional
    public LicenseTermTypeResponse updateLicenseTermType(Long id, LicenseTermTypeRequest request) {
        LicenseTermTypeEntity entity = termTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại thời hạn license với ID: " + id));
        if (java.util.Set.of("PERPETUAL","SUBSCRIPTION").contains(entity.getCode()) && (!entity.getCode().equals(request.getCode()) || Boolean.FALSE.equals(request.getActive()))) throw new com.company.itam.common.exception.AppException(org.springframework.http.HttpStatus.CONFLICT,"SYSTEM_CATALOG_LOCKED","System catalog code cannot be changed, disabled or deleted");
        if (!entity.getCode().equalsIgnoreCase(request.getCode().trim())
                && termTypeRepository.existsByCode(request.getCode().trim())) {
            throw new DuplicateResourceException("Mã loại thời hạn license đã tồn tại: " + request.getCode());
        }
        entity.setCode(request.getCode().trim());
        entity.setName(request.getName().trim());
        if (request.getActive() != null) {
            entity.setActive(request.getActive());
        }
        return toTermTypeResponse(termTypeRepository.save(entity));
    }

    @Transactional
    public void deleteLicenseTermType(Long id) {
        LicenseTermTypeEntity entity = termTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại thời hạn license với ID: " + id));
        if (java.util.Set.of("PERPETUAL","SUBSCRIPTION").contains(entity.getCode())) throw new com.company.itam.common.exception.AppException(org.springframework.http.HttpStatus.CONFLICT,"SYSTEM_CATALOG_LOCKED","System catalog code cannot be changed, disabled or deleted");
        if (licenseDetailsRepository.existsByTermTypeId(id)) {
            throw new CatalogInUseException("Loại thời hạn license đang được sử dụng, không thể xóa");
        }
        termTypeRepository.delete(entity);
    }

    @Transactional
    public LicenseTermTypeResponse toggleActiveTermType(Long id, Boolean active) {
        LicenseTermTypeEntity entity = termTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại thời hạn license với ID: " + id));
        if (java.util.Set.of("PERPETUAL","SUBSCRIPTION").contains(entity.getCode()) && !Boolean.TRUE.equals(active)) throw new com.company.itam.common.exception.AppException(org.springframework.http.HttpStatus.CONFLICT,"SYSTEM_CATALOG_LOCKED","System catalog code cannot be changed, disabled or deleted");
        entity.setActive(active != null ? active : !Boolean.TRUE.equals(entity.getActive()));
        return toTermTypeResponse(termTypeRepository.save(entity));
    }

    // ==========================================
    // MAPPERS
    // ==========================================
    private AssetCategoryResponse toCategoryResponse(AssetCategoryEntity entity) {
        return new AssetCategoryResponse(entity.getCategoryId(), entity.getCode(), entity.getName(), entity.getIsActive());
    }

    private AssetTypeResponse toTypeResponse(AssetTypeEntity entity) {
        return new AssetTypeResponse(
                entity.getTypeId(),
                entity.getCode(),
                entity.getCategory() != null ? entity.getCategory().getCategoryId() : null,
                entity.getCategory() != null ? entity.getCategory().getName() : null,
                entity.getName(),
                entity.getIsActive()
        );
    }

    private AssetStatusResponse toStatusResponse(AssetStatusEntity entity) {
        return new AssetStatusResponse(entity.getStatusId(), entity.getCode(), entity.getName(), entity.getIsActive());
    }

    private AssetConditionResponse toConditionResponse(AssetConditionEntity entity) {
        return new AssetConditionResponse(entity.getConditionId(), entity.getCode(), entity.getName(), entity.getIsActive());
    }

    private ModelResponse toModelResponse(ModelEntity entity) {
        return new ModelResponse(
                entity.getModelId(),
                entity.getName(),
                entity.getBrand(),
                entity.getType() != null ? entity.getType().getTypeId() : null,
                entity.getType() != null ? entity.getType().getName() : null,
                entity.getDefaultCpu(),
                entity.getDefaultRam(),
                entity.getDefaultStorage(),
                entity.getDefaultGraphicsCard(),
                entity.getIsActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private SoftwareCatalogResponse toSoftwareResponse(SoftwareCatalogEntity entity) {
        return new SoftwareCatalogResponse(
                entity.getSoftwareCatalogId(),
                entity.getName(),
                entity.getManufacturer(),
                entity.getVersion(),
                entity.getIsActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private LicenseAssignmentTypeResponse toAssignmentTypeResponse(LicenseAssignmentTypeEntity entity) {
        return new LicenseAssignmentTypeResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getActive());
    }

    private LicenseTermTypeResponse toTermTypeResponse(LicenseTermTypeEntity entity) {
        return new LicenseTermTypeResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getActive());
    }
}
