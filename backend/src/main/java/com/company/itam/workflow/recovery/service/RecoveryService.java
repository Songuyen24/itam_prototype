package com.company.itam.workflow.recovery.service;

import com.company.itam.asset.entity.*;
import com.company.itam.asset.repository.*;
import com.company.itam.asset.relationship.entity.AssetRelationshipEntity;
import com.company.itam.asset.relationship.enums.RelationshipType;
import com.company.itam.asset.service.LicenseCodes;
import com.company.itam.asset.service.AssetAuditService;
import com.company.itam.audit.entity.AuditLogEntity;
import com.company.itam.audit.repository.AuditLogRepository;
import com.company.itam.catalog.repository.AssetStatusRepository;
import com.company.itam.common.enums.*;
import com.company.itam.common.exception.*;
import com.company.itam.common.util.MessageHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final AssetAuditService assetAudit;
    private final MessageHelper messages;
    private final ObjectMapper mapper;

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
            JdbcTemplate jdbc, AssetAuditService assetAudit, MessageHelper messages, ObjectMapper mapper) {
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
        this.assetAudit = assetAudit;
        this.messages = messages;
        this.mapper = mapper;
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
        throw new AppException(HttpStatus.CONFLICT, code, code);
    }

    // ========================================================================
    // SMART CHECK — 4 scenarios: A, B, C, D
    //
    // Scenario A (main): IT selects a DEVICE IN_USE → auto-discover components, OEM, Per-User
    // Scenario B: IT selects a Per-User LICENSE asset alone → BLOCKED with message
    // Scenario C: IT selects a COMPONENT alone → BLOCKED with message
    // Scenario D: IT selects an OEM LICENSE alone → BLOCKED with message (OEM is always auto-bundled)
    // ========================================================================

    public SmartCheckResponse smartCheck(SmartCheckRequest request) { return prepare(request, false).response(); }

    /** One discovery contract for preview and completion.  IDs are scalar until locks are held. */
    private Prepared prepare(SmartCheckRequest request, boolean lock) {
        if (request.assetIds().stream().anyMatch(id -> id == null || id < 1)
                || request.allocationIds() != null && request.allocationIds().stream().anyMatch(id -> id == null || id < 1)) fail("RECOVERY_DUPLICATE");
        var returner = users.findById(request.returnerUserId()).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
        if (returner.getAccountStatus() != AccountStatus.ACTIVE) fail("RECOVERY_RETURNER_INACTIVE");
        if (request.assetIds().isEmpty() && (request.allocationIds() == null || request.allocationIds().isEmpty())) fail("EMPTY_TRANSACTION");
        List<Long> idList = new ArrayList<>(new LinkedHashSet<>(request.assetIds())); // keep order, dedup
        if (request.allocationIds() != null) for (Long allocationId : request.allocationIds())
            idList.add(allocations.licenseId(allocationId).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND")));
        idList = new ArrayList<>(new LinkedHashSet<>(idList));
        if (new HashSet<>(request.assetIds()).size() != request.assetIds().size()) fail("RECOVERY_DUPLICATE");
        if (request.allocationIds()!=null && (request.allocationIds().stream().anyMatch(Objects::isNull) || new HashSet<>(request.allocationIds()).size()!=request.allocationIds().size())) fail("RECOVERY_DUPLICATE");
        var scalarLinks = links(idList);
        var lockIds = new TreeSet<>(idList);
        scalarLinks.forEach(l -> { lockIds.add(l.parent()); lockIds.add(l.child()); });
        if (lock) {
            for (Long id : lockIds) assets.lockById(id).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
            if (!scalarLinks.equals(links(idList))) fail("RECOVERY_CHANGED");
        }

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

        // Per-User licenses are selected by allocation, not by the package as a whole.
        for (var lic : selectedPerUserLic) {
            boolean selected = false;
            for (var alloc : allocations.findAllByLicenseAssetId(lic.getAssetId())) {
                if (request.allocationIds() != null && request.allocationIds().contains(alloc.getAllocationId())) {
                    selected = true;
                    if (!validPerUser(alloc, request.returnerUserId())) { blocked.add(blocked(lic, request.returnerUserId())); continue; }
                    optional.add(new SmartCheckResponse.OptionalAsset(
                            alloc.getAllocationId(), lic.getAssetId(), lic.getAssetTag(), lic.getName(),
                            alloc.getSeatCount(),
                            alloc.getUser() != null ? alloc.getUser().getUserId() : null,
                            alloc.getUser() != null ? alloc.getUser().getFullName() : null,
                            alloc.getDevice() == null ? null : alloc.getDevice().getAssetId(),
                            alloc.getDevice() == null ? null : alloc.getDevice().getAssetTag(),
                            true
                    ));
                }
            }
            if (!selected) fail("RECOVERY_CHANGED");
        }

        // ----------------------------------------------------------------
        // Components may be returned independently; only the component changes state.
        // ----------------------------------------------------------------
        for (var comp : selectedComponents) {
            if (!physicalValid(comp, request.returnerUserId())) blocked.add(blocked(comp, request.returnerUserId()));
            else {
                required.add(new SmartCheckResponse.RequiredAsset(comp.getAssetId(), comp.getAssetTag(), comp.getName(), categoryCode(comp), messages.getMessage("RECOVERY_COMPONENT_SELECTED")));
                componentDecisions.add(componentDecision(comp, !request.assetIds().contains(parentId(comp.getAssetId()))));
            }
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
                    ? messages.getMessage("RECOVERY_OEM_NOT_ALLOWED") + " (" + parentTag + ")"
                    : messages.getMessage("RECOVERY_OEM_NOT_ALLOWED");
            blocked.add(new SmartCheckResponse.BlockedAsset(
                    lic.getAssetId(), lic.getAssetTag(), lic.getName(),
                    categoryCode(lic), msg));
        }

        // ----------------------------------------------------------------
        // SCENARIO A: DEVICE(s) selected → main recovery flow
        // ----------------------------------------------------------------

        // Verify DEVICE status — only IN_USE can be recovered
        for (var dev : selectedDevices) {
            if (!physicalValid(dev, request.returnerUserId())) {
                blocked.add(blocked(dev, request.returnerUserId()));
            } else {
                required.add(new SmartCheckResponse.RequiredAsset(
                        dev.getAssetId(), dev.getAssetTag(), dev.getName(),
                        categoryCode(dev), messages.getMessage("RECOVERY_DEVICE_SELECTED")));
            }
        }

        // Discover relationships for selected devices
        // Components → IT chooses KEEP_ATTACHED / DETACH
        for (var dev : selectedDevices) {
            for (var rel : relationships.findByParentAssetAssetId(dev.getAssetId())) {
                if (rel.getRelationshipType() == RelationshipType.COMPONENT_OF) {
                    var child = rel.getChildAsset();
                    if (!physicalValid(child, request.returnerUserId())) { blocked.add(blocked(child, request.returnerUserId())); continue; }
                    if (selectedComponents.contains(child)) continue;
                    required.add(new SmartCheckResponse.RequiredAsset(child.getAssetId(), child.getAssetTag(), child.getName(), categoryCode(child), messages.getMessage("RECOVERY_COMPONENT_SELECTED")));
                    componentDecisions.add(componentDecision(child, false));
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
                        var allocation = rel.getAllocation();
                        if (allocation == null || allocation.getStatus() != LicenseAllocationStatus.ACTIVE || allocation.getUser() == null
                                || !allocation.getUser().getUserId().equals(request.returnerUserId()) || allocation.getDevice() == null
                                || !allocation.getDevice().getAssetId().equals(dev.getAssetId()) || !allocation.getLicense().getAssetId().equals(lic.getAssetId())) {
                            blocked.add(new SmartCheckResponse.BlockedAsset(lic.getAssetId(), lic.getAssetTag(), lic.getName(), categoryCode(lic), messages.getMessage("RECOVERY_OEM_BUNDLED")));
                            continue;
                        }
                        boolean alreadyBlocked = selectedOemLic.contains(lic);
                        if (!alreadyBlocked) {
                            required.add(new SmartCheckResponse.RequiredAsset(
                                    lic.getAssetId(), lic.getAssetTag(), lic.getName(),
                                    categoryCode(lic), messages.getMessage("RECOVERY_OEM_BUNDLED")));
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
                    if (alloc.getStatus() != LicenseAllocationStatus.ACTIVE) continue;
                    if (!validPerUser(alloc, request.returnerUserId())) { blocked.add(blocked(lic, request.returnerUserId())); continue; }
                    optional.add(new SmartCheckResponse.OptionalAsset(
                            alloc.getAllocationId(), lic.getAssetId(), lic.getAssetTag(), lic.getName(),
                            alloc.getSeatCount(),
                            alloc.getUser() != null ? alloc.getUser().getUserId() : null,
                            alloc.getUser() != null ? alloc.getUser().getFullName() : null,
                            dev.getAssetId(),
                            dev.getAssetTag(),
                            true
                    ));
                }
            }
        }

        // Build fingerprint
        String fingerprint = buildFingerprint(required, optional, blocked, componentDecisions, request);

        // Warnings summary
        if (!blocked.isEmpty()) {
            warnings.add(messages.getMessage("RECOVERY_WARNING_BLOCKED"));
        } else if (required.isEmpty() && optional.isEmpty()) {
            warnings.add(messages.getMessage("RECOVERY_WARNING_EMPTY"));
        } else {
            warnings.add(messages.getMessage("RECOVERY_WARNING_REVIEW"));
        }

        var uniqueRequired = new LinkedHashMap<Long, SmartCheckResponse.RequiredAsset>();
        for (var value : required) uniqueRequired.putIfAbsent(value.assetId(), value);
        required = new ArrayList<>(uniqueRequired.values());
        var uniqueOptional = new LinkedHashMap<Long, SmartCheckResponse.OptionalAsset>();
        for (var value : optional) uniqueOptional.putIfAbsent(value.allocationId(), value);
        optional = new ArrayList<>(uniqueOptional.values());
        return new Prepared(new SmartCheckResponse(required, optional, blocked, componentDecisions, warnings, fingerprint), List.copyOf(lockIds), scalarLinks);
    }

    // ========================================================================
    // COMPLETE
    // ========================================================================

    @Transactional
    public RecoveryResponse complete(RecoveryRequest request) {
        if (request.assetIds().isEmpty() && (request.allocationIds() == null || request.allocationIds().isEmpty())) fail("EMPTY_TRANSACTION");
        List<Long> idList = new ArrayList<>(new LinkedHashSet<>(request.assetIds()));
        if (request.allocationIds() != null) for (Long allocationId : request.allocationIds())
            idList.add(allocations.licenseId(allocationId).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND")));
        idList = new ArrayList<>(new LinkedHashSet<>(idList));
        if (new HashSet<>(request.assetIds()).size() != request.assetIds().size()) fail("RECOVERY_DUPLICATE");

        var returner = users.findById(request.returnerUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (returner.getAccountStatus() != AccountStatus.ACTIVE) fail("RECOVERY_RETURNER_INACTIVE");

        var location = locations.findById(request.receivingLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Location not found"));
        if (!Boolean.TRUE.equals(location.getIsActive())) fail("RECOVERY_LOCATION_INACTIVE");

        var scRequest = new SmartCheckRequest(request.assetIds(), request.returnerUserId(), request.allocationIds(), request.reason());
        var scResult = prepare(scRequest, true).response();

        // Block if any asset is blocked (extra safety — shouldn't happen if UI follows Smart Check)
        if (!scResult.blockedAssets().isEmpty()) fail("RECOVERY_BLOCKED_ASSETS");
        var previewAllocations = scResult.optionalAssets().stream().map(SmartCheckResponse.OptionalAsset::allocationId).collect(Collectors.toSet());
        var requestedAllocations = request.allocationIds() == null ? Set.<Long>of() : new HashSet<>(request.allocationIds());
        if (requestedAllocations.size() != (request.allocationIds() == null ? 0 : request.allocationIds().size()) || !previewAllocations.containsAll(requestedAllocations)) fail("RECOVERY_CHANGED");
        var perUserActions = request.perUserActions() == null ? Set.<Long>of() : request.perUserActions().keySet();
        if (!previewAllocations.equals(perUserActions)) fail("RECOVERY_CHANGED");
        if (request.perUserActions() != null && request.perUserActions().values().stream().anyMatch(Objects::isNull)) fail("RECOVERY_CHANGED");
        if (!requestedAllocations.stream().allMatch(id -> Boolean.TRUE.equals(request.perUserActions().get(id)))) fail("RECOVERY_CHANGED");
        if (request.componentActions() != null) for (var entry : request.componentActions().entrySet()) {
            if (scResult.componentDecisions().stream().noneMatch(d -> d.assetId().equals(entry.getKey())) || entry.getValue() == null
                    || scResult.componentDecisions().stream().filter(d -> d.assetId().equals(entry.getKey())).noneMatch(d -> d.options().stream().anyMatch(o -> o.action().equals(entry.getValue().componentAction())))) fail("RECOVERY_CHANGED");
        }
        var requiredComponents = scResult.componentDecisions().stream().map(SmartCheckResponse.ComponentDecision::assetId).collect(Collectors.toSet());
        if (!requiredComponents.equals(request.componentActions() == null ? Set.of() : request.componentActions().keySet())) fail("RECOVERY_CHANGED");

        String computedFingerprint = scResult.fingerprint();
        if (request.expectedFingerprint() == null || request.expectedFingerprint().isBlank() || !request.expectedFingerprint().equals(computedFingerprint)) {
            fail("RECOVERY_CHANGED");
        }

        var inStockStatus = statuses.findByCode(AssetStatus.IN_STOCK)
                .orElseThrow(() -> new ResourceNotFoundException("IN_STOCK status not found"));

        var actor = users.findByEmail(currentUser()).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
        var tx = new TransactionEntity();
        tx.setTransactionCode("RC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        tx.setType(TransactionType.RECOVERY);
        tx.setStatus(TransactionStatus.COMPLETED);
        tx.setRequester(actor);
        tx.setProcessedBy(actor);
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

        var physicalIds = new TreeSet<Long>();
        var packageIds = new TreeSet<Long>();
        var originalParents = new HashMap<Long, Long>();
        var beforeAssets = new HashMap<Long, Map<String, Object>>();
        var workAllocations = new TreeMap<Long, LicenseAllocationEntity>();
        for (Long id : idList) {
            var selected = asset(id);
            if (categoryOf(selected) != AssetCategory.LICENSE) physicalIds.add(id);
            if (categoryOf(selected) == AssetCategory.DEVICE) for (var rel : relationships.findByParentAssetAssetId(id)) {
                var child = rel.getChildAsset();
                if (rel.getRelationshipType() == RelationshipType.COMPONENT_OF) {
                    physicalIds.add(child.getAssetId()); originalParents.put(child.getAssetId(), id);
                }
                if (rel.getRelationshipType() == RelationshipType.INSTALLED_ON && rel.getAllocation() != null && LicenseCodes.OEM.equals(licenseCode(rel.getChildAsset())))
                    workAllocations.put(rel.getAllocation().getAllocationId(), rel.getAllocation());
            }
        }
        for (Long allocationId : previewAllocations)
            workAllocations.put(allocationId, allocations.findById(allocationId).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND")));
        for (var allocation : workAllocations.values()) packageIds.add(allocation.getLicense().getAssetId());
        for (Long id : physicalIds) {
            beforeAssets.put(id, assetAudit.snapshot(asset(id)));
            if (categoryOf(asset(id)) == AssetCategory.COMPONENT) relationships.findByChildAssetAssetId(id).stream()
                    .filter(rel -> rel.getRelationshipType() == RelationshipType.COMPONENT_OF).findFirst()
                    .ifPresent(rel -> originalParents.put(id, rel.getParentAsset().getAssetId()));
        }
        for (Long id : packageIds) beforeAssets.put(id, assetAudit.snapshot(asset(id)));

        for (Long id : physicalIds) {
            var recovered = asset(id);
            recovered.setStatus(inStockStatus); recovered.setAssignedTo(null); recovered.setLocation(location); assets.save(recovered);
            var action = request.componentActions() == null ? null : request.componentActions().get(id);
            if (action != null) {
                for (var rel : relationships.findByChildAssetAssetId(id)) if (rel.getRelationshipType() == RelationshipType.COMPONENT_OF) {
                    var linkBefore = new LinkedHashMap<String, Object>(); linkBefore.put("relationshipId", rel.getRelationshipId()); linkBefore.put("type", rel.getRelationshipType().name()); linkBefore.put("parentAssetId", rel.getParentAsset().getAssetId()); linkBefore.put("componentAssetId", id);
                    var linkAfter = new LinkedHashMap<String, Object>(); linkAfter.put("relationshipId", rel.getRelationshipId()); linkAfter.put("type", rel.getRelationshipType().name()); linkAfter.put("parentAssetId", "DETACH".equals(action.componentAction()) ? null : rel.getParentAsset().getAssetId()); linkAfter.put("componentAssetId", id);
                    var linkAudit = new AuditLogEntity(); linkAudit.setEntityType("ASSET"); linkAudit.setEntityId(id); linkAudit.setTransaction(tx); linkAudit.setAction(action.componentAction()); linkAudit.setActor(actor); linkAudit.setOldData(linkBefore); linkAudit.setNewData(linkAfter); auditLogs.save(linkAudit);
                    if ("DETACH".equals(action.componentAction())) relationships.delete(rel);
                }
            }
            var audit = new AuditLogEntity(); audit.setEntityType("ASSET"); audit.setEntityId(id); audit.setTransaction(tx); audit.setAction("RECOVERED"); audit.setActor(actor);
            audit.setOldData(beforeAssets.get(id)); audit.setNewData(assetAudit.snapshot(recovered)); auditLogs.save(audit);
        }

        var resultsByPackage = new TreeMap<Long, List<RecoveryResponse.AllocationResult>>();
        var decisionsByPackage = new TreeMap<Long, List<Map<String, Object>>>();
        for (var allocation : workAllocations.values()) {
            var license = allocation.getLicense();
            var before = new LinkedHashMap<String, Object>();
            before.put("allocationId", allocation.getAllocationId()); before.put("userId", allocation.getUser() == null ? null : allocation.getUser().getUserId());
            before.put("deviceId", allocation.getDevice() == null ? null : allocation.getDevice().getAssetId()); before.put("seats", allocation.getSeatCount()); before.put("status", allocation.getStatus().name());
            String assignment = licenseCode(license); Long relationshipId = relationships.findByAllocationAllocationId(allocation.getAllocationId()).map(AssetRelationshipEntity::getRelationshipId).orElse(null);
            before.put("relationshipId", relationshipId);
            String action;
            if (LicenseCodes.OEM.equals(assignment)) {
                allocation.setUser(null); allocation.setStatus(LicenseAllocationStatus.RESERVED); action = "RESERVED";
            } else {
                var relation = relationships.findByAllocationAllocationId(allocation.getAllocationId());
                relation.ifPresent(rel -> {
                    var linkBefore = new LinkedHashMap<String, Object>(); linkBefore.put("relationshipId", rel.getRelationshipId()); linkBefore.put("parentAssetId", rel.getParentAsset().getAssetId()); linkBefore.put("childAssetId", rel.getChildAsset().getAssetId()); linkBefore.put("allocationId", allocation.getAllocationId());
                    var linkAfter = new LinkedHashMap<String, Object>(); linkAfter.put("relationshipId", rel.getRelationshipId()); linkAfter.put("parentAssetId", null); linkAfter.put("childAssetId", rel.getChildAsset().getAssetId()); linkAfter.put("allocationId", allocation.getAllocationId());
                    var linkAudit = new AuditLogEntity(); linkAudit.setEntityType("ASSET"); linkAudit.setEntityId(license.getAssetId()); linkAudit.setTransaction(tx); linkAudit.setAction("UNLINK"); linkAudit.setActor(actor); linkAudit.setOldData(linkBefore); linkAudit.setNewData(linkAfter); auditLogs.save(linkAudit);
                    relationships.delete(rel);
                });
                boolean release = request.perUserActions() != null && Boolean.TRUE.equals(request.perUserActions().get(allocation.getAllocationId()));
                allocation.setDevice(null);
                if (release) { allocation.setStatus(LicenseAllocationStatus.RELEASED); allocation.setReleasedAt(Instant.now()); allocation.setRecoveryTransactionId(tx.getTransactionId()); action = "RELEASED"; }
                else action = "UNLINKED";
            }
            allocations.save(allocation);
            var after = new LinkedHashMap<String, Object>();
            after.put("allocationId", allocation.getAllocationId()); after.put("userId", allocation.getUser() == null ? null : allocation.getUser().getUserId());
            after.put("deviceId", allocation.getDevice() == null ? null : allocation.getDevice().getAssetId()); after.put("relationshipId", LicenseCodes.OEM.equals(assignment) ? relationshipId : null); after.put("seats", allocation.getSeatCount()); after.put("status", allocation.getStatus().name());
            var audit = new AuditLogEntity(); audit.setEntityType("ASSET"); audit.setEntityId(license.getAssetId()); audit.setTransaction(tx); audit.setAction(action); audit.setActor(actor); audit.setOldData(before); audit.setNewData(after); auditLogs.save(audit);
            resultsByPackage.computeIfAbsent(license.getAssetId(), ignored -> new ArrayList<>()).add(new RecoveryResponse.AllocationResult(allocation.getAllocationId(), allocation.getSeatCount(), action, assignment));
            var decision = new LinkedHashMap<String, Object>(); decision.put("action", action); decision.put("before", before); decision.put("after", after);
            decisionsByPackage.computeIfAbsent(license.getAssetId(), ignored -> new ArrayList<>()).add(decision);
        }
        allocations.flush();

        List<RecoveryResponse.RecoveryLine> lines = new ArrayList<>();
        int lineNumber = 0;
        for (Long id : physicalIds) {
            var recovered = asset(id); var txAsset = new TransactionAssetEntity(); txAsset.setTransaction(tx); txAsset.setAsset(recovered); txAsset.setLineNumber(++lineNumber); txAssets.save(txAsset);
            var details = new LinkedHashMap<>(beforeAssets.get(id)); details.put("actorName", actor.getFullName()); details.put("actorUserId", actor.getUserId());
            if (request.componentActions() != null && request.componentActions().containsKey(id)) details.put("componentAction", request.componentActions().get(id).componentAction());
            lines.add(new RecoveryResponse.RecoveryLine(id, recovered.getAssetTag(), recovered.getName(), categoryCode(recovered), originalParents.get(id), 0, List.of(), details));
        }
        for (Long id : packageIds) {
            var license = asset(id); var txAsset = new TransactionAssetEntity(); txAsset.setTransaction(tx); txAsset.setAsset(license); txAsset.setLineNumber(++lineNumber); txAssets.save(txAsset);
            var allocationResults = resultsByPackage.getOrDefault(id, List.of()); var details = new LinkedHashMap<>(beforeAssets.get(id)); details.put("actorName", actor.getFullName()); details.put("actorUserId", actor.getUserId()); details.put("allocationDecisions", decisionsByPackage.getOrDefault(id, List.of()));
            lines.add(new RecoveryResponse.RecoveryLine(id, license.getAssetTag(), license.getName(), categoryCode(license), null,
                    allocationResults.stream().filter(result -> !"UNLINKED".equals(result.action())).mapToInt(RecoveryResponse.AllocationResult::seats).sum(), allocationResults, details));
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

        var response = new RecoveryResponse(
                tx.getTransactionId(), tx.getTransactionCode(), tx.getCompletedAt(),
                returner.getUserId(), returner.getFullName(), returner.getEmail(),
                location.getLocationId(), location.getName(),
                request.recoveryDate(), request.reason(),
                computedFingerprint, lines);
        try {
            jdbc.update("INSERT INTO transaction_publication_snapshots(transaction_id,snapshot) VALUES (?,CAST(? AS jsonb))",
                    tx.getTransactionId(), mapper.writeValueAsString(response));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Cannot encode recovery snapshot", e); }
        return response;
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private record Link(Long id, Long parent, Long child, Long allocation, String type) {}
    private record Prepared(SmartCheckResponse response, List<Long> lockedIds, List<Link> links) {}
    private List<Link> links(Collection<Long> ids) {
        var result = new TreeMap<Long, Link>();
        for (Long id : ids) jdbc.query("SELECT relationship_id,parent_asset_id,child_asset_id,allocation_id,relationship_type FROM asset_relationships WHERE parent_asset_id=? OR child_asset_id=? ORDER BY relationship_id",
                (rs, n) -> new Link(rs.getLong(1), rs.getLong(2), rs.getLong(3), (Long) rs.getObject(4), rs.getString(5)), id, id).forEach(link -> result.put(link.id(), link));
        return new ArrayList<>(result.values());
    }

    private boolean physicalValid(AssetEntity asset, Long returnerId) {
        return asset.getStatus().getCode() == AssetStatus.IN_USE && asset.getAssignedTo() != null && returnerId.equals(asset.getAssignedTo().getUserId());
    }
    private boolean validPerUser(LicenseAllocationEntity allocation, Long returnerId) {
        if (allocation.getStatus() != LicenseAllocationStatus.ACTIVE || allocation.getUser() == null
                || !returnerId.equals(allocation.getUser().getUserId()) || !LicenseCodes.PER_USER.equals(licenseCode(allocation.getLicense()))) return false;
        var relation = relationships.findByAllocationAllocationId(allocation.getAllocationId()).orElse(null);
        if (allocation.getDevice() == null) return relation == null;
        return physicalValid(allocation.getDevice(), returnerId) && relation != null && relation.getRelationshipType() == RelationshipType.INSTALLED_ON
                && relation.getParentAsset().getAssetId().equals(allocation.getDevice().getAssetId()) && relation.getChildAsset().getAssetId().equals(allocation.getLicense().getAssetId());
    }
    private SmartCheckResponse.BlockedAsset blocked(AssetEntity asset, Long returnerId) {
        return new SmartCheckResponse.BlockedAsset(asset.getAssetId(), asset.getAssetTag(), asset.getName(), categoryCode(asset), messages.getMessage("RECOVERY_DEVICE_INVALID"));
    }
    private SmartCheckResponse.ComponentDecision componentDecision(AssetEntity component, boolean standalone) {
        if (standalone) return new SmartCheckResponse.ComponentDecision(component.getAssetId(), component.getAssetTag(), component.getName(), List.of(
                new SmartCheckResponse.DecisionOption("DETACH", messages.getMessage("RECOVERY_DETACH"), messages.getMessage("RECOVERY_DETACH_DESCRIPTION"))), "DETACH");
        return new SmartCheckResponse.ComponentDecision(component.getAssetId(), component.getAssetTag(), component.getName(), List.of(
                new SmartCheckResponse.DecisionOption("KEEP_ATTACHED", messages.getMessage("RECOVERY_KEEP"), messages.getMessage("RECOVERY_KEEP_DESCRIPTION")),
                new SmartCheckResponse.DecisionOption("DETACH", messages.getMessage("RECOVERY_DETACH"), messages.getMessage("RECOVERY_DETACH_DESCRIPTION"))), "KEEP_ATTACHED");
    }

    private Long parentId(Long assetId) {
        return relationships.findByChildAssetAssetId(assetId).stream()
                .filter(r -> r.getRelationshipType() == RelationshipType.COMPONENT_OF)
                .map(r -> r.getParentAsset().getAssetId()).findFirst().orElse(null);
    }

    private String buildFingerprint(List<SmartCheckResponse.RequiredAsset> required,
                                   List<SmartCheckResponse.OptionalAsset> optional,
                                   List<SmartCheckResponse.BlockedAsset> blocked,
                                   List<SmartCheckResponse.ComponentDecision> decisions, SmartCheckRequest request) {
        try {
            var ids = new TreeSet<Long>(); ids.addAll(request.assetIds());
            required.forEach(item -> ids.add(item.assetId())); optional.forEach(item -> ids.add(item.assetId()));
            var data = new StringBuilder("returner=").append(request.returnerUserId()).append("|assets=").append(new TreeSet<>(request.assetIds()))
                    .append("|allocations=").append(request.allocationIds() == null ? List.of() : new TreeSet<>(request.allocationIds()));
            for (Long id : ids) { var a = asset(id); data.append("|a:").append(id).append(':').append(a.getVersion()).append(':').append(a.getStatus().getCode()).append(':')
                    .append(a.getAssignedTo() == null ? null : a.getAssignedTo().getUserId()).append(':').append(a.getType().getTypeId()); }
            var allocationIds = new TreeSet<Long>(); if (request.allocationIds() != null) allocationIds.addAll(request.allocationIds()); optional.forEach(item -> allocationIds.add(item.allocationId()));
            for (var link : links(ids)) { data.append("|r:").append(link.id()).append(':').append(link.parent()).append(':').append(link.child()).append(':').append(link.allocation()).append(':').append(link.type()); if (link.allocation() != null) allocationIds.add(link.allocation()); }
            for (Long id : allocationIds) { var a = allocations.findById(id).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND")); data.append("|l:").append(id).append(':').append(a.getLicense().getAssetId()).append(':').append(a.getStatus()).append(':')
                    .append(a.getDevice() == null ? null : a.getDevice().getAssetId()).append(':').append(a.getUser() == null ? null : a.getUser().getUserId()).append(':').append(a.getSeatCount()).append(':').append(a.getReleasedAt()); }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
