package com.company.itam.asset.service;

import com.company.itam.asset.dto.request.LicenseDetailsRequest;
import com.company.itam.asset.dto.response.LicenseDetailsResponse;
import com.company.itam.asset.entity.*;
import com.company.itam.asset.repository.*;
import com.company.itam.catalog.repository.*;
import com.company.itam.common.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.Objects;

@Service
public class LicenseDetailsService {
    private final AssetLicenseDetailsRepository details;
    private final SoftwareCatalogRepository software;
    private final LicenseAssignmentTypeRepository assignments;
    private final LicenseTermTypeRepository terms;
    private final LicenseAllocationRepository allocations;
    public LicenseDetailsService(AssetLicenseDetailsRepository details, SoftwareCatalogRepository software,
            LicenseAssignmentTypeRepository assignments, LicenseTermTypeRepository terms, LicenseAllocationRepository allocations) {
        this.details=details; this.software=software; this.assignments=assignments; this.terms=terms; this.allocations=allocations;
    }
    public boolean assignedTo(Long licenseId,Long userId) { return allocations.existsByLicenseAssetIdAndUserUserIdAndStatus(licenseId,userId,LicenseAllocationStatus.ACTIVE); }
    public void save(AssetEntity asset, LicenseDetailsRequest request) {
        if (request == null || request.seatCount() == null || request.seatCount() < 1) fail("LICENSE_DETAILS_REQUIRED");
        var sw=software.findById(request.softwareCatalogId()).orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
        var assignment=assignments.findById(request.assignmentTypeId()).orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
        var term=terms.findById(request.termTypeId()).orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
        if (!Boolean.TRUE.equals(sw.getIsActive()) || !Boolean.TRUE.equals(assignment.getActive()) || !Boolean.TRUE.equals(term.getActive())) fail("LICENSE_DETAILS_REQUIRED");
        if (LicenseCodes.SUBSCRIPTION.equals(term.getCode()) && request.expiryDate()==null) fail("LICENSE_EXPIRY_REQUIRED");
        var d=asset.getLicenseDetails();
        if (d==null) { d=new AssetLicenseDetailsEntity(); d.setAsset(asset); }
        else if (allocations.existsByLicenseAssetId(asset.getAssetId()) &&
                (!Objects.equals(d.getAssignmentType().getId(),request.assignmentTypeId()) ||
                 !Objects.equals(d.getSoftwareCatalog().getSoftwareCatalogId(),request.softwareCatalogId()))) fail("LICENSE_IDENTITY_LOCKED");
        if (allocations.usedSeats(asset.getAssetId()) > request.seatCount()) fail("LICENSE_CAPACITY_EXCEEDED");
        d.setSoftwareCatalog(sw); d.setAssignmentType(assignment); d.setTermType(term);
        d.setSeatCount(request.seatCount()); d.setLicenseKey(request.licenseKey()); d.setExpiryDate(request.expiryDate());
        asset.setLicenseDetails(details.save(d));
    }
    public LicenseDetailsResponse response(AssetEntity asset, boolean revealKey) {
        var d=asset.getLicenseDetails();
        if (d==null) return null;
        long used=allocations.usedSeats(asset.getAssetId());
        return new LicenseDetailsResponse(d.getSoftwareCatalog().getSoftwareCatalogId(),d.getSoftwareCatalog().getName(),
                d.getAssignmentType().getId(),d.getAssignmentType().getCode(),d.getTermType().getId(),d.getTermType().getCode(),
                d.getSeatCount(),used,d.getSeatCount()-used,revealKey?d.getLicenseKey():null,d.getExpiryDate());
    }
    private void fail(String code) { throw new AppException(HttpStatus.CONFLICT,code,code); }
}
