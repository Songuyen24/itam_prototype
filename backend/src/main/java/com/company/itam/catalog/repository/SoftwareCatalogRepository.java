package com.company.itam.catalog.repository;

import com.company.itam.catalog.entity.SoftwareCatalogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SoftwareCatalogRepository extends JpaRepository<SoftwareCatalogEntity, Long> {
    Page<SoftwareCatalogEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Page<SoftwareCatalogEntity> findByManufacturerContainingIgnoreCase(String manufacturer, Pageable pageable);
    Page<SoftwareCatalogEntity> findByIsActive(Boolean isActive, Pageable pageable);
}
