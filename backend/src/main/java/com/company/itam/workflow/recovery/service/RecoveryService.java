package com.company.itam.workflow.recovery.service;

import com.company.itam.asset.entity.*;
import com.company.itam.asset.repository.*;
import com.company.itam.asset.relationship.entity.AssetRelationshipEntity;
import com.company.itam.asset.relationship.enums.RelationshipType;
import com.company.itam.asset.service.LicenseCodes;
import com.company.itam.audit.entity.AuditLogEntity;
import com.company.itam.audit.repository.AuditLogRepository;
import com.company.itam.catalog.repository.AssetStatusRepository;
import com.company.itam.common.enums.*;
import com.company.itam.common.exception.*;
import com.company.itam.location.entity.LocationEntity;
import com.company.itam.location.repository.LocationRepository;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.user.repository.UserRepository;
import com.company.itam.workflow.core.entity.*;
import com.company.itam.workflow.core.enums.*;
import com.company.itam.workflow.core.repository.*;
import com.company.itam.workflow.recovery.dto.*;
import com.company.itam.workflow.recovery.entity.TransactionRecoveryDetailEntity;
import com.company.itam.workflow.recovery.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
public class RecoveryService {

    private final AssetRepository assets;
    private final LicenseAllocationRepository allocations;
    private final com.company.itam.asset.relationship.repository.AssetRelationshipRepository relationships;
    private final TransactionRepository transactions;
    private final TransactionAssetRepository txAssets;
    private final TransactionRecoveryDetailRepository recoveryDetails;
    private final UserRepository users;
    private final LocationRepository locations;
    private final AssetStatusRepository statuses;
    private final AuditLogRepository auditLogs;
    private final JdbcTemplate jdbc;

    public RecoveryService(
            AssetRepository assets,
            LicenseAllocationRepository allocations,
            com.company.itam.asset.relationship.repository.AssetRelationshipRepository relationships,
            TransactionRepository transactions,
            TransactionAssetRepository txAssets,
            TransactionRecoveryDetailRepository recoveryDetails,
            UserRepository users,
            LocationRepository locations,
            AssetStatusRepository statuses,
            AuditLogRepository auditLogs,
            JdbcTemplate jdbc) {
        this.assets = assets;
        this.allocations = allocations;
        this.relationships = relationships;
        this.transactions = transactions;
        this.txAssets = txAssets;
        this.recoveryDetails = recoveryDetails;
        this.users = users;
        this.locations = locations;
        this.statuses = statuses;
        this.auditLogs = auditLogs;
        this.jdbc = jdbc;
    }

    private String currentUser() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private AssetEntity asset(Long id) {
        return assets.findById(id).orElseThrow(() -> new ResourceNotFoundException("Asset not found: " + id));
    }

    private AssetCategory categoryOf(AssetEntity a) {
        var type = a.getType();
        if (type == null) return null;
        var catEntity = type.getCategory();
        return catEntity != null ? catEntity.getCode() : null;
    }

    private String categoryCode(AssetEntity a) {
        var cat = categoryOf(a);
        return cat != null ? cat.name() : null;
    }

    private String licenseCode(AssetEntity lic) {
        return lic.getLicenseDetails() != null && lic.getLicenseDetails().getAssignmentType() != null
                ? lic.getLicenseDetails().getAssignmentType().getCode() : null;
    }

    private void fail(String code) {
        throw new AppException(HttpStatus.BAD_REQUEST, code, code);
    }

    // ========================================================================
    // SMART CHECK — 4 scenarios: A, B, C, D
    //
    // Scenario A (main): IT selects a DEVICE IN_USE → auto-discover components, OEM, Per-User
    // Scenario B: IT selects a Per-User LICENSE asset alone → BLOCKED with message
    // Scenario C: IT selects a COMPONENT alone → BLOCKED with message
    // Scenario D: IT selects an OEM LICENSE alone → BLOCKED with message (OEM is always auto-bundled)
    // ========================================================================

