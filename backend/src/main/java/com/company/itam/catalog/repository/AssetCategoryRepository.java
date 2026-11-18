package com.company.itam.catalog.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.itam.catalog.entity.AssetCategoryEntity;
import com.company.itam.common.enums.AssetCategory;

@Repository
public interface AssetCategoryRepository extends JpaRepository<AssetCategoryEntity, Long> {
    Optional<AssetCategoryEntity> findByCode(AssetCategory code);

    boolean existsByCode(AssetCategory code);
}
