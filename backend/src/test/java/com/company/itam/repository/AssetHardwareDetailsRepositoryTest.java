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
class AssetHardwareDetailsRepositoryTest {

    @Autowired
    private AssetHardwareDetailsRepository assetHardwareDetailsRepository;

    @Autowired
    private AssetRepository assetRepository;

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

    private AssetEntity asset;

    @BeforeEach
    void setUp() {
        AssetCategoryEntity category = new AssetCategoryEntity();
        category.setCode(AssetCategory.HARDWARE);
        category.setName("Hardware");
        category.setIsActive(true);
        category = assetCategoryRepository.save(category);

        AssetStatusEntity status = new AssetStatusEntity();
        status.setCode(AssetStatus.IN_STOCK);
        status.setName("In Stock");
        status.setIsActive(true);
        status = assetStatusRepository.save(status);

        RoleEntity role = new RoleEntity();
        role.setCode(com.company.itam.common.enums.Role.IT);
        role.setName("IT Staff");
        role.setIsActive(true);
        role = roleRepository.save(role);

        DepartmentEntity department = new DepartmentEntity();
        department.setCode("IT");
        department.setName("Information Technology");
        department.setIsActive(true);
        department = departmentRepository.save(department);

        UserEntity user = new UserEntity();
        user.setEmail("test@company.com");
        user.setFullName("Test User");
        user.setRole(role);
        user.setDepartment(department);
        user.setIsActive(true);
        user = userRepository.save(user);

        asset = new AssetEntity();
        asset.setAssetTag("AST-SN-TEST");
        asset.setName("Test Asset");
        asset.setCategoryId(AssetCategory.HARDWARE);
        asset.setStatusId(AssetStatus.IN_STOCK);
        asset.setCreatedBy(user);
        asset = assetRepository.save(asset);
    }

    @Test
    void saveHardwareDetails_shouldPersistSuccessfully() {
        AssetHardwareDetailsEntity hwDetails = new AssetHardwareDetailsEntity();
        hwDetails.setAssetId(asset.getAssetId());
        hwDetails.setSerialNumber("SN-UNIQUE-001");
        hwDetails.setConditionId(AssetCondition.NEW);
        hwDetails.setActualCpu("Intel i9");
        hwDetails.setActualRam("32GB");
        hwDetails.setActualStorage("1TB NVMe");
        hwDetails.setActualGraphicsCard("RTX 4090");
        assetHardwareDetailsRepository.save(hwDetails);

        Optional<AssetHardwareDetailsEntity> found = assetHardwareDetailsRepository.findById(asset.getAssetId());
        assertThat(found).isPresent();
        assertThat(found.get().getSerialNumber()).isEqualTo("SN-UNIQUE-001");
        assertThat(found.get().getConditionId()).isEqualTo(AssetCondition.NEW);
    }

    @Test
    void findBySerialNumber_shouldReturnUniqueHardwareDetails() {
        AssetHardwareDetailsEntity hwDetails = new AssetHardwareDetailsEntity();
        hwDetails.setAssetId(asset.getAssetId());
        hwDetails.setSerialNumber("SN-UNIQUE-SERIAL");
        hwDetails.setConditionId(AssetCondition.USED);
        assetHardwareDetailsRepository.save(hwDetails);

        Optional<AssetHardwareDetailsEntity> found = assetHardwareDetailsRepository.findBySerialNumber("SN-UNIQUE-SERIAL");
        assertThat(found).isPresent();
        assertThat(found.get().getSerialNumber()).isEqualTo("SN-UNIQUE-SERIAL");
    }

    @Test
    void existsBySerialNumber_shouldReturnTrue_whenSerialExists() {
        AssetHardwareDetailsEntity hwDetails = new AssetHardwareDetailsEntity();
        hwDetails.setAssetId(asset.getAssetId());
        hwDetails.setSerialNumber("SN-EXISTS-001");
        hwDetails.setConditionId(AssetCondition.NEW);
        assetHardwareDetailsRepository.save(hwDetails);

        assertThat(assetHardwareDetailsRepository.existsBySerialNumber("SN-EXISTS-001")).isTrue();
        assertThat(assetHardwareDetailsRepository.existsBySerialNumber("SN-NOT-EXISTS")).isFalse();
    }
}
