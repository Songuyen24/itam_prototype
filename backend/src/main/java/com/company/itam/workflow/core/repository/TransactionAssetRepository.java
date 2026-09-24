package com.company.itam.workflow.core.repository;

import com.company.itam.workflow.core.entity.TransactionAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionAssetRepository extends JpaRepository<TransactionAssetEntity, Long> {
    List<TransactionAssetEntity> findByTransactionTransactionId(Long transactionId);
}
