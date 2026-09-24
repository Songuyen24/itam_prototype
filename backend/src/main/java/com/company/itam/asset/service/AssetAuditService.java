package com.company.itam.asset.service;

import com.company.itam.asset.entity.AssetEntity;
import com.company.itam.audit.entity.AuditLogEntity;
import com.company.itam.audit.repository.AuditLogRepository;
import com.company.itam.user.entity.UserEntity;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class AssetAuditService {
    private final AuditLogRepository logs;
    public AssetAuditService(AuditLogRepository logs) { this.logs = logs; }
    public Map<String,Object> snapshot(AssetEntity asset) {
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("assetTag", asset.getAssetTag());
        data.put("name", asset.getName());
        data.put("typeId", asset.getType().getTypeId());
        data.put("status", asset.getStatus().getCode().name());
        data.put("purchaseCost", asset.getPurchaseCost());
        data.put("purchaseDate",asset.getPurchaseDate()==null?null:asset.getPurchaseDate().toString());
        data.put("poNumber",asset.getPoNumber());
        data.put("departmentId",asset.getDepartment()==null?null:asset.getDepartment().getDepartmentId());
        data.put("locationId",asset.getLocation()==null?null:asset.getLocation().getLocationId());
        data.put("supplierId",asset.getSupplier()==null?null:asset.getSupplier().getSupplierId());
        data.put("assignedTo",asset.getAssignedTo()==null?null:asset.getAssignedTo().getUserId());
        if (asset.getHardwareDetails()!=null) {
            var h=asset.getHardwareDetails();
            data.put("serialNumber",h.getSerialNumber());
            data.put("modelId",h.getModel()==null?null:h.getModel().getModelId());
            data.put("conditionId",h.getCondition()==null?null:h.getCondition().getConditionId());
            data.put("warrantyExpiration",h.getWarrantyExpiration()==null?null:h.getWarrantyExpiration().toString());
            data.put("actualCpu",h.getActualCpu());data.put("actualRam",h.getActualRam());
            data.put("actualStorage",h.getActualStorage());data.put("actualGraphicsCard",h.getActualGraphicsCard());
        }
        if (asset.getLicenseDetails() != null) {
            var d = asset.getLicenseDetails();
            data.put("seatCount", d.getSeatCount());
            data.put("softwareCatalogId", d.getSoftwareCatalog().getSoftwareCatalogId());
            data.put("assignmentType", d.getAssignmentType().getCode());
            data.put("termType", d.getTermType().getCode());
            data.put("expiryDate", d.getExpiryDate() == null ? null : d.getExpiryDate().toString());
        }
        return data; // Deliberate allowlist: never serialize license keys.
    }
    public void record(Long assetId, String action, UserEntity actor, Map<String,Object> before, Map<String,Object> after) {
        var log = new AuditLogEntity();
        log.setEntityType("ASSET"); log.setEntityId(assetId); log.setAction(action);
        log.setActor(actor); log.setOldData(before); log.setNewData(after);
        logs.save(log);
    }
}
