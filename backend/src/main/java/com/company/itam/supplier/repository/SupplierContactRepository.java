package com.company.itam.supplier.repository;

import com.company.itam.supplier.entity.SupplierContactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupplierContactRepository extends JpaRepository<SupplierContactEntity, Long> {
    List<SupplierContactEntity> findBySupplierSupplierId(Long supplierId);
}
