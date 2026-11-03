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
import com.company.itam.workflow.core.dto.WorkflowProgressModels.*;
import com.company.itam.workflow.core.service.WorkflowProgressService;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
public class DisposalService {
    private final com.company.itam.workflow.core.service.WorkflowAccessService workflowAccess;
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
    private final WorkflowProgressService progress;

    public DisposalService(AssetRepository assets, AssetRelationshipRepository relationships,
            LicenseAllocationRepository allocations, TransactionRepository transactions,
            TransactionAssetRepository lines, TransactionDisposalDetailRepository details,
            AssetStatusRepository statuses, UserRepository users, AuditLogRepository audit,
            JdbcTemplate jdbc, ObjectMapper mapper, com.company.itam.workflow.core.service.WorkflowAccessService workflowAccess,
            @Value("${itam.disposal.minimum-age-years:5}") int minimumAgeYears) {
        this.assets = assets; this.relationships = relationships; this.allocations = allocations;
        this.transactions = transactions; this.lines = lines; this.details = details;
        this.statuses = statuses; this.users = users; this.audit = audit;
        this.jdbc = jdbc; this.mapper = mapper; this.minimumAgeYears = minimumAgeYears;
        this.workflowAccess = workflowAccess;
        this.progress = new WorkflowProgressService(jdbc, mapper);
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
            try {
                var frozen=mapper.readTree(snapshot).deepCopy();
                if(frozen.has("proposalAssets")) {
                    ((ObjectNode)frozen).set("assets",frozen.get("proposalAssets"));
                    ((ObjectNode)frozen).set("oemAllocations",frozen.get("proposalOemAllocations"));
                    ((ObjectNode)frozen).set("perUserLinks",frozen.get("proposalPerUserLinks"));
                }
                return mapper.treeToValue(frozen,DisposalResponse.class);
            }
            catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("Invalid disposal publication snapshot", ex);
            }
        }
        var detail = details.findById(id).orElseThrow(() -> new ResourceNotFoundException("DISPOSAL_NOT_FOUND"));
        return response(tx, detail, prepareStored(lines.findByTransactionTransactionId(id), false));
    }

    @Transactional
    @PreAuthorize("hasAuthority('IT_STAFF')")
    public DisposalResponse create(CreateRequest request) {
        return create(request, TransactionStatus.PENDING);
    }

    @Transactional
    @PreAuthorize("hasAuthority('IT_STAFF')")
    public DisposalResponse createDraft(CreateRequest request) {
        return create(request, TransactionStatus.DRAFT);
    }

    private DisposalResponse create(CreateRequest request, TransactionStatus status) {
        Prepared checked = prepareSelection(request.assetIds(), true);
        if (!fingerprint(null, null, checked).equals(request.expectedFingerprint())) conflict("DISPOSAL_CHANGED");
        UserEntity actor = actor();
        var tx = new TransactionEntity();
        tx.setTransactionCode("DI-" + UUID.randomUUID()); tx.setType(TransactionType.DISPOSAL);
        tx.setStatus(status); tx.setRequester(actor); tx.setNotes(request.reason());
        transactions.saveAndFlush(tx);
        var detail = new TransactionDisposalDetailEntity(); detail.setTransaction(tx);
        detail.setReason(request.reason().trim()); detail.setDisposalDate(request.disposalDate()); details.save(detail);
        int number = 0;
        for (AssetLine selected : checked.assetLines()) {
            var item = new TransactionAssetEntity(); item.setTransaction(tx);
            item.setAsset(assets.getReferenceById(selected.assetId())); item.setLineNumber(++number);
            item.setNotes(selected.autoAdded() ? "AUTO_ADDED" : null); lines.save(item);
        }
        lines.flush();
        if(status==TransactionStatus.PENDING) progress.save(tx.getTransactionId(),groups(tx));
        recordTransaction(tx, actor, "DISPOSAL_CREATED", Map.of("status", status.name(),
                "assetIds", checked.assetLines().stream().map(AssetLine::assetId).toList()));
        return response(tx, detail, checked);
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
    public DisposalResponse resolvePerUser(Long id, PerUserRequest request) {
        TransactionEntity tx = editable(id);
        var detail = details.findById(id).orElseThrow(() -> new ResourceNotFoundException("DISPOSAL_NOT_FOUND"));
        var stored = lines.findByTransactionTransactionId(id);
        Prepared current = prepareStored(stored, true);
        requireAvailable(current, id);
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
        tx.setContentVersion(tx.getContentVersion()+1);
        recordTransaction(tx, actor, "DISPOSAL_PER_USER_RESOLVED",
                Map.of("allocationIds", new ArrayList<>(decisions.keySet())));
        return response(tx, detail, prepareStored(stored, false));
    }

    @Transactional
    public DisposalResponse edit(Long id, CreateRequest request) {
        var tx = editable(id);
        var detail = details.findById(id).orElseThrow();
        var before = prepareStored(lines.findByTransactionTransactionId(id),true);
        if (!fingerprint(tx,detail,before).equals(request.expectedTransactionFingerprint())) conflict("DISPOSAL_CHANGED");
        Prepared checked = prepareSelection(request.assetIds(), true);
        if (!fingerprint(null, null, checked).equals(request.expectedFingerprint())) conflict("DISPOSAL_CHANGED");
        detail.setReason(request.reason().trim()); detail.setDisposalDate(request.disposalDate());
        tx.setNotes(request.reason().trim()); tx.setUpdatedAt(Instant.now());
        tx.setContentVersion(tx.getContentVersion()+1);
        lines.deleteAll(lines.findByTransactionTransactionId(id)); lines.flush();
        int number = 0;
        for (AssetLine selected : checked.assetLines()) {
            var item = new TransactionAssetEntity(); item.setTransaction(tx);
            item.setAsset(assets.getReferenceById(selected.assetId())); item.setLineNumber(++number);
            item.setNotes(selected.autoAdded() ? "AUTO_ADDED" : null); lines.save(item);
        }
        recordTransaction(tx, actor(), "EDIT_DRAFT", Map.of("status","DRAFT"));
        return response(tx,detail,checked);
    }

    @Transactional
    public DisposalResponse transition(Long id, String action, String expectedFingerprint) {
        var tx = transactions.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("DISPOSAL_NOT_FOUND"));
        if (tx.getType() != TransactionType.DISPOSAL) throw new ResourceNotFoundException("DISPOSAL_NOT_FOUND");
        var detail = details.findById(id).orElseThrow();
        var prepared = prepareStored(lines.findByTransactionTransactionId(id), true);
        if (!fingerprint(tx,detail,prepared).equals(expectedFingerprint)) conflict("DISPOSAL_CHANGED");
        workflowAccess.requireCreator(tx,"cancel".equals(action) && tx.getStatus() == TransactionStatus.PENDING);
        if (progress.read(id).stream().anyMatch(g->Set.of("APPROVED","COMPLETED").contains(g.status()))) conflict("DOCUMENT_LOCKED");
        TransactionStatus next;
        if ("withdraw".equals(action) && tx.getStatus() == TransactionStatus.PENDING) next = TransactionStatus.DRAFT;
        else if ("submit".equals(action) && tx.getStatus() == TransactionStatus.DRAFT) next = TransactionStatus.PENDING;
        else if ("cancel".equals(action) && (tx.getStatus() == TransactionStatus.DRAFT || tx.getStatus() == TransactionStatus.PENDING)) next = TransactionStatus.CANCELLED;
        else { conflict("DOCUMENT_LOCKED"); return null; }
        if (next == TransactionStatus.PENDING) {
            validateStoredExpansion(lines.findByTransactionTransactionId(id), prepared);
            requireAvailable(prepared, id);
        }
        tx.setStatus(next); tx.setUpdatedAt(Instant.now());
        if(next==TransactionStatus.PENDING) { jdbc.update("DELETE FROM workflow_progress WHERE transaction_id=?",id); progress.save(id,groups(tx)); }
        else if(next==TransactionStatus.DRAFT) jdbc.update("DELETE FROM workflow_progress WHERE transaction_id=?",id);
        else if(next==TransactionStatus.CANCELLED) progress.save(id,groups(tx).stream().map(g->new Group(g.groupId(),"CANCELLED",g.assetIds(),g.allocationIds(),g.reason(),g.approvedAt(),g.dueDate())).toList());
        tx.setContentVersion(tx.getContentVersion()+1);
        recordTransaction(tx,actor(),action.toUpperCase(Locale.ROOT),Map.of("status",next.name()));
        return response(tx,detail,prepared);
    }

    private TransactionEntity editable(Long id) {
        var tx = transactions.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("DISPOSAL_NOT_FOUND"));
        if (tx.getType() != TransactionType.DISPOSAL || tx.getStatus() != TransactionStatus.DRAFT) conflict("DOCUMENT_LOCKED");
        workflowAccess.requireCreator(tx,false);
        return tx;
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
        requireAvailable(current, id);
        if (!current.perUserLinks().isEmpty()) conflict("DISPOSAL_PER_USER_LINKED");

        return decide(id,new Action(tx.getContentVersion(),groups(tx).stream().filter(g->"PENDING".equals(g.status())).map(Group::groupId).toList(),null),"APPROVED");
    }

    public Map<String,Object> progress(Long id) {
        var tx=disposal(id);
        return Map.of("contentVersion",tx.getContentVersion(),"groups",groups(tx));
    }
    private List<Group> groups(TransactionEntity tx) {
        var saved=progress.read(tx.getTransactionId());
        if(!saved.isEmpty()) return saved;
        var ids=lines.findByTransactionTransactionId(tx.getTransactionId()).stream().map(l->l.getAsset().getAssetId()).collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        var sets=new ArrayList<TreeSet<Long>>();
        for(var id:ids) sets.add(new TreeSet<>(Set.of(id)));
        for(var link:scalarLinks(ids)) if(ids.contains(link.child()) && RelationshipType.COMPONENT_OF.name().equals(link.type())) {
            var a=sets.stream().filter(v->v.contains(link.parent())).findFirst().orElseThrow();
            var b=sets.stream().filter(v->v.contains(link.child())).findFirst().orElseThrow();
            if(a!=b){a.addAll(b);sets.remove(b);}
        }
        return sets.stream().map(v->new Group("asset-"+v.first(),Set.of(TransactionStatus.COMPLETED,TransactionStatus.REJECTED,TransactionStatus.CANCELLED,TransactionStatus.APPROVED).contains(tx.getStatus())?tx.getStatus().name():"PENDING",List.copyOf(v),scalarLinks(v).stream().filter(l->LicenseCodes.OEM.equals(l.assignment())).map(ScalarLink::allocation).toList(),null,tx.getProcessedAt(),tx.getProcessedAt()==null?null:DisposalDeadlineService.addWorkingDays(tx.getProcessedAt().atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate(),7))).toList();
    }
    @Transactional
    public DisposalResponse decide(Long id,Action request,String status) {
        var tx=transactions.findForUpdate(id).orElseThrow();
        if(tx.getType()!=TransactionType.DISPOSAL || !Set.of(TransactionStatus.PENDING,TransactionStatus.APPROVED,TransactionStatus.PARTIAL).contains(tx.getStatus())) conflict("DOCUMENT_LOCKED");
        if(!"CANCELLED".equals(status) && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ADMIN"))) throw new org.springframework.security.access.AccessDeniedException("ADMIN required");
        progress.requireVersion(tx,request.expectedVersion());
        if(!"APPROVED".equals(status) && (request.reason()==null||request.reason().isBlank())) bad("TRANSACTION_REQUEST_INVALID");
        var all=groups(tx);
        var selected=all.stream().filter(g->request.groupIds().contains(g.groupId())).toList();
        if(selected.isEmpty()||selected.size()!=new HashSet<>(request.groupIds()).size()||selected.size()!=request.groupIds().size()||selected.stream().anyMatch(g->!"PENDING".equals(g.status())&&!("CANCELLED".equals(status)&&"APPROVED".equals(g.status())))) conflict("WORKFLOW_SELECTION_INVALID");
        if("CANCELLED".equals(status) && selected.stream().anyMatch(g->"PENDING".equals(g.status()))) workflowAccess.requireCreator(tx,true);
        var selectedIds=selected.stream().flatMap(g->g.assetIds().stream()).collect(java.util.stream.Collectors.toSet());
        var checked=prepareStored(lines.findByTransactionTransactionId(id).stream().filter(l->selectedIds.contains(l.getAsset().getAssetId())).toList(),true);
        if("APPROVED".equals(status)) {
            validateStoredExpansion(lines.findByTransactionTransactionId(id).stream().filter(l->selectedIds.contains(l.getAsset().getAssetId())).toList(),checked);
            requireAvailable(checked,id);
            if(!checked.perUserLinks().isEmpty()) conflict("DISPOSAL_PER_USER_LINKED");
            for(var line:checked.assetLines()) if(!line.autoAdded()) eligible(assetState(line.assetId()));
        }
        var next=all.stream().map(g->selected.contains(g)?new Group(g.groupId(),status,g.assetIds(),g.allocationIds(),request.reason(),"APPROVED".equals(status)?Instant.now():g.approvedAt(),"APPROVED".equals(status)?workingDeadline():g.dueDate()):g).toList();
        progress.save(id,next);progress.advance(tx);updateState(tx,next);
        recordTransaction(tx,actor(),"DISPOSAL_"+status,Map.of("groupIds",request.groupIds(),"reason",Objects.toString(request.reason(),"")));
        transactions.flush();
        if(tx.getStatus()==TransactionStatus.COMPLETED) {
            savePublicationSnapshot(tx,response(tx,details.findById(id).orElseThrow(),prepareStored(lines.findByTransactionTransactionId(id),false)));
        }
        return get(id);
    }
    private LocalDate workingDeadline(){return DisposalDeadlineService.addWorkingDays(LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")),7);}
    private void updateState(TransactionEntity tx,List<Group> groups){
        boolean pending=groups.stream().anyMatch(g->"PENDING".equals(g.status()));
        boolean approved=groups.stream().anyMatch(g->"APPROVED".equals(g.status()));
        boolean done=groups.stream().anyMatch(g->"COMPLETED".equals(g.status()));
        tx.setStatus(done?(pending||approved?TransactionStatus.PARTIAL:TransactionStatus.COMPLETED):approved?TransactionStatus.APPROVED:pending?TransactionStatus.PENDING:groups.stream().allMatch(g->"REJECTED".equals(g.status()))?TransactionStatus.REJECTED:TransactionStatus.CANCELLED);
        boolean processed=Set.of(TransactionStatus.APPROVED,TransactionStatus.PARTIAL,TransactionStatus.COMPLETED,TransactionStatus.REJECTED).contains(tx.getStatus());
        tx.setProcessedBy(processed?actor():null);tx.setProcessedAt(processed?Instant.now():null);tx.setCompletedAt(tx.getStatus()==TransactionStatus.COMPLETED?Instant.now():null);
        tx.setRejectionReason(tx.getStatus()==TransactionStatus.REJECTED?groups.stream().map(Group::reason).filter(Objects::nonNull).findFirst().orElse("Rejected"):null);
    }

    @Transactional
    public DisposalResponse execute(Long id, ExecuteRequest request) {
        var tx = transactions.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("DISPOSAL_NOT_FOUND"));
        if (tx.getType()!=TransactionType.DISPOSAL || !Set.of(TransactionStatus.APPROVED,TransactionStatus.PARTIAL,TransactionStatus.PENDING).contains(tx.getStatus())) conflict("DOCUMENT_LOCKED");
        progress.requireVersion(tx,request.expectedVersion());
        if (request.executionDate()==null || request.executionDate().isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")))) bad("TRANSACTION_REQUEST_INVALID");
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM documents d JOIN transaction_document_links l USING(document_id) WHERE l.transaction_id=? AND d.document_id=? AND d.document_type IN ('DISPOSAL_REPORT','OTHER') AND d.publication_version IS NULL)", Boolean.class,id,request.documentId()))) bad("TRANSACTION_REQUEST_INVALID");
        var detail = details.findById(id).orElseThrow();
        var stored = lines.findByTransactionTransactionId(id);
        var all=groups(tx);
        var selected=progress.requireSelection(all,request.groupIds(),"APPROVED");
        var selectedIds=selected.stream().flatMap(g->g.assetIds().stream()).collect(java.util.stream.Collectors.toSet());
        var current = prepareStored(stored.stream().filter(l->selectedIds.contains(l.getAsset().getAssetId())).toList(),true);
        validateStoredExpansion(stored.stream().filter(l->selectedIds.contains(l.getAsset().getAssetId())).toList(),current);
        requireAvailable(current,id);
        if (!current.perUserLinks().isEmpty()) conflict("DISPOSAL_PER_USER_LINKED");
        tx.setContentVersion(tx.getContentVersion()+1);
        recordTransaction(tx,actor(),"DISPOSAL_EXECUTION_EVIDENCE",Map.of("documentId",request.documentId(),"executionDate",request.executionDate().toString(),"groupIds",request.groupIds()));
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

        Instant now = Instant.now();
        var next=all.stream().map(g->selected.contains(g)?new Group(g.groupId(),"COMPLETED",g.assetIds(),g.allocationIds(),g.reason(),g.approvedAt(),g.dueDate(),request.executionDate(),request.documentId()):g).toList();
        progress.save(id,next);updateState(tx,next);tx.setUpdatedAt(now);
        recordTransaction(tx, actor, "DISPOSAL_COMPLETED", Map.of("status", "COMPLETED"));
        transactions.flush(); assets.flush(); audit.flush();
        DisposalResponse result = response(tx, detail, prepareStored(stored, false));
        if(tx.getStatus()==TransactionStatus.COMPLETED) savePublicationSnapshot(tx, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    public DisposalResponse reject(Long id, RejectRequest request) {
        var tx=pending(id);progress.requireVersion(tx,request.expectedVersion());
        return decide(id,new Action(request.expectedVersion(),groups(tx).stream().filter(g->"PENDING".equals(g.status())).map(Group::groupId).toList(),request.reason()),"REJECTED");
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
        requireAvailable(prepared, -1L);
        return prepared;
    }

    private void requireAvailable(Prepared prepared, Long transactionId) {
        for (AssetLine line : prepared.assetLines()) {
            progress.requireAssetAvailable(line.assetId(),transactionId);
            if (assets.hasPendingRecovery(line.assetId())) conflict("RECOVERY_ASSET_RESERVED");
            if (assets.hasPendingHandover(line.assetId())) conflict("HANDOVER_ASSET_RESERVED");
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM transaction_assets ta JOIN transactions t USING(transaction_id)
                    WHERE ta.asset_id=? AND t.type='DISPOSAL' AND t.status IN ('PENDING','APPROVED','PARTIAL') AND t.transaction_id<>?
                    AND (NOT EXISTS(SELECT 1 FROM workflow_progress wp WHERE wp.transaction_id=t.transaction_id)
                         OR EXISTS(SELECT 1 FROM workflow_progress wp, jsonb_array_elements(wp.groups) g
                            WHERE wp.transaction_id=t.transaction_id AND g->>'status' IN ('PENDING','APPROVED')
                              AND (g->'assetIds') @> to_jsonb(ARRAY[ta.asset_id]))))
                    """, Boolean.class, line.assetId(), transactionId))) conflict("DISPOSAL_ALREADY_PENDING");
        }
    }

    private SortedMap<Long, Boolean> selectionTargets(Collection<Long> selectedIds, List<ScalarLink> links) {
        if(links.stream().anyMatch(link->RelationshipType.COMPONENT_OF.name().equals(link.type())
                && selectedIds.contains(link.child()) && !selectedIds.contains(link.parent()))) conflict("WORKFLOW_GROUP_REQUIRED");
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
                prepared.oemAllocations(), prepared.perUserLinks(), prepared.warnings(), fingerprint(tx, detail, prepared), tx.getRequester().getUserId(), tx.getRejectionReason());
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
        var completed=progress.read(tx.getTransactionId()).stream().filter(g->"COMPLETED".equals(g.status())).flatMap(g->g.assetIds().stream()).collect(java.util.stream.Collectors.toSet());
        if(!progress.read(tx.getTransactionId()).isEmpty()) {
            snapshot.set("proposalAssets",snapshot.get("assets"));
            snapshot.set("proposalOemAllocations",snapshot.get("oemAllocations"));
            snapshot.set("proposalPerUserLinks",snapshot.get("perUserLinks"));
            snapshot.set("assets",mapper.valueToTree(response.assets().stream().filter(a->completed.contains(a.assetId())).toList()));
            snapshot.set("oemAllocations",mapper.valueToTree(response.oemAllocations().stream().filter(a->completed.contains(a.deviceId())).toList()));
            snapshot.set("perUserLinks",mapper.valueToTree(response.perUserLinks().stream().filter(a->completed.contains(a.deviceId())).toList()));
            snapshot.set("groups",mapper.valueToTree(progress.read(tx.getTransactionId())));
        }
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
        return users.findByPrincipal(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
    }

    private void recordAsset(TransactionEntity tx, AssetEntity asset, UserEntity actor, String before) {
        var log = new AuditLogEntity(); log.setEntityType("ASSET"); log.setEntityId(asset.getAssetId());
        log.setTransaction(tx); log.setActor(actor); log.setAction("DISPOSAL_EXECUTED");
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
