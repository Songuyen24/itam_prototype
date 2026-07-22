package com.company.itam.transaction.entity;

import com.company.itam.asset.entity.AssetEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import java.util.Map;

@Entity
@Table(name = "transaction_assets")
public class TransactionAssetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_asset_id")
    private Long transactionAssetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private TransactionEntity transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private AssetEntity asset;

    @Type(JsonType.class)
    @Column(name = "draft_data", columnDefinition = "jsonb")
    private Map<String, Object> draftData;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public TransactionAssetEntity() {}

    public Long getTransactionAssetId() {
        return transactionAssetId;
    }

    public void setTransactionAssetId(Long transactionAssetId) {
        this.transactionAssetId = transactionAssetId;
    }

    public TransactionEntity getTransaction() {
        return transaction;
    }

    public void setTransaction(TransactionEntity transaction) {
        this.transaction = transaction;
    }

    public AssetEntity getAsset() {
        return asset;
    }

    public void setAsset(AssetEntity asset) {
        this.asset = asset;
    }

    public Map<String, Object> getDraftData() {
        return draftData;
    }

    public void setDraftData(Map<String, Object> draftData) {
        this.draftData = draftData;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
