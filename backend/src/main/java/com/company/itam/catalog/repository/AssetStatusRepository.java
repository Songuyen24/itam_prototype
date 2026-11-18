package com.company.itam.catalog.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.itam.catalog.entity.AssetStatusEntity;
import com.company.itam.common.enums.AssetStatus;

@Repository
public interface AssetStatusRepository extends JpaRepository<AssetStatusEntity, Long> {
    Optional<AssetStatusEntity> findByCode(AssetStatus code);

    boolean existsByCode(AssetStatus code);
}
