package com.company.itam.repository;

import com.company.itam.asset.entity.AssetEntity;
import com.company.itam.asset.entity.AssetHardwareDetailsEntity;
import com.company.itam.asset.repository.AssetHardwareDetailsRepository;
import com.company.itam.asset.repository.AssetRepository;
import com.company.itam.catalog.entity.AssetCategoryEntity;
import com.company.itam.catalog.entity.AssetStatusEntity;
import com.company.itam.catalog.repository.AssetCategoryRepository;
import com.company.itam.catalog.repository.AssetStatusRepository;
import com.company.itam.common.enums.AssetCategory;
import com.company.itam.common.enums.AssetCondition;
import com.company.itam.common.enums.AssetStatus;
import com.company.itam.department.entity.DepartmentEntity;
import com.company.itam.department.repository.DepartmentRepository;
import com.company.itam.role.entity.RoleEntity;
import com.company.itam.role.repository.RoleRepository;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:tc:postgresql:16:///itam_test",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.enabled=true",
    "spring.flyway.baseline-on-migrate=true"
})
class AssetRepositoryTest {

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetHardwareDetailsRepository assetHardwareDetailsRepository;

    @Autowired
    private AssetCategoryRepository assetCategoryRepository;

    @Autowired
    private AssetStatusRepository assetStatusRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    private AssetCategoryEntity category;
    private AssetStatusEntity status;
    private RoleEntity role;
    private DepartmentEntity department;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        category = new AssetCategoryEntity();
        category.setCode(AssetCategory.HARDWARE);
        category.setName("Hardware");
        category.setIsActive(true);
        category = assetCategoryRepository.save(category);

        status = new AssetStatusEntity();
        status.setCode(AssetStatus.IN_STOCK);
        status.setName("In Stock");
        status.setIsActive(true);
        status = assetStatusRepository.save(status);

        role = new RoleEntity();
        role.setCode(com.company.itam.common.enums.Role.IT);
        role.setName("IT Staff");
        role.setIsActive(true);
        role = roleRepository.save(role);

        department = new DepartmentEntity();
        department.setCode("IT");
        department.setName("Information Technology");
        department.setIsActive(true);
        department = departmentRepository.save(department);

        user = new UserEntity();
        user.setEmail("test@company.com");
        user.setFullName("Test User");
        user.setRole(role);
        user.setDepartment(department);
        user.setIsActive(true);
        user = userRepository.save(user);
    }

    @Test
    void saveAsset_withHardwareDetails_shouldPersistSuccessfully() {
        AssetEntity asset = new AssetEntity();
        asset.setAssetTag("AST-001");
        asset.setName("Test Laptop");
        asset.setCategoryId(AssetCategory.HARDWARE);
        asset.setStatusId(AssetStatus.IN_STOCK);
        asset.setPurchaseCost(BigDecimal.valueOf(15000000));
        asset.setPurchaseDate(LocalDate.now());
        asset.setCreatedBy(user);
        asset = assetRepository.save(asset);

        AssetHardwareDetailsEntity hwDetails = new AssetHardwareDetailsEntity();
        hwDetails.setAssetId(asset.getAssetId());
        hwDetails.setSerialNumber("SN-12345");
        hwDetails.setConditionId(AssetCondition.NEW);
        hwDetails.setActualCpu("Intel i7");
        hwDetails.setActualRam("16GB");
        hwDetails.setActualStorage("512GB SSD");
        assetHardwareDetailsRepository.save(hwDetails);

        Optional<AssetEntity> found = assetRepository.findById(asset.getAssetId());
        assertThat(found).isPresent();
        assertThat(found.get().getAssetTag()).isEqualTo("AST-001");
        assertThat(found.get().getCategoryId()).isEqualTo(AssetCategory.HARDWARE);
    }

    @Test
    void findByAssetTag_shouldReturnUniqueAsset() {
        AssetEntity asset = new AssetEntity();
        asset.setAssetTag("AST-UNIQUE-001");
        asset.setName("Unique Asset");
        asset.setCategoryId(AssetCategory.HARDWARE);
        asset.setStatusId(AssetStatus.IN_STOCK);
        asset.setCreatedBy(user);
        assetRepository.save(asset);

        Optional<AssetEntity> found = assetRepository.findByAssetTag("AST-UNIQUE-001");
        assertThat(found).isPresent();
        assertThat(found.get().getAssetTag()).isEqualTo("AST-UNIQUE-001");
    }

    @Test
    void existsByAssetTag_shouldReturnTrue_whenAssetExists() {
        AssetEntity asset = new AssetEntity();
        asset.setAssetTag("AST-EXISTS-001");
        asset.setName("Existing Asset");
        asset.setCategoryId(AssetCategory.HARDWARE);
        asset.setStatusId(AssetStatus.IN_STOCK);
        asset.setCreatedBy(user);
        assetRepository.save(asset);

        assertThat(assetRepository.existsByAssetTag("AST-EXISTS-001")).isTrue();
        assertThat(assetRepository.existsByAssetTag("AST-NOT-EXISTS")).isFalse();
    }

    @Test
    void findByStatus_shouldReturnAssetsWithStatus() {
        AssetEntity asset1 = new AssetEntity();
        asset1.setAssetTag("AST-STATUS-001");
        asset1.setName("Stock Asset");
        asset1.setCategoryId(AssetCategory.HARDWARE);
        asset1.setStatusId(AssetStatus.IN_STOCK);
        asset1.setCreatedBy(user);
        assetRepository.save(asset1);

        AssetEntity asset2 = new AssetEntity();
        asset2.setAssetTag("AST-STATUS-002");
        asset2.setName("In Use Asset");
        asset2.setCategoryId(AssetCategory.HARDWARE);
        asset2.setStatusId(AssetStatus.IN_USE);
        asset2.setCreatedBy(user);
        assetRepository.save(asset2);

        var inStockAssets = assetRepository.findByStatusId(AssetStatus.IN_STOCK,
            org.springframework.data.domain.Pageable.unpaged());
        assertThat(inStockAssets.getContent()).hasSize(1);
        assertThat(inStockAssets.getContent().get(0).getAssetTag()).isEqualTo("AST-STATUS-001");
    }
}
