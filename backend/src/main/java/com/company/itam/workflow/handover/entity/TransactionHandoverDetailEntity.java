package com.company.itam.workflow.handover.entity;

import com.company.itam.location.entity.LocationEntity;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.workflow.core.entity.TransactionEntity;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "transaction_handover_details")
public class TransactionHandoverDetailEntity {
    @Id @Column(name = "transaction_id") private Long transactionId;
    @MapsId @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "transaction_id")
    private TransactionEntity transaction;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recipient_user_id", nullable = false)
    private UserEntity recipient;
    @Column(name = "handover_date", nullable = false) private LocalDate handoverDate;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "destination_location_id", nullable = false)
    private LocationEntity destinationLocation;

    public Long getTransactionId() { return transactionId; }
    public TransactionEntity getTransaction() { return transaction; }
    public void setTransaction(TransactionEntity transaction) { this.transaction = transaction; }
    public UserEntity getRecipient() { return recipient; }
    public void setRecipient(UserEntity recipient) { this.recipient = recipient; }
    public LocalDate getHandoverDate() { return handoverDate; }
    public void setHandoverDate(LocalDate handoverDate) { this.handoverDate = handoverDate; }
    public LocationEntity getDestinationLocation() { return destinationLocation; }
    public void setDestinationLocation(LocationEntity value) { this.destinationLocation = value; }
}
