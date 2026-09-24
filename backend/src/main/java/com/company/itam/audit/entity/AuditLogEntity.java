package com.company.itam.audit.entity;

import com.company.itam.workflow.core.entity.TransactionEntity;
import com.company.itam.user.entity.UserEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_log_id")
    private Long auditLogId;
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;
    @Column(name = "entity_id", nullable = false)
    private Long entityId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private TransactionEntity transaction;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private UserEntity actor;
    @Column(name = "action", nullable = false, length = 50)
    private String action;
    @Type(JsonType.class)
    @Column(name = "old_data", columnDefinition = "jsonb")
    private Map<String, Object> oldData;
    @Type(JsonType.class)
    @Column(name = "new_data", columnDefinition = "jsonb")
    private Map<String, Object> newData;
    @Column(name = "details", columnDefinition = "text")
    private String details;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getAuditLogId() { return auditLogId; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public TransactionEntity getTransaction() { return transaction; }
    public void setTransaction(TransactionEntity transaction) { this.transaction = transaction; }
    public UserEntity getActor() { return actor; }
    public void setActor(UserEntity actor) { this.actor = actor; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Map<String, Object> getOldData() { return oldData; }
    public void setOldData(Map<String, Object> oldData) { this.oldData = oldData; }
    public Map<String, Object> getNewData() { return newData; }
    public void setNewData(Map<String, Object> newData) { this.newData = newData; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
