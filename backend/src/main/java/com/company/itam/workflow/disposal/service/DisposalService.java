package com.company.itam.workflow.disposal.service;

import com.company.itam.asset.entity.AssetEntity;
import com.company.itam.asset.relationship.enums.RelationshipType;
import com.company.itam.asset.relationship.repository.AssetRelationshipRepository;
import com.company.itam.asset.repository.AssetRepository;
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
    private final TransactionRepository transactions;
    private final TransactionAssetRepository lines;
    private final TransactionDisposalDetailRepository details;
    private final AssetStatusRepository statuses;
    private final UserRepository users;
    private final AuditLogRepository audit;
    private final JdbcTemplate jdbc;
    private final int minimumAgeYears;

    public DisposalService(AssetRepository assets, AssetRelationshipRepository relationships,
            TransactionRepository transactions, TransactionAssetRepository lines,
            TransactionDisposalDetailRepository details, AssetStatusRepository statuses,
            UserRepository users, AuditLogRepository audit, JdbcTemplate jdbc,
            @Value("${itam.disposal.minimum-age-years:5}") int minimumAgeYears) {
        this.assets = assets; this.relationships = relationships; this.transactions = transactions;
        this.lines = lines; this.details = details; this.statuses = statuses; this.users = users;
        this.audit = audit; this.jdbc = jdbc; this.minimumAgeYears = minimumAgeYears;
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

    public SmartCheckResponse smartCheck(SmartCheckRequest request) { return prepare(request.assetIds(), false); }

    @Transactional
    public DisposalResponse create(CreateRequest request) {
        SmartCheckResponse checked = prepare(request.assetIds(), true);
        if (!checked.fingerprint().equals(request.expectedFingerprint())) conflict("DISPOSAL_CHANGED");
        UserEntity actor = actor();
        var tx = new TransactionEntity();
        tx.setTransactionCode("DI-" + UUID.randomUUID()); tx.setType(TransactionType.DISPOSAL);
        tx.setStatus(TransactionStatus.PENDING); tx.setRequester(actor); tx.setNotes(request.reason());
        transactions.saveAndFlush(tx);
        var detail = new TransactionDisposalDetailEntity(); detail.setTransaction(tx);
        detail.setReason(request.reason().trim()); detail.setDisposalDate(request.disposalDate()); details.save(detail);
        int number = 0;
        for (AssetLine selected : checked.assets()) {
            var item = new TransactionAssetEntity(); item.setTransaction(tx);
            item.setAsset(assets.getReferenceById(selected.assetId())); item.setLineNumber(++number);
            item.setNotes(selected.autoAdded() ? "AUTO_ADDED" : null); lines.save(item);
        }
        recordTransaction(tx, actor, "DISPOSAL_CREATED", Map.of("status", "PENDING", "assetIds", checked.assets().stream().map(AssetLine::assetId).toList()));
        return response(tx, detail, checked);
    }

    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    public DisposalResponse approve(Long id) {
        TransactionEntity tx = pending(id);
        UserEntity actor = actor();
        var storedLines = lines.findByTransactionTransactionId(id);
        if (storedLines.isEmpty()) conflict("EMPTY_TRANSACTION");
        var ids = storedLines.stream().map(line -> line.getAsset().getAssetId()).sorted().toList();
        ids.forEach(assetId -> assets.lockById(assetId).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND")));
        var originalIds = storedLines.stream().filter(line -> !"AUTO_ADDED".equals(line.getNotes()))
                .map(line -> line.getAsset().getAssetId()).toList();
        var currentIds = smartCheck(new SmartCheckRequest(originalIds)).assets().stream().map(AssetLine::assetId).sorted().toList();
        if (!ids.equals(currentIds)) conflict("DISPOSAL_CHANGED");
        if (hasPerUserLink(ids)) conflict("DISPOSAL_PER_USER_LINKED");
        var retired = statuses.findByCode(AssetStatus.RETIRED).orElseThrow();
        for (var item : storedLines) {
            AssetEntity asset = assets.findById(item.getAsset().getAssetId()).orElseThrow();
            if (asset.getStatus().getCode() == AssetStatus.RETIRED) conflict("DISPOSAL_ASSET_NOT_ELIGIBLE");
            String before = asset.getStatus().getCode().name();
            asset.setStatus(retired); asset.setAssignedTo(null); asset.setUpdatedBy(actor);
            recordAsset(tx, asset, actor, before);
        }
        Instant now = Instant.now(); tx.setStatus(TransactionStatus.COMPLETED); tx.setProcessedBy(actor);
        tx.setProcessedAt(now); tx.setCompletedAt(now); tx.setUpdatedAt(now);
        recordTransaction(tx, actor, "DISPOSAL_COMPLETED", Map.of("status", "COMPLETED"));
        var detail = details.findById(id).orElseThrow();
        return response(tx, detail, snapshot(storedLines, List.of()));
    }

    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    public DisposalResponse reject(Long id, RejectRequest request) {
        TransactionEntity tx = pending(id); UserEntity actor = actor(); Instant now = Instant.now();
        tx.setStatus(TransactionStatus.REJECTED); tx.setProcessedBy(actor); tx.setProcessedAt(now);
        tx.setUpdatedAt(now); tx.setRejectionReason(request.reason().trim());
        recordTransaction(tx, actor, "DISPOSAL_REJECTED", Map.of("status", "REJECTED", "reason", request.reason().trim()));
        var storedLines = lines.findByTransactionTransactionId(id);
        return response(tx, details.findById(id).orElseThrow(), snapshot(storedLines, List.of()));
    }

    private SmartCheckResponse prepare(List<Long> selectedIds, boolean lock) {
        var selected = new TreeSet<>(selectedIds);
        if (selected.isEmpty()) conflict("EMPTY_TRANSACTION");
        if (selected.size() != selectedIds.size()) conflict("DISPOSAL_DUPLICATE");
        var expanded = new TreeMap<Long, AssetLine>();
        var warnings = new ArrayList<String>();
        for (Long id : selected) {
            AssetEntity asset = load(id, lock); eligible(asset);
            expanded.put(id, line(asset, false));
            for (var relation : relationships.findByParentAssetAssetId(id)) {
                AssetEntity child = relation.getChildAsset();
                if (relation.getRelationshipType() == RelationshipType.COMPONENT_OF || isOem(relation)) {
                    if (child.getStatus().getCode() != AssetStatus.RETIRED) expanded.put(child.getAssetId(), line(child, true));
                } else if (isPerUser(relation)) {
                    warnings.add("PER_USER_LINKED:" + child.getAssetTag());
                }
            }
        }
        if (lock) {
            for (Long id : expanded.keySet()) assets.lockById(id).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
            for (Long id : expanded.keySet()) {
                if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM transaction_assets ta JOIN transactions t USING(transaction_id) WHERE ta.asset_id=? AND t.type='DISPOSAL' AND t.status='PENDING')",
                        Boolean.class, id))) conflict("DISPOSAL_ALREADY_PENDING");
            }
        }
        return new SmartCheckResponse(new ArrayList<>(expanded.values()), warnings, fingerprint(expanded.keySet(), warnings));
    }

    private SmartCheckResponse snapshot(List<TransactionAssetEntity> stored, List<String> warnings) {
        var result = stored.stream().map(item -> line(item.getAsset(), false)).toList();
        return new SmartCheckResponse(result, warnings, fingerprint(result.stream().map(AssetLine::assetId).toList(), warnings));
    }

    private TransactionEntity pending(Long id) {
        var tx = transactions.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("DISPOSAL_NOT_FOUND"));
        if (tx.getType() != TransactionType.DISPOSAL || tx.getStatus() != TransactionStatus.PENDING) conflict("TRANSACTION_ALREADY_FINISHED");
        return tx;
    }

    private AssetEntity load(Long id, boolean lock) {
        return (lock ? assets.lockById(id) : assets.findById(id)).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
    }

    private void eligible(AssetEntity asset) {
        AssetStatus status = asset.getStatus().getCode();
        boolean oldStock = status == AssetStatus.IN_STOCK && asset.getPurchaseDate() != null
                && !asset.getPurchaseDate().isAfter(LocalDate.now().minusYears(minimumAgeYears));
        if (status != AssetStatus.DAMAGED && !oldStock) conflict("DISPOSAL_ASSET_NOT_ELIGIBLE");
    }

    private boolean isOem(com.company.itam.asset.relationship.entity.AssetRelationshipEntity relation) {
        return relation.getRelationshipType() == RelationshipType.INSTALLED_ON
                && relation.getChildAsset().getLicenseDetails() != null
                && "OEM".equals(relation.getChildAsset().getLicenseDetails().getAssignmentType().getCode());
    }

    private boolean isPerUser(com.company.itam.asset.relationship.entity.AssetRelationshipEntity relation) {
        return relation.getRelationshipType() == RelationshipType.INSTALLED_ON
                && relation.getChildAsset().getLicenseDetails() != null
                && "PER_USER".equals(relation.getChildAsset().getLicenseDetails().getAssignmentType().getCode());
    }

    private boolean hasPerUserLink(Collection<Long> ids) {
        return relationships.findByParentAssetAssetIdIn(new ArrayList<>(ids)).stream().anyMatch(this::isPerUser);
    }

    private AssetLine line(AssetEntity asset, boolean autoAdded) {
        return new AssetLine(asset.getAssetId(), asset.getAssetTag(), asset.getName(),
                asset.getType().getCategory().getCode().name(), autoAdded);
    }

    private DisposalResponse response(TransactionEntity tx, TransactionDisposalDetailEntity detail, SmartCheckResponse check) {
        return new DisposalResponse(tx.getTransactionId(), tx.getTransactionCode(), tx.getStatus().name(), detail.getReason(),
                detail.getDisposalDate(), tx.getCreatedAt(), check.assets(), check.warnings(), check.fingerprint());
    }

    private UserEntity actor() {
        return users.findByEmail(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
    }

    private void recordAsset(TransactionEntity tx, AssetEntity asset, UserEntity actor, String before) {
        var log = new AuditLogEntity(); log.setEntityType("ASSET"); log.setEntityId(asset.getAssetId()); log.setTransaction(tx);
        log.setActor(actor); log.setAction("DISPOSAL_APPROVED"); log.setOldData(Map.of("status", before)); log.setNewData(Map.of("status", "RETIRED")); audit.save(log);
    }

    private void recordTransaction(TransactionEntity tx, UserEntity actor, String action, Map<String, Object> data) {
        var log = new AuditLogEntity(); log.setEntityType("TRANSACTION"); log.setEntityId(tx.getTransactionId());
        log.setTransaction(tx); log.setActor(actor); log.setAction(action); log.setNewData(data); audit.save(log);
    }

    private String fingerprint(Collection<Long> ids, Collection<String> warnings) {
        try {
            String source = ids.stream().sorted().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("") + "|" + String.join(",", warnings);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private void conflict(String code) { throw new AppException(HttpStatus.CONFLICT, code, code); }
    private void bad(String code) { throw new AppException(HttpStatus.BAD_REQUEST, code, code); }
}
