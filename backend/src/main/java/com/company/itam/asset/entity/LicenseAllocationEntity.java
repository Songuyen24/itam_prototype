package com.company.itam.asset.entity;
import jakarta.persistence.*;
import java.time.Instant;
import com.company.itam.user.entity.UserEntity;
@Entity @Table(name="license_allocations")
public class LicenseAllocationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name="allocation_id")
    private Long allocationId;
    public Long getAllocationId() { return allocationId; }
    public void setAllocationId(Long value) { allocationId = value; }
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="license_asset_id",nullable=false)
    private AssetEntity license;
    public AssetEntity getLicense() { return license; }
    public void setLicense(AssetEntity value) { license = value; }
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="device_asset_id")
    private AssetEntity device;
    public AssetEntity getDevice() { return device; }
    public void setDevice(AssetEntity value) { device = value; }
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id")
    private UserEntity user;
    public UserEntity getUser() { return user; }
    public void setUser(UserEntity value) { user = value; }
    @Column(name="seat_count",nullable=false)
    private int seatCount;
    public int getSeatCount() { return seatCount; }
    public void setSeatCount(int value) { seatCount = value; }
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20)
    private LicenseAllocationStatus status;
    public LicenseAllocationStatus getStatus() { return status; }
    public void setStatus(LicenseAllocationStatus value) { status = value; }
    @Column(name="handover_transaction_id")
    private Long handoverTransactionId;
    public Long getHandoverTransactionId() { return handoverTransactionId; }
    public void setHandoverTransactionId(Long value) { handoverTransactionId = value; }
    @Column(name="recovery_transaction_id")
    private Long recoveryTransactionId;
    public Long getRecoveryTransactionId() { return recoveryTransactionId; }
    public void setRecoveryTransactionId(Long value) { recoveryTransactionId = value; }
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by",nullable=false)
    private UserEntity createdBy;
    public UserEntity getCreatedBy() { return createdBy; }
    public void setCreatedBy(UserEntity value) { createdBy = value; }
    @Column(name="created_at",nullable=false)
    private Instant createdAt;
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    @Column(name="released_at")
    private Instant releasedAt;
    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant value) { releasedAt = value; }
    @PrePersist void onCreate() { createdAt = Instant.now(); }
}
