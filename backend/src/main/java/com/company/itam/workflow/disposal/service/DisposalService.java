package com.company.itam.workflow.disposal.service;

import com.company.itam.asset.entity.AssetEntity;
import com.company.itam.asset.entity.LicenseAllocationEntity;
import com.company.itam.asset.entity.LicenseAllocationStatus;
import com.company.itam.asset.relationship.entity.AssetRelationshipEntity;
import com.company.itam.asset.relationship.enums.RelationshipType;
import com.company.itam.asset.relationship.repository.AssetRelationshipRepository;
import com.company.itam.asset.repository.AssetRepository;
import com.company.itam.asset.repository.LicenseAllocationRepository;
import com.company.itam.asset.service.LicenseCodes;
import com.company.itam.audit.entity.AuditLogEntity;
import com.company.itam.audit.repository.AuditLogRepository;
import com.company.itam.catalog.repository.AssetStatusRepository;
import com.company.itam.common.enums.AssetCategory;
import com.company.itam.common.enums.AssetStatus;
import com.company.itam.common.exception.AppException;
import com.company.itam.common.exception.ResourceNotFoundException;
import com.company.itam.common.pagination.PageResponse;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.user.repository.UserRepository;
import com.company.itam.workflow.core.entity.TransactionAssetEntity;
import com.company.itam.workflow.core.entity.TransactionEntity;
import com.company.itam.workflow.core.enums.TransactionStatus;
import com.company.itam.workflow.core.enums.TransactionType;
import com.company.itam.workflow.core.repository.TransactionAssetRepository;
import com.company.itam.workflow.core.repository.TransactionRepository;
import com.company.itam.workflow.disposal.dto.DisposalModels.*;
import com.company.itam.workflow.disposal.entity.TransactionDisposalDetailEntity;
import com.company.itam.workflow.disposal.repository.TransactionDisposalDetailRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
public class DisposalService {
    private final AssetRepository assets;
    private final AssetRelationshipRepository relationships;
    private final LicenseAllocationRepository allocations;
    private final TransactionRepository transactions;
    private final TransactionAssetRepository lines;
    private final TransactionDisposalDetailRepository details;
    private final AssetStatusRepository statuses;
    private final UserRepository users;
    private final AuditLogRepository audit;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final int minimumAgeYears;

    public DisposalService(AssetRepository assets, AssetRelationshipRepository relationships,
            LicenseAllocationRepository allocations, TransactionRepository transactions,
            TransactionAssetRepository lines, TransactionDisposalDetailRepository details,
            AssetStatusRepository statuses, UserRepository users, AuditLogRepository audit,
            JdbcTemplate jdbc, ObjectMapper mapper,
            @Value("${itam.disposal.minimum-age-years:5}") int minimumAgeYears) {
        this.assets = assets; this.relationships = relationships; this.allocations = allocations;
        this.transactions = transactions; this.lines = lines; this.details = details;
        this.statuses = statuses; this.users = users; this.audit = audit;
        this.jdbc = jdbc; this.mapper = mapper; this.minimumAgeYears = minimumAgeYears;
    }

