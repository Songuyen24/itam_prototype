package com.company.itam.asset.service;

import com.company.itam.asset.entity.*;
import com.company.itam.asset.repository.*;
import com.company.itam.common.exception.*;
import com.company.itam.common.enums.AssetStatus;
import com.company.itam.user.entity.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.Map;

/** Transaction-scoped integration contract for T15/T16. No direct allocation HTTP writes. */
@Service
@Transactional(propagation=Propagation.MANDATORY)
@org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
public class LicenseAllocationService {
    private final AssetRepository assets;
    private final LicenseAllocationRepository allocations;
    private final AssetAuditService audit;
    private final JdbcTemplate jdbc;
    private final com.company.itam.asset.relationship.repository.AssetRelationshipRepository relationships;
    public LicenseAllocationService(AssetRepository assets, LicenseAllocationRepository allocations, AssetAuditService audit, JdbcTemplate jdbc,
            com.company.itam.asset.relationship.repository.AssetRelationshipRepository relationships) {
        this.assets=assets; this.allocations=allocations; this.audit=audit; this.jdbc=jdbc;
        this.relationships=relationships;
    }
    public LicenseAllocationEntity reserveOem(AssetEntity license, AssetEntity device, UserEntity actor) {
        capacity(license,1);
        if (!LicenseCodes.OEM.equals(license.getLicenseDetails().getAssignmentType().getCode())) fail("LICENSE_WORKFLOW_REQUIRED");
        var allocation=new LicenseAllocationEntity();
        allocation.setLicense(license); allocation.setDevice(device); allocation.setSeatCount(1);
        allocation.setStatus(LicenseAllocationStatus.RESERVED); allocation.setCreatedBy(actor);
        allocations.saveAndFlush(allocation);
        audit.record(license.getAssetId(),"RESERVE_SEAT",actor,null,Map.of("allocationId",allocation.getAllocationId(),"deviceId",device.getAssetId(),"seats",1));
        return allocation;
    }
    public LicenseAllocationEntity allocatePerUser(Long licenseId, UserEntity recipient, int seats, Long handoverId, UserEntity actor) {
        var license=lock(licenseId);
        if (recipient.getAccountStatus()!=com.company.itam.common.enums.AccountStatus.ACTIVE) fail("LICENSE_WORKFLOW_REQUIRED");
        capacity(license,seats);
        checkTransaction(handoverId,"HANDOVER",recipient.getUserId());
        checkLine(handoverId,licenseId);
        if (!LicenseCodes.PER_USER.equals(license.getLicenseDetails().getAssignmentType().getCode())) fail("LICENSE_WORKFLOW_REQUIRED");
        capacity(license,seats);
        var allocation=new LicenseAllocationEntity();
        allocation.setLicense(license); allocation.setUser(recipient); allocation.setSeatCount(seats);
        allocation.setStatus(LicenseAllocationStatus.ACTIVE); allocation.setHandoverTransactionId(handoverId); allocation.setCreatedBy(actor);
        allocations.saveAndFlush(allocation);
        audit.record(licenseId,"ALLOCATE_SEATS",actor,null,Map.of("allocationId",allocation.getAllocationId(),"seats",seats,"transactionId",handoverId));
        return allocation;
    }
    public void attachPerUser(Long allocationId, Long deviceId, UserEntity actor) {
        Long licenseId=allocations.licenseId(allocationId).orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
        for (Long id:new java.util.TreeSet<>(java.util.List.of(licenseId,deviceId))) lock(id);
        var a=allocations.findById(allocationId).orElseThrow();
        var device=assets.findById(deviceId).orElseThrow();
        if (a.getStatus()!=LicenseAllocationStatus.ACTIVE || a.getDevice()!=null || a.getSeatCount()!=1
                || !LicenseCodes.PER_USER.equals(a.getLicense().getLicenseDetails().getAssignmentType().getCode())
                || device.getType().getCategory().getCode()!=com.company.itam.common.enums.AssetCategory.DEVICE
                || device.getStatus().getCode()!=AssetStatus.IN_USE || device.getAssignedTo()==null
                || !device.getAssignedTo().getUserId().equals(a.getUser().getUserId())) fail("LICENSE_WORKFLOW_REQUIRED");
        a.setDevice(device);
        var r=new com.company.itam.asset.relationship.entity.AssetRelationshipEntity();
        r.setParentAsset(device);r.setChildAsset(a.getLicense());r.setAllocation(a);r.setCreatedBy(actor);
        r.setRelationshipType(com.company.itam.asset.relationship.enums.RelationshipType.INSTALLED_ON);
        relationships.saveAndFlush(r);
        var data=Map.<String,Object>of("allocationId",allocationId,"parentId",deviceId,"childId",licenseId);
        audit.record(deviceId,"LINK",actor,null,data);audit.record(licenseId,"LINK",actor,null,data);
    }
    public void activateOem(Long allocationId, UserEntity recipient, Long handoverId, UserEntity actor) {
        var licenseId=allocations.licenseId(allocationId).orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
        lock(licenseId);
        var a=allocations.findById(allocationId).orElseThrow();
        checkTransaction(handoverId,"HANDOVER",recipient.getUserId());
        if (a.getStatus()!=LicenseAllocationStatus.RESERVED || !LicenseCodes.OEM.equals(a.getLicense().getLicenseDetails().getAssignmentType().getCode())) fail("LICENSE_WORKFLOW_REQUIRED");
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM transaction_assets WHERE transaction_id=? AND asset_id=?)",Boolean.class,handoverId,a.getDevice().getAssetId()))) fail("LICENSE_WORKFLOW_REQUIRED");
        a.setUser(recipient); a.setHandoverTransactionId(handoverId); a.setStatus(LicenseAllocationStatus.ACTIVE);
        audit.record(a.getLicense().getAssetId(),"ACTIVATE_SEAT",actor,null,Map.of("allocationId",allocationId,"transactionId",handoverId));
    }
    public void releasePerUser(Long allocationId, Long recoveryId, UserEntity actor) {
        var licenseId=allocations.licenseId(allocationId).orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
        lock(licenseId);
        var a=allocations.findById(allocationId).orElseThrow();
        if (a.getStatus()!=LicenseAllocationStatus.ACTIVE || !LicenseCodes.PER_USER.equals(a.getLicense().getLicenseDetails().getAssignmentType().getCode())) fail("LICENSE_WORKFLOW_REQUIRED");
        checkTransaction(recoveryId,"RECOVERY",a.getUser().getUserId());
        checkLine(recoveryId,a.getLicense().getAssetId());
        relationships.findByAllocationAllocationId(allocationId).ifPresent(r->{
            var data=Map.<String,Object>of("allocationId",allocationId,"parentId",r.getParentAsset().getAssetId(),"childId",r.getChildAsset().getAssetId());
            audit.record(r.getParentAsset().getAssetId(),"UNLINK",actor,data,null);
            audit.record(r.getChildAsset().getAssetId(),"UNLINK",actor,data,null);
            relationships.delete(r);
        });
        a.setStatus(LicenseAllocationStatus.RELEASED); a.setReleasedAt(Instant.now()); a.setRecoveryTransactionId(recoveryId);
        audit.record(a.getLicense().getAssetId(),"RELEASE_SEATS",actor,null,Map.of("allocationId",allocationId,"seats",a.getSeatCount(),"transactionId",recoveryId));
        allocations.flush();
    }
    private AssetEntity lock(Long id) {
        return assets.lockById(id).orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
    }
    private void capacity(AssetEntity license,int seats) {
        if (license.getLicenseDetails()==null || license.getStatus().getCode()!=AssetStatus.IN_STOCK || seats<1) fail("LICENSE_WORKFLOW_REQUIRED");
        if (allocations.usedSeats(license.getAssetId())+seats>license.getLicenseDetails().getSeatCount()) fail("LICENSE_CAPACITY_EXCEEDED");
    }
    private void checkTransaction(Long id,String type,Long userId) {
        String table="HANDOVER".equals(type)?"transaction_handover_details":"transaction_recovery_details";
        String column="HANDOVER".equals(type)?"recipient_user_id":"returner_user_id";
        if (id==null || !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM transactions t JOIN "+table+" d USING(transaction_id) WHERE t.transaction_id=? AND t.type=? AND t.status='COMPLETED' AND d."+column+"=?)",Boolean.class,id,type,userId))) fail("LICENSE_WORKFLOW_REQUIRED");
    }
    private void checkLine(Long transactionId,Long assetId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM transaction_assets WHERE transaction_id=? AND asset_id=?)",Boolean.class,transactionId,assetId))) fail("LICENSE_WORKFLOW_REQUIRED");
    }
    private void fail(String code) { throw new AppException(HttpStatus.CONFLICT,code,code); }
}