    public SmartCheckResponse smartCheck(SmartCheckRequest request) {
        if (request.assetIds().isEmpty()) fail("EMPTY_TRANSACTION");
        List<Long> idList = new ArrayList<>(new LinkedHashSet<>(request.assetIds())); // keep order, dedup
        if (idList.size() != request.assetIds().size()) fail("RECOVERY_DUPLICATE");

        List<String> warnings = new ArrayList<>();
        List<SmartCheckResponse.RequiredAsset> required = new ArrayList<>();
        List<SmartCheckResponse.OptionalAsset> optional = new ArrayList<>();
        List<SmartCheckResponse.BlockedAsset> blocked = new ArrayList<>();
        List<SmartCheckResponse.ComponentDecision> componentDecisions = new ArrayList<>();

        // Categorize selected assets by type
        List<AssetEntity> selectedDevices    = new ArrayList<>(); // DEVICE category
        List<AssetEntity> selectedComponents = new ArrayList<>(); // COMPONENT category
        List<AssetEntity> selectedPerUserLic = new ArrayList<>(); // Per-User LICENSE assets
        List<AssetEntity> selectedOemLic    = new ArrayList<>(); // OEM LICENSE assets
        List<AssetEntity> selectedOther      = new ArrayList<>(); // everything else (e.g. other category)

        for (Long id : idList) {
            var a = asset(id);
            var cat = categoryOf(a);
            if (cat == null) {
                selectedOther.add(a);
                continue;
            }
            switch (cat) {
                case DEVICE     -> selectedDevices.add(a);
                case COMPONENT  -> selectedComponents.add(a);
                case LICENSE    -> {
                    String code = licenseCode(a);
                    if (LicenseCodes.PER_USER.equals(code)) {
                        selectedPerUserLic.add(a);
                    } else if (LicenseCodes.OEM.equals(code)) {
                        selectedOemLic.add(a);
                    } else {
                        selectedOther.add(a);
                    }
                }
                default -> selectedOther.add(a);
            }
        }

        // ----------------------------------------------------------------
        // SCENARIO B: Per-User License selected alone → BLOCK
        // Smart Check trả blocked kèm message yêu cầu chọn thiết bị cha trước
        // ----------------------------------------------------------------
        for (var lic : selectedPerUserLic) {
            // Tìm xem license này đang gắn trên thiết bị nào (qua allocation)
            Long parentDeviceId = null;
            for (var alloc : allocations.findAllByLicenseAssetId(lic.getAssetId())) {
                if (alloc.getDevice() != null) {
                    parentDeviceId = alloc.getDevice().getAssetId();
                    break;
                }
            }
            String msg = parentDeviceId != null
                    ? "Per-User License must be recovered together with its parent device. Please select the device instead."
                    : "Per-User License has no active device attachment.";
            blocked.add(new SmartCheckResponse.BlockedAsset(
                    lic.getAssetId(), lic.getAssetTag(), lic.getName(),
                    categoryCode(lic), msg));
            // Vẫn thêm vào optional để hiển thị trong dialog — IT sẽ thấy bị BLOCKED
            for (var alloc : allocations.findAllByLicenseAssetId(lic.getAssetId())) {
                if (alloc.getDevice() != null) {
                    optional.add(new SmartCheckResponse.OptionalAsset(
                            alloc.getAllocationId(), lic.getAssetId(), lic.getAssetTag(), lic.getName(),
                            alloc.getSeatCount(),
                            alloc.getUser() != null ? alloc.getUser().getUserId() : null,
                            alloc.getUser() != null ? alloc.getUser().getFullName() : null,
                            alloc.getDevice().getAssetId(),
                            alloc.getDevice().getAssetTag(),
                            false // blocked = not auto-selected
                    ));
                    break;
                }
            }
        }

        // ----------------------------------------------------------------
        // SCENARIO C: Component selected alone → BLOCK with parent device info
        // ----------------------------------------------------------------
        for (var comp : selectedComponents) {
            // Tìm parent device
            String parentTag = null;
            for (var rel : relationships.findByChildAssetAssetId(comp.getAssetId())) {
                if (rel.getParentAsset() != null) {
                    parentTag = rel.getParentAsset().getAssetTag();
                    break;
                }
            }
            String msg = parentTag != null
                    ? "Component must be recovered together with its parent device (" + parentTag + "). Please select the device instead."
                    : "Component has no parent device.";
            blocked.add(new SmartCheckResponse.BlockedAsset(
                    comp.getAssetId(), comp.getAssetTag(), comp.getName(),
                    categoryCode(comp), msg));
        }

        // ----------------------------------------------------------------
        // SCENARIO D: OEM License selected alone → BLOCK (OEM is always auto-bundled)
        // ----------------------------------------------------------------
        for (var lic : selectedOemLic) {
            String parentTag = null;
            for (var rel : relationships.findByChildAssetAssetId(lic.getAssetId())) {
                if (rel.getParentAsset() != null) {
                    parentTag = rel.getParentAsset().getAssetTag();
                    break;
                }
            }
            String msg = parentTag != null
                    ? "OEM License is always bundled with its parent device (" + parentTag + "). Please select the device instead."
                    : "OEM License must be recovered together with a device.";
            blocked.add(new SmartCheckResponse.BlockedAsset(
                    lic.getAssetId(), lic.getAssetTag(), lic.getName(),
                    categoryCode(lic), msg));
        }

        // ----------------------------------------------------------------
        // SCENARIO A: DEVICE(s) selected → main recovery flow
        // ----------------------------------------------------------------

        // Verify DEVICE status — only IN_USE can be recovered
        for (var dev : selectedDevices) {
            if (dev.getStatus().getCode() != AssetStatus.IN_USE) {
                blocked.add(new SmartCheckResponse.BlockedAsset(
                        dev.getAssetId(), dev.getAssetTag(), dev.getName(),
                        categoryCode(dev), "Device is not IN_USE. Only IN_USE devices can be recovered."));
            } else {
                required.add(new SmartCheckResponse.RequiredAsset(
                        dev.getAssetId(), dev.getAssetTag(), dev.getName(),
                        categoryCode(dev), "Device selected for recovery"));
            }
        }

        // Discover relationships for selected devices
        Set<Long> allRelatedIds = new HashSet<>();
        for (var dev : selectedDevices) {
            allRelatedIds.add(dev.getAssetId());
            // children (components, licenses)
            for (var rel : relationships.findByParentAssetAssetId(dev.getAssetId())) {
                allRelatedIds.add(rel.getChildAsset().getAssetId());
            }
            // parent (if any device is a component — should not happen after B/C block, but safe)
            for (var rel : relationships.findByChildAssetAssetId(dev.getAssetId())) {
                allRelatedIds.add(rel.getParentAsset().getAssetId());
            }
        }

        // Components → IT chooses KEEP_ATTACHED / DETACH
        for (var dev : selectedDevices) {
            for (var rel : relationships.findByParentAssetAssetId(dev.getAssetId())) {
                if (rel.getRelationshipType() == RelationshipType.COMPONENT_OF) {
                    var child = rel.getChildAsset();
                    var childStatus = child.getStatus().getCode();
                    // Only IN_USE or RETIRED components appear as decisions
                    if (childStatus == AssetStatus.IN_USE || childStatus == AssetStatus.RETIRED) {
                        // If component was also selected by user → it is already in blocked (C) — skip duplicate
                        if (selectedComponents.contains(child)) continue;
                        componentDecisions.add(new SmartCheckResponse.ComponentDecision(
                                child.getAssetId(), child.getAssetTag(), child.getName(),
                                List.of(
                                        new SmartCheckResponse.DecisionOption(
                                                "KEEP_ATTACHED", "Giữ nguyên / Keep",
                                                "Component stays linked to the recovered device."),
                                        new SmartCheckResponse.DecisionOption(
                                                "DETACH", "Tách riêng / Detach",
                                                "Component will be detached and returned as a separate item.")
                                ),
                                "KEEP_ATTACHED" // default
                        ));
                    }
                }
            }
        }

        // OEM Licenses on devices → always in required, never removable (P08)
        for (var dev : selectedDevices) {
            for (var rel : relationships.findByParentAssetAssetId(dev.getAssetId())) {
                if (rel.getRelationshipType() == RelationshipType.INSTALLED_ON) {
                    var lic = rel.getChildAsset();
                    String licCode = licenseCode(lic);
                    // If OEM was also selected by user → already blocked (D) — still show as required for clarity
                    if (licCode != null && LicenseCodes.OEM.equals(licCode)) {
                        boolean alreadyBlocked = selectedOemLic.contains(lic);
                        if (!alreadyBlocked) {
                            required.add(new SmartCheckResponse.RequiredAsset(
                                    lic.getAssetId(), lic.getAssetTag(), lic.getName(),
                                    categoryCode(lic), "OEM License — automatically bundled with device, cannot be recovered separately."));
                        }
                    }
                }
            }
        }

        // Per-User Licenses on devices → optional, default selected (P09)
        for (var dev : selectedDevices) {
            for (var alloc : allocations.findByDeviceAssetId(dev.getAssetId())) {
                var lic = alloc.getLicense();
                String licCode = licenseCode(lic);
                if (licCode != null && LicenseCodes.PER_USER.equals(licCode)) {
                    // If Per-User was also selected by user → already blocked (B) — still show in optional for display
                    boolean alreadyBlocked = selectedPerUserLic.contains(lic);
                    optional.add(new SmartCheckResponse.OptionalAsset(
                            alloc.getAllocationId(), lic.getAssetId(), lic.getAssetTag(), lic.getName(),
                            alloc.getSeatCount(),
                            alloc.getUser() != null ? alloc.getUser().getUserId() : null,
                            alloc.getUser() != null ? alloc.getUser().getFullName() : null,
                            dev.getAssetId(),
                            dev.getAssetTag(),
                            !alreadyBlocked // only pre-selected if not blocked
                    ));
                }
            }
        }

        // Build fingerprint
        String fingerprint = buildFingerprint(required, optional, blocked, componentDecisions);

        // Warnings summary
        if (!blocked.isEmpty()) {
            warnings.add("Some selected items cannot be recovered. Review the blocked items below and remove them before proceeding.");
        } else if (required.isEmpty() && optional.isEmpty()) {
            warnings.add("No recoverable assets found in the selection.");
        } else {
            warnings.add("Review all items carefully before confirming recovery.");
        }

        return new SmartCheckResponse(required, optional, blocked, componentDecisions, warnings, fingerprint);
    }

