package com.company.itam.catalog.repository;

import com.company.itam.catalog.entity.ModelEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModelRepository extends JpaRepository<ModelEntity, Long> {
    Page<ModelEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Page<ModelEntity> findByBrandContainingIgnoreCase(String brand, Pageable pageable);
    Page<ModelEntity> findByIsActive(Boolean isActive, Pageable pageable);

    @Query("SELECT m FROM ModelEntity m LEFT JOIN FETCH m.type WHERE m.modelId = :id")
    Optional<ModelEntity> findByIdWithType(@Param("id") Long id);
}
