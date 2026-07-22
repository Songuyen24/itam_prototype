package com.company.itam.supplier.repository;

import com.company.itam.supplier.entity.SupplierEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<SupplierEntity, Long> {
    Optional<SupplierEntity> findByCode(String code);
    boolean existsByCode(String code);
    Page<SupplierEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Page<SupplierEntity> findByIsActive(Boolean isActive, Pageable pageable);

    @Query("SELECT s FROM SupplierEntity s LEFT JOIN FETCH s.contacts WHERE s.supplierId = :id")
    Optional<SupplierEntity> findByIdWithContacts(@Param("id") Long id);
}
