package com.company.itam.transaction.repository;

import com.company.itam.transaction.entity.TransactionAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionAssetRepository extends JpaRepository<TransactionAssetEntity, Long> {
    List<TransactionAssetEntity> findByTransactionTransactionId(Long transactionId);
}
