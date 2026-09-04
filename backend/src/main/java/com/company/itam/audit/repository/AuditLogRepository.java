package com.company.itam.audit.repository;

import com.company.itam.audit.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    /**
     * Lấy lịch sử audit của một entity, kèm actor + transaction được nạp sẵn.
     * Tránh LazyInitializationException khi AuditLogMapper/AuditLogService đọc actor/transaction.
     */
    @Query("SELECT a FROM AuditLogEntity a LEFT JOIN FETCH a.actor LEFT JOIN FETCH a.transaction WHERE a.entityType = :entityType AND a.entityId = :entityId ORDER BY a.createdAt DESC")
    List<AuditLogEntity> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId);
}
