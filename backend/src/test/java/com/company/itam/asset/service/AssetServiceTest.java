package com.company.itam.asset.service;

import com.company.itam.asset.dto.request.CreateHardwareAssetRequest;
import com.company.itam.asset.dto.request.UpdateHardwareAssetRequest;
import com.company.itam.asset.dto.request.ValidateUniquenessRequest;
import com.company.itam.asset.dto.response.AssetDetailResponse;
import com.company.itam.asset.dto.response.UniquenessValidationResponse;
import com.company.itam.asset.entity.AssetEntity;
import com.company.itam.asset.entity.AssetHardwareDetailsEntity;
import com.company.itam.asset.mapper.AssetMapper;
import com.company.itam.asset.repository.AssetHardwareDetailsRepository;
import com.company.itam.asset.repository.AssetRepository;
import com.company.itam.catalog.entity.*;
import com.company.itam.catalog.repository.*;
import com.company.itam.common.enums.AssetCategory;
import com.company.itam.common.enums.AssetCondition;
import com.company.itam.common.enums.AssetStatus;
import com.company.itam.common.exception.AppException;
import com.company.itam.common.exception.DuplicateResourceException;
import com.company.itam.common.exception.ResourceNotFoundException;
import com.company.itam.department.entity.DepartmentEntity;
import com.company.itam.department.repository.DepartmentRepository;
import com.company.itam.location.entity.LocationEntity;
import com.company.itam.location.repository.LocationRepository;
import com.company.itam.supplier.entity.SupplierEntity;
import com.company.itam.supplier.repository.SupplierRepository;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

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

    @Spy
    private AssetMapper assetMapper = new AssetMapper();

    @InjectMocks
    private AssetService assetService;

    private AssetTypeEntity sampleType;
    private AssetStatusEntity inStockStatus;
    private AssetStatusEntity inUseStatus;
    private ModelEntity sampleModel;
    private UserEntity sampleUser;

    @BeforeEach
    void setUp() {
        AssetCategoryEntity category = new AssetCategoryEntity();
        category.setCategoryId(1L);
        category.setCode(AssetCategory.DEVICE);
        category.setName("Device");

        sampleType = new AssetTypeEntity();
        sampleType.setTypeId(1L);
        sampleType.setCode("LAPTOP");
        sampleType.setName("Laptop");
        sampleType.setCategory(category);

        inStockStatus = new AssetStatusEntity();
        inStockStatus.setStatusId(1L);
        inStockStatus.setCode(AssetStatus.IN_STOCK);
        inStockStatus.setName("In Stock");

        inUseStatus = new AssetStatusEntity();
        inUseStatus.setStatusId(2L);
        inUseStatus.setCode(AssetStatus.IN_USE);
        inUseStatus.setName("In Use");

        sampleModel = new ModelEntity();
        sampleModel.setModelId(10L);
        sampleModel.setName("ThinkPad T14");
        sampleModel.setBrand("Lenovo");
        sampleModel.setDefaultCpu("Intel Core i5-1335U");
        sampleModel.setDefaultRam("16GB DDR4");
        sampleModel.setDefaultStorage("512GB NVMe SSD");
        sampleModel.setDefaultGraphicsCard("Intel Iris Xe");

        sampleUser = new UserEntity();
        sampleUser.setUserId(100L);
        sampleUser.setFullName("Nguyen Van A");
        sampleUser.setEmail("a.nguyen@company.com");

        // Mutating asset operations resolve the actor from the authenticated session.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        sampleUser.getEmail(), null,
                        List.of(new SimpleGrantedAuthority("ADMIN"))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAssetById_withoutAuthentication_doesNotReadRepository() {
        SecurityContextHolder.clearContext();

        AppException exception = assertThrows(AppException.class, () -> assetService.getAssetById(77L));

        assertEquals("UNAUTHORIZED", exception.getCode());
        verifyNoInteractions(assetRepository);
    }

    @Test
    @DisplayName("USER không đọc được chi tiết tài sản gán cho người khác")
    void getAssetById_userScope_rejectsOtherUsersAsset() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        sampleUser.getEmail(), null,
                        List.of(new SimpleGrantedAuthority("USER"))));
        when(userRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUser));

        UserEntity anotherUser = new UserEntity();
        anotherUser.setUserId(200L);
        AssetEntity otherAsset = new AssetEntity();
        otherAsset.setAssetId(77L);
        otherAsset.setAssignedTo(anotherUser);
        when(assetRepository.findByIdWithHardwareDetails(77L)).thenReturn(Optional.of(otherAsset));

        assertThrows(ResourceNotFoundException.class, () -> assetService.getAssetById(77L));
    }

    @Test
    @DisplayName("Tạo tài sản phần cứng thành công - cấu hình actual rỗng thì dùng default của model")
    void createHardwareAsset_success_withDefaultFallback() {
        CreateHardwareAssetRequest request = new CreateHardwareAssetRequest();
        request.setAssetTag("AST-001");
        request.setName("Laptop Developer 01");
        request.setTypeId(1L);
        request.setModelId(10L);
        request.setSerialNumber("SN-12345");
        request.setPurchaseCost(new BigDecimal("25000000"));
        // actual specs left null/empty

        when(assetRepository.existsByAssetTag("AST-001")).thenReturn(false);
        when(assetHardwareDetailsRepository.existsBySerialNumber("SN-12345")).thenReturn(false);
        when(assetTypeRepository.findById(1L)).thenReturn(Optional.of(sampleType));
        when(assetStatusRepository.findByCode(AssetStatus.IN_STOCK)).thenReturn(Optional.of(inStockStatus));
        when(modelRepository.findById(10L)).thenReturn(Optional.of(sampleModel));
        when(userRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUser));

        when(assetRepository.save(any(AssetEntity.class))).thenAnswer(invocation -> {
            AssetEntity entity = invocation.getArgument(0);
            entity.setAssetId(50L);
            return entity;
        });

        when(assetHardwareDetailsRepository.save(any(AssetHardwareDetailsEntity.class))).thenAnswer(invocation -> {
            AssetHardwareDetailsEntity entity = invocation.getArgument(0);
            return entity;
        });

        AssetDetailResponse response = assetService.createHardwareAsset(request);

        assertNotNull(response);
        assertEquals(50L, response.getAssetId());
        assertEquals("AST-001", response.getAssetTag());
        assertEquals("Laptop Developer 01", response.getName());
        assertEquals("SN-12345", response.getSerialNumber());

        // Effective specs must fall back to model defaults
        assertEquals("Intel Core i5-1335U", response.getEffectiveCpu());
        assertEquals("16GB DDR4", response.getEffectiveRam());
        assertEquals("512GB NVMe SSD", response.getEffectiveStorage());
        assertEquals("Intel Iris Xe", response.getEffectiveGraphicsCard());

        assertNull(response.getHardwareConfig().getActualCpu());
        assertEquals("Intel Core i5-1335U", response.getHardwareConfig().getDefaultCpu());
        assertEquals("Intel Core i5-1335U", response.getHardwareConfig().getEffectiveCpu());

        verify(assetRepository, times(1)).save(any(AssetEntity.class));
        verify(assetHardwareDetailsRepository, times(1)).save(any(AssetHardwareDetailsEntity.class));
    }

    @Test
    @DisplayName("Tạo tài sản phần cứng thành công - cấu hình actual ghi đè default của model")
    void createHardwareAsset_success_withActualOverride() {
        CreateHardwareAssetRequest request = new CreateHardwareAssetRequest();
        request.setAssetTag("AST-002");
        request.setName("Laptop Developer 02 Upgrade");
        request.setTypeId(1L);
        request.setModelId(10L);
        request.setSerialNumber("SN-99999");
        request.setActualRam("32GB DDR4");
        request.setActualStorage("1TB NVMe SSD");

        when(assetRepository.existsByAssetTag("AST-002")).thenReturn(false);
        when(assetHardwareDetailsRepository.existsBySerialNumber("SN-99999")).thenReturn(false);
        when(assetTypeRepository.findById(1L)).thenReturn(Optional.of(sampleType));
        when(assetStatusRepository.findByCode(AssetStatus.IN_STOCK)).thenReturn(Optional.of(inStockStatus));
        when(modelRepository.findById(10L)).thenReturn(Optional.of(sampleModel));
        when(userRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUser));

        when(assetRepository.save(any(AssetEntity.class))).thenAnswer(invocation -> {
            AssetEntity entity = invocation.getArgument(0);
            entity.setAssetId(51L);
            return entity;
        });
        when(assetHardwareDetailsRepository.save(any(AssetHardwareDetailsEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssetDetailResponse response = assetService.createHardwareAsset(request);

        assertNotNull(response);
        // Overridden specs
        assertEquals("32GB DDR4", response.getEffectiveRam());
        assertEquals("1TB NVMe SSD", response.getEffectiveStorage());
        // Fallback specs
        assertEquals("Intel Core i5-1335U", response.getEffectiveCpu());
        assertEquals("Intel Iris Xe", response.getEffectiveGraphicsCard());
    }

    @Test
    @DisplayName("Chặn tạo tài sản khi Asset Tag đã tồn tại")
    void createHardwareAsset_duplicateAssetTag_throwsDuplicateResourceException() {
        CreateHardwareAssetRequest request = new CreateHardwareAssetRequest();
        request.setAssetTag("AST-EXISTS");
        request.setName("Laptop Duplicated");
        request.setTypeId(1L);

        when(assetRepository.existsByAssetTag("AST-EXISTS")).thenReturn(true);

        DuplicateResourceException exception = assertThrows(DuplicateResourceException.class, () ->
                assetService.createHardwareAsset(request)
        );

        assertEquals("DUPLICATE_ASSET_TAG", exception.getCode());
        verify(assetRepository, never()).save(any());
    }

    @Test
    @DisplayName("Chặn tạo tài sản khi Serial Number đã tồn tại")
    void createHardwareAsset_duplicateSerialNumber_throwsDuplicateResourceException() {
        CreateHardwareAssetRequest request = new CreateHardwareAssetRequest();
        request.setAssetTag("AST-NEW");
        request.setName("Laptop Test");
        request.setTypeId(1L);
        request.setSerialNumber("SN-EXISTING");

        when(assetRepository.existsByAssetTag("AST-NEW")).thenReturn(false);
        when(assetHardwareDetailsRepository.existsBySerialNumber("SN-EXISTING")).thenReturn(true);

        DuplicateResourceException exception = assertThrows(DuplicateResourceException.class, () ->
                assetService.createHardwareAsset(request)
        );

        assertEquals("DUPLICATE_SERIAL_NUMBER", exception.getCode());
        verify(assetRepository, never()).save(any());
    }

    @Test
    @DisplayName("Chặn tạo tài sản khi giá mua âm")
    void createHardwareAsset_negativeCost_throwsException() {
        CreateHardwareAssetRequest request = new CreateHardwareAssetRequest();
        request.setAssetTag("AST-NEG");
        request.setName("Laptop Neg Cost");
        request.setTypeId(1L);
        request.setPurchaseCost(new BigDecimal("-1000"));

        when(assetRepository.existsByAssetTag("AST-NEG")).thenReturn(false);

        AppException exception = assertThrows(AppException.class, () ->
                assetService.createHardwareAsset(request)
        );

        assertEquals("VALIDATION_ERROR", exception.getCode());
        verify(assetRepository, never()).save(any());
    }

    @Test
    @DisplayName("Chặn gán người dùng khi tài sản ở trạng thái IN_STOCK")
    void createHardwareAsset_inStockWithAssignedUser_throwsException() {
        CreateHardwareAssetRequest request = new CreateHardwareAssetRequest();
        request.setAssetTag("AST-VALID");
        request.setName("Laptop In Stock");
        request.setTypeId(1L);
        request.setAssignedToUserId(100L); // invalid for IN_STOCK

        when(assetRepository.existsByAssetTag("AST-VALID")).thenReturn(false);
        when(assetTypeRepository.findById(1L)).thenReturn(Optional.of(sampleType));
        when(assetStatusRepository.findByCode(AssetStatus.IN_STOCK)).thenReturn(Optional.of(inStockStatus));

        AppException exception = assertThrows(AppException.class, () ->
                assetService.createHardwareAsset(request)
        );

        assertEquals("INVALID_ASSIGNMENT", exception.getCode());
    }

    @Test
    @DisplayName("Chặn tạo tài sản IN_USE mà không có người dùng")
    void createHardwareAsset_inUseWithoutUser_throwsException() {
        CreateHardwareAssetRequest request = new CreateHardwareAssetRequest();
        request.setAssetTag("AST-INUSE");
        request.setName("Laptop In Use");
        request.setTypeId(1L);
        request.setStatusId(2L);
        request.setAssignedToUserId(null); // missing for IN_USE

        when(assetRepository.existsByAssetTag("AST-INUSE")).thenReturn(false);
        when(assetTypeRepository.findById(1L)).thenReturn(Optional.of(sampleType));
        when(assetStatusRepository.findById(2L)).thenReturn(Optional.of(inUseStatus));

        AppException exception = assertThrows(AppException.class, () ->
                assetService.createHardwareAsset(request)
        );

        assertEquals("ASSIGNMENT_REQUIRED", exception.getCode());
    }

    @Test
    @DisplayName("Cập nhật thông tin tài sản phần cứng thành công")
    void updateHardwareAsset_success() {
        AssetEntity existingAsset = new AssetEntity();
        existingAsset.setAssetId(10L);
        existingAsset.setAssetTag("AST-10");
        existingAsset.setName("Old Laptop");
        existingAsset.setType(sampleType);
        existingAsset.setStatus(inStockStatus);

        AssetHardwareDetailsEntity existingHw = new AssetHardwareDetailsEntity();
        existingHw.setAssetId(10L);
        existingHw.setSerialNumber("SN-10");
        existingHw.setModel(sampleModel);
        existingAsset.setHardwareDetails(existingHw);

        when(assetRepository.findByIdWithHardwareDetails(10L)).thenReturn(Optional.of(existingAsset));
        when(assetRepository.findByAssetTag("AST-10")).thenReturn(Optional.empty());
        when(assetTypeRepository.findById(1L)).thenReturn(Optional.of(sampleType));
        when(modelRepository.findById(10L)).thenReturn(Optional.of(sampleModel));
        when(userRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUser));
        when(assetHardwareDetailsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(assetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateHardwareAssetRequest updateReq = new UpdateHardwareAssetRequest();
        updateReq.setAssetTag("AST-10");
        updateReq.setName("Updated Laptop");
        updateReq.setTypeId(1L);
        updateReq.setModelId(10L);
        updateReq.setSerialNumber("SN-10-NEW");

        AssetDetailResponse updated = assetService.updateHardwareAsset(10L, updateReq);

        assertNotNull(updated);
        assertEquals("AST-10", updated.getAssetTag());
        assertEquals("Updated Laptop", updated.getName());
        assertEquals("SN-10-NEW", updated.getSerialNumber());
    }

    @Test
    @DisplayName("Kiểm tra tính khả dụng của Asset Tag và Serial Number")
    void validateUniqueness_test() {
        when(assetRepository.findByAssetTag("TAG-FREE")).thenReturn(Optional.empty());
        when(assetHardwareDetailsRepository.findBySerialNumber("SN-FREE")).thenReturn(Optional.empty());

        ValidateUniquenessRequest req = new ValidateUniquenessRequest("TAG-FREE", "SN-FREE", null);
        UniquenessValidationResponse res = assetService.validateUniqueness(req);

        assertTrue(res.isAssetTagAvailable());
        assertTrue(res.isSerialNumberAvailable());

        // Test with existing tag
        AssetEntity otherAsset = new AssetEntity();
        otherAsset.setAssetId(99L);
        when(assetRepository.findByAssetTag("TAG-BUSY")).thenReturn(Optional.of(otherAsset));

        ValidateUniquenessRequest reqBusy = new ValidateUniquenessRequest("TAG-BUSY", null, 1L);
        UniquenessValidationResponse resBusy = assetService.validateUniqueness(reqBusy);

        assertFalse(resBusy.isAssetTagAvailable());
    }

    @Test
    @DisplayName("Ném ResourceNotFoundException khi không tìm thấy tài sản")
    void getAssetById_notFound_throwsException() {
        when(assetRepository.findByIdWithHardwareDetails(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                assetService.getAssetById(999L)
        );
    }
}
