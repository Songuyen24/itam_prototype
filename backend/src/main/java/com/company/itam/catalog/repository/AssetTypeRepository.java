package com.company.itam.catalog.repository;

import com.company.itam.catalog.entity.AssetTypeEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssetTypeRepository extends JpaRepository<AssetTypeEntity, Long> {
    Optional<AssetTypeEntity> findByCode(String code);
    boolean existsByCode(String code);
    Page<AssetTypeEntity> findByTypeNameContainingIgnoreCase(String name, Pageable pageable);
    Page<AssetTypeEntity> findByIsActive(Boolean isActive, Pageable pageable);
}
