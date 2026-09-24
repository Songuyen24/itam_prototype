package com.company.itam.asset.dto.response;

import java.time.LocalDate;

public record LicenseDetailsResponse(Long softwareCatalogId, String softwareName,
        Long assignmentTypeId, String assignmentTypeCode, Long termTypeId, String termTypeCode,
        int seatCount, long allocatedSeats, long availableSeats, String licenseKey, LocalDate expiryDate) {}
