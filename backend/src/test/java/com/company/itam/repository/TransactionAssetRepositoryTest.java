package com.company.itam.repository;

import com.company.itam.transaction.entity.TransactionEntity;
import com.company.itam.transaction.entity.TransactionAssetEntity;
import com.company.itam.transaction.repository.TransactionAssetRepository;
import com.company.itam.transaction.repository.TransactionRepository;
import com.company.itam.asset.entity.AssetEntity;
import com.company.itam.asset.repository.AssetRepository;
import com.company.itam.catalog.entity.AssetCategoryEntity;
import com.company.itam.catalog.entity.AssetStatusEntity;
import com.company.itam.catalog.repository.AssetCategoryRepository;
import com.company.itam.catalog.repository.AssetStatusRepository;
import com.company.itam.common.enums.AssetCategory;
import com.company.itam.common.enums.AssetStatus;
import com.company.itam.common.enums.TransactionStatus;
import com.company.itam.common.enums.TransactionType;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
class TransactionAssetRepositoryTest {

    @Autowired
    private TransactionAssetRepository transactionAssetRepository;

    @Autowired
    private TransactionRepository transactionRepository;

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

    private TransactionEntity transaction;
    private AssetEntity asset;
    private UserEntity requester;

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

        requester = new UserEntity();
        requester.setEmail("requester@company.com");
        requester.setFullName("Requester User");
        requester.setRole(role);
        requester.setDepartment(department);
        requester.setIsActive(true);
        requester = userRepository.save(requester);

        asset = new AssetEntity();
        asset.setAssetTag("AST-TRANS-001");
        asset.setName("Transaction Test Asset");
        asset.setCategoryId(AssetCategory.HARDWARE);
        asset.setStatusId(AssetStatus.IN_STOCK);
        asset.setCreatedBy(requester);
        asset = assetRepository.save(asset);

        transaction = new TransactionEntity();
        transaction.setTransactionCode("TXN-" + UUID.randomUUID().toString().substring(0, 8));
        transaction.setType(TransactionType.IMPORT);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setRequester(requester);
        transaction = transactionRepository.save(transaction);
    }

    @Test
    void saveTransactionAsset_withAssetId_shouldPersistSuccessfully() {
        TransactionAssetEntity txAsset = new TransactionAssetEntity();
        txAsset.setTransaction(transaction);
        txAsset.setAsset(asset);
        txAsset.setNotes("Test notes");
        transactionAssetRepository.save(txAsset);

        List<TransactionAssetEntity> found = transactionAssetRepository.findByTransactionTransactionId(transaction.getTransactionId());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getAsset()).isNotNull();
        assertThat(found.get(0).getAsset().getAssetTag()).isEqualTo("AST-TRANS-001");
    }

    @Test
    void saveTransactionAsset_withDraftData_shouldPersistSuccessfully() {
        TransactionAssetEntity txAsset = new TransactionAssetEntity();
        txAsset.setTransaction(transaction);

        Map<String, Object> draftData = new HashMap<>();
        draftData.put("name", "Draft Asset");
        draftData.put("category", "HARDWARE");
        txAsset.setDraftData(draftData);
        txAsset.setNotes("Draft asset for import");
        transactionAssetRepository.save(txAsset);

        List<TransactionAssetEntity> found = transactionAssetRepository.findByTransactionTransactionId(transaction.getTransactionId());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getDraftData()).isNotNull();
        assertThat(found.get(0).getDraftData().get("name")).isEqualTo("Draft Asset");
    }

    @Test
    void findByTransaction_shouldReturnAllAssetsInTransaction() {
        TransactionAssetEntity txAsset1 = new TransactionAssetEntity();
        txAsset1.setTransaction(transaction);
        txAsset1.setAsset(asset);
        transactionAssetRepository.save(txAsset1);

        AssetEntity asset2 = new AssetEntity();
        asset2.setAssetTag("AST-TRANS-002");
        asset2.setName("Second Test Asset");
        asset2.setCategoryId(AssetCategory.HARDWARE);
        asset2.setStatusId(AssetStatus.IN_STOCK);
        asset2.setCreatedBy(requester);
        asset2 = assetRepository.save(asset2);

        TransactionAssetEntity txAsset2 = new TransactionAssetEntity();
        txAsset2.setTransaction(transaction);
        txAsset2.setAsset(asset2);
        transactionAssetRepository.save(txAsset2);

        List<TransactionAssetEntity> found = transactionAssetRepository.findByTransactionTransactionId(transaction.getTransactionId());
        assertThat(found).hasSize(2);
    }
}
