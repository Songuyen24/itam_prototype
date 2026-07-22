package com.company.itam.transaction.repository;

import com.company.itam.transaction.entity.TransactionEntity;
import com.company.itam.common.enums.TransactionStatus;
import com.company.itam.common.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {
    Optional<TransactionEntity> findByTransactionCode(String transactionCode);
    boolean existsByTransactionCode(String transactionCode);

    Page<TransactionEntity> findByType(TransactionType type, Pageable pageable);
    Page<TransactionEntity> findByStatus(TransactionStatus status, Pageable pageable);
    Page<TransactionEntity> findByTypeAndStatus(TransactionType type, TransactionStatus status, Pageable pageable);
    Page<TransactionEntity> findByRequesterUserId(Long requesterId, Pageable pageable);
    Page<TransactionEntity> findByRelatedUserUserId(Long relatedUserId, Pageable pageable);

    @Query("SELECT t FROM TransactionEntity t LEFT JOIN FETCH t.transactionAssets WHERE t.transactionId = :id")
    Optional<TransactionEntity> findByIdWithAssets(@Param("id") Long id);
}
