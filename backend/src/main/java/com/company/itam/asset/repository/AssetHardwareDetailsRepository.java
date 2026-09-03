package com.company.itam.asset.repository;

import com.company.itam.asset.entity.AssetHardwareDetailsEntity;
import com.company.itam.common.enums.AssetCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssetHardwareDetailsRepository extends JpaRepository<AssetHardwareDetailsEntity, Long> {
    Optional<AssetHardwareDetailsEntity> findBySerialNumber(String serialNumber);
    boolean existsBySerialNumber(String serialNumber);
    boolean existsByModelModelId(Long modelId);
    boolean existsByConditionCode(AssetCondition condition);
}
