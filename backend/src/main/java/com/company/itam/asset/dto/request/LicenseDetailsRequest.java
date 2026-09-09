package com.company.itam.asset.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record LicenseDetailsRequest(
        @NotNull(message = "{validation.required}") Long softwareCatalogId,
        @NotNull(message = "{validation.required}") Long assignmentTypeId,
        @NotNull(message = "{validation.required}") Long termTypeId,
        @NotNull(message = "{validation.required}") @Min(value = 1, message = "{validation.minimum}") Integer seatCount,
        @Size(max = 500, message = "{validation.size}") String licenseKey,
        LocalDate expiryDate) {}
