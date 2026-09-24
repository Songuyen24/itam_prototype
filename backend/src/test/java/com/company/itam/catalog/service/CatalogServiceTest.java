package com.company.itam.catalog.service;

import com.company.itam.asset.repository.AssetHardwareDetailsRepository;
import com.company.itam.asset.repository.AssetLicenseDetailsRepository;
import com.company.itam.asset.repository.AssetRepository;
import com.company.itam.catalog.dto.AssetTypeRequest;
import com.company.itam.catalog.dto.AssetTypeResponse;
import com.company.itam.catalog.dto.ModelRequest;
import com.company.itam.catalog.dto.ModelResponse;
import com.company.itam.catalog.entity.AssetCategoryEntity;
import com.company.itam.catalog.entity.AssetTypeEntity;
import com.company.itam.catalog.entity.ModelEntity;
import com.company.itam.catalog.repository.*;
import com.company.itam.common.enums.AssetCategory;
import com.company.itam.common.exception.CatalogInUseException;
import com.company.itam.common.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private AssetCategoryRepository categoryRepository;

    @Mock
    private AssetTypeRepository typeRepository;

    @Mock
    private AssetStatusRepository statusRepository;

    @Mock
    private AssetConditionRepository conditionRepository;

    @Mock
    private ModelRepository modelRepository;

    @Mock
    private SoftwareCatalogRepository softwareCatalogRepository;

    @Mock
    private LicenseAssignmentTypeRepository assignmentTypeRepository;

    @Mock
    private LicenseTermTypeRepository termTypeRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetHardwareDetailsRepository hardwareDetailsRepository;

    @Mock
    private AssetLicenseDetailsRepository licenseDetailsRepository;

    @InjectMocks
    private CatalogService catalogService;

    private AssetCategoryEntity deviceCategory;
    private AssetTypeEntity laptopType;

    @BeforeEach
    void setUp() {
        deviceCategory = new AssetCategoryEntity();
        deviceCategory.setCategoryId(1L);
        deviceCategory.setCode(AssetCategory.DEVICE);
        deviceCategory.setName("Device");

        laptopType = new AssetTypeEntity();
        laptopType.setTypeId(10L);
        laptopType.setCode("LAPTOP");
        laptopType.setName("Laptop");
        laptopType.setCategory(deviceCategory);
        laptopType.setIsActive(true);
    }

    @Test
    void createAssetType_success() {
        AssetTypeRequest request = new AssetTypeRequest("TABLET", 1L, "Tablet", true);
        when(typeRepository.existsByCodeIgnoreCase("TABLET")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(deviceCategory));
        when(typeRepository.save(any(AssetTypeEntity.class))).thenAnswer(inv -> {
            AssetTypeEntity e = inv.getArgument(0);
            e.setTypeId(11L);
            return e;
        });

        AssetTypeResponse response = catalogService.createAssetType(request);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("TABLET");
        assertThat(response.getName()).isEqualTo("Tablet");
        assertThat(response.getCategoryId()).isEqualTo(1L);
    }

    @Test
    void createAssetType_duplicateCode_throwsException() {
        AssetTypeRequest request = new AssetTypeRequest("LAPTOP", 1L, "Laptop", true);
        when(typeRepository.existsByCodeIgnoreCase("LAPTOP")).thenReturn(true);

        assertThatThrownBy(() -> catalogService.createAssetType(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Mã loại tài sản đã tồn tại");
    }

    @Test
    void deleteAssetType_inUseByAsset_throwsCatalogInUseException() {
        when(typeRepository.findById(10L)).thenReturn(Optional.of(laptopType));
        when(assetRepository.existsByTypeTypeId(10L)).thenReturn(true);

        assertThatThrownBy(() -> catalogService.deleteAssetType(10L))
                .isInstanceOf(CatalogInUseException.class)
                .hasMessageContaining("đang được sử dụng");

        verify(typeRepository, never()).delete(any());
    }

    @Test
    void createModel_success() {
        ModelRequest request = new ModelRequest();
        request.setName("ThinkPad T14");
        request.setBrand("Lenovo");
        request.setTypeId(10L);
        request.setDefaultCpu("Intel Core i7");
        request.setDefaultRam("16GB");

        when(modelRepository.existsByTypeTypeIdAndBrandIgnoreCaseAndNameIgnoreCase(10L, "Lenovo", "ThinkPad T14"))
                .thenReturn(false);
        when(typeRepository.findById(10L)).thenReturn(Optional.of(laptopType));
        when(modelRepository.save(any(ModelEntity.class))).thenAnswer(inv -> {
            ModelEntity m = inv.getArgument(0);
            m.setModelId(100L);
            return m;
        });

        ModelResponse response = catalogService.createModel(request);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("ThinkPad T14");
        assertThat(response.getBrand()).isEqualTo("Lenovo");
        assertThat(response.getTypeId()).isEqualTo(10L);
    }

    @Test
    void deleteModel_inUseByAsset_throwsCatalogInUseException() {
        ModelEntity model = new ModelEntity();
        model.setModelId(100L);
        when(modelRepository.findById(100L)).thenReturn(Optional.of(model));
        when(hardwareDetailsRepository.existsByModelModelId(100L)).thenReturn(true);

        assertThatThrownBy(() -> catalogService.deleteModel(100L))
                .isInstanceOf(CatalogInUseException.class)
                .hasMessageContaining("Model đang được sử dụng bởi tài sản");

        verify(modelRepository, never()).delete(any());
    }

    @Test
    void deleteAssetCategory_inUseByType_throwsCatalogInUseException() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(deviceCategory));
        when(typeRepository.existsByCategoryCategoryId(1L)).thenReturn(true);

        assertThatThrownBy(() -> catalogService.deleteAssetCategory(1L))
                .isInstanceOf(CatalogInUseException.class)
                .hasMessageContaining("Nhóm tài sản đang chứa loại tài sản");

        verify(categoryRepository, never()).delete(any());
    }
}
