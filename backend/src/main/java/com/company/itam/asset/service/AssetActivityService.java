package com.company.itam.asset.service;

import com.company.itam.asset.repository.*;
import com.company.itam.audit.repository.AuditLogRepository;
import com.company.itam.common.exception.ResourceNotFoundException;
import com.company.itam.common.pagination.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
@Transactional(readOnly=true)
@PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
public class AssetActivityService {
    private final AssetRepository assets;
    private final LicenseAllocationRepository allocations;
    private final AuditLogRepository logs;
    public AssetActivityService(AssetRepository assets,LicenseAllocationRepository allocations,AuditLogRepository logs) {
        this.assets=assets; this.allocations=allocations; this.logs=logs;
    }
    public record Allocation(Long allocationId,int seats,String status,Long deviceId,String deviceTag,Long userId,String userName,Instant createdAt,Instant releasedAt,Long handoverTransactionId,Long recoveryTransactionId) {}
    public record History(Long id,String action,String actor,Instant createdAt) {}
    public PageResponse<Allocation> allocations(Long id,Pageable page) {
        if (!assets.existsById(id)) throw new ResourceNotFoundException("RESOURCE_NOT_FOUND");
        return PageResponse.of(allocations.findByLicenseAssetId(id,page).map(a->new Allocation(a.getAllocationId(),a.getSeatCount(),a.getStatus().name(),
                a.getDevice()==null?null:a.getDevice().getAssetId(),a.getDevice()==null?null:a.getDevice().getAssetTag(),
                a.getUser()==null?null:a.getUser().getUserId(),a.getUser()==null?null:a.getUser().getFullName(),a.getCreatedAt(),a.getReleasedAt(),a.getHandoverTransactionId(),a.getRecoveryTransactionId())));
    }
    public PageResponse<History> history(Long id,Pageable page) {
        if (!assets.existsById(id)) throw new ResourceNotFoundException("RESOURCE_NOT_FOUND");
        return PageResponse.of(logs.findByEntityTypeAndEntityId("ASSET",id,page).map(a->new History(a.getAuditLogId(),a.getAction(),a.getActor().getFullName(),a.getCreatedAt())));
    }
}