    // ========================================================================
    // COMPLETE
    // ========================================================================

    @Transactional
    public RecoveryResponse complete(RecoveryRequest request) {
        if (request.assetIds().isEmpty()) fail("EMPTY_TRANSACTION");
        List<Long> idList = new ArrayList<>(new LinkedHashSet<>(request.assetIds()));
        if (idList.size() != request.assetIds().size()) fail("RECOVERY_DUPLICATE");

        var returner = users.findById(request.returnerUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (returner.getAccountStatus() != AccountStatus.ACTIVE) fail("RECOVERY_RETURNER_INACTIVE");

        var location = locations.findById(request.receivingLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Location not found"));
        if (!Boolean.TRUE.equals(location.getIsActive())) fail("RECOVERY_LOCATION_INACTIVE");

        for (Long id : idList) {
            assets.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Asset not found: " + id));
        }

        // Validate ownership and status
        for (Long id : idList) {
            var a = asset(id);
            if (a.getAssignedTo() == null || !a.getAssignedTo().getUserId().equals(request.returnerUserId())) {
                fail("RECOVERY_ASSET_NOT_OWNED");
            }
            if (a.getStatus().getCode() != AssetStatus.IN_USE) {
                fail("RECOVERY_ASSET_NOT_IN_USE");
            }
        }

        var scRequest = new SmartCheckRequest(request.assetIds(), request.reason());
        var scResult = smartCheck(scRequest);

        // Block if any asset is blocked (extra safety — shouldn't happen if UI follows Smart Check)
        if (!scResult.blockedAssets().isEmpty()) {
            String blockedList = scResult.blockedAssets().stream()
                    .map(b -> b.assetTag() + ": " + b.reason())
                    .collect(Collectors.joining("; "));
            fail("RECOVERY_BLOCKED_ASSETS");
        }

        String computedFingerprint = scResult.fingerprint();
        if (request.expectedFingerprint() != null && !request.expectedFingerprint().equals(computedFingerprint)) {
            fail("RECOVERY_CHANGED");
        }

        var inStockStatus = statuses.findByCode(AssetStatus.IN_STOCK)
                .orElseThrow(() -> new ResourceNotFoundException("IN_STOCK status not found"));

        var tx = new TransactionEntity();
        tx.setTransactionCode("RC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        tx.setType(TransactionType.RECOVERY);
        tx.setStatus(TransactionStatus.COMPLETED);
        tx.setProcessedBy(returner);
        tx.setProcessedAt(Instant.now());
        tx.setCompletedAt(Instant.now());
        tx.setNotes(request.reason());
        transactions.saveAndFlush(tx);

        var detail = new TransactionRecoveryDetailEntity();
        detail.setTransaction(tx);
        detail.setReturner(returner);
        detail.setRecoveryDate(request.recoveryDate());
        detail.setReceivingLocation(location);
        detail.setReason(request.reason());
        recoveryDetails.saveAndFlush(detail);

        List<RecoveryResponse.RecoveryLine> lines = new ArrayList<>();

        for (Long assetId : idList) {
            var a = asset(assetId);

            a.setStatus(inStockStatus);
            a.setAssignedTo(null);
            a.setLocation(location);
            assets.save(a);

            var txAsset = new TransactionAssetEntity();
            txAsset.setTransaction(tx);
            txAsset.setAsset(a);
            txAssets.save(txAsset);

            // Component decisions
            List<AssetRelationshipEntity> rels = relationships.findByParentAssetAssetId(assetId);
            for (var rel : rels) {
                if (rel.getRelationshipType() == RelationshipType.COMPONENT_OF) {
                    Long childId = rel.getChildAsset().getAssetId();
                    var action = request.componentActions().get(childId);
                    if (action != null && "DETACH".equals(action.componentAction())) {
                        relationships.delete(rel);
                    }
                }
            }

            // License allocations
            List<RecoveryResponse.AllocationResult> allocResults = new ArrayList<>();
            for (var alloc : allocations.findByDeviceAssetId(assetId)) {
                var lic = alloc.getLicense();
                String licCode = licenseCode(lic);

                if (licCode != null && LicenseCodes.OEM.equals(licCode)) {
                    alloc.setDevice(null);
                    alloc.setStatus(LicenseAllocationStatus.RESERVED);
                    allocations.save(alloc);
                    allocResults.add(new RecoveryResponse.AllocationResult(
                            alloc.getAllocationId(), alloc.getSeatCount(), "RESERVED", "OEM"));
                } else if (licCode != null && LicenseCodes.PER_USER.equals(licCode)) {
                    Boolean recoverPerUser = request.perUserActions().get(alloc.getAllocationId());
                    if (recoverPerUser != null && recoverPerUser) {
                        alloc.setDevice(null);
                        alloc.setStatus(LicenseAllocationStatus.RELEASED);
                        alloc.setReleasedAt(Instant.now());
                        allocations.save(alloc);
                        allocResults.add(new RecoveryResponse.AllocationResult(
                                alloc.getAllocationId(), alloc.getSeatCount(), "RELEASED", "PER_USER"));
                    } else {
                        alloc.setDevice(null);
                        allocations.save(alloc);
                        allocResults.add(new RecoveryResponse.AllocationResult(
                                alloc.getAllocationId(), alloc.getSeatCount(), "UNLINKED", "PER_USER"));
                    }
                }
            }

            var actor = users.findByEmail(currentUser()).orElse(returner);
            var audit = new AuditLogEntity();
            audit.setEntityType("ASSET");
            audit.setEntityId(a.getAssetId());
            audit.setAction("RECOVERED");
            audit.setActor(actor);
            audit.setNewData(Map.of(
                    "status", "IN_STOCK",
                    "transactionId", tx.getTransactionId(),
                    "returnerUserId", returner.getUserId(),
                    "recoveryDate", request.recoveryDate().toString()
            ));
            auditLogs.save(audit);

            lines.add(new RecoveryResponse.RecoveryLine(
                    a.getAssetId(), a.getAssetTag(), a.getName(), categoryCode(a),
                    null, 0, allocResults, Map.of()
            ));
        }

        var txAudit = new AuditLogEntity();
        txAudit.setEntityType("TRANSACTION");
        txAudit.setEntityId(tx.getTransactionId());
        txAudit.setAction("RECOVERY_COMPLETED");
        txAudit.setActor(users.findByEmail(currentUser()).orElse(returner));
        txAudit.setNewData(Map.of(
                "status", "COMPLETED",
                "returnerUserId", returner.getUserId(),
                "locationId", location.getLocationId(),
                "recoveryDate", request.recoveryDate().toString(),
                "assetIds", idList
        ));
        auditLogs.save(txAudit);

        return new RecoveryResponse(
                tx.getTransactionId(), tx.getTransactionCode(), tx.getCompletedAt(),
                returner.getUserId(), returner.getFullName(), returner.getEmail(),
                location.getLocationId(), location.getName(),
                request.recoveryDate(), request.reason(),
                computedFingerprint, lines
        );
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private String buildFingerprint(List<SmartCheckResponse.RequiredAsset> required,
                                   List<SmartCheckResponse.OptionalAsset> optional,
                                   List<SmartCheckResponse.BlockedAsset> blocked,
                                   List<SmartCheckResponse.ComponentDecision> decisions) {
        try {
            String data = required.size() + ":" + optional.size() + ":" + blocked.size() + ":" + decisions.size() + ":" +
                    required.stream().map(r -> r.assetId().toString()).sorted().collect(Collectors.joining(",")) + "|" +
                    optional.stream().map(o -> o.allocationId().toString()).sorted().collect(Collectors.joining(",")) + "|" +
                    blocked.stream().map(b -> b.assetId().toString()).sorted().collect(Collectors.joining(","));
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
