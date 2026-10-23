package com.company.itam.audit.repository;

import com.company.itam.audit.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    @Query(value = "SELECT a FROM AuditLogEntity a LEFT JOIN FETCH a.actor LEFT JOIN FETCH a.transaction WHERE "
            + "(:assetId IS NULL OR (a.entityType = 'ASSET' AND a.entityId = :assetId) OR a.transaction IN "
            + "(SELECT t FROM TransactionEntity t JOIN t.transactionAssets i WHERE i.asset.assetId = :assetId) "
            + "OR (a.entityType = 'TRANSACTION' AND a.entityId IN "
            + "(SELECT t.transactionId FROM TransactionEntity t JOIN t.transactionAssets i WHERE i.asset.assetId = :assetId))) "
            + "AND (:actorId IS NULL OR a.actor.userId = :actorId) "
            + "AND (:action IS NULL OR a.action = :action) "
            + "AND a.createdAt >= :fromTime AND a.createdAt < :toTime",
            countQuery = "SELECT COUNT(a) FROM AuditLogEntity a WHERE "
            + "(:assetId IS NULL OR (a.entityType = 'ASSET' AND a.entityId = :assetId) OR a.transaction IN "
            + "(SELECT t FROM TransactionEntity t JOIN t.transactionAssets i WHERE i.asset.assetId = :assetId) "
            + "OR (a.entityType = 'TRANSACTION' AND a.entityId IN "
            + "(SELECT t.transactionId FROM TransactionEntity t JOIN t.transactionAssets i WHERE i.asset.assetId = :assetId))) "
            + "AND (:actorId IS NULL OR a.actor.userId = :actorId) "
            + "AND (:action IS NULL OR a.action = :action) "
            + "AND a.createdAt >= :fromTime AND a.createdAt < :toTime")
    Page<AuditLogEntity> search(Long assetId, Long actorId, String action, Instant fromTime, Instant toTime, Pageable page);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"actor"})
    org.springframework.data.domain.Page<AuditLogEntity> findByEntityTypeAndEntityId(String type,Long id,org.springframework.data.domain.Pageable page);

    /**
     * Lấy lịch sử audit của một entity, kèm actor + transaction được nạp sẵn.
     * Tránh LazyInitializationException khi AuditLogMapper/AuditLogService đọc actor/transaction.
     */
    @Query("SELECT a FROM AuditLogEntity a LEFT JOIN FETCH a.actor LEFT JOIN FETCH a.transaction WHERE a.entityType = :entityType AND a.entityId = :entityId ORDER BY a.createdAt DESC")
    List<AuditLogEntity> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId);
}
