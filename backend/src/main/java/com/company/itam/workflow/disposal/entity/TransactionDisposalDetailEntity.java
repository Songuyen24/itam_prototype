package com.company.itam.workflow.disposal.entity;

import com.company.itam.workflow.core.entity.TransactionEntity;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "transaction_disposal_details")
public class TransactionDisposalDetailEntity {
    @Id @Column(name = "transaction_id") private Long transactionId;
    @MapsId @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "transaction_id")
    private TransactionEntity transaction;
    @Column(name = "reason", nullable = false, columnDefinition = "text") private String reason;
    @Column(name = "disposal_date") private LocalDate disposalDate;

    public Long getTransactionId() { return transactionId; }
    public TransactionEntity getTransaction() { return transaction; }
    public void setTransaction(TransactionEntity transaction) { this.transaction = transaction; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDate getDisposalDate() { return disposalDate; }
    public void setDisposalDate(LocalDate disposalDate) { this.disposalDate = disposalDate; }
}
