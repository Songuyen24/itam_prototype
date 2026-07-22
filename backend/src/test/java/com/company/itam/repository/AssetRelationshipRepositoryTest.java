package com.company.itam.repository;

import com.company.itam.asset.entity.AssetEntity;
import com.company.itam.asset.repository.AssetRepository;
import com.company.itam.catalog.entity.AssetCategoryEntity;
import com.company.itam.catalog.entity.AssetStatusEntity;
import com.company.itam.catalog.repository.AssetCategoryRepository;
import com.company.itam.catalog.repository.AssetStatusRepository;
import com.company.itam.common.enums.AssetCategory;
import com.company.itam.common.enums.AssetStatus;
import com.company.itam.department.entity.DepartmentEntity;
import com.company.itam.department.repository.DepartmentRepository;
import com.company.itam.role.entity.RoleEntity;
import com.company.itam.role.repository.RoleRepository;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.user.repository.UserRepository;
import com.company.itam.relationship.entity.AssetRelationshipEntity;
import com.company.itam.relationship.repository.AssetRelationshipRepository;
import com.company.itam.common.enums.RelationshipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

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
class AssetRelationshipRepositoryTest {

    @Autowired
    private AssetRelationshipRepository assetRelationshipRepository;

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

    private AssetEntity parentAsset;
    private AssetEntity childAsset;
    private UserEntity creator;

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

        creator = new UserEntity();
        creator.setEmail("creator@company.com");
        creator.setFullName("Creator User");
        creator.setRole(role);
        creator.setDepartment(department);
        creator.setIsActive(true);
        creator = userRepository.save(creator);

        parentAsset = new AssetEntity();
        parentAsset.setAssetTag("AST-PARENT-001");
        parentAsset.setName("Parent Laptop");
        parentAsset.setCategoryId(AssetCategory.HARDWARE);
        parentAsset.setStatusId(AssetStatus.IN_STOCK);
        parentAsset.setCreatedBy(creator);
        parentAsset = assetRepository.save(parentAsset);

        childAsset = new AssetEntity();
        childAsset.setAssetTag("AST-CHILD-001");
        childAsset.setName("Child RAM Module");
        childAsset.setCategoryId(AssetCategory.HARDWARE);
        childAsset.setStatusId(AssetStatus.IN_STOCK);
        childAsset.setCreatedBy(creator);
        childAsset = assetRepository.save(childAsset);
    }

    @Test
    void saveRelationship_shouldPersistSuccessfully() {
        AssetRelationshipEntity relationship = new AssetRelationshipEntity();
        relationship.setParentAsset(parentAsset);
        relationship.setChildAsset(childAsset);
        relationship.setRelationshipType(RelationshipType.COMPONENT_OF);
        relationship.setCreatedBy(creator);
        assetRelationshipRepository.save(relationship);

        List<AssetRelationshipEntity> found = assetRelationshipRepository.findByParentAssetAssetId(parentAsset.getAssetId());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getRelationshipType()).isEqualTo(RelationshipType.COMPONENT_OF);
    }

    @Test
    void findByParentAndType_shouldReturnRelationships() {
        AssetRelationshipEntity relationship = new AssetRelationshipEntity();
        relationship.setParentAsset(parentAsset);
        relationship.setChildAsset(childAsset);
        relationship.setRelationshipType(RelationshipType.COMPONENT_OF);
        relationship.setCreatedBy(creator);
        assetRelationshipRepository.save(relationship);

        List<AssetRelationshipEntity> found = assetRelationshipRepository.findByParentAndType(
            parentAsset.getAssetId(), RelationshipType.COMPONENT_OF);
        assertThat(found).hasSize(1);
    }

    @Test
    void findByChildAsset_shouldReturnParentRelationships() {
        AssetRelationshipEntity relationship = new AssetRelationshipEntity();
        relationship.setParentAsset(parentAsset);
        relationship.setChildAsset(childAsset);
        relationship.setRelationshipType(RelationshipType.INSTALLED_ON);
        relationship.setCreatedBy(creator);
        assetRelationshipRepository.save(relationship);

        List<AssetRelationshipEntity> found = assetRelationshipRepository.findByChildAssetAssetId(childAsset.getAssetId());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getRelationshipType()).isEqualTo(RelationshipType.INSTALLED_ON);
    }
}
