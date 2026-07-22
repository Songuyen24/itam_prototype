package com.company.itam.catalog.repository;

import com.company.itam.catalog.entity.AssetConditionEntity;
import com.company.itam.common.enums.AssetCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssetConditionRepository extends JpaRepository<AssetConditionEntity, Long> {
    Optional<AssetConditionEntity> findByCode(AssetCondition code);
    boolean existsByCode(AssetCondition code);
}
