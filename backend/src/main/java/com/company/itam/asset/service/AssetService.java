package com.company.itam.asset.service;

import com.company.itam.asset.dto.request.AssetSearchCriteria;
import com.company.itam.asset.dto.request.CreateHardwareAssetRequest;
import com.company.itam.asset.dto.request.UpdateHardwareAssetRequest;
import com.company.itam.asset.dto.request.ValidateUniquenessRequest;
import com.company.itam.asset.dto.response.AssetDetailResponse;
import com.company.itam.asset.dto.response.AssetResponse;
import com.company.itam.asset.dto.response.UniquenessValidationResponse;
import com.company.itam.asset.entity.AssetEntity;
import com.company.itam.asset.entity.AssetHardwareDetailsEntity;
import com.company.itam.asset.mapper.AssetMapper;
import com.company.itam.asset.repository.AssetHardwareDetailsRepository;
import com.company.itam.asset.repository.AssetRepository;
import com.company.itam.asset.specification.AssetSpecification;
import com.company.itam.catalog.entity.*;
import com.company.itam.catalog.repository.*;
import com.company.itam.common.enums.AssetStatus;
import com.company.itam.common.exception.AppException;
import com.company.itam.common.exception.DuplicateResourceException;
import com.company.itam.common.exception.ResourceNotFoundException;
import com.company.itam.common.pagination.PageResponse;
import com.company.itam.department.entity.DepartmentEntity;
import com.company.itam.department.repository.DepartmentRepository;
import com.company.itam.location.entity.LocationEntity;
import com.company.itam.location.repository.LocationRepository;
import com.company.itam.supplier.entity.SupplierEntity;
import com.company.itam.supplier.repository.SupplierRepository;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AssetService {

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
    private final AssetMapper assetMapper;
    private final LicenseDetailsService licenses;
    private final AssetAuditService audit;

    public AssetService(
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
            AssetMapper assetMapper, LicenseDetailsService licenses, AssetAuditService audit) {
        this.licenses = licenses; this.audit = audit;
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
        this.assetMapper = assetMapper;
    }

    public PageResponse<AssetResponse> getAssets(AssetSearchCriteria criteria, Pageable pageable) {
        Authentication authentication = requireAuthentication();
        if (!canReadInventory(authentication)) {
            throw new AccessDeniedException("Access denied");
        }
        Page<AssetEntity> page = assetRepository.findAll(new AssetSpecification(criteria), pageable);
        return PageResponse.of(page.map(assetMapper::toResponse));
    }

    public PageResponse<AssetResponse> getMyAssets(String keyword, Pageable pageable) {
        Authentication authentication = requireAuthentication();
        requireAssetReader(authentication);
        AssetSearchCriteria criteria = new AssetSearchCriteria();
        criteria.setKeyword(keyword);
        criteria.setAssignedTo(resolveCurrentUser(authentication).getUserId());
        Page<AssetEntity> page = assetRepository.findAll(new AssetSpecification(criteria), pageable);
        return PageResponse.of(page.map(assetMapper::toResponse));
    }

    public AssetDetailResponse getAssetById(Long id) {
        Authentication authentication = requireAuthentication();
        requireAssetReader(authentication);
        AssetEntity asset = assetRepository.findByIdWithHardwareDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài sản với ID: " + id));
        if (!canReadInventory(authentication)) {
            UserEntity currentUser = resolveCurrentUser(authentication);
            if ((asset.getAssignedTo() == null
                    || !Objects.equals(asset.getAssignedTo().getUserId(), currentUser.getUserId()))
                    && !(asset.getLicenseDetails()!=null && licenses.assignedTo(id,currentUser.getUserId()))) {
                // Do not reveal whether another user's asset exists.
                throw new ResourceNotFoundException("Không tìm thấy tài sản với ID: " + id);
            }
        }
        var response = assetMapper.toDetailResponse(asset);
        if (asset.getLicenseDetails()!=null) response.setLicense(licenses.response(asset, canReadInventory(authentication)));
        return response;
    }

    @Transactional
    public AssetDetailResponse createHardwareAsset(CreateHardwareAssetRequest request) {
        return createHardware(request, false);
    }

    @Transactional
    public AssetDetailResponse createHardwareDraft(CreateHardwareAssetRequest request) {
        Authentication auth = requireAuthentication();
        if (!hasAuthority(auth,"ADMIN") && !hasAuthority(auth,"PUR_STAFF")) throw new AccessDeniedException("Access denied");
        return createHardware(request, true);
    }

    private AssetDetailResponse createHardware(CreateHardwareAssetRequest request, boolean draft) {
        String assetTag = normalizeString(request.getAssetTag());
        if (assetTag == null) assetTag = assetRepository.nextGeneratedAssetTag();
        if (assetRepository.existsByAssetTag(assetTag)) {
            throw new DuplicateResourceException("DUPLICATE_ASSET_TAG", "Mã tài sản đã tồn tại: " + assetTag);
        }

        String serialNumber = (request.getSerialNumber() != null && !request.getSerialNumber().trim().isEmpty())
                ? request.getSerialNumber().trim() : null;

        if (serialNumber != null && assetHardwareDetailsRepository.existsBySerialNumber(serialNumber)) {
            throw new DuplicateResourceException("DUPLICATE_SERIAL_NUMBER", "Số Serial Number đã tồn tại: " + serialNumber);
        }

        if (request.getPurchaseCost() != null && request.getPurchaseCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Giá mua không được âm");
        }

        AssetTypeEntity type = assetTypeRepository.findById(request.getTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại tài sản với ID: " + request.getTypeId()));

        AssetStatusEntity status;
        if (request.getStatusId() != null) {
            status = assetStatusRepository.findById(request.getStatusId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trạng thái với ID: " + request.getStatusId()));
        } else {
            status = assetStatusRepository.findByCode(AssetStatus.IN_STOCK)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trạng thái mặc định IN_STOCK"));
        }

        if ((status.getCode() == AssetStatus.PENDING_IMPORT) != draft) {
            throw new AppException(HttpStatus.CONFLICT,"IMPORT_ASSET_LOCKED","Receiving assets must be edited through their draft");
        }
        if (draft && type.getCategory()!=null && type.getCategory().getCode()!=com.company.itam.common.enums.AssetCategory.DEVICE) {
            throw new AppException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","This endpoint supports devices only");
        }
        boolean license = type.getCategory()!=null && type.getCategory().getCode()==com.company.itam.common.enums.AssetCategory.LICENSE;
        if (license && (request.getAssignedToUserId()!=null || status.getCode()!=AssetStatus.IN_STOCK))
            throw new AppException(HttpStatus.CONFLICT,"ASSET_WORKFLOW_REQUIRED","ASSET_WORKFLOW_REQUIRED");
        if (!license && request.getLicense()!=null) throw new AppException(HttpStatus.BAD_REQUEST,"LICENSE_DETAILS_REQUIRED","LICENSE_DETAILS_REQUIRED");
        validateStatusAndAssignment(status.getCode(), request.getAssignedToUserId());

        AssetEntity asset = new AssetEntity();
        asset.setAssetTag(assetTag);
        asset.setName(request.getName().trim());
        asset.setType(type);
        asset.setStatus(status);
        asset.setPoNumber(request.getPoNumber());
        asset.setPurchaseDate(request.getPurchaseDate());
        asset.setPurchaseCost(request.getPurchaseCost() != null ? request.getPurchaseCost() : BigDecimal.ZERO);

        if (request.getAssignedToUserId() != null) {
            UserEntity assignedTo = userRepository.findById(request.getAssignedToUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng được gán với ID: " + request.getAssignedToUserId()));
            asset.setAssignedTo(assignedTo);
        }

        if (request.getDepartmentId() != null) {
            DepartmentEntity department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng ban với ID: " + request.getDepartmentId()));
            asset.setDepartment(department);
        }

        if (request.getLocationId() != null) {
            LocationEntity location = locationRepository.findById(request.getLocationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vị trí với ID: " + request.getLocationId()));
            asset.setLocation(location);
        }

        if (request.getSupplierId() != null) {
            SupplierEntity supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + request.getSupplierId()));
            asset.setSupplier(supplier);
        }

        UserEntity currentUser = resolveCurrentUser();
        asset.setCreatedBy(currentUser);
        asset.setUpdatedBy(currentUser);

        AssetEntity savedAsset = assetRepository.save(asset);

        if (license) {
            if (serialNumber!=null || request.getModelId()!=null || request.getConditionId()!=null || request.getWarrantyExpiration()!=null || request.getActualCpu()!=null || request.getActualRam()!=null || request.getActualStorage()!=null || request.getActualGraphicsCard()!=null)
                throw new AppException(HttpStatus.BAD_REQUEST,"LICENSE_HARDWARE_FIELDS","LICENSE_HARDWARE_FIELDS");
            licenses.save(savedAsset,request.getLicense());
            audit.record(savedAsset.getAssetId(),"CREATE",currentUser,null,audit.snapshot(savedAsset));
            var response=assetMapper.toDetailResponse(savedAsset); response.setLicense(licenses.response(savedAsset,true)); return response;
        }
        // Save Hardware Details
        AssetHardwareDetailsEntity hwDetails = new AssetHardwareDetailsEntity();

        hwDetails.setAsset(savedAsset);
        hwDetails.setSerialNumber(serialNumber);
        hwDetails.setWarrantyExpiration(request.getWarrantyExpiration());
        hwDetails.setActualCpu(normalizeString(request.getActualCpu()));
        hwDetails.setActualRam(normalizeString(request.getActualRam()));
        hwDetails.setActualStorage(normalizeString(request.getActualStorage()));
        hwDetails.setActualGraphicsCard(normalizeString(request.getActualGraphicsCard()));

        if (request.getModelId() != null) {
            ModelEntity model = modelRepository.findById(request.getModelId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy model với ID: " + request.getModelId()));
            if (model.getType()!=null && !Objects.equals(model.getType().getTypeId(),type.getTypeId()))
                throw new AppException(HttpStatus.BAD_REQUEST,"MODEL_TYPE_MISMATCH","Model does not match asset type");
            hwDetails.setModel(model);
        }

        if (request.getConditionId() != null) {
            AssetConditionEntity condition = assetConditionRepository.findById(request.getConditionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tình trạng với ID: " + request.getConditionId()));
            hwDetails.setCondition(condition);
        }

        AssetHardwareDetailsEntity savedHwDetails = assetHardwareDetailsRepository.save(hwDetails);
        savedAsset.setHardwareDetails(savedHwDetails);

        audit.record(savedAsset.getAssetId(),"CREATE",currentUser,null,audit.snapshot(savedAsset));
        return assetMapper.toDetailResponse(savedAsset);
    }

    @Transactional
    public AssetDetailResponse updateHardwareAsset(Long id, UpdateHardwareAssetRequest request) {
        AssetEntity asset = assetRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài sản với ID: " + id));

        if (asset.getStatus().getCode() == AssetStatus.PENDING_IMPORT || assetRepository.hasReceivingHistory(id)) {
            throw new AppException(HttpStatus.CONFLICT,"IMPORT_ASSET_LOCKED","Receiving assets must be edited through their draft");
        }
        var before = audit.snapshot(asset);
        String assetTag = normalizeString(request.getAssetTag());
        if (assetTag != null && !assetTag.equals(asset.getAssetTag())) {
            throw new AppException(HttpStatus.CONFLICT, "ASSET_TAG_IMMUTABLE", "Asset tag cannot be changed after saving");
        }
        assetTag = asset.getAssetTag();
        Optional<AssetEntity> existingTag = assetRepository.findByAssetTag(assetTag);
        if (existingTag.isPresent() && !existingTag.get().getAssetId().equals(id)) {
            throw new DuplicateResourceException("DUPLICATE_ASSET_TAG", "Mã tài sản đã tồn tại: " + assetTag);
        }

        String serialNumber = (request.getSerialNumber() != null && !request.getSerialNumber().trim().isEmpty())
                ? request.getSerialNumber().trim() : null;

        if (serialNumber != null) {
            Optional<AssetHardwareDetailsEntity> existingSerial = assetHardwareDetailsRepository.findBySerialNumber(serialNumber);
            if (existingSerial.isPresent() && !existingSerial.get().getAssetId().equals(id)) {
                throw new DuplicateResourceException("DUPLICATE_SERIAL_NUMBER", "Số Serial Number đã tồn tại: " + serialNumber);
            }
        }

        if (request.getPurchaseCost() != null && request.getPurchaseCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Giá mua không được âm");
        }

        AssetTypeEntity type = assetTypeRepository.findById(request.getTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại tài sản với ID: " + request.getTypeId()));

        AssetStatusEntity status = asset.getStatus();
        if (request.getStatusId() != null && !request.getStatusId().equals(status.getStatusId())) {
            status = assetStatusRepository.findById(request.getStatusId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trạng thái với ID: " + request.getStatusId()));
        }

        if (!Objects.equals(type.getTypeId(),asset.getType().getTypeId()) || status.getCode()!=asset.getStatus().getCode()
                || !Objects.equals(request.getAssignedToUserId(),asset.getAssignedTo()==null?null:asset.getAssignedTo().getUserId()))
            throw new AppException(HttpStatus.CONFLICT,"ASSET_WORKFLOW_REQUIRED","ASSET_WORKFLOW_REQUIRED");
        boolean license = asset.getLicenseDetails()!=null;
        if (!license && request.getLicense()!=null) throw new AppException(HttpStatus.BAD_REQUEST,"LICENSE_DETAILS_REQUIRED","LICENSE_DETAILS_REQUIRED");
        validateStatusAndAssignment(status.getCode(), request.getAssignedToUserId());

        asset.setAssetTag(assetTag);
        asset.setName(request.getName().trim());
        asset.setType(type);
        asset.setStatus(status);
        asset.setPoNumber(request.getPoNumber());
        asset.setPurchaseDate(request.getPurchaseDate());
        if (request.getPurchaseCost() != null) {
            asset.setPurchaseCost(request.getPurchaseCost());
        }

        if (request.getAssignedToUserId() != null) {
            UserEntity assignedTo = userRepository.findById(request.getAssignedToUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng được gán với ID: " + request.getAssignedToUserId()));
            asset.setAssignedTo(assignedTo);
        } else {
            asset.setAssignedTo(null);
        }

        if (request.getDepartmentId() != null) {
            DepartmentEntity department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng ban với ID: " + request.getDepartmentId()));
            asset.setDepartment(department);
        } else {
            asset.setDepartment(null);
        }

        if (request.getLocationId() != null) {
            LocationEntity location = locationRepository.findById(request.getLocationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vị trí với ID: " + request.getLocationId()));
            asset.setLocation(location);
        } else {
            asset.setLocation(null);
        }

        if (request.getSupplierId() != null) {
            SupplierEntity supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + request.getSupplierId()));
            asset.setSupplier(supplier);
        } else {
            asset.setSupplier(null);
        }

        UserEntity currentUser = resolveCurrentUser();
        asset.setUpdatedBy(currentUser);

        if (license) {
            if (serialNumber!=null || request.getModelId()!=null || request.getConditionId()!=null || request.getWarrantyExpiration()!=null || request.getActualCpu()!=null || request.getActualRam()!=null || request.getActualStorage()!=null || request.getActualGraphicsCard()!=null)
                throw new AppException(HttpStatus.BAD_REQUEST,"LICENSE_HARDWARE_FIELDS","LICENSE_HARDWARE_FIELDS");
            licenses.save(asset,request.getLicense());
            audit.record(id,"UPDATE",currentUser,before,audit.snapshot(asset));
            var response=assetMapper.toDetailResponse(asset); response.setLicense(licenses.response(asset,true)); return response;
        }
        // Update hardware details
        AssetHardwareDetailsEntity hwDetails = asset.getHardwareDetails();
        if (hwDetails == null) {
            hwDetails = new AssetHardwareDetailsEntity();

            hwDetails.setAsset(asset);
        }

        hwDetails.setSerialNumber(serialNumber);
        hwDetails.setWarrantyExpiration(request.getWarrantyExpiration());
        hwDetails.setActualCpu(normalizeString(request.getActualCpu()));
        hwDetails.setActualRam(normalizeString(request.getActualRam()));
        hwDetails.setActualStorage(normalizeString(request.getActualStorage()));
        hwDetails.setActualGraphicsCard(normalizeString(request.getActualGraphicsCard()));

        if (request.getModelId() != null) {
            ModelEntity model = modelRepository.findById(request.getModelId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy model với ID: " + request.getModelId()));
            if (model.getType()!=null && !Objects.equals(model.getType().getTypeId(),type.getTypeId()))
                throw new AppException(HttpStatus.BAD_REQUEST,"MODEL_TYPE_MISMATCH","MODEL_TYPE_MISMATCH");
            hwDetails.setModel(model);
        } else {
            hwDetails.setModel(null);
        }

        if (request.getConditionId() != null) {
            AssetConditionEntity condition = assetConditionRepository.findById(request.getConditionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tình trạng với ID: " + request.getConditionId()));
            hwDetails.setCondition(condition);
        } else {
            hwDetails.setCondition(null);
        }

        AssetHardwareDetailsEntity savedHw = assetHardwareDetailsRepository.save(hwDetails);
        asset.setHardwareDetails(savedHw);

        AssetEntity savedAsset = assetRepository.save(asset);
        audit.record(id,"UPDATE",currentUser,before,audit.snapshot(savedAsset));
        return assetMapper.toDetailResponse(savedAsset);
    }

    public UniquenessValidationResponse validateUniqueness(ValidateUniquenessRequest request) {
        boolean tagAvailable = true;
        String tagMsg = "Mã tài sản hợp lệ";
        if (request.getAssetTag() != null && !request.getAssetTag().trim().isEmpty()) {
            Optional<AssetEntity> existing = assetRepository.findByAssetTag(request.getAssetTag().trim());
            if (existing.isPresent() && !existing.get().getAssetId().equals(request.getExcludeAssetId())) {
                tagAvailable = false;
                tagMsg = "Mã tài sản đã tồn tại trong hệ thống";
            }
        }

        boolean serialAvailable = true;
        String serialMsg = "Số serial number hợp lệ";
        if (request.getSerialNumber() != null && !request.getSerialNumber().trim().isEmpty()) {
            Optional<AssetHardwareDetailsEntity> existing = assetHardwareDetailsRepository.findBySerialNumber(request.getSerialNumber().trim());
            if (existing.isPresent() && !existing.get().getAssetId().equals(request.getExcludeAssetId())) {
                serialAvailable = false;
                serialMsg = "Số serial number đã tồn tại trong hệ thống";
            }
        }

        return new UniquenessValidationResponse(tagAvailable, tagMsg, serialAvailable, serialMsg);
    }

    private void validateStatusAndAssignment(AssetStatus status, Long assignedToUserId) {
        if ((status == AssetStatus.IN_STOCK || status == AssetStatus.RETIRED || status == AssetStatus.PENDING_IMPORT) && assignedToUserId != null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_ASSIGNMENT",
                    "Tài sản ở trạng thái " + status.name() + " không được gán người sử dụng");
        }
        if (status == AssetStatus.IN_USE && assignedToUserId == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_REQUIRED",
                    "Tài sản ở trạng thái IN_USE bắt buộc phải có người sử dụng");
        }
    }

    private UserEntity resolveCurrentUser() {
        return resolveCurrentUser(requireAuthentication());
    }

    private UserEntity resolveCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"));
    }

    private Authentication requireAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
        }
        return authentication;
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private boolean canReadInventory(Authentication authentication) {
        return hasAuthority(authentication, "ADMIN") || hasAuthority(authentication, "IT_STAFF");
    }

    private void requireAssetReader(Authentication authentication) {
        if (!canReadInventory(authentication) && !hasAuthority(authentication, "USER")) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private String normalizeString(String val) {
        if (val == null || val.trim().isEmpty()) {
            return null;
        }
        return val.trim();
    }
}
