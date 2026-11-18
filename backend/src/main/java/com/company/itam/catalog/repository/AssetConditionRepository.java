package com.company.itam.catalog.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.itam.catalog.entity.AssetConditionEntity;
import com.company.itam.common.enums.AssetCondition;

@Repository
public interface AssetConditionRepository extends JpaRepository<AssetConditionEntity, Long> {
    Optional<AssetConditionEntity> findByCode(AssetCondition code);

    boolean existsByCode(AssetCondition code);
}