    public PageResponse<AssetLine> candidates(String keyword, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || keyword.length() > 100) bad("TRANSACTION_REQUEST_INVALID");
        String search = keyword.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        LocalDate cutoff = LocalDate.now().minusYears(minimumAgeYears);
        return PageResponse.of(assets.findAll((root, query, b) -> b.and(
                b.or(b.equal(root.get("status").get("code"), AssetStatus.DAMAGED),
                        b.and(b.equal(root.get("status").get("code"), AssetStatus.IN_STOCK), b.lessThanOrEqualTo(root.get("purchaseDate"), cutoff))),
                b.or(b.like(b.lower(root.get("assetTag")), "%" + search + "%", '\\'),
                        b.like(b.lower(root.get("name")), "%" + search + "%", '\\'))),
                PageRequest.of(page, size, Sort.by("assetTag"))).map(a -> line(a, false)));
    }

    public SmartCheckResponse smartCheck(SmartCheckRequest request) {
        return check(prepareSelection(request.assetIds(), false));
    }

    public DisposalResponse get(Long id) {
        TransactionEntity tx = disposal(id);
        if (tx.getStatus() == TransactionStatus.COMPLETED) {
            String snapshot = jdbc.queryForList("SELECT snapshot::text FROM transaction_publication_snapshots WHERE transaction_id=?",
                    String.class, id).stream().findFirst().orElseThrow(() ->
                    new AppException(HttpStatus.CONFLICT, "PUBLICATION_SNAPSHOT_MISSING", "PUBLICATION_SNAPSHOT_MISSING"));
            try { return mapper.readValue(snapshot, DisposalResponse.class); }
            catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("Invalid disposal publication snapshot", ex);
            }
        }
        var detail = details.findById(id).orElseThrow(() -> new ResourceNotFoundException("DISPOSAL_NOT_FOUND"));
        return response(tx, detail, prepareStored(lines.findByTransactionTransactionId(id), false));
    }

    @Transactional
    public DisposalResponse create(CreateRequest request) {
        Prepared checked = prepareSelection(request.assetIds(), true);
        if (!fingerprint(null, null, checked).equals(request.expectedFingerprint())) conflict("DISPOSAL_CHANGED");
        UserEntity actor = actor();
        var tx = new TransactionEntity();
        tx.setTransactionCode("DI-" + UUID.randomUUID()); tx.setType(TransactionType.DISPOSAL);
        tx.setStatus(TransactionStatus.PENDING); tx.setRequester(actor); tx.setNotes(request.reason());
        transactions.saveAndFlush(tx);
        var detail = new TransactionDisposalDetailEntity(); detail.setTransaction(tx);
        detail.setReason(request.reason().trim()); detail.setDisposalDate(request.disposalDate()); details.save(detail);
        int number = 0;
        for (AssetLine selected : checked.assetLines()) {
            var item = new TransactionAssetEntity(); item.setTransaction(tx);
            item.setAsset(assets.getReferenceById(selected.assetId())); item.setLineNumber(++number);
            item.setNotes(selected.autoAdded() ? "AUTO_ADDED" : null); lines.save(item);
        }
        recordTransaction(tx, actor, "DISPOSAL_CREATED", Map.of("status", "PENDING",
                "assetIds", checked.assetLines().stream().map(AssetLine::assetId).toList()));
        return response(tx, detail, checked);
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
    public DisposalResponse resolvePerUser(Long id, PerUserRequest request) {
        TransactionEntity tx = pending(id);
        var detail = details.findById(id).orElseThrow(() -> new ResourceNotFoundException("DISPOSAL_NOT_FOUND"));
        var stored = lines.findByTransactionTransactionId(id);
        Prepared current = prepareStored(stored, true);
        if (!fingerprint(tx, detail, current).equals(request.expectedFingerprint())) conflict("DISPOSAL_CHANGED");

        var decisions = new TreeMap<Long, Boolean>();
        for (PerUserDecision decision : request.decisions()) {
            if (decisions.put(decision.allocationId(), decision.release()) != null) conflict("DISPOSAL_CHANGED");
        }
        var expected = new TreeSet<>(current.perUserLinks().stream().map(PerUserLink::allocationId).toList());
        if (!expected.equals(decisions.keySet())) conflict("DISPOSAL_CHANGED");

        UserEntity actor = actor();
        current.links().stream().filter(link -> LicenseCodes.PER_USER.equals(link.assignment()))
                .sorted(Comparator.comparing(link -> link.allocation().getAllocationId())).forEach(link -> {
                    LicenseAllocationEntity allocation = link.allocation();
                    if (allocation.getStatus() != LicenseAllocationStatus.ACTIVE || allocation.getDevice() == null
                            || allocation.getUser() == null || !decisions.containsKey(allocation.getAllocationId())) {
                        conflict("DISPOSAL_CHANGED");
                    }
                    var before = allocationData(allocation, link.relationship());
                    relationships.delete(link.relationship());
                    allocation.setDevice(null);
                    boolean release = decisions.get(allocation.getAllocationId());
                    if (release) {
                        allocation.setStatus(LicenseAllocationStatus.RELEASED);
                        allocation.setReleasedAt(Instant.now());
                    }
                    allocations.save(allocation);
                    recordAllocation(tx, allocation, actor,
                            release ? "DISPOSAL_PER_USER_RELEASED" : "DISPOSAL_PER_USER_UNLINKED",
                            before, allocationData(allocation, null));
                });
        relationships.flush(); allocations.flush(); audit.flush();
        recordTransaction(tx, actor, "DISPOSAL_PER_USER_RESOLVED",
                Map.of("allocationIds", new ArrayList<>(decisions.keySet())));
        return response(tx, detail, prepareStored(stored, false));
    }

    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    public DisposalResponse approve(Long id, ApproveRequest request) {
        TransactionEntity tx = pending(id);
        var detail = details.findById(id).orElseThrow(() -> new ResourceNotFoundException("DISPOSAL_NOT_FOUND"));
        var stored = lines.findByTransactionTransactionId(id);
        if (stored.isEmpty()) conflict("EMPTY_TRANSACTION");
        Prepared current = prepareStored(stored, true);
        if (!fingerprint(tx, detail, current).equals(request.expectedFingerprint())) conflict("DISPOSAL_CHANGED");
        validateStoredExpansion(stored, current);
        if (!current.perUserLinks().isEmpty()) conflict("DISPOSAL_PER_USER_LINKED");

        UserEntity actor = actor();
        var retired = statuses.findByCode(AssetStatus.RETIRED).orElseThrow();
        for (AssetEntity asset : current.assets()) {
            if (asset.getStatus().getCode() == AssetStatus.RETIRED) conflict("DISPOSAL_ASSET_NOT_ELIGIBLE");
            String before = asset.getStatus().getCode().name();
            asset.setStatus(retired); asset.setAssignedTo(null); asset.setUpdatedBy(actor);
            recordAsset(tx, asset, actor, before);
        }
        current.links().stream().filter(link -> LicenseCodes.OEM.equals(link.assignment()))
                .sorted(Comparator.comparing(link -> link.allocation().getAllocationId())).forEach(link -> {
                    var allocation = link.allocation();
                    if (allocation.getStatus() == LicenseAllocationStatus.RELEASED) conflict("DISPOSAL_CHANGED");
                    var before = allocationData(allocation, link.relationship());
                    allocation.setUser(null); allocation.setStatus(LicenseAllocationStatus.RESERVED);
                    allocations.save(allocation);
                    recordAllocation(tx, allocation, actor, "DISPOSAL_OEM_RETAINED", before,
                            allocationData(allocation, link.relationship()));
                });
        allocations.flush(); assets.flush(); audit.flush();

        Instant now = Instant.now(); tx.setStatus(TransactionStatus.COMPLETED); tx.setProcessedBy(actor);
        tx.setProcessedAt(now); tx.setCompletedAt(now); tx.setUpdatedAt(now);
        recordTransaction(tx, actor, "DISPOSAL_COMPLETED", Map.of("status", "COMPLETED"));
        transactions.flush(); assets.flush(); audit.flush();
        DisposalResponse result = response(tx, detail, prepareStored(stored, false));
        savePublicationSnapshot(tx, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    public DisposalResponse reject(Long id, RejectRequest request) {
        TransactionEntity tx = pending(id); UserEntity actor = actor(); Instant now = Instant.now();
        tx.setStatus(TransactionStatus.REJECTED); tx.setProcessedBy(actor); tx.setProcessedAt(now);
        tx.setUpdatedAt(now); tx.setRejectionReason(request.reason().trim());
        recordTransaction(tx, actor, "DISPOSAL_REJECTED", Map.of("status", "REJECTED", "reason", request.reason().trim()));
        var detail = details.findById(id).orElseThrow();
        return response(tx, detail, prepareStored(lines.findByTransactionTransactionId(id), false));
    }

    private Prepared prepareSelection(List<Long> selectedIds, boolean lock) {
        var selected = new TreeSet<>(selectedIds);
        if (selected.isEmpty()) conflict("EMPTY_TRANSACTION");
        if (selected.size() != selectedIds.size()) conflict("DISPOSAL_DUPLICATE");
        List<ScalarLink> discovered = scalarLinks(selected);
        SortedMap<Long, Boolean> targets = selectionTargets(selected, discovered);
        if (lock) {
            discovered = lockTopology(selected, targets, discovered);
            targets = selectionTargets(selected, discovered);
        }
        Prepared prepared = buildPrepared(targets, discovered);
        for (AssetLine line : prepared.assetLines()) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM transaction_assets ta JOIN transactions t USING(transaction_id)
                    WHERE ta.asset_id=? AND t.type='DISPOSAL' AND t.status='PENDING')
                    """, Boolean.class, line.assetId()))) conflict("DISPOSAL_ALREADY_PENDING");
        }
        return prepared;
    }

    private SortedMap<Long, Boolean> selectionTargets(Collection<Long> selectedIds, List<ScalarLink> links) {
        var targets = new TreeMap<Long, Boolean>();
        for (Long id : selectedIds) {
            AssetState asset = assetState(id); eligible(asset);
            if (AssetCategory.LICENSE.name().equals(asset.category()) && usedSeats(id) > 0) {
                conflict("DISPOSAL_LICENSE_ALLOCATED");
            }
            targets.put(id, false);
            for (var relation : links) {
                if (relation.parent().equals(id) && RelationshipType.COMPONENT_OF.name().equals(relation.type())
                        && !AssetStatus.RETIRED.name().equals(assetState(relation.child()).status())) {
                    targets.merge(relation.child(), true,
                            (oldValue, newValue) -> oldValue && newValue);
                }
            }
        }
        return targets;
    }

    private Prepared prepareStored(List<TransactionAssetEntity> stored, boolean lock) {
        var targets = new TreeMap<Long, Boolean>();
        for (var item : stored) {
            if (!legacyInformationalOem(item)) {
                targets.put(item.getAsset().getAssetId(), "AUTO_ADDED".equals(item.getNotes()));
            }
        }
        var parentIds = new TreeSet<>(targets.keySet());
        List<ScalarLink> discovered = scalarLinks(parentIds);
        if (lock) discovered = lockTopology(parentIds, targets, discovered);
        return buildPrepared(targets, discovered);
    }

    private Prepared buildPrepared(SortedMap<Long, Boolean> targets, List<ScalarLink> expectedLinks) {
        var targetAssets = new ArrayList<AssetEntity>();
        var assetLines = new ArrayList<AssetLine>();
        for (var entry : targets.entrySet()) {
            AssetEntity asset = asset(entry.getKey());
            targetAssets.add(asset); assetLines.add(line(asset, entry.getValue()));
        }

        var links = new ArrayList<LinkState>();
        for (AssetEntity parent : targetAssets) {
            for (var relation : relationships.findByParentAssetAssetId(parent.getAssetId())) {
                String assignment = assignment(relation);
                LicenseAllocationEntity allocation = relation.getAllocation();
                if (relation.getRelationshipType() == RelationshipType.INSTALLED_ON
                        && (assignment == null || allocation == null || allocation.getDevice() == null
                        || !Objects.equals(allocation.getDevice().getAssetId(), parent.getAssetId())
                        || !Objects.equals(allocation.getLicense().getAssetId(), relation.getChildAsset().getAssetId()))) {
                    conflict("DISPOSAL_CHANGED");
                }
                links.add(new LinkState(relation, allocation, assignment));
            }
        }
        links.sort(Comparator.comparing(link -> link.relationship().getRelationshipId()));
        if (!expectedLinks.equals(links.stream().map(this::scalar).toList())) conflict("DISPOSAL_CHANGED");

        var oem = links.stream().filter(link -> LicenseCodes.OEM.equals(link.assignment()))
                .map(link -> new OemAllocation(link.allocation().getAllocationId(),
                        link.relationship().getChildAsset().getAssetId(), link.relationship().getChildAsset().getAssetTag(),
                        link.relationship().getParentAsset().getAssetId(), link.relationship().getParentAsset().getAssetTag(),
                        link.allocation().getSeatCount())).sorted(Comparator.comparing(OemAllocation::allocationId)).toList();
        var perUser = links.stream().filter(link -> LicenseCodes.PER_USER.equals(link.assignment()))
                .map(link -> new PerUserLink(link.allocation().getAllocationId(),
                        link.relationship().getChildAsset().getAssetId(), link.relationship().getChildAsset().getAssetTag(),
                        link.relationship().getParentAsset().getAssetId(), link.relationship().getParentAsset().getAssetTag(),
                        link.allocation().getUser() == null ? null : link.allocation().getUser().getFullName(),
                        link.allocation().getSeatCount())).sorted(Comparator.comparing(PerUserLink::allocationId)).toList();
        var warnings = perUser.stream().map(link -> "PER_USER_LINKED:" + link.assetTag()).toList();
        return new Prepared(targetAssets, assetLines, oem, perUser, warnings, links, expectedLinks);
    }

    private void validateStoredExpansion(List<TransactionAssetEntity> stored, Prepared current) {
        var originalIds = stored.stream().filter(item -> !"AUTO_ADDED".equals(item.getNotes()))
                .map(item -> item.getAsset().getAssetId()).toList();
        var expected = new TreeSet<>(selectionTargets(originalIds, scalarLinks(originalIds)).keySet());
        var actual = current.assetLines().stream().map(AssetLine::assetId)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        if (!expected.equals(actual)) conflict("DISPOSAL_CHANGED");
    }

    private List<ScalarLink> lockTopology(Collection<Long> parentIds, SortedMap<Long, Boolean> targets,
            List<ScalarLink> discovered) {
        var ids = new TreeSet<Long>();
        ids.addAll(targets.keySet());
        discovered.forEach(link -> {
            ids.add(link.parent()); ids.add(link.child());
        });
        ids.forEach(id -> assets.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND")));
        List<ScalarLink> locked = scalarLinks(parentIds);
        if (!discovered.equals(locked)) conflict("DISPOSAL_CHANGED");
        var allocationIds = locked.stream().map(ScalarLink::allocation).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        if (!allocationIds.isEmpty() && allocations.lockAllById(allocationIds).size() != allocationIds.size()) {
            conflict("DISPOSAL_CHANGED");
        }
        List<ScalarLink> verified = scalarLinks(parentIds);
        if (!locked.equals(verified)) conflict("DISPOSAL_CHANGED");
        return verified;
    }

    private boolean legacyInformationalOem(TransactionAssetEntity item) {
        return "AUTO_ADDED".equals(item.getNotes())
                && LicenseCodes.OEM.equals(assetState(item.getAsset().getAssetId()).assignment());
    }

    private AssetState assetState(Long id) {
        return jdbc.query("""
                SELECT a.asset_id,s.code,c.code,a.purchase_date,a.version,a.assigned_to,lat.code
                FROM assets a JOIN asset_statuses s USING(status_id)
                JOIN asset_types t USING(type_id) JOIN asset_categories c USING(category_id)
                LEFT JOIN asset_license_details ld USING(asset_id)
                LEFT JOIN license_assignment_types lat USING(license_assignment_type_id)
                WHERE a.asset_id=?
                """, (rs, n) -> new AssetState(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getObject(4, LocalDate.class), rs.getLong(5), (Long) rs.getObject(6), rs.getString(7)), id)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
    }

    private List<ScalarLink> scalarLinks(Collection<Long> parentIds) {
        var result = new TreeMap<Long, ScalarLink>();
        for (Long parentId : new TreeSet<>(parentIds)) {
            jdbc.query("""
                    SELECT r.relationship_id,r.parent_asset_id,r.child_asset_id,r.allocation_id,
                           r.relationship_type,lat.code,la.status,la.device_asset_id,la.user_id,la.seat_count
                    FROM asset_relationships r
                    LEFT JOIN license_allocations la USING(allocation_id)
                    LEFT JOIN asset_license_details ld ON ld.asset_id=r.child_asset_id
                    LEFT JOIN license_assignment_types lat USING(license_assignment_type_id)
                    WHERE r.parent_asset_id=? ORDER BY r.relationship_id
                    """, (rs, n) -> new ScalarLink(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                    (Long) rs.getObject(4), rs.getString(5), rs.getString(6), rs.getString(7),
                    (Long) rs.getObject(8), (Long) rs.getObject(9), (Integer) rs.getObject(10)), parentId)
                    .forEach(link -> result.put(link.id(), link));
        }
        return new ArrayList<>(result.values());
    }

    private ScalarLink scalar(LinkState link) {
        var relation = link.relationship(); var allocation = link.allocation();
        return new ScalarLink(relation.getRelationshipId(), relation.getParentAsset().getAssetId(),
                relation.getChildAsset().getAssetId(), allocation == null ? null : allocation.getAllocationId(),
                relation.getRelationshipType().name(), link.assignment(),
                allocation == null ? null : allocation.getStatus().name(),
                allocation == null || allocation.getDevice() == null ? null : allocation.getDevice().getAssetId(),
                allocation == null || allocation.getUser() == null ? null : allocation.getUser().getUserId(),
                allocation == null ? null : allocation.getSeatCount());
    }

    private long usedSeats(Long licenseId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(seat_count),0) FROM license_allocations WHERE license_asset_id=? AND status<>'RELEASED'",
                Long.class, licenseId);
    }

    private TransactionEntity pending(Long id) {
        var tx = transactions.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("DISPOSAL_NOT_FOUND"));
        if (tx.getType() != TransactionType.DISPOSAL || tx.getStatus() != TransactionStatus.PENDING) {
            conflict("TRANSACTION_ALREADY_FINISHED");
        }
        return tx;
    }

    private TransactionEntity disposal(Long id) {
        var tx = transactions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DISPOSAL_NOT_FOUND"));
        if (tx.getType() != TransactionType.DISPOSAL) throw new ResourceNotFoundException("DISPOSAL_NOT_FOUND");
        return tx;
    }

    private AssetEntity asset(Long id) {
        return assets.findById(id).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
    }

    private void eligible(AssetState asset) {
        boolean oldStock = AssetStatus.IN_STOCK.name().equals(asset.status()) && asset.purchaseDate() != null
                && !asset.purchaseDate().isAfter(LocalDate.now().minusYears(minimumAgeYears));
        if (!AssetStatus.DAMAGED.name().equals(asset.status()) && !oldStock) {
            conflict("DISPOSAL_ASSET_NOT_ELIGIBLE");
        }
    }

    private AssetCategory category(AssetEntity asset) {
        return asset.getType().getCategory().getCode();
    }

    private String assignment(AssetRelationshipEntity relation) {
        return relation.getRelationshipType() == RelationshipType.INSTALLED_ON
                ? licenseCode(relation.getChildAsset()) : null;
    }

    private String licenseCode(AssetEntity asset) {
        return asset.getLicenseDetails() == null || asset.getLicenseDetails().getAssignmentType() == null
                ? null : asset.getLicenseDetails().getAssignmentType().getCode();
    }

    private AssetLine line(AssetEntity asset, boolean autoAdded) {
        return new AssetLine(asset.getAssetId(), asset.getAssetTag(), asset.getName(), category(asset).name(),
                asset.getHardwareDetails() == null ? null : asset.getHardwareDetails().getSerialNumber(), autoAdded);
    }

    private SmartCheckResponse check(Prepared prepared) {
        return new SmartCheckResponse(prepared.assetLines(), prepared.oemAllocations(), prepared.perUserLinks(),
                prepared.warnings(), fingerprint(null, null, prepared));
    }

    private DisposalResponse response(TransactionEntity tx, TransactionDisposalDetailEntity detail, Prepared prepared) {
        UserEntity displayActor = tx.getProcessedBy() == null ? tx.getRequester() : tx.getProcessedBy();
        return new DisposalResponse(tx.getTransactionId(), tx.getTransactionCode(), tx.getStatus().name(), detail.getReason(),
                detail.getDisposalDate(), tx.getCreatedAt(), displayActor.getFullName(), prepared.assetLines(),
                prepared.oemAllocations(), prepared.perUserLinks(), prepared.warnings(), fingerprint(tx, detail, prepared));
    }

    private String fingerprint(TransactionEntity tx, TransactionDisposalDetailEntity detail, Prepared prepared) {
        try {
            var state = new LinkedHashMap<String, Object>();
            if (tx != null) {
                state.put("transactionId", tx.getTransactionId()); state.put("status", tx.getStatus().name());
                state.put("contentVersion", tx.getContentVersion()); state.put("reason", detail.getReason());
                state.put("disposalDate", detail.getDisposalDate());
            }
            var assetStates = new ArrayList<Map<String, Object>>();
            for (AssetEntity asset : prepared.assets()) {
                AssetState scalar = assetState(asset.getAssetId());
                var value = new LinkedHashMap<String, Object>();
                value.put("assetId", scalar.id()); value.put("status", scalar.status());
                value.put("version", scalar.version()); value.put("assignedTo", scalar.assignedTo());
                assetStates.add(value);
            }
            state.put("assets", assetStates);
            state.put("assetLines", prepared.assetLines());
            state.put("oemAllocations", prepared.oemAllocations());
            state.put("perUserLinks", prepared.perUserLinks());
            state.put("links", prepared.scalarLinks());
            byte[] source = mapper.writeValueAsString(state).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        } catch (java.security.NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Map<String, Object> allocationData(LicenseAllocationEntity allocation,
            AssetRelationshipEntity relationship) {
        var data = new LinkedHashMap<String, Object>();
        data.put("allocationId", allocation.getAllocationId());
        data.put("licenseAssetId", allocation.getLicense().getAssetId());
        data.put("assetTag", allocation.getLicense().getAssetTag());
        data.put("userId", allocation.getUser() == null ? null : allocation.getUser().getUserId());
        data.put("userName", allocation.getUser() == null ? null : allocation.getUser().getFullName());
        data.put("deviceId", allocation.getDevice() == null ? null : allocation.getDevice().getAssetId());
        data.put("deviceTag", allocation.getDevice() == null ? null : allocation.getDevice().getAssetTag());
        data.put("relationshipId", relationship == null ? null : relationship.getRelationshipId());
        data.put("seats", allocation.getSeatCount()); data.put("status", allocation.getStatus().name());
        return data;
    }

    private void savePublicationSnapshot(TransactionEntity tx, DisposalResponse response) {
        ObjectNode snapshot = mapper.valueToTree(response);
        var decisions = snapshot.putArray("perUserDecisions");
        jdbc.query("""
                SELECT action,old_data::text,new_data::text FROM audit_logs
                WHERE transaction_id=? AND action IN ('DISPOSAL_PER_USER_RELEASED','DISPOSAL_PER_USER_UNLINKED')
                ORDER BY audit_log_id
                """, rs -> {
            var decision = decisions.addObject();
            decision.put("action", rs.getString(1).endsWith("RELEASED") ? "RELEASED" : "UNLINKED");
            try {
                decision.set("before", mapper.readTree(rs.getString(2)));
                decision.set("after", mapper.readTree(rs.getString(3)));
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("Invalid disposal audit snapshot", ex);
            }
        }, tx.getTransactionId());
        jdbc.update("INSERT INTO transaction_publication_snapshots(transaction_id,snapshot) VALUES (?,CAST(? AS jsonb))",
                tx.getTransactionId(), snapshot.toString());
    }

    private UserEntity actor() {
        return users.findByEmail(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
    }

    private void recordAsset(TransactionEntity tx, AssetEntity asset, UserEntity actor, String before) {
        var log = new AuditLogEntity(); log.setEntityType("ASSET"); log.setEntityId(asset.getAssetId());
        log.setTransaction(tx); log.setActor(actor); log.setAction("DISPOSAL_APPROVED");
        log.setOldData(Map.of("status", before)); log.setNewData(Map.of("status", "RETIRED")); audit.save(log);
    }

    private void recordAllocation(TransactionEntity tx, LicenseAllocationEntity allocation, UserEntity actor,
            String action, Map<String, Object> before, Map<String, Object> after) {
        var log = new AuditLogEntity(); log.setEntityType("ASSET");
        log.setEntityId(allocation.getLicense().getAssetId()); log.setTransaction(tx); log.setActor(actor);
        log.setAction(action); log.setOldData(before); log.setNewData(after); audit.save(log);
    }

    private void recordTransaction(TransactionEntity tx, UserEntity actor, String action, Map<String, Object> data) {
        var log = new AuditLogEntity(); log.setEntityType("TRANSACTION"); log.setEntityId(tx.getTransactionId());
        log.setTransaction(tx); log.setActor(actor); log.setAction(action); log.setNewData(data); audit.save(log);
    }

    private void conflict(String code) { throw new AppException(HttpStatus.CONFLICT, code, code); }
    private void bad(String code) { throw new AppException(HttpStatus.BAD_REQUEST, code, code); }

    private record LinkState(AssetRelationshipEntity relationship, LicenseAllocationEntity allocation,
                             String assignment) {}
    private record AssetState(Long id, String status, String category, LocalDate purchaseDate,
                              long version, Long assignedTo, String assignment) {}
    private record ScalarLink(Long id, Long parent, Long child, Long allocation, String type,
                              String assignment, String allocationStatus, Long device, Long user,
                              Integer seats) {}
    private record Prepared(List<AssetEntity> assets, List<AssetLine> assetLines,
                            List<OemAllocation> oemAllocations, List<PerUserLink> perUserLinks,
                            List<String> warnings, List<LinkState> links, List<ScalarLink> scalarLinks) {}
}
