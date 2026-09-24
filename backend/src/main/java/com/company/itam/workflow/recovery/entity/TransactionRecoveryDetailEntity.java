package com.company.itam.workflow.recovery.entity;

import com.company.itam.location.entity.LocationEntity;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.workflow.core.entity.TransactionEntity;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "transaction_recovery_details")
public class TransactionRecoveryDetailEntity {
    @Id @Column(name = "transaction_id") private Long transactionId;
    @MapsId @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "transaction_id")
    private TransactionEntity transaction;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "returner_user_id", nullable = false)
    private UserEntity returner;
    @Column(name = "recovery_date", nullable = false) private LocalDate recoveryDate;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "receiving_location_id", nullable = false)
    private LocationEntity receivingLocation;
    @Column(name = "reason", nullable = false, columnDefinition = "text") private String reason;

    public Long getTransactionId() { return transactionId; }
    public TransactionEntity getTransaction() { return transaction; }
    public void setTransaction(TransactionEntity transaction) { this.transaction = transaction; }
    public UserEntity getReturner() { return returner; }
    public void setReturner(UserEntity returner) { this.returner = returner; }
    public LocalDate getRecoveryDate() { return recoveryDate; }
    public void setRecoveryDate(LocalDate recoveryDate) { this.recoveryDate = recoveryDate; }
    public LocationEntity getReceivingLocation() { return receivingLocation; }
    public void setReceivingLocation(LocationEntity value) { this.receivingLocation = value; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
