package com.company.itam.workflow.core.repository;

import com.company.itam.workflow.core.entity.TransactionEntity;
import com.company.itam.workflow.core.enums.TransactionStatus;
import com.company.itam.workflow.core.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long>, JpaSpecificationExecutor<TransactionEntity> {
    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"requester","processedBy"})
    Page<TransactionEntity> findAll(org.springframework.data.jpa.domain.Specification<TransactionEntity> spec, Pageable pageable);
    Optional<TransactionEntity> findByTransactionCode(String transactionCode);
    boolean existsByTransactionCode(String transactionCode);

    Page<TransactionEntity> findByType(TransactionType type, Pageable pageable);
    Page<TransactionEntity> findByStatus(TransactionStatus status, Pageable pageable);
    Page<TransactionEntity> findByTypeAndStatus(TransactionType type, TransactionStatus status, Pageable pageable);
    Page<TransactionEntity> findByRequesterUserId(Long requesterId, Pageable pageable);
    @Query("SELECT t FROM TransactionEntity t LEFT JOIN FETCH t.transactionAssets WHERE t.transactionId = :id")
    Optional<TransactionEntity> findByIdWithAssets(@Param("id") Long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from TransactionEntity t where t.transactionId = :id")
    java.util.Optional<TransactionEntity> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
