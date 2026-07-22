package com.company.itam.location.repository;

import com.company.itam.location.entity.LocationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<LocationEntity, Long> {
    Optional<LocationEntity> findByCode(String code);
    boolean existsByCode(String code);
    Page<LocationEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Page<LocationEntity> findByIsActive(Boolean isActive, Pageable pageable);
}
