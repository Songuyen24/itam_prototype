package com.company.itam.workflow.receiving.entity;

import com.company.itam.location.entity.LocationEntity;
import com.company.itam.supplier.entity.SupplierEntity;
import com.company.itam.workflow.core.entity.TransactionEntity;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "transaction_import_details")
public class TransactionImportDetailEntity {
    @Id @Column(name = "transaction_id") private Long transactionId;
    @MapsId @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "transaction_id")
    private TransactionEntity transaction;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "supplier_id", nullable = false)
    private SupplierEntity supplier;
    @Column(name = "po_number", nullable = false, length = 100) private String poNumber;
    @Column(name = "import_date", nullable = false) private LocalDate importDate;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "receiving_location_id", nullable = false)
    private LocationEntity receivingLocation;

    public Long getTransactionId() { return transactionId; }
    public TransactionEntity getTransaction() { return transaction; }
    public void setTransaction(TransactionEntity transaction) { this.transaction = transaction; }
    public SupplierEntity getSupplier() { return supplier; }
    public void setSupplier(SupplierEntity supplier) { this.supplier = supplier; }
    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }
    public LocalDate getImportDate() { return importDate; }
    public void setImportDate(LocalDate importDate) { this.importDate = importDate; }
    public LocationEntity getReceivingLocation() { return receivingLocation; }
    public void setReceivingLocation(LocationEntity value) { this.receivingLocation = value; }
}
