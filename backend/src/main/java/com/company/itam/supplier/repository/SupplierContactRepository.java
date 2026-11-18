package com.company.itam.supplier.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.itam.supplier.entity.SupplierContactEntity;

@Repository
public interface SupplierContactRepository extends JpaRepository<SupplierContactEntity, Long> {
    List<SupplierContactEntity> findBySupplierSupplierId(Long supplierId);
}
