package com.company.itam.asset.mapper;

import com.company.itam.asset.dto.response.AssetDetailResponse;
import com.company.itam.asset.dto.response.AssetResponse;
import com.company.itam.asset.dto.response.HardwareConfigDto;
import com.company.itam.asset.entity.AssetEntity;
import com.company.itam.asset.entity.AssetHardwareDetailsEntity;
import com.company.itam.catalog.entity.ModelEntity;
import org.springframework.stereotype.Component;

@Component
public class AssetMapper {

    public AssetResponse toResponse(AssetEntity entity) {
        if (entity == null) {
            return null;
        }

        AssetResponse response = new AssetResponse();
        populateBasicFields(response, entity);
        return response;
    }

    public AssetDetailResponse toDetailResponse(AssetEntity entity) {
        if (entity == null) {
            return null;
        }

        AssetDetailResponse response = new AssetDetailResponse();
        populateBasicFields(response, entity);

        AssetHardwareDetailsEntity hw = entity.getHardwareDetails();
        if (hw != null) {
            response.setWarrantyExpiration(hw.getWarrantyExpiration());
            response.setHardwareConfig(toHardwareConfig(hw));
        } else {
            response.setHardwareConfig(new HardwareConfigDto());
        }

        if (entity.getCreatedBy() != null) {
            response.setCreatedByUserId(entity.getCreatedBy().getUserId());
            response.setCreatedByFullName(entity.getCreatedBy().getFullName());
        }

        if (entity.getUpdatedBy() != null) {
            response.setUpdatedByUserId(entity.getUpdatedBy().getUserId());
            response.setUpdatedByFullName(entity.getUpdatedBy().getFullName());
        }

        return response;
    }

    private void populateBasicFields(AssetResponse response, AssetEntity entity) {
        response.setAssetId(entity.getAssetId());
        response.setAssetTag(entity.getAssetTag());
        response.setName(entity.getName());

        // Type & Category
        if (entity.getType() != null) {
            response.setTypeId(entity.getType().getTypeId());
            response.setTypeCode(entity.getType().getCode());
            response.setTypeName(entity.getType().getName());

            if (entity.getType().getCategory() != null) {
                response.setCategoryId(entity.getType().getCategory().getCategoryId());
                response.setCategoryCode(entity.getType().getCategory().getCode().name());
                response.setCategoryName(entity.getType().getCategory().getName());
            }
        }

        // Status
        if (entity.getStatus() != null) {
            response.setStatusId(entity.getStatus().getStatusId());
            response.setStatusCode(entity.getStatus().getCode().name());
            response.setStatusName(entity.getStatus().getName());
        }

        // Hardware details (model, condition, serial, effective specs)
        AssetHardwareDetailsEntity hw = entity.getHardwareDetails();
        if (hw != null) {
            response.setSerialNumber(hw.getSerialNumber());

            if (hw.getModel() != null) {
                ModelEntity model = hw.getModel();
                response.setModelId(model.getModelId());
                response.setModelName(model.getName());
                response.setModelBrand(model.getBrand());
            }

            if (hw.getCondition() != null) {
                response.setConditionId(hw.getCondition().getConditionId());
                response.setConditionCode(hw.getCondition().getCode().name());
                response.setConditionName(hw.getCondition().getName());
            }

            HardwareConfigDto config = toHardwareConfig(hw);
            response.setEffectiveCpu(config.getEffectiveCpu());
            response.setEffectiveRam(config.getEffectiveRam());
            response.setEffectiveStorage(config.getEffectiveStorage());
            response.setEffectiveGraphicsCard(config.getEffectiveGraphicsCard());
        }

        // Assigned user
        if (entity.getAssignedTo() != null) {
            response.setAssignedToUserId(entity.getAssignedTo().getUserId());
            response.setAssignedToFullName(entity.getAssignedTo().getFullName());
            response.setAssignedToEmail(entity.getAssignedTo().getEmail());
        }

        // Department
        if (entity.getDepartment() != null) {
            response.setDepartmentId(entity.getDepartment().getDepartmentId());
            response.setDepartmentCode(entity.getDepartment().getCode());
            response.setDepartmentName(entity.getDepartment().getName());
        }

        // Location
        if (entity.getLocation() != null) {
            response.setLocationId(entity.getLocation().getLocationId());
            response.setLocationCode(entity.getLocation().getCode());
            response.setLocationName(entity.getLocation().getName());
        }

        // Supplier
        if (entity.getSupplier() != null) {
            response.setSupplierId(entity.getSupplier().getSupplierId());
            response.setSupplierCode(entity.getSupplier().getCode());
            response.setSupplierName(entity.getSupplier().getName());
        }

        // Purchase & timestamps
        response.setPoNumber(entity.getPoNumber());
        response.setPurchaseDate(entity.getPurchaseDate());
        response.setPurchaseCost(entity.getPurchaseCost());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
    }

    public HardwareConfigDto toHardwareConfig(AssetHardwareDetailsEntity hw) {
        HardwareConfigDto config = new HardwareConfigDto();
        if (hw == null) {
            return config;
        }

        ModelEntity model = hw.getModel();

        // Defaults from Model
        if (model != null) {
            config.setDefaultCpu(model.getDefaultCpu());
            config.setDefaultRam(model.getDefaultRam());
            config.setDefaultStorage(model.getDefaultStorage());
            config.setDefaultGraphicsCard(model.getDefaultGraphicsCard());
        }

        // Actuals from Hardware Details
        config.setActualCpu(hw.getActualCpu());
        config.setActualRam(hw.getActualRam());
        config.setActualStorage(hw.getActualStorage());
        config.setActualGraphicsCard(hw.getActualGraphicsCard());

        // Effective specs: fallback to Default if Actual is blank or null
        config.setEffectiveCpu(resolveEffectiveValue(hw.getActualCpu(), config.getDefaultCpu()));
        config.setEffectiveRam(resolveEffectiveValue(hw.getActualRam(), config.getDefaultRam()));
        config.setEffectiveStorage(resolveEffectiveValue(hw.getActualStorage(), config.getDefaultStorage()));
        config.setEffectiveGraphicsCard(resolveEffectiveValue(hw.getActualGraphicsCard(), config.getDefaultGraphicsCard()));

        return config;
    }

    private String resolveEffectiveValue(String actual, String defaultVal) {
        if (actual != null && !actual.trim().isEmpty()) {
            return actual.trim();
        }
        return defaultVal;
    }
}
