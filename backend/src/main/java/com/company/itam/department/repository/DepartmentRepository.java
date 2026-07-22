package com.company.itam.department.repository;

import com.company.itam.department.entity.DepartmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<DepartmentEntity, Long> {
    Optional<DepartmentEntity> findByCode(String code);
    boolean existsByCode(String code);
    Page<DepartmentEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Page<DepartmentEntity> findByIsActive(Boolean isActive, Pageable pageable);
}
