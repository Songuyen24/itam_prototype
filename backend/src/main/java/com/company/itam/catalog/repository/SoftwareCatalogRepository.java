package com.company.itam.catalog.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.company.itam.catalog.entity.SoftwareCatalogEntity;

@Repository
public interface SoftwareCatalogRepository extends JpaRepository<SoftwareCatalogEntity, Long> {
    Page<SoftwareCatalogEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<SoftwareCatalogEntity> findByManufacturerContainingIgnoreCase(String manufacturer, Pageable pageable);

    Page<SoftwareCatalogEntity> findByNameContainingIgnoreCaseOrManufacturerContainingIgnoreCase(
            String name, String manufacturer, Pageable pageable);

    Page<SoftwareCatalogEntity> findByIsActive(Boolean isActive, Pageable pageable);

    boolean existsByManufacturerIgnoreCaseAndNameIgnoreCaseAndVersionIgnoreCase(
            String manufacturer, String name, String version);

    boolean existsByNameIgnoreCaseAndManufacturerIgnoreCase(String name, String manufacturer);

    @Query(
            "SELECT e FROM SoftwareCatalogEntity e WHERE (:active IS NULL OR e.isActive=:active) AND (lower(e.name) LIKE lower(concat('%',:search,'%')) OR lower(e.manufacturer) LIKE lower(concat('%',:search,'%')))")
    Page<SoftwareCatalogEntity> searchActive(
            @Param("search") String search, @Param("active") Boolean active, Pageable pageable);
}
